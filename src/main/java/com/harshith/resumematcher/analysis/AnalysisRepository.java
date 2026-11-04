package com.harshith.resumematcher.analysis;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AnalysisRepository extends JpaRepository<Analysis, Long> {

    Optional<Analysis> findByIdAndUserId(Long id, Long userId);

    List<Analysis> findTop100ByUserIdOrderByCreatedAtDescIdDesc(Long userId);
}
