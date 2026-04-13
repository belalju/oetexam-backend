package com.oet.test.repository;

import com.oet.test.entity.TestPart;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TestPartRepository extends JpaRepository<TestPart, Long> {

    List<TestPart> findByTestIdOrderBySortOrderAsc(Long testId);
}
