package com.oet.attempt.repository;

import com.oet.attempt.entity.AttemptAnswer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface AttemptAnswerRepository extends JpaRepository<AttemptAnswer, Long> {

    List<AttemptAnswer> findByAttemptId(Long attemptId);

    Optional<AttemptAnswer> findByAttemptIdAndQuestionId(Long attemptId, Long questionId);

    @Query("""
            SELECT a FROM AttemptAnswer a
            LEFT JOIN FETCH a.question q
            LEFT JOIN FETCH q.correctAnswer ca
            LEFT JOIN FETCH ca.correctOption
            LEFT JOIN FETCH a.selectedOption
            WHERE a.attempt.id = :attemptId
            """)
    List<AttemptAnswer> findByAttemptIdWithDetails(Long attemptId);
}
