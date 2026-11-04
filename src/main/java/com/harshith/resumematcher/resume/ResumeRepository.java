package com.harshith.resumematcher.resume;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface ResumeRepository extends JpaRepository<Resume, Long> {

    // Every lookup includes the owner, so one user can never reach another user's resume.

    Optional<Resume> findByIdAndUserId(Long id, Long userId);

    long countByUserId(Long userId);

    /** List view: no full text is loaded, only its length. */
    @Query("""
            select new com.harshith.resumematcher.resume.ResumeSummary(
                r.id, r.fileName, length(r.extractedText), r.createdAt)
            from Resume r
            where r.userId = :userId
            order by r.createdAt desc, r.id desc
            """)
    List<ResumeSummary> findSummariesByUserId(Long userId);

    @Modifying
    @Query("delete from Resume r where r.id = :id and r.userId = :userId")
    int deleteByIdAndUserId(Long id, Long userId);

    List<Resume> findAllByUserIdOrderByCreatedAtDescIdDesc(Long userId);

    /** Retention: resumes created before the cutoff. */
    @Modifying
    @Query("delete from Resume r where r.createdAt < :cutoff")
    int deleteCreatedBefore(Instant cutoff);
}
