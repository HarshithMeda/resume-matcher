package com.harshith.resumematcher.analysis.engine;

import java.util.List;

/**
 * Writes 3 to 5 short tips for improving a resume for a specific job.
 *
 * <p>Rules every implementation must follow:
 * <ul>
 *   <li>Never invent experience. Phrase gaps conditionally: "If you've used Docker, add it...".</li>
 *   <li>Never log or store the resume or job description text.</li>
 * </ul>
 * The default is {@link RuleBasedSuggestionProvider}; an AI-backed provider can replace it the same
 * way as {@link MatchAnalyzer}.
 */
public interface SuggestionProvider {

    int MIN_TIPS = 3;
    int MAX_TIPS = 5;

    String name();

    List<String> suggest(String resumeText, String jobDescription, MatchResult result);
}
