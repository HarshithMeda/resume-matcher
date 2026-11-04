package com.harshith.resumematcher.resume;

import java.time.Instant;

public record ResumeDetail(Long id, String fileName, int characterCount, Instant createdAt, String extractedText) {

    public static ResumeDetail from(Resume r) {
        return new ResumeDetail(r.getId(), r.getFileName(), r.getExtractedText().length(),
                r.getCreatedAt(), r.getExtractedText());
    }
}
