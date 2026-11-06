package com.oet.test.repository;

import com.oet.test.entity.OetTest;
import com.oet.test.enums.SubTestType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface TestRepository extends JpaRepository<OetTest, Long> {

    Page<OetTest> findAllByPublishedTrue(Pageable pageable);

    Page<OetTest> findAllByPublishedTrueAndSubTestType(SubTestType subTestType, Pageable pageable);

    // Publish order defines which tests a package unlocks; pass Pageable.ofSize(n) for the first n.
    @Query("SELECT t.id FROM OetTest t WHERE t.published = true AND t.subTestType = :subTestType "
            + "ORDER BY t.createdAt ASC, t.id ASC")
    List<Long> findPublishedIdsInPublishOrder(@Param("subTestType") SubTestType subTestType, Pageable limit);

    Page<OetTest> findAllByIdIn(Collection<Long> ids, Pageable pageable);

    @Query("SELECT DISTINCT t FROM OetTest t LEFT JOIN FETCH t.parts WHERE t.id = :id")
    Optional<OetTest> findWithFullDetailById(@Param("id") Long id);
}
