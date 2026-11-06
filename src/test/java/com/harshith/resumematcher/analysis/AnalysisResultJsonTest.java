package com.harshith.resumematcher.analysis;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;

/** Analyses saved before either-or groups existed must still load. */
class AnalysisResultJsonTest {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    @Test
    void resultsSavedBeforeEitherOrGroupsStillLoad() {
        String old = """
                {"engine":"rule-based-v1","matchScore":56,
                 "scoreBreakdown":{"formula":"f","components":[]},
                 "matchedSkills":[{"name":"Java","category":"language","importance":"REQUIRED"}],
                 "missingRequiredSkills":[{"name":"Docker","category":"devops"}],
                 "missingPreferredSkills":[{"name":"AWS","category":"cloud","subSkills":[{"name":"Amazon S3","category":"cloud"}]}],
                 "matchedKeywords":[],"missingKeywords":[],"summary":"s","tips":["t"]}
                """;
        AnalysisResult r = JSON.readValue(old, AnalysisResult.class);
        assertThat(r.matchedSkills().getFirst().eitherOr()).isFalse();
        assertThat(r.missingRequiredSkills().getFirst().eitherOr()).isFalse();
        assertThat(r.missingPreferredSkills().getFirst().label()).isEqualTo("AWS (Amazon S3)");
    }

    @Test
    void eitherOrFlagIsOnlyWrittenWhenTrue() {
        AnalysisResult r = JSON.readValue("""
                {"engine":"e","matchScore":1,"scoreBreakdown":{"formula":"f","components":[]},
                 "matchedSkills":[{"name":"Java","category":"language","importance":"REQUIRED"},
                                  {"name":"Power BI or Tableau","category":"analytics","importance":"PREFERRED","eitherOr":true,
                                   "subSkills":[{"name":"Power BI","category":"analytics","found":true}]}],
                 "missingRequiredSkills":[],"missingPreferredSkills":[],"matchedKeywords":[],"missingKeywords":[],
                 "summary":"s","tips":[]}
                """, AnalysisResult.class);
        String json = JSON.writeValueAsString(r);
        assertThat(json).contains("\"eitherOr\":true").containsOnlyOnce("eitherOr");
    }
}
