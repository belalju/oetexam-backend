package com.oet.test.repository;

import com.oet.test.entity.TextPassage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TextPassageRepository extends JpaRepository<TextPassage, Long> {

    List<TextPassage> findByTestPartTestIdOrderBySortOrderAsc(Long testId);
}
