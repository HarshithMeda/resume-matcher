package com.harshith.resumematcher.user;

import com.harshith.resumematcher.analysis.AnalysisResponse;
import com.harshith.resumematcher.resume.ResumeDetail;

import java.time.Instant;
import java.util.List;

/** Everything stored about a user (GET /api/me/export). The password hash is deliberately left out. */
public record AccountExport(
        String format,
        Instant exportedAt,
        UserResponse account,
        List<ResumeDetail> resumes,
        List<AnalysisResponse> analyses) {
}
