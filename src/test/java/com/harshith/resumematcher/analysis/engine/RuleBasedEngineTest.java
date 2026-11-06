package com.harshith.resumematcher.analysis.engine;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

/** The analyzer and suggestion provider working together on made-up text. */
class RuleBasedEngineTest {

    private static final SkillDictionary DICT = new SkillDictionary();
    private static final RuleBasedMatchAnalyzer ANALYZER = new RuleBasedMatchAnalyzer(
            DICT, new RequirementClassifier(DICT), new KeywordExtractor(DICT));
    private static final RuleBasedSuggestionProvider TIPS = new RuleBasedSuggestionProvider();

    private static final String RESUME = """
            Alex Placeholder - Backend developer
            Built payment services with Java, Spring Boot and PostgreSQL. Wrote unit tests with JUnit.
            """;
    private static final String JOB = """
            Requirements:
            - Java and Spring Boot for payment services
            - Docker
            Nice to have:
            - Apache Kafka
            """;

    @Test
    void findsMatchedAndMissingSkills() {
        MatchResult r = ANALYZER.analyze(RESUME, JOB);
        assertThat(r.matchedSkills()).extracting(MatchedSkill::name).containsExactly("Java", "Spring Boot");
        assertThat(r.missingRequiredSkills()).extracting(MissingSkill::name).containsExactly("Docker");
        assertThat(r.missingPreferredSkills()).extracting(MissingSkill::name).containsExactly("Apache Kafka");
        assertThat(r.matchedKeywords()).contains("payment");
        assertThat(r.matchScore()).isBetween(0, 100);
        assertThat(r.summary()).contains("2 of 3 required skills").contains("Docker");
    }

    @Test
    void tipsAreConditionalAndThreeToFive() {
        MatchResult r = ANALYZER.analyze(RESUME, JOB);
        List<String> tips = TIPS.suggest(RESUME, JOB, r);
        assertThat(tips).hasSizeBetween(3, 5);
        assertThat(tips.getFirst()).startsWith("If you've used Docker, add it to your skills section");
        assertThat(tips).anyMatch(t -> t.contains("Apache Kafka") && t.contains("If you have any of them, mention it"));
    }

    // ---- skill groups ----

    private static final String AWS_JOB = """
            Requirements:
            - Java
            - AWS (EC2, S3, RDS)
            """;

    @Test
    void groupIsMatchedByAnyItemAndCountsOnce() {
        MatchResult r = ANALYZER.analyze("Java developer. Stored files in S3.", AWS_JOB);
        assertThat(r.missingRequiredSkills()).isEmpty();
        assertThat(r.matchedSkills()).extracting(MatchedSkill::name).containsExactly("Java", "AWS");
        assertThat(r.matchedSkills().get(1).subSkills()).containsExactly(
                new SubSkill("Amazon EC2", "cloud", false, null),
                new SubSkill("Amazon S3", "cloud", true, null),
                new SubSkill("Amazon RDS", "cloud", false, null));
        ScoreComponent required = r.scoreBreakdown().components().getFirst();
        assertThat(required.matched()).isEqualTo(2);
        assertThat(required.total()).isEqualTo(2); // Java + one AWS group, not 5 skills
    }

    @Test
    void groupIsMatchedByTheParentAlone() {
        MatchResult r = ANALYZER.analyze("Java developer on AWS.", AWS_JOB);
        assertThat(r.missingRequiredSkills()).isEmpty();
        assertThat(r.matchedSkills().get(1).subSkills()).noneMatch(SubSkill::found);
    }

    @Test
    void missingGroupShowsItsItemsAndTheTipNamesThem() {
        MatchResult r = ANALYZER.analyze("Java developer.", AWS_JOB);
        assertThat(r.missingRequiredSkills()).hasSize(1);
        MissingSkill aws = r.missingRequiredSkills().getFirst();
        assertThat(aws.subSkills()).extracting(Skill::name).containsExactly("Amazon EC2", "Amazon S3", "Amazon RDS");
        assertThat(TIPS.suggest("Java developer.", AWS_JOB, r).getFirst())
                .startsWith("If you've used AWS (Amazon EC2, Amazon S3 or Amazon RDS), add it");
    }

    // ---- implied skills (resume side only) ----

    @Test
    void impliedSkillsCountAsMatchedAndAreLabelled() {
        MatchResult r = ANALYZER.analyze("Built repositories with Spring Data JPA.",
                "Requirements: JPA, Hibernate and Spring Data JPA.");
        assertThat(r.missingRequiredSkills()).isEmpty();
        assertThat(r.matchedSkills()).extracting(MatchedSkill::name, MatchedSkill::impliedBy).containsExactly(
                tuple("JPA", "Spring Data JPA"),
                tuple("Hibernate", "Spring Data JPA"),
                tuple("Spring Data JPA", null));
        assertThat(r.scoreBreakdown().components().getFirst().matched()).isEqualTo(3);
    }

