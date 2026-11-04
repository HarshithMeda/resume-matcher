package com.harshith.resumematcher.analysis.engine;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ScoreCalculatorTest {

    private static List<Integer> weights(ScoreCalculator.Result r) {
        return r.breakdown().components().stream().map(ScoreComponent::weight).toList();
    }

    @Test
    void everythingFoundIs100AndNothingFoundIs0() {
        assertThat(ScoreCalculator.calculate(8, 8, 3, 3, 10, 10).score()).isEqualTo(100);
        assertThat(ScoreCalculator.calculate(0, 8, 0, 3, 0, 10).score()).isEqualTo(0);
    }

    @Test
    void weightedFormula() {
        // 70 × 7/8 + 20 × 1/3 + 10 × 6/10 = 61.25 + 6.67 + 6 = 73.9 → 74
        ScoreCalculator.Result r = ScoreCalculator.calculate(7, 8, 1, 3, 6, 10);
        assertThat(r.score()).isEqualTo(74);
        assertThat(weights(r)).containsExactly(70, 20, 10);
        assertThat(r.breakdown().components()).extracting(ScoreComponent::points).containsExactly(61.3, 6.7, 6.0);
        assertThat(r.breakdown().components().getFirst().explanation())
                .isEqualTo("7 of 8 required skills found (88%) = 61.3 of 70 points");
    }

    @Test
    void requiredSkillsDominate() {
        int allRequiredNoExtras = ScoreCalculator.calculate(5, 5, 0, 4, 0, 10).score();
        int noRequiredAllExtras = ScoreCalculator.calculate(0, 5, 4, 4, 10, 10).score();
        assertThat(allRequiredNoExtras).isEqualTo(70);
        assertThat(noRequiredAllExtras).isEqualTo(30);
    }

    @Test
    void missingPartsShareTheirWeight() {
        // No preferred skills listed: 70 and 10 scale to 87.5 and 12.5 → 88 + 12 (tie goes to the earlier part).
        ScoreCalculator.Result noPreferred = ScoreCalculator.calculate(4, 4, 0, 0, 0, 10);
        assertThat(weights(noPreferred)).containsExactly(88, 0, 12);
        assertThat(noPreferred.score()).isEqualTo(88);

        // Only keywords: they're worth everything.
        ScoreCalculator.Result keywordsOnly = ScoreCalculator.calculate(0, 0, 0, 0, 3, 4);
        assertThat(weights(keywordsOnly)).containsExactly(0, 0, 100);
        assertThat(keywordsOnly.score()).isEqualTo(75);
    }

    @Test
    void weightsAlwaysAddUpTo100WhenAnythingIsListed() {
        int[][] totals = {{1, 1, 1}, {1, 0, 0}, {0, 1, 0}, {0, 0, 1}, {1, 1, 0}, {0, 1, 1}, {1, 0, 1}};
        for (int[] t : totals) {
            ScoreCalculator.Result r = ScoreCalculator.calculate(0, t[0], 0, t[1], 0, t[2]);
            assertThat(weights(r).stream().mapToInt(Integer::intValue).sum()).isEqualTo(100);
        }
    }

    @Test
    void emptyJobScoresZero() {
        ScoreCalculator.Result r = ScoreCalculator.calculate(0, 0, 0, 0, 0, 0);
        assertThat(r.score()).isZero();
        assertThat(weights(r)).containsExactly(0, 0, 0);
    }

    @Test
    void rejectsImpossibleCounts() {
        assertThatThrownBy(() -> ScoreCalculator.calculate(5, 4, 0, 0, 0, 0))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
