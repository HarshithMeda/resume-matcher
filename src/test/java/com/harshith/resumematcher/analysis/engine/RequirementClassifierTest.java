package com.harshith.resumematcher.analysis.engine;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static com.harshith.resumematcher.analysis.engine.Importance.PREFERRED;
import static com.harshith.resumematcher.analysis.engine.Importance.REQUIRED;
import static org.assertj.core.api.Assertions.assertThat;

class RequirementClassifierTest {

    private static final RequirementClassifier CLASSIFIER = new RequirementClassifier(new SkillDictionary());

    private static Map<String, Importance> classify(String jd) {
        Map<String, Importance> byName = new LinkedHashMap<>();
        CLASSIFIER.classify(jd).forEach(js -> byName.put(js.displayName(), js.importance()));
        return byName;
    }

    /** Display names, e.g. ["Power BI or Tableau", "SQL"]. */
    private static List<String> names(String jd) {
        return CLASSIFIER.classify(jd).stream().map(JobSkill::displayName).toList();
    }

    // ---- either-or groups ----

    @Test
    void skillsJoinedByOrAreOneGroup() {
        assertThat(names("Power BI or Tableau")).containsExactly("Power BI or Tableau");
        assertThat(names("Java or Kotlin, plus SQL")).containsExactly("Java or Kotlin", "SQL");
        assertThat(names("Either Python or R")).containsExactly("Python or R");
        assertThat(names("Java and/or Kotlin")).containsExactly("Java or Kotlin");
        assertThat(names("Kafka or RabbitMQ or ActiveMQ")).containsExactly("Apache Kafka, RabbitMQ or ActiveMQ");
    }

    @Test
    void commaListsEndingInOrAreOneGroup() {
        assertThat(names("Experience with AWS, Azure or GCP")).containsExactly("AWS, Azure or GCP");
        assertThat(names("AWS, Azure, or GCP")).containsExactly("AWS, Azure or GCP"); // Oxford comma
    }

    @Test
    void slashBetweenSkillsMeansOr() {
        assertThat(names("Power BI/Tableau dashboards")).containsExactly("Power BI or Tableau");
        assertThat(names("C/C++")).containsExactly("C or C++");
        // A slash inside a skill's own name is not a separator.
        assertThat(names("CI/CD pipelines")).containsExactly("CI/CD");
    }

    @Test
    void listsJoinedByAndAreNotGroups() {
        assertThat(names("Java, Spring Boot and PostgreSQL")).containsExactly("Java", "Spring Boot", "PostgreSQL");
        assertThat(names("Power BI and Tableau")).containsExactly("Power BI", "Tableau");
        assertThat(names("Java and Kotlin or Scala")).containsExactly("Java", "Kotlin or Scala");
    }

    @Test
    void commaListOnlyJoinsTheGroupWhileCategoriesMatch() {
        // Java and SQL are separate requirements; only Docker and Kubernetes are alternatives.
        assertThat(names("Java, SQL, Docker or Kubernetes")).containsExactly("Java", "SQL", "Docker or Kubernetes");
    }

    @Test
    void eitherOrKeepsItsImportance() {
        String jd = """
                Requirements:
                - Java or Kotlin
                Nice to have:
                - Power BI or Tableau
                """;
        assertThat(classify(jd)).containsExactly(
                Map.entry("Java or Kotlin", REQUIRED), Map.entry("Power BI or Tableau", PREFERRED));
        JobSkill group = CLASSIFIER.classify(jd).getFirst();
        assertThat(group.isEitherOr()).isTrue();
        assertThat(group.alternatives()).extracting(Skill::name).containsExactly("Java", "Kotlin");
    }

    @Test
    void optionAlsoListedOnItsOwnIsNotCountedTwice() {
        String jd = """
                Requirements:
                - Java
                Nice to have:
                - Java or Kotlin
                """;
        assertThat(names(jd)).containsExactly("Java");
    }

    @Test
    void parentGroupsStaySeparateFromEitherOr() {
        assertThat(CLASSIFIER.classify("AWS (EC2 or S3)")).singleElement().satisfies(js -> {
            assertThat(js.isEitherOr()).isFalse();
            assertThat(js.subSkills()).extracting(Skill::name).containsExactly("Amazon EC2", "Amazon S3");
        });
    }

    /** Top-level skill name -> names of its group items (empty list if not a group). */
    private static Map<String, List<String>> groups(String jd) {
        Map<String, List<String>> byName = new LinkedHashMap<>();
        CLASSIFIER.classify(jd).forEach(js ->
                byName.put(js.skill().name(), js.subSkills().stream().map(Skill::name).toList()));
        return byName;
    }

    // ---- skill groups like "AWS (EC2, S3, RDS)" ----

    @Test
    void parentWithItemsInParenthesesIsOneGroup() {
        String jd = """
                Nice to have:
                - AWS (EC2, S3, RDS)
                - Terraform
                """;
        assertThat(groups(jd)).containsExactly(
                Map.entry("AWS", List.of("Amazon EC2", "Amazon S3", "Amazon RDS")),
                Map.entry("Terraform", List.of()));
        assertThat(classify(jd)).containsExactly(Map.entry("AWS", PREFERRED), Map.entry("Terraform", PREFERRED));
    }

