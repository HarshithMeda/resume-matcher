package com.harshith.resumematcher.analysis.engine;

/**
 * Compares a resume with a job description.
 *
 * <p>The default is {@link RuleBasedMatchAnalyzer} (free, runs locally). Another implementation,
 * such as one backed by a local AI model, can replace it by registering its own bean of this type
 * (e.g. marked {@code @Primary}, or enabled with {@code @ConditionalOnProperty}).
 *
 * <p>Implementations must not log or store the resume or job description text.
 */
public interface MatchAnalyzer {

    /** Short id saved with each analysis, e.g. "rule-based-v1". */
    String name();

    MatchResult analyze(String resumeText, String jobDescription);
}
