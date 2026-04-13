package com.oet.test.repository;

import com.oet.test.entity.CorrectAnswer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface CorrectAnswerRepository extends JpaRepository<CorrectAnswer, Long> {

    Optional<CorrectAnswer> findByQuestionId(Long questionId);

    @Query("SELECT ca FROM CorrectAnswer ca JOIN ca.question q JOIN q.questionGroup qg JOIN qg.testPart tp WHERE tp.test.id = :testId")
    List<CorrectAnswer> findAllByTestId(Long testId);
}
