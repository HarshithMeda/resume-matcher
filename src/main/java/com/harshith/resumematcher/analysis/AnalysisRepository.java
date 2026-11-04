package com.harshith.resumematcher.analysis;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface AnalysisRepository extends JpaRepository<Analysis, Long> {

    Optional<Analysis> findByIdAndUserId(Long id, Long userId);

    List<Analysis> findTop100ByUserIdOrderByCreatedAtDescIdDesc(Long userId);

    List<Analysis> findAllByUserIdOrderByCreatedAtDescIdDesc(Long userId);

    long countByUserId(Long userId);

    /** Retention: analyses past the cutoff, plus those of resumes past the cutoff (they'd cascade anyway). */
    @Modifying
    @Query("""
            delete from Analysis a
            where a.createdAt < :cutoff
               or a.resumeId in (select r.id from Resume r where r.createdAt < :cutoff)
            """)
    int deleteExpired(Instant cutoff);
}
