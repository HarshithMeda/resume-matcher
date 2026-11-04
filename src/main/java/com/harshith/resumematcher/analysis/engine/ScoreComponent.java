package com.harshith.resumematcher.analysis.engine;

/**
 * One line of the score breakdown.
 *
 * @param weight          how many of the 100 points this part is worth for this job
 * @param coveragePercent matched / total, as a whole percentage
 * @param points          weight × matched / total, rounded to one decimal
 */
public record ScoreComponent(
        String name,
        int weight,
        int matched,
        int total,
        int coveragePercent,
        double points,
        String explanation) {
}
