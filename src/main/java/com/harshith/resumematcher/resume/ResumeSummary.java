package com.harshith.resumematcher.resume;

import java.time.Instant;

/** One row in GET /api/resumes: everything except the text itself. */
public record ResumeSummary(Long id, String fileName, Integer characterCount, Instant createdAt) {
}
