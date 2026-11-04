package com.harshith.resumematcher.analysis;

import com.harshith.resumematcher.analysis.engine.MatchResult;
import com.harshith.resumematcher.analysis.engine.MatchedSkill;
import com.harshith.resumematcher.analysis.engine.ScoreBreakdown;
import com.harshith.resumematcher.analysis.engine.MissingSkill;

import java.util.List;

/** What gets saved in analyses.result (as JSON). Contains no resume or job description text. */
public record AnalysisResult(
        String engine,
        int matchScore,
        ScoreBreakdown scoreBreakdown,
        List<MatchedSkill> matchedSkills,
        List<MissingSkill> missingRequiredSkills,
        List<MissingSkill> missingPreferredSkills,
        List<String> matchedKeywords,
        List<String> missingKeywords,
        String summary,
        List<String> tips) {

    static AnalysisResult of(String engine, MatchResult r, List<String> tips) {
        return new AnalysisResult(engine, r.matchScore(), r.scoreBreakdown(), r.matchedSkills(),
                r.missingRequiredSkills(), r.missingPreferredSkills(), r.matchedKeywords(),
                r.missingKeywords(), r.summary(), tips);
    }
}
