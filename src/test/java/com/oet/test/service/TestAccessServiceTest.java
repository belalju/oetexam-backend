package com.oet.test.service;

import com.oet.common.exception.PackageAccessException;
import com.oet.test.entity.OetTest;
import com.oet.test.enums.SubTestType;
import com.oet.test.repository.TestRepository;
import com.oet.user.entity.User;
import com.oet.user.entity.UserRole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TestAccessServiceTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 5, 12, 0);

    @Mock
    private TestRepository testRepository;

    private TestAccessService service;

    @BeforeEach
    void setUp() {
        Clock fixed = Clock.fixed(NOW.toInstant(ZoneOffset.UTC), ZoneOffset.UTC);
        service = new TestAccessService(testRepository, fixed);
    }

    @Test
    void accessibleTestIds_activePackage_limitsEachModuleToAllowedCount() {
        User user = applicant(2, NOW.plusDays(10));
        when(testRepository.findPublishedIdsInPublishOrder(SubTestType.READING, Pageable.ofSize(2)))
                .thenReturn(List.of(1L, 2L));
        when(testRepository.findPublishedIdsInPublishOrder(SubTestType.LISTENING, Pageable.ofSize(2)))
                .thenReturn(List.of(5L, 6L));

        assertThat(service.accessibleTestIds(user, null)).containsExactlyInAnyOrder(1L, 2L, 5L, 6L);
        assertThat(service.accessibleTestIds(user, SubTestType.READING)).containsExactly(1L, 2L);
    }

    @Test
    void accessibleTestIds_expiredPackage_returnsNothing() {
        User user = applicant(35, NOW.minusSeconds(1));

        assertThat(service.accessibleTestIds(user, null)).isEmpty();
        verifyNoInteractions(testRepository);
    }

    @Test
    void accessibleTestIds_noPackage_returnsNothing() {
        User user = applicant(null, null);

        assertThat(service.accessibleTestIds(user, SubTestType.LISTENING)).isEmpty();
        verifyNoInteractions(testRepository);
    }

    @Test
    void assertCanAccess_testInsideAllowance_passes() {
        User user = applicant(1, NOW.plusDays(1));
        when(testRepository.findPublishedIdsInPublishOrder(any(), any())).thenReturn(List.of(7L));

        assertThatCode(() -> service.assertCanAccess(user, test(7L))).doesNotThrowAnyException();
    }

    @Test
    void assertCanAccess_testOutsideAllowance_throws() {
        User user = applicant(1, NOW.plusDays(1));
        when(testRepository.findPublishedIdsInPublishOrder(any(), any())).thenReturn(List.of(7L));

        assertThatThrownBy(() -> service.assertCanAccess(user, test(8L)))
                .isInstanceOf(PackageAccessException.class)
                .hasMessageContaining("not included");
    }

    @Test
    void assertCanAccess_expiredPackage_throws() {
        User user = applicant(35, NOW.minusDays(1));

        assertThatThrownBy(() -> service.assertCanAccess(user, test(7L)))
                .isInstanceOf(PackageAccessException.class)
                .hasMessageContaining("active package");
    }

    @Test
    void assertCanAccess_admin_isUnrestricted() {
        User admin = User.builder().role(UserRole.ADMIN).build();

        assertThatCode(() -> service.assertCanAccess(admin, test(99L))).doesNotThrowAnyException();
        verifyNoInteractions(testRepository);
    }

    private static User applicant(Integer allowedTests, LocalDateTime expiresAt) {
        return User.builder()
                .role(UserRole.APPLICANT)
                .noOfAllowedTest(allowedTests)
                .packageExpirationDate(expiresAt)
                .build();
    }

    private static OetTest test(Long id) {
        return OetTest.builder().id(id).subTestType(SubTestType.READING).published(true).build();
    }
}
