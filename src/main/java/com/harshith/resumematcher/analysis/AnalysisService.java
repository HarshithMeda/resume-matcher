package com.harshith.resumematcher.analysis;

import com.harshith.resumematcher.analysis.engine.MatchAnalyzer;
import com.harshith.resumematcher.analysis.engine.MatchResult;
import com.harshith.resumematcher.analysis.engine.SuggestionProvider;
import com.harshith.resumematcher.error.ApiException;
import com.harshith.resumematcher.ratelimit.DailyLimiter;
import com.harshith.resumematcher.ratelimit.RateLimitedException;
import com.harshith.resumematcher.ratelimit.RateLimits;
import com.harshith.resumematcher.resume.Resume;
import com.harshith.resumematcher.resume.ResumeRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

import java.time.Clock;
import java.time.Duration;
import java.util.List;

@Service
public class AnalysisService {

    private final AnalysisRepository analyses;
    private final ResumeRepository resumes;
    private final MatchAnalyzer analyzer;
    private final SuggestionProvider suggestions;
    private final JsonMapper json;
    private final RateLimits limits;
    private final Clock clock;

    public AnalysisService(AnalysisRepository analyses, ResumeRepository resumes, MatchAnalyzer analyzer,
                           SuggestionProvider suggestions, JsonMapper json, RateLimits limits, Clock clock) {
        this.analyses = analyses;
        this.resumes = resumes;
        this.analyzer = analyzer;
        this.suggestions = suggestions;
        this.json = json;
        this.limits = limits;
        this.clock = clock;
    }

    @Transactional
    public AnalysisResponse analyze(long userId, AnalysisRequest request) {
        DailyLimiter daily = limits.analysesPerUser();
        daily.tryAcquire(userId).ifPresent(resetAt -> {
            Duration wait = Duration.between(clock.instant(), resetAt);
            throw new RateLimitedException("You've reached the limit of " + daily.limit() + " analyses per day."
                    + " It resets at 00:00 UTC, in " + RateLimitedException.describe(wait) + ".", wait);
        });
        try {
            return runAnalysis(userId, request);
        } catch (RuntimeException e) {
            daily.release(userId); // failed requests (e.g. unknown resume) don't use up the allowance
            throw e;
        }
    }

    private AnalysisResponse runAnalysis(long userId, AnalysisRequest request) {
        Resume resume = resumes.findByIdAndUserId(request.resumeId(), userId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Resume not found"));

        // The job description is used here and then dropped; it's never saved or logged.
        String jobDescription = request.jobDescription();
        MatchResult match = analyzer.analyze(resume.getExtractedText(), jobDescription);
        List<String> tips = suggestions.suggest(resume.getExtractedText(), jobDescription, match);
        tips = tips.subList(0, Math.min(SuggestionProvider.MAX_TIPS, tips.size()));

        AnalysisResult result = AnalysisResult.of(analyzer.name(), match, tips);
        Analysis saved = analyses.save(new Analysis(userId, resume.getId(), result.matchScore(),
                json.writeValueAsString(result)));
        return AnalysisResponse.of(saved, result);
    }

    @Transactional(readOnly = true)
    public List<AnalysisListItem> list(long userId) {
        return analyses.findTop100ByUserIdOrderByCreatedAtDescIdDesc(userId).stream()
                .map(a -> new AnalysisListItem(a.getId(), a.getResumeId(), a.getMatchScore(),
                        read(a).summary(), a.getCreatedAt()))
                .toList();
    }

    @Transactional(readOnly = true)
    public AnalysisResponse get(long userId, long analysisId) {
        Analysis a = analyses.findByIdAndUserId(analysisId, userId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Analysis not found"));
        return AnalysisResponse.of(a, read(a));
    }

    /** Every analysis of the user, for the data export. */
    @Transactional(readOnly = true)
    public List<AnalysisResponse> exportAll(long userId) {
        return analyses.findAllByUserIdOrderByCreatedAtDescIdDesc(userId).stream()
                .map(a -> AnalysisResponse.of(a, read(a)))
                .toList();
    }

    private AnalysisResult read(Analysis a) {
        return json.readValue(a.getResult(), AnalysisResult.class);
    }
}
