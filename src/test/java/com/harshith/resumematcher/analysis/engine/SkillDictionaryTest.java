package com.harshith.resumematcher.analysis.engine;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SkillDictionaryTest {

    private static final SkillDictionary DICT = new SkillDictionary();

    private static List<String> names(String text) {
        return DICT.findIn(text).stream().map(Skill::name).toList();
    }

    @Test
    void loadsAboutOneHundredFiftySkillsWithValidCategories() {
        assertThat(DICT.size()).isBetween(140, 200);
        assertThat(DICT.all()).allSatisfy(s -> assertThat(SkillDictionary.CATEGORIES).contains(s.category()));
    }

    // ---- aliases ----

    @ParameterizedTest
    @ValueSource(strings = {"Spring Boot", "spring boot", "SPRING BOOT", "springboot", "spring-boot", "Spring\nBoot"})
    void springBootAliases(String text) {
        assertThat(names(text)).containsExactly("Spring Boot");
    }

    @Test
    void otherAliasesMapToCanonicalNames() {
        assertThat(names("Deployed with k8s")).containsExactly("Kubernetes");
        assertThat(names("Postgres and golang")).containsExactly("PostgreSQL", "Go");
        assertThat(names("built RESTful services")).containsExactly("REST APIs");
        assertThat(names("test-driven development")).containsExactly("TDD");
    }

    @Test
    void resultsAreInOrderOfAppearanceWithoutDuplicates() {
        assertThat(names("Docker, Java, docker again, JAVA again")).containsExactly("Docker", "Java");
    }

    // ---- whole words ----

    @Test
    void javaDoesNotMatchJavaScript() {
        assertThat(names("Senior JavaScript developer")).containsExactly("JavaScript");
        assertThat(names("Java and JavaScript")).containsExactly("Java", "JavaScript");
        assertThat(names("Javanese cuisine")).isEmpty();
    }

    @Test
    void cVersusCppVersusCSharp() {
        assertThat(names("Expert in C++")).containsExactly("C++");
        assertThat(names("Expert in C#")).containsExactly("C#");
        assertThat(names("Expert in C")).containsExactly("C");
        assertThat(names("C, C++ and C#")).containsExactlyInAnyOrder("C", "C++", "C#");
        assertThat(names("C/C++")).containsExactlyInAnyOrder("C", "C++");
        assertThat(names("Objective-C")).containsExactly("Objective-C");
    }

    @Test
    void shortNamesDoNotMatchInsideOtherWordsOrPhrases() {
        assertThat(names("Reporting to the C-level team")).isEmpty();
        assertThat(names("Our R&D department")).isEmpty();
        assertThat(names("go-to-market strategy, we go fast")).isEmpty();
        assertThat(names("We use Go for services")).containsExactly("Go");
    }

    @Test
    void ambiguousEnglishWordsNeedTheirCapitalisation() {
        assertThat(names("we react quickly and take a rest")).isEmpty();
        assertThat(names("React frontend, REST backend")).containsExactly("React", "REST APIs");
    }

    @Test
    void punctuationAroundSkillsIsFine() {
        assertThat(names("(Java), Kotlin; Docker.")).containsExactly("Java", "Kotlin", "Docker");
    }

    @Test
    void dottedNamesAreHandled() {
        assertThat(names("Node.js backend")).containsExactly("Node.js");      // not JavaScript via ".js"
        assertThat(names("ASP.NET Core and .NET")).containsExactly(".NET");
        assertThat(names("Vue.js and React.js")).containsExactly("Vue.js", "React");
    }

    @Test
    void skillInsideALongerSkillNameDoesNotCountSeparately() {
        assertThat(names("GitHub Actions pipelines")).containsExactly("GitHub Actions");
        assertThat(names("Tailwind CSS")).containsExactly("Tailwind CSS");
        assertThat(names("React Native apps")).containsExactly("React Native");
        // ...but a separate mention still counts.
        assertThat(names("GitHub Actions, code on GitHub")).containsExactly("GitHub Actions", "GitHub");
    }

    @Test
    void longestSkillAtEndOfText() {
        assertThat(DICT.skillAtEnd("Experience with Spring Data JPA ")).contains(new Skill("Spring Data JPA", "framework"));
        assertThat(DICT.skillAtEnd("AWS")).contains(new Skill("AWS", "cloud"));
        assertThat(DICT.skillAtEnd("Cloud platforms")).isEmpty();
    }

    // ---- implies ----

    private static Map<String, String> implied(String resumeText) {
        Map<String, String> byName = new LinkedHashMap<>();
        DICT.impliedBy(DICT.findIn(resumeText)).forEach((target, source) -> byName.put(target.name(), source.name()));
        return byName;
    }

    @Test
    void springSkillsImplyTheirBasics() {
        assertThat(implied("Spring Data JPA")).containsExactly(
                Map.entry("JPA", "Spring Data JPA"),
                Map.entry("Hibernate", "Spring Data JPA"),
                Map.entry("Spring Framework", "Spring Data JPA"));
        assertThat(implied("Spring Boot")).containsExactly(Map.entry("Spring Framework", "Spring Boot"));
        assertThat(implied("Spring Security")).containsExactly(Map.entry("Spring Framework", "Spring Security"));
    }

    @Test
    void otherCommonImplications() {
        assertThat(implied("PostgreSQL")).containsExactly(Map.entry("SQL", "PostgreSQL"));
        assertThat(implied("Amazon S3")).containsExactly(Map.entry("AWS", "Amazon S3"));
        assertThat(implied("Django")).containsExactly(Map.entry("Python", "Django"));
        assertThat(implied("Next.js")).containsOnlyKeys("React", "JavaScript");
        assertThat(implied("JUnit")).containsExactly(Map.entry("Unit testing", "JUnit"));
        assertThat(implied("Scrum")).containsExactly(Map.entry("Agile", "Scrum"));
    }

    @Test
    void skillsTheResumeNamesAreNotMarkedImplied() {
        assertThat(implied("Spring Boot and Spring Framework")).isEmpty();
        assertThat(implied("Hibernate, JPA")).isEmpty();
    }

    @Test
    void firstImplyingSkillIsTheLabel() {
        assertThat(implied("Spring Security, then Spring Boot"))
                .containsExactly(Map.entry("Spring Framework", "Spring Security"));
    }

    @Test
    void implicationsAreOneLevelOnly() {
        // GitHub Actions implies GitHub, and GitHub implies Git, but GitHub Actions alone doesn't imply Git.
        assertThat(implied("GitHub Actions")).containsOnlyKeys("CI/CD", "GitHub");
    }

    @Test
    void invalidImpliesEntriesAreRejectedAtStartup() {
        String unknown = """
                {"skills": [{"name": "A1", "category": "tool", "aliases": [], "implies": ["Nope"]}]}""";
        String self = """
                {"skills": [{"name": "A1", "category": "tool", "aliases": [], "implies": ["A1"]}]}""";
        assertThatThrownBy(() -> new SkillDictionary(stream(unknown))).hasMessageContaining("invalid implies entry: Nope");
        assertThatThrownBy(() -> new SkillDictionary(stream(self))).hasMessageContaining("invalid implies entry: A1");
    }

    private static java.io.InputStream stream(String s) {
        return new java.io.ByteArrayInputStream(s.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    @Test
    void removeSkillsBlanksOutMentions() {
        assertThat(DICT.removeSkills("Java services on Kubernetes").strip()).isEqualTo("services on");
    }
}
