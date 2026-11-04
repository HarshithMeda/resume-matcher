package com.harshith.resumematcher.privacy;

import com.harshith.resumematcher.analysis.AnalysisRepository;
import com.harshith.resumematcher.resume.ResumeRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

/**
 * Deletes resumes and analyses older than app.retention.days (default 90), every day at the
 * time in app.retention.cron (UTC). Accounts are kept until the user deletes them.
 */
@Service
public class RetentionService {

    private static final Logger log = LoggerFactory.getLogger(RetentionService.class);

    public record Purged(int resumes, int analyses) {
    }

    private final ResumeRepository resumes;
    private final AnalysisRepository analyses;
    private final Clock clock;
    private final int retentionDays;

    public RetentionService(ResumeRepository resumes, AnalysisRepository analyses, Clock clock,
                            @Value("${app.retention.days}") int retentionDays) {
        if (retentionDays < 1) {
            throw new IllegalStateException("app.retention.days must be at least 1");
        }
        this.resumes = resumes;
        this.analyses = analyses;
        this.clock = clock;
        this.retentionDays = retentionDays;
    }

    public int retentionDays() {
        return retentionDays;
    }

    @Scheduled(cron = "${app.retention.cron}", zone = "UTC")
    public void scheduledPurge() {
        Purged purged = purgeExpired();
        // Counts only: never anything from the deleted rows.
        log.info("Retention: deleted {} resumes and {} analyses older than {} days",
                purged.resumes(), purged.analyses(), retentionDays);
    }

    @Transactional
    public Purged purgeExpired() {
        Instant cutoff = clock.instant().minus(Duration.ofDays(retentionDays));
        int analysesDeleted = analyses.deleteExpired(cutoff); // first, so the count includes cascaded ones
        int resumesDeleted = resumes.deleteCreatedBefore(cutoff);
        return new Purged(resumesDeleted, analysesDeleted);
    }
}
