package com.harshith.resumematcher.analysis.engine;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** The free, local matcher: dictionary skills + required/preferred detection + keyword overlap. */
@Component
public class RuleBasedMatchAnalyzer implements MatchAnalyzer {

    private final SkillDictionary dictionary;
    private final RequirementClassifier classifier;
    private final KeywordExtractor keywords;

    public RuleBasedMatchAnalyzer(SkillDictionary dictionary, RequirementClassifier classifier,
                                  KeywordExtractor keywords) {
        this.dictionary = dictionary;
        this.classifier = classifier;
        this.keywords = keywords;
    }

    @Override
    public String name() {
        return "rule-based-v1";
    }

    @Override
    public MatchResult analyze(String resumeText, String jobDescription) {
        List<JobSkill> jobSkills = classifier.classify(jobDescription);
        List<Skill> resumeFound = dictionary.findIn(resumeText);
        Set<Skill> direct = new HashSet<>(resumeFound);
        // Resume side only: "Spring Data JPA" on a resume also counts as JPA, Hibernate and Spring Framework.
        Map<Skill, Skill> implied = dictionary.impliedBy(resumeFound);

        List<MatchedSkill> matched = new ArrayList<>();
        List<MissingSkill> missingRequired = new ArrayList<>();
        List<MissingSkill> missingPreferred = new ArrayList<>();
        int requiredTotal = 0;
        int preferredTotal = 0;

        for (JobSkill js : jobSkills) {
            Skill skill = js.skill();
            boolean required = js.importance() == Importance.REQUIRED;
            if (required) {
                requiredTotal++;
            } else {
                preferredTotal++;
            }
            // A group like "AWS (EC2, S3, RDS)" counts once and is matched by the parent or any item.
            List<SubSkill> subs = js.subSkills().stream()
                    .map(s -> direct.contains(s) ? new SubSkill(s.name(), s.category(), true, null)
                            : implied.containsKey(s) ? new SubSkill(s.name(), s.category(), true, implied.get(s).name())
                            : new SubSkill(s.name(), s.category(), false, null))
                    .toList();

            // A direct mention (of the skill or a group item) beats an implied one.
            boolean foundDirectly = direct.contains(skill)
                    || subs.stream().anyMatch(s -> s.found() && s.impliedBy() == null);
            String impliedBy = foundDirectly ? null
                    : implied.containsKey(skill) ? implied.get(skill).name()
                    : subs.stream().filter(SubSkill::found).map(SubSkill::impliedBy).findFirst().orElse(null);

            if (foundDirectly || impliedBy != null) {
                matched.add(new MatchedSkill(skill.name(), skill.category(), js.importance(), impliedBy, subs));
            } else {
                (required ? missingRequired : missingPreferred)
                        .add(new MissingSkill(skill.name(), skill.category(), js.subSkills()));
            }
        }
        // Required first; within each group, the order the job lists them. (List.sort is stable.)
        matched.sort(Comparator.comparing(MatchedSkill::importance));

        KeywordExtractor.KeywordResult kw = keywords.compare(jobDescription, resumeText);
        int requiredMatched = requiredTotal - missingRequired.size();
        int preferredMatched = preferredTotal - missingPreferred.size();

        ScoreCalculator.Result score = ScoreCalculator.calculate(
                requiredMatched, requiredTotal,
                preferredMatched, preferredTotal,
                kw.matched().size(), kw.total());

        String summary = summary(score.score(), requiredMatched, requiredTotal,
                preferredMatched, preferredTotal, missingRequired);

        return new MatchResult(score.score(), score.breakdown(), List.copyOf(matched),
                List.copyOf(missingRequired), List.copyOf(missingPreferred),
                kw.matched(), kw.missing(), summary);
    }

    static String summary(int score, int reqMatched, int reqTotal, int prefMatched, int prefTotal,
                          List<MissingSkill> missingRequired) {
        String band = score >= 80 ? "Strong match" : score >= 60 ? "Good match" : score >= 40 ? "Partial match" : "Weak match";
        StringBuilder sb = new StringBuilder(band).append(" (").append(score).append("/100). ");

        if (reqTotal > 0) {
            sb.append("Your resume shows ").append(reqMatched).append(" of ").append(reqTotal).append(" required skills");
            if (prefTotal > 0) {
                sb.append(" and ").append(prefMatched).append(" of ").append(prefTotal).append(" preferred skills");
            }
            sb.append('.');
        } else if (prefTotal > 0) {
            sb.append("The job lists no clearly required skills; your resume shows ")
                    .append(prefMatched).append(" of ").append(prefTotal).append(" preferred skills.");
        } else {
            sb.append("No known skills were found in this job description, so the score is based on keywords only.");
        }

        if (!missingRequired.isEmpty()) {
            List<String> names = missingRequired.stream().map(MissingSkill::name).toList();
            sb.append(" Missing required skills: ").append(Text.joinNames(names, 3)).append('.');
        } else if (reqTotal > 0) {
            sb.append(" You cover every required skill.");
        }
        return sb.toString();
    }
}