    @Test
    void directMentionBeatsImplication() {
        MatchResult r = ANALYZER.analyze("JPA and Spring Data JPA", "Requirements: JPA");
        assertThat(r.matchedSkills().getFirst().impliedBy()).isNull();
    }

    @Test
    void implicationsAreNotUsedOnTheJobSide() {
        // The job names Spring Data JPA only, so JPA and Hibernate are not added as requirements.
        MatchResult r = ANALYZER.analyze("Java developer", "Requirements: Spring Data JPA");
        assertThat(r.missingRequiredSkills()).extracting(MissingSkill::name).containsExactly("Spring Data JPA");
        assertThat(r.scoreBreakdown().components().getFirst().total()).isEqualTo(1);
    }

    @Test
    void groupCanBeMatchedThroughAnImpliedItem() {
        MatchResult r = ANALYZER.analyze("Services built with Spring Boot",
                "Requirements: Java (Spring Framework)");
        MatchedSkill group = r.matchedSkills().getFirst();
        assertThat(group.name()).isEqualTo("Java");
        assertThat(group.impliedBy()).isEqualTo("Spring Boot");
        assertThat(group.subSkills()).containsExactly(new SubSkill("Spring Framework", "framework", true, "Spring Boot"));
    }

    @Test
    void groupCanBeMatchedThroughAnImpliedParent() {
        MatchResult r = ANALYZER.analyze("Ran workloads on OpenShift", "Requirements: Kubernetes (Helm)");
        assertThat(r.matchedSkills()).extracting(MatchedSkill::name, MatchedSkill::impliedBy)
                .containsExactly(tuple("Kubernetes", "OpenShift"));
    }

    @Test
    void alreadyMentionsTipOnlyListsSkillsTheResumeNames() {
        String resume = "Built repositories with Spring Data JPA.";
        String job = "Requirements: JPA and Spring Data JPA.";
        List<String> tips = TIPS.suggest(resume, job, ANALYZER.analyze(resume, job));
        assertThat(tips).anyMatch(t -> t.startsWith("Your resume already mentions Spring Data JPA."));
        assertThat(tips).noneMatch(t -> t.contains("already mentions JPA"));
    }

    @Test
    void namesInThePostingAreNotKeywordsButAcronymsAndRealWordsAre() {
        KeywordExtractor kw = new KeywordExtractor(DICT);
        List<String> keywords = kw.keywords("""
                Business Analyst - Example Health Partners
                Lead workshops with clinics on patient scheduling. Build HIPAA-compliant reports for business users.
                """);
        assertThat(keywords).doesNotContain("example", "health", "partners", "lead", "analyst");
        assertThat(keywords).contains("business", "workshops", "clinics", "patient", "scheduling", "hipaa");
    }

    @Test
    void softSkillsAndCertificationsGetTheirOwnGroupedTips() {
        String job = """
                Requirements:
                - SQL, Tableau and Jira
                - Excellent communication skills and strong problem-solving skills
                - CBAP and PMP certifications required
                """;
        List<String> tips = TIPS.suggest("Analyst", job, ANALYZER.analyze("Analyst", job));
        assertThat(tips).contains(
                "The job asks for communication skills and problem-solving skills. Where they describe you, show them"
                        + " with concrete examples in your role bullets rather than only listing the words.",
                "The job asks for CBAP or PMP certification. If you hold one, list it in a Certifications section.");
        // Written as an either-or, it's one certification group with the usual wording.
        String eitherJob = "Requirements: CBAP or PMP certification.";
        assertThat(TIPS.suggest("Analyst", eitherJob, ANALYZER.analyze("Analyst", eitherJob)).getFirst())
                .isEqualTo("If you hold the CBAP or PMP certification, list it in a Certifications section."
                        + " If you don't, leave it out.");
        assertThat(tips).noneMatch(t -> t.contains("learning the basics") && t.contains("communication"));
    }

    // ---- either-or groups ----

    @Test
    void eitherOrIsMatchedByAnyOptionAndShowsWhichOne() {
        String job = "Requirements: SQL. Nice to have: Power BI or Tableau.";
        MatchResult r = ANALYZER.analyze("Analyst using SQL and Tableau dashboards.", job);
        assertThat(r.missingPreferredSkills()).isEmpty();
        MatchedSkill group = r.matchedSkills().stream().filter(MatchedSkill::eitherOr).findFirst().orElseThrow();
        assertThat(group.name()).isEqualTo("Power BI or Tableau");
        assertThat(group.importance()).isEqualTo(Importance.PREFERRED);
        assertThat(group.impliedBy()).isNull();
        assertThat(group.subSkills()).containsExactly(
                new SubSkill("Power BI", "analytics", false, null),
                new SubSkill("Tableau", "analytics", true, null));
        // Counts as one preferred skill, not two.
        assertThat(r.scoreBreakdown().components().get(1).total()).isEqualTo(1);
    }

