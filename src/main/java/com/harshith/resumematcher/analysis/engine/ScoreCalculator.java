package com.harshith.resumematcher.analysis.engine;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.IntStream;

/**
 * The scoring formula. Pure arithmetic, no text handling, so it's easy to test.
 *
 * <pre>
 *   score = 70 × (required skills found / required skills listed)
 *         + 20 × (preferred skills found / preferred skills listed)
 *         + 10 × (keywords found / keywords picked)
 * </pre>
 *
 * If the job lists nothing in one part (e.g. no preferred skills), that part's points are
 * shared among the other parts in proportion to their weights, so the total is still out of 100.
 */
public final class ScoreCalculator {

    static final int REQUIRED_WEIGHT = 70;
    static final int PREFERRED_WEIGHT = 20;
    static final int KEYWORD_WEIGHT = 10;

    static final String FORMULA = "score = 70% required-skill coverage + 20% preferred-skill coverage"
            + " + 10% keyword coverage. If the job lists nothing for a part, its share goes to the others.";

    private ScoreCalculator() {
    }

    public record Result(int score, ScoreBreakdown breakdown) {
    }

    public static Result calculate(int requiredMatched, int requiredTotal,
                                   int preferredMatched, int preferredTotal,
                                   int keywordsMatched, int keywordsTotal) {
        String[] names = {"Required skills", "Preferred skills", "Keywords"};
        String[] nouns = {"required skills", "preferred skills", "keywords"};
        int[] base = {REQUIRED_WEIGHT, PREFERRED_WEIGHT, KEYWORD_WEIGHT};
        int[] matched = {requiredMatched, preferredMatched, keywordsMatched};
        int[] total = {requiredTotal, preferredTotal, keywordsTotal};

        int[] weight = effectiveWeights(base, total);

        double exactScore = 0;
        List<ScoreComponent> components = new ArrayList<>();
        for (int i = 0; i < names.length; i++) {
            if (matched[i] < 0 || total[i] < 0 || matched[i] > total[i]) {
                throw new IllegalArgumentException("Invalid counts for " + names[i]);
            }
            double points = total[i] == 0 ? 0 : (double) weight[i] * matched[i] / total[i];
            exactScore += points;
            int coverage = total[i] == 0 ? 0 : (int) Math.round(100.0 * matched[i] / total[i]);
            double shownPoints = Math.round(points * 10) / 10.0;
            String explanation = total[i] == 0
                    ? "The job lists no " + nouns[i] + ", so this part's share went to the others"
                    : matched[i] + " of " + total[i] + " " + nouns[i] + " found (" + coverage + "%) = "
                    + format(shownPoints) + " of " + weight[i] + " points";
            components.add(new ScoreComponent(names[i], weight[i], matched[i], total[i], coverage, shownPoints, explanation));
        }

        int score = (int) Math.max(0, Math.min(100, Math.round(exactScore)));
        return new Result(score, new ScoreBreakdown(FORMULA, components));
    }

    /**
     * Base weights of the parts the job actually has, scaled to add up to exactly 100.
     * Leftover points from rounding go to the parts with the largest fractions (ties: earlier part).
     */
    static int[] effectiveWeights(int[] base, int[] total) {
        int activeSum = IntStream.range(0, base.length).filter(i -> total[i] > 0).map(i -> base[i]).sum();
        int[] weights = new int[base.length];
        if (activeSum == 0) {
            return weights; // nothing to score
        }
        double[] exact = new double[base.length];
        int assigned = 0;
        for (int i = 0; i < base.length; i++) {
            if (total[i] > 0) {
                exact[i] = 100.0 * base[i] / activeSum;
                weights[i] = (int) Math.floor(exact[i]);
                assigned += weights[i];
            }
        }
        List<Integer> byFraction = IntStream.range(0, base.length).filter(i -> total[i] > 0).boxed()
                .sorted(Comparator.comparingDouble((Integer i) -> exact[i] - weights[i]).reversed())
                .toList();
        for (int k = 0; k < 100 - assigned; k++) {
            weights[byFraction.get(k % byFraction.size())]++;
        }
        return weights;
    }

    private static String format(double d) {
        return d == Math.rint(d) ? String.valueOf((long) d) : String.valueOf(d);
    }
}
