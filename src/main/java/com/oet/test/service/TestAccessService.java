package com.oet.test.service;

import com.oet.common.exception.PackageAccessException;
import com.oet.test.entity.OetTest;
import com.oet.test.enums.SubTestType;
import com.oet.test.repository.TestRepository;
import com.oet.user.entity.User;
import com.oet.user.entity.UserRole;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

/**
 * Decides which published tests an applicant's pricing package unlocks.
 * A package grants its first {@code noOfAllowedTest} published tests per module (Reading, Listening),
 * oldest first, so the set does not shift when new tests are published. Admins are unrestricted;
 * applicants without a package, or whose package has expired, get no tests.
 */
@Service
@RequiredArgsConstructor
public class TestAccessService {

    private final TestRepository testRepository;
    private final Clock clock;

    public boolean isUnrestricted(User user) {
        return user.getRole() == UserRole.ADMIN;
    }

    /**
     * Ids of the published tests the user's package unlocks; {@code subTestType} null means every module.
     * Not meaningful for unrestricted users — check {@link #isUnrestricted(User)} first.
     */
    @Transactional(readOnly = true)
    public List<Long> accessibleTestIds(User user, SubTestType subTestType) {
        if (!hasActivePackage(user)) {
            return List.of();
        }
        List<SubTestType> modules = subTestType == null ? Arrays.asList(SubTestType.values()) : List.of(subTestType);
        Pageable limit = Pageable.ofSize(user.getNoOfAllowedTest());
        return modules.stream()
                .flatMap(module -> testRepository.findPublishedIdsInPublishOrder(module, limit).stream())
                .toList();
    }

    @Transactional(readOnly = true)
    public void assertCanAccess(User user, OetTest test) {
        if (isUnrestricted(user)) {
            return;
        }
        if (!hasActivePackage(user)) {
            throw new PackageAccessException("An active package is required to access tests");
        }
        if (!accessibleTestIds(user, test.getSubTestType()).contains(test.getId())) {
            throw new PackageAccessException("This test is not included in your package");
        }
    }

    private boolean hasActivePackage(User user) {
        return user.getNoOfAllowedTest() != null
                && user.getNoOfAllowedTest() > 0
                && user.getPackageExpirationDate() != null
                && user.getPackageExpirationDate().isAfter(LocalDateTime.now(clock));
    }
}
