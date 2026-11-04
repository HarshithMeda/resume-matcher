package com.harshith.resumematcher.analysis;

import com.harshith.resumematcher.analysis.engine.MatchedSkill;
import com.harshith.resumematcher.analysis.engine.ScoreBreakdown;
import com.harshith.resumematcher.analysis.engine.MissingSkill;

import java.time.Instant;
import java.util.List;

public record AnalysisResponse(
        Long id,
        Long resumeId,
        Instant createdAt,
        String engine,
        int matchScore,
        String summary,
        ScoreBreakdown scoreBreakdown,
        List<MatchedSkill> matchedSkills,
        List<MissingSkill> missingRequiredSkills,
        List<MissingSkill> missingPreferredSkills,
        List<String> matchedKeywords,
        List<String> missingKeywords,
        List<String> tips) {

    static AnalysisResponse of(Analysis a, AnalysisResult r) {
        return new AnalysisResponse(a.getId(), a.getResumeId(), a.getCreatedAt(), r.engine(), r.matchScore(),
                r.summary(), r.scoreBreakdown(), r.matchedSkills(), r.missingRequiredSkills(),
                r.missingPreferredSkills(), r.matchedKeywords(), r.missingKeywords(), r.tips());
    }
}
