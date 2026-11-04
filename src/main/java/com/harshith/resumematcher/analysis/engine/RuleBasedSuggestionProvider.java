package com.harshith.resumematcher.analysis.engine;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Tips built from the match result. Gaps are always phrased as "if you have...", never as a claim,
 * so the user is never nudged to add experience they don't have.
 */
@Component
public class RuleBasedSuggestionProvider implements SuggestionProvider {

    private static final int MAX_SINGLE_SKILL_TIPS = 2;
    private static final int FEW_NUMBERS = 3;
    private static final Pattern NUMBER = Pattern.compile("\\d+");

    @Override
    public String name() {
        return "rule-based-v1";
    }

    @Override
    public List<String> suggest(String resumeText, String jobDescription, MatchResult result) {
        List<String> tips = new ArrayList<>();
        List<MissingSkill> missingRequiredSkills = result.missingRequiredSkills();
        List<String> missingRequired = missingRequiredSkills.stream().map(MissingSkill::name).toList();
        List<String> missingPreferred = result.missingPreferredSkills().stream().map(MissingSkill::name).toList();

        // 1. Missing required skills: the biggest lever on the score.
        for (MissingSkill skill : missingRequiredSkills.subList(0, Math.min(MAX_SINGLE_SKILL_TIPS, missingRequired.size()))) {
            // "used Docker" reads well for tools; "worked with microservices" for practices.
            String verb = skill.category().equals("practice") ? "worked with " : "used ";
            // For a group the label lists its items: "If you've used AWS (Amazon EC2, Amazon S3 or Amazon RDS)".
            tips.add("If you've " + verb + skill.label() + ", add it to your skills section and mention where"
                    + " you applied it in a project or role. If you haven't, leave it out.");
        }
        if (missingRequired.size() > MAX_SINGLE_SKILL_TIPS) {
            List<String> rest = missingRequired.subList(MAX_SINGLE_SKILL_TIPS, missingRequired.size());
            tips.add(rest.size() == 1
                    ? "The job also requires " + rest.getFirst() + ". Add it only if you've worked with it;"
                            + " if not, consider learning the basics before applying."
                    : "The job also requires " + Text.joinNames(rest, 4) + ". Add only the ones you've"
                            + " worked with; for the others, consider learning the basics before applying.");
        }

        // 2. Missing preferred skills.
        if (!missingPreferred.isEmpty()) {
            tips.add("The job lists " + Text.joinNames(missingPreferred, 3) + " as nice to have. If you have"
                    + " experience with any of them, mention it; it's fine to leave out ones you haven't used.");
        }

        // 3. Wording from the posting.
        if (!result.missingKeywords().isEmpty()) {
            List<String> quoted = result.missingKeywords().stream().limit(4).map(k -> "\"" + k + "\"").toList();
            tips.add("The posting uses words like " + Text.joinNames(quoted, 4) + ". Where they truthfully"
                    + " describe your work, use the same wording.");
        }

        // 4. Skills already there: make them count. Implied ones aren't actually named, so leave them out.
        List<String> matchedRequired = result.matchedSkills().stream()
                .filter(s -> s.importance() == Importance.REQUIRED && s.impliedBy() == null)
                .map(MatchedSkill::name).toList();
        if (!matchedRequired.isEmpty()) {
            tips.add("Your resume already mentions " + Text.joinNames(matchedRequired, 3) + ". Back them up"
                    + " with a bullet point that shows what you built with them and the result.");
        }

        // 5. Few numbers in the resume.
        if (NUMBER.matcher(resumeText).results().count() < FEW_NUMBERS) {
            tips.add("Add measurable results where you can (users, response times, team size, time saved),"
                    + " using figures that reflect your real work.");
        }

        // Fallbacks, so there are always at least 3 tips.
        if (missingRequired.isEmpty() && missingPreferred.isEmpty() && !result.matchedSkills().isEmpty()) {
            tips.add("You cover every skill this job lists. Use your summary at the top to connect your"
                    + " experience to the kind of work the posting describes.");
        }
        tips.add("Use the same skill names as the posting (for example \"PostgreSQL\" rather than an"
                + " abbreviation) so applicant tracking systems recognise them.");
        tips.add("Put the experience most relevant to this job near the top of your resume.");
        tips.add("Tailor the summary at the top of your resume to this specific role.");

        return List.copyOf(tips.subList(0, Math.min(MAX_TIPS, tips.size())));
    }
}
