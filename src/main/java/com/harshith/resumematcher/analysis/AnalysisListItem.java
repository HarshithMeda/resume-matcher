package com.harshith.resumematcher.analysis;

import java.time.Instant;

/** One row in GET /api/analyses. */
public record AnalysisListItem(Long id, Long resumeId, int matchScore, String summary, Instant createdAt) {
}
