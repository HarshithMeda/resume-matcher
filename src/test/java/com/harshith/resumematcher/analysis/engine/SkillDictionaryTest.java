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
    void loadsTheDictionaryWithValidCategories() {
        assertThat(DICT.size()).isBetween(200, 260);
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

    // ---- business analysis, analytics, product, certifications, soft skills ----

    @Test
    void excelTheProductButNotTheVerb() {
        assertThat(names("Advanced Excel and SQL")).containsExactly("Excel", "SQL");
        assertThat(names("Proficient in Microsoft Excel")).containsExactly("Excel");
        assertThat(names("Excellent Excel skills")).containsExactly("Excel");
        assertThat(names("Built models in EXCEL.")).containsExactly("Excel");
        // The verb, in any position or case, is not the product.
        assertThat(names("You will excel at turning data into decisions.")).isEmpty();
        assertThat(names("Excel at stakeholder management")).containsExactly("Stakeholder management");
        assertThat(names("People who Excel in ambiguity thrive here")).isEmpty();
        assertThat(names("Our teams excel and grow together, delivering excellent results")).isEmpty();
    }

    @Test
    void businessAnalysisSkillsAndAliases() {
        assertThat(names("Requirements elicitation workshops")).containsExactly("Requirements gathering");
        assertThat(names("Wrote user stories with acceptance criteria")).containsExactly("User stories", "Acceptance criteria");
        assertThat(names("Coordinated user acceptance testing (UAT)")).containsExactly("UAT");
        assertThat(names("As-is and to-be process maps in BPMN 2.0")).containsExactly("Process mapping", "BPMN");
        assertThat(names("Gap analysis and process improvement")).containsExactly("Gap analysis", "Business process improvement");
        assertThat(names("UML diagrams and use case modelling")).containsExactly("UML", "Use case modeling");
        assertThat(names("Certified Business Analysis Professional (CBAP); ECBA; PMP"))
                .containsExactly("CBAP", "ECBA", "PMP");
    }

    @Test
    void everydayPhrasesAreNotSkills() {
        // "use cases" alone is everyday product talk; only BA forms like "writing use cases" count.
        assertThat(names("Explore new AI use cases with customers")).isEmpty();
        assertThat(names("Writing use cases for the claims system")).containsExactly("Use case modeling");
        // Soft skills only when explicitly named.
        assertThat(names("Report to the leadership team on our communication tools")).isEmpty();
        assertThat(names("Communication, leadership and presentation are part of every job")).isEmpty();
        assertThat(names("Build the presentation layer in React")).containsExactly("React");
        assertThat(names("Excellent written and verbal communication skills")).containsExactly("Communication skills");
        assertThat(names("Strong problem-solving and leadership skills")).containsExactly("Problem-solving skills", "Leadership skills");
        assertThat(names("Comfortable presenting to senior stakeholders")).containsExactly("Presentation skills");
        // "stakeholder communication" is a way of saying stakeholder management, not the soft skill.
        assertThat(names("Stakeholder communication")).containsExactly("Stakeholder management");
    }

    @Test
    void analyticsAndProductSkills() {
        assertThat(names("Dashboards in Power BI, Tableau and Looker"))
                .containsExactly("Power BI", "Tableau", "Looker");
        assertThat(names("Data visualisation, statistical analysis and A/B tests"))
                .containsExactly("Data visualization", "Statistics", "A/B testing");
        assertThat(names("Data cleansing with pandas")).containsExactly("Data cleaning", "Pandas");
        assertThat(names("Pivot tables and VLOOKUPs")).containsExactly("Pivot tables", "VLOOKUP");
        assertThat(names("Own the product roadmap, write PRDs and run backlog refinement"))
                .containsExactly("Roadmapping", "Product requirements documents", "Backlog management");
        assertThat(names("Jira and Confluence in a Scrum team")).containsExactly("Jira", "Confluence", "Scrum");
    }

    @Test
    void businessAndDataImplications() {
        assertThat(implied("VLOOKUP")).containsExactly(Map.entry("Excel", "VLOOKUP"));
        assertThat(implied("Pivot tables")).containsExactly(Map.entry("Excel", "Pivot tables"));
        assertThat(implied("Power BI")).containsExactly(Map.entry("Data visualization", "Power BI"));
        assertThat(implied("A/B testing")).containsExactly(Map.entry("Statistics", "A/B testing"));
        assertThat(implied("BPMN")).containsExactly(Map.entry("Process mapping", "BPMN"));
        assertThat(implied("User stories")).containsExactly(Map.entry("Agile", "User stories"));
        assertThat(implied("pandas")).containsOnlyKeys("Python", "Data analysis");
    }

    // ---- wording variants, so real resumes aren't marked missing for different wording ----

    @ParameterizedTest
    @ValueSource(strings = {"user acceptance testing", "UAT testing", "UAT sessions", "user acceptance tests"})
    void uatVariants(String text) {
        assertThat(names(text)).containsExactly("UAT");
    }

    @ParameterizedTest
    @ValueSource(strings = {"stakeholder management", "stakeholder engagement", "stakeholder communication",
            "managed stakeholders", "managing stakeholders", "liaised with stakeholders",
            "communicated with stakeholders", "stakeholder relationships"})
    void stakeholderManagementVariants(String text) {
        assertThat(names(text)).containsExactly("Stakeholder management");
    }

    @Test
    void otherBusinessAndDataVariants() {
        assertThat(names("wrote a user story")).containsExactly("User stories");
        assertThat(names("each acceptance criterion")).containsExactly("Acceptance criteria");
        assertThat(names("Ran requirements workshops and wrote BRDs")).containsExactly("Requirements gathering");
        assertThat(names("Kept the Jira backlog refined")).containsExactly("Jira", "Backlog management");
        assertThat(names("Groomed the backlog every sprint")).containsExactly("Backlog management");
        assertThat(names("Completed a fit-gap analysis")).containsExactly("Gap analysis");
        assertThat(names("Documented processes with swimlane diagrams")).containsExactly("Process mapping");
        assertThat(names("Sequence diagrams for the claims API")).containsExactly("UML");
        assertThat(names("Streamlined processes for month-end close")).containsExactly("Business process improvement");
        assertThat(names("Analysed data and built dashboards")).containsExactly("Data analysis", "Data visualization");
        assertThat(names("Data preparation and hypothesis testing")).containsExactly("Data cleaning", "Statistics");
        assertThat(names("Presented findings to the board")).containsExactly("Presentation skills");
    }

    @Test
    void variantsStayStrictEnoughThatUnrelatedTextDoesNotCount() {
        assertThat(names("Translate functional requirements into technical designs")).isEmpty();
        assertThat(names("A culture of continuous improvement")).isEmpty();
        assertThat(names("Monitor service dashboards and the deployment process")).isEmpty();
        assertThat(names("The stakeholders are our customers")).isEmpty();
        assertThat(names("Our product has a long backlog of ideas")).isEmpty();
    }

    @Test
    void removeSkillsBlanksOutMentions() {
        assertThat(DICT.removeSkills("Java services on Kubernetes").strip()).isEqualTo("services on");
    }
}