    @Test
    void eitherOrCanBeMatchedThroughAnImpliedSkill() {
        MatchResult r = ANALYZER.analyze("Stored files in Amazon S3.", "Requirements: AWS or Azure");
        MatchedSkill group = r.matchedSkills().getFirst();
        assertThat(group.name()).isEqualTo("AWS or Azure");
        assertThat(group.impliedBy()).isEqualTo("Amazon S3");
        assertThat(group.subSkills().getFirst()).isEqualTo(new SubSkill("AWS", "cloud", true, "Amazon S3"));
    }

    @Test
    void missingEitherOrNamesAllOptionsInTheTip() {
        String job = "Requirements: AWS, Azure or GCP";
        MatchResult r = ANALYZER.analyze("Java developer", job);
        MissingSkill missing = r.missingRequiredSkills().getFirst();
        assertThat(missing.name()).isEqualTo("AWS, Azure or GCP");
        assertThat(missing.eitherOr()).isTrue();
        assertThat(missing.subSkills()).extracting(Skill::name).containsExactly("AWS", "Azure", "GCP");
        assertThat(TIPS.suggest("Java developer", job, r).getFirst()).isEqualTo(
                "The job asks for AWS, Azure or GCP. If you've used any of them, add it to your skills section"
                        + " and mention where you applied it. If you haven't, leave them out.");
    }

    @Test
    void alreadyMentionsTipNamesTheOptionTheResumeHas() {
        String resume = "Built services in Kotlin.";
        String job = "Requirements: Java or Kotlin.";
        assertThat(TIPS.suggest(resume, job, ANALYZER.analyze(resume, job)))
                .anyMatch(t -> t.startsWith("Your resume already mentions Kotlin."));
    }

    @Test
    void certificationEitherOrReadsNaturally() {
        assertThat(RuleBasedSuggestionProvider.missingSkillTip(
                new MissingSkill("CBAP or ECBA", "certification", List.of(), true)))
                .isEqualTo("If you hold the CBAP or ECBA certification, list it in a Certifications section."
                        + " If you don't, leave it out.");
    }

    @Test
    void emailsAndLinksDoNotCountAsKeywordMatches() {
        KeywordExtractor kw = new KeywordExtractor(DICT);
        KeywordExtractor.KeywordResult r = kw.compare(
                "Example Corp needs someone to own example dashboards and reporting pipelines.",
                "Contact: pat@example.com, https://example.org/pat. Built reporting pipelines.");
        assertThat(r.missing()).contains("example");
        assertThat(r.matched()).contains("reporting", "pipelines");
    }

    @Test
    void practicesAreWorkedWithNotUsed() {
        MatchResult r = ANALYZER.analyze("Java developer", "Requirements: Java and microservices experience.");
        List<String> tips = TIPS.suggest("Java developer", "", r);
        assertThat(tips.getFirst()).startsWith("If you've worked with Microservices,");
    }

    @Test
    void certificationAndSoftSkillTipsFitTheKindOfSkill() {
        assertThat(RuleBasedSuggestionProvider.missingSkillTip(new MissingSkill("CBAP", "certification", null)))
                .isEqualTo("If you hold the CBAP certification, list it in a Certifications section. If you don't, leave it out.");
        assertThat(RuleBasedSuggestionProvider.missingSkillTip(new MissingSkill("Communication skills", "soft-skill", null)))
                .startsWith("The job asks for communication skills. If that describes you, show it with a concrete example");
        assertThat(RuleBasedSuggestionProvider.missingSkillTip(new MissingSkill("Gap analysis", "business-analysis", null)))
                .startsWith("If you've worked with Gap analysis, add it to your skills section");
        assertThat(RuleBasedSuggestionProvider.missingSkillTip(new MissingSkill("Docker", "devops", null)))
                .startsWith("If you've used Docker, add it to your skills section");
    }

    @Test
    void businessAnalystJobEndToEnd() {
        String resume = "Business analyst. Gathered requirements in workshops, wrote user stories with acceptance"
                + " criteria, built pivot tables and Power BI dashboards. Excellent communication skills.";
        String job = """
                Requirements:
                - Requirements gathering and user stories
                - Advanced Excel and data visualization
                - Strong communication skills
                Nice to have:
                - CBAP certification
                You will excel at working with stakeholders.
                """;
        MatchResult r = ANALYZER.analyze(resume, job);
        assertThat(r.matchedSkills()).extracting(MatchedSkill::name, MatchedSkill::impliedBy).contains(
                tuple("Requirements gathering", null),
                tuple("User stories", null),
                tuple("Excel", "Pivot tables"),
                tuple("Data visualization", "Power BI"),
                tuple("Communication skills", null));
        assertThat(r.missingRequiredSkills()).isEmpty();
        assertThat(r.missingPreferredSkills()).extracting(MissingSkill::name).containsExactly("CBAP");
    }

    @Test
    void alwaysAtLeastThreeTips() {
        MatchResult r = ANALYZER.analyze("Resume 2020 2021 2022 2023", "a b c d e");
        assertThat(TIPS.suggest("Resume 2020 2021 2022 2023", "a b c d e", r)).hasSizeBetween(3, 5);
    }
}
