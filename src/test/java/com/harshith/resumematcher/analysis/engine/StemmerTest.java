package com.harshith.resumematcher.analysis.engine;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

class StemmerTest {

    /** Every word in a row must get the same stem. */
    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "process | processes | processing | processed",
            "document | documents | documented | documenting | documentation",
            "schedule | schedules | scheduled | scheduling",
            "technology | technologies",
            "plan | plans | planned | planning",
            "run | running",
            "optimize | optimizes | optimized | optimizing | optimization | optimisation",
            "apply | applies | applied | applying",
            "inventory | inventories",
            "business | businesses",
            "claim | claims | claimed",
            "bill | billing | billed",
            "staff | staffing",
            "report | reports | reported | reporting",
            "inform | information",
    })
    void formsOfAWordShareAStem(String row) {
        String[] words = Arrays.stream(row.split("\\|")).map(String::strip).toArray(String[]::new);
        String expected = Stemmer.stem(words[0]);
        for (String w : words) {
            assertThat(Stemmer.stem(w)).as(w).isEqualTo(expected);
        }
    }

    @Test
    void differentWordsStayDifferent() {
        // No -er, -ment or -al rules: these must not merge.
        assertThat(Stemmer.stem("retailer")).isNotEqualTo(Stemmer.stem("retail"));
        assertThat(Stemmer.stem("segment")).isEqualTo("segment");
        assertThat(Stemmer.stem("technical")).isNotEqualTo(Stemmer.stem("technique"));
        assertThat(Stemmer.stem("process")).isNotEqualTo(Stemmer.stem("processor"));
    }

    @Test
    void shortAndTrickyWordsAreLeftAlone() {
        assertThat(Stemmer.stem("data")).isEqualTo("data");
        assertThat(Stemmer.stem("thing")).isEqualTo("thing");
        assertThat(Stemmer.stem("need")).isEqualTo("need");
        assertThat(Stemmer.stem("status")).isEqualTo("status");
        assertThat(Stemmer.stem("analysis")).isEqualTo("analysis");
        assertThat(Stemmer.stem("Processing")).isEqualTo("process"); // case doesn't matter
    }
}