    @Test
    void multiWordParentAndSeveralGroupsInOneLine() {
        assertThat(groups("Requirements: CI/CD (Jenkins, GitHub Actions) and Spring Boot (Spring Security)"))
                .containsExactly(
                        Map.entry("CI/CD", List.of("Jenkins", "GitHub Actions")),
                        Map.entry("Spring Boot", List.of("Spring Security")));
    }

    @Test
    void itemListedOnItsOwnElsewhereLeavesTheGroup() {
        String jd = """
                Requirements:
                - Amazon S3
                Nice to have:
                - AWS (EC2, S3)
                """;
        assertThat(groups(jd)).containsExactly(
                Map.entry("Amazon S3", List.of()),
                Map.entry("AWS", List.of("Amazon EC2")));
        assertThat(classify(jd)).containsExactly(Map.entry("Amazon S3", REQUIRED), Map.entry("AWS", PREFERRED));
    }

    @Test
    void parenthesesWithoutAParentSkillAreNotAGroup() {
        assertThat(groups("Cloud platforms (AWS, GCP)")).containsExactly(
                Map.entry("AWS", List.of()), Map.entry("GCP", List.of()));
        assertThat(groups("Java (8 or newer)")).containsExactly(Map.entry("Java", List.of()));
    }

    @Test
    void parenthesisWithItsOwnWordingIsNotAGroup() {
        assertThat(groups("Requirements: Spring Boot (Kafka a plus)")).containsExactly(
                Map.entry("Spring Boot", List.of()), Map.entry("Apache Kafka", List.of()));
    }

    @Test
    void sectionHeadings() {
        String jd = """
                Requirements:
                - Java
                - Spring Boot
                Nice to have:
                - Kafka
                - Docker
                """;
        assertThat(classify(jd)).containsExactly(
                Map.entry("Java", REQUIRED), Map.entry("Spring Boot", REQUIRED),
                Map.entry("Apache Kafka", PREFERRED), Map.entry("Docker", PREFERRED));
    }

    @Test
    void preferredQualificationsHeadingIsPreferred() {
        String jd = """
                Minimum qualifications
                • 3+ years with Java
                Preferred qualifications
                • Experience with AWS
                """;
        assertThat(classify(jd)).containsExactly(Map.entry("Java", REQUIRED), Map.entry("AWS", PREFERRED));
    }

    @Test
    void wordingInsideASentence() {
        String jd = "You must have strong Java skills. Experience with Kafka is a plus. Docker is nice to have.";
        assertThat(classify(jd)).containsExactly(
                Map.entry("Java", REQUIRED), Map.entry("Apache Kafka", PREFERRED), Map.entry("Docker", PREFERRED));
    }

    @Test
    void inlineLabels() {
        assertThat(classify("Required: Java, SQL. Preferred: AWS"))
                .containsExactly(Map.entry("Java", REQUIRED), Map.entry("SQL", REQUIRED), Map.entry("AWS", PREFERRED));
    }

    @Test
    void notRequiredMeansPreferred() {
        assertThat(classify("Terraform knowledge is helpful but not required."))
                .containsExactly(Map.entry("Terraform", PREFERRED));
    }

    @Test
    void unclearWordingDefaultsToRequired() {
        assertThat(classify("We build payment services in Java and Kafka on AWS."))
                .containsExactly(Map.entry("Java", REQUIRED), Map.entry("Apache Kafka", REQUIRED), Map.entry("AWS", REQUIRED));
    }

    @Test
    void lineWordingBeatsSection() {
        String jd = """
                Nice to have:
                - GraphQL
                - Must know SQL
                """;
        assertThat(classify(jd)).containsExactly(Map.entry("GraphQL", PREFERRED), Map.entry("SQL", REQUIRED));
    }

    @Test
    void neutralHeadingEndsAPreferredSection() {
        String jd = """
                Nice to have:
                - Kafka
                Responsibilities:
                - Build APIs with Java
                """;
        assertThat(classify(jd)).containsExactly(Map.entry("Apache Kafka", PREFERRED), Map.entry("Java", REQUIRED));
    }

    @Test
    void requiredAnywhereWins() {
        String jd = """
                Requirements:
                - Docker
                Nice to have:
                - Docker and Kubernetes
                """;
        assertThat(classify(jd)).containsExactly(Map.entry("Docker", REQUIRED), Map.entry("Kubernetes", PREFERRED));
    }

    @Test
    void parenthesisWithItsOwnWordingIsSplitOff() {
        assertThat(classify("Requirements: Java, Spring Boot (Kafka a plus)")).containsExactly(
                Map.entry("Java", REQUIRED), Map.entry("Spring Boot", REQUIRED), Map.entry("Apache Kafka", PREFERRED));
        assertThat(classify("Docker (preferred)")).containsExactly(Map.entry("Docker", PREFERRED));
    }
}
