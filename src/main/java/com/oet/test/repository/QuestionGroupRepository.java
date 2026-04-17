package com.oet.test.repository;

import com.oet.test.entity.QuestionGroup;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface QuestionGroupRepository extends JpaRepository<QuestionGroup, Long> {

    List<QuestionGroup> findByTestPartTestIdOrderBySortOrderAsc(Long testId);
}
