package com.oet.test.repository;

import com.oet.test.entity.OetTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface TestRepository extends JpaRepository<OetTest, Long> {

    Page<OetTest> findAllByPublishedTrue(Pageable pageable);

    @Query("SELECT DISTINCT t FROM OetTest t LEFT JOIN FETCH t.parts WHERE t.id = :id")
    Optional<OetTest> findWithFullDetailById(@Param("id") Long id);
}
