package com.harshith.resumematcher.analysis;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;

@Entity
@Table(name = "analyses")
public class Analysis {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false, updatable = false)
    private Long userId;

    @Column(name = "resume_id", nullable = false, updatable = false)
    private Long resumeId;

    @Column(name = "match_score", nullable = false, updatable = false)
    private Integer matchScore;

    /** {@link AnalysisResult} as JSON. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "result", nullable = false, updatable = false, columnDefinition = "jsonb")
    private String result;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected Analysis() {
        // for JPA
    }

    public Analysis(Long userId, Long resumeId, int matchScore, String resultJson) {
        this.userId = userId;
        this.resumeId = resumeId;
        this.matchScore = matchScore;
        this.result = resultJson;
        this.createdAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public Long getUserId() {
        return userId;
    }

    public Long getResumeId() {
        return resumeId;
    }

    public Integer getMatchScore() {
        return matchScore;
    }

    public String getResult() {
        return result;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
