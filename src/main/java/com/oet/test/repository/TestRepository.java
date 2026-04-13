package com.oet.test.repository;

import com.oet.test.entity.OetTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface TestRepository extends JpaRepository<OetTest, Long> {

    Page<OetTest> findAllByPublishedTrue(Pageable pageable);

    @EntityGraph(attributePaths = {"parts", "parts.passages", "parts.questionGroups",
            "parts.questionGroups.questions", "parts.questionGroups.questions.options",
            "parts.questionGroups.questions.correctAnswer",
            "parts.questionGroups.questions.correctAnswer.correctOption"})
    Optional<OetTest> findWithFullDetailById(Long id);
}
