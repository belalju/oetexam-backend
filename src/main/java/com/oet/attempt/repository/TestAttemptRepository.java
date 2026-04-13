package com.oet.attempt.repository;

import com.oet.attempt.entity.AttemptStatus;
import com.oet.attempt.entity.TestAttempt;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface TestAttemptRepository extends JpaRepository<TestAttempt, Long> {

    Page<TestAttempt> findByUserIdOrderByStartedAtDesc(Long userId, Pageable pageable);

    Optional<TestAttempt> findByIdAndUserId(Long id, Long userId);

    List<TestAttempt> findByTestIdAndStatus(Long testId, AttemptStatus status);

    @Query("SELECT COUNT(a) FROM TestAttempt a WHERE a.test.id = :testId AND a.status = 'COMPLETED'")
    long countCompletedByTestId(Long testId);

    @Query("SELECT AVG(a.totalScore) FROM TestAttempt a WHERE a.test.id = :testId AND a.status = 'COMPLETED'")
    Double averageScoreByTestId(Long testId);
}
