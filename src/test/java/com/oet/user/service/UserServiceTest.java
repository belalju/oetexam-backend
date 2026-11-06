package com.oet.user.service;

import com.oet.common.exception.NotFoundException;
import com.oet.user.dto.UserProfileDto;
import com.oet.user.entity.User;
import com.oet.user.entity.UserRole;
import com.oet.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    private static final String EMAIL = "applicant@example.com";

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private UserService userService;

    @Test
    void getProfile_paidUser_includesPackageDetails() {
        LocalDateTime expiresAt = LocalDateTime.of(2026, 12, 31, 10, 0);
        User user = applicant();
        user.setPackageCode("gold");
        user.setNoOfAllowedTest(35);
        user.setPackageExpirationDate(expiresAt);
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user));

        UserProfileDto profile = userService.getProfile(EMAIL);

        assertThat(profile.packageCode()).isEqualTo("gold");
        assertThat(profile.noOfAllowedTest()).isEqualTo(35);
        assertThat(profile.packageExpirationDate()).isEqualTo(expiresAt);
    }

    @Test
    void getProfile_unpaidUser_leavesPackageDetailsNull() {
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(applicant()));

        UserProfileDto profile = userService.getProfile(EMAIL);

        assertThat(profile.email()).isEqualTo(EMAIL);
        assertThat(profile.packageCode()).isNull();
        assertThat(profile.noOfAllowedTest()).isNull();
        assertThat(profile.packageExpirationDate()).isNull();
    }

    @Test
    void getProfile_unknownUser_throwsNotFound() {
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.getProfile(EMAIL)).isInstanceOf(NotFoundException.class);
    }

    private static User applicant() {
        return User.builder().email(EMAIL).firstName("Test").lastName("User").role(UserRole.APPLICANT).build();
    }
}
