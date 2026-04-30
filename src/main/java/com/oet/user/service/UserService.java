package com.oet.user.service;

import com.oet.common.exception.NotFoundException;
import com.oet.user.dto.UserProfileDto;
import com.oet.user.entity.User;
import com.oet.user.entity.UserRole;
import com.oet.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public UserProfileDto getProfile(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new NotFoundException("User not found"));
        return toDto(user);
    }

    @Transactional(readOnly = true)
    public UserProfileDto getUserById(Long id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("User not found with id: " + id));
        return toDto(user);
    }

    @Transactional(readOnly = true)
    public List<UserProfileDto> getAllApplicants() {
        return userRepository.findByRoleNot(UserRole.ADMIN)
                .stream()
                .map(this::toDto)
                .toList();
    }

    @Transactional
    public UserProfileDto toggleUserStatus(Long id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("User not found with id: " + id));
        user.setActive(!user.isActive());
        return toDto(userRepository.save(user));
    }

    private UserProfileDto toDto(User user) {
        return new UserProfileDto(
                user.getId(),
                user.getEmail(),
                user.getFirstName(),
                user.getLastName(),
                user.getRole(),
                user.getProfession(),
                user.isActive(),
                user.getCreatedAt()
        );
    }
}
