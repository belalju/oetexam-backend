package com.oet.test.repository;

import com.oet.test.entity.QuestionOption;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface QuestionOptionRepository extends JpaRepository<QuestionOption, Long> {

    @Modifying
    @Query("DELETE FROM QuestionOption o WHERE o.question.id = :questionId")
    void deleteAllByQuestionId(Long questionId);
}
