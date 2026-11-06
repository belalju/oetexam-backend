package com.oet.test.repository;

import com.oet.test.entity.Question;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface QuestionRepository extends JpaRepository<Question, Long> {

    @Query("""
            SELECT q FROM Question q
            LEFT JOIN FETCH q.correctAnswer ca
            LEFT JOIN FETCH ca.correctOption
            JOIN FETCH q.questionGroup qg JOIN FETCH qg.testPart tp
            WHERE tp.test.id = :testId
            ORDER BY tp.sortOrder, tp.partLabel, q.questionNumber
            """)
    List<Question> findAllByTestIdWithCorrectAnswers(Long testId);

    List<Question> findByQuestionGroupIdOrderBySortOrderAsc(Long groupId);
}
