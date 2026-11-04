package com.harshith.resumematcher.analysis.engine;

import java.util.List;

/** What a {@link MatchAnalyzer} found. Tips come separately from a {@link SuggestionProvider}. */
public record MatchResult(
        int matchScore,
        ScoreBreakdown scoreBreakdown,
        List<MatchedSkill> matchedSkills,
        List<MissingSkill> missingRequiredSkills,
        List<MissingSkill> missingPreferredSkills,
        List<String> matchedKeywords,
        List<String> missingKeywords,
        String summary) {
}
