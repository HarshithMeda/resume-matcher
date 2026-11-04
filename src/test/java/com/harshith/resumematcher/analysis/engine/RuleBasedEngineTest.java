package com.harshith.resumematcher.analysis.engine;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

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
        assertThat(tips).anyMatch(t -> t.contains("Apache Kafka") && t.contains("If you have experience"));
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
                new SubSkill("Amazon EC2", "cloud", false),
                new SubSkill("Amazon S3", "cloud", true),
                new SubSkill("Amazon RDS", "cloud", false));
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
    void alwaysAtLeastThreeTips() {
        MatchResult r = ANALYZER.analyze("Resume 2020 2021 2022 2023", "a b c d e");
        assertThat(TIPS.suggest("Resume 2020 2021 2022 2023", "a b c d e", r)).hasSizeBetween(3, 5);
    }
}
