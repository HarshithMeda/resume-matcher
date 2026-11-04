package com.harshith.resumematcher.analysis;

import com.harshith.resumematcher.analysis.engine.MatchAnalyzer;
import com.harshith.resumematcher.analysis.engine.MatchResult;
import com.harshith.resumematcher.analysis.engine.SuggestionProvider;
import com.harshith.resumematcher.error.ApiException;
import com.harshith.resumematcher.resume.Resume;
import com.harshith.resumematcher.resume.ResumeRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;

@Service
public class AnalysisService {

    private final AnalysisRepository analyses;
    private final ResumeRepository resumes;
    private final MatchAnalyzer analyzer;
    private final SuggestionProvider suggestions;
    private final JsonMapper json;

    public AnalysisService(AnalysisRepository analyses, ResumeRepository resumes, MatchAnalyzer analyzer,
                           SuggestionProvider suggestions, JsonMapper json) {
        this.analyses = analyses;
        this.resumes = resumes;
        this.analyzer = analyzer;
        this.suggestions = suggestions;
        this.json = json;
    }

    @Transactional
    public AnalysisResponse analyze(long userId, AnalysisRequest request) {
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

    private AnalysisResult read(Analysis a) {
        return json.readValue(a.getResult(), AnalysisResult.class);
    }
}
