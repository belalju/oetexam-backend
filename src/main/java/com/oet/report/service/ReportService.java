package com.oet.report.service;

import com.oet.attempt.entity.TestAttempt;
import com.oet.attempt.repository.TestAttemptRepository;
import com.oet.common.exception.NotFoundException;
import com.oet.report.dto.TestAnalyticsResponse;
import com.oet.report.dto.UserScoreReport;
import com.oet.test.entity.OetTest;
import com.oet.test.repository.TestRepository;
import com.oet.user.entity.User;
import com.oet.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ReportService {

    private final TestRepository testRepository;
    private final TestAttemptRepository attemptRepository;
    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public TestAnalyticsResponse getTestAnalytics(Long testId) {
        OetTest test = testRepository.findById(testId)
                .orElseThrow(() -> new NotFoundException("Test not found: " + testId));

        long total = attemptRepository.count();
        long completed = attemptRepository.countCompletedByTestId(testId);
        Double avgScore = attemptRepository.averageScoreByTestId(testId);

        // Approximate max score from first completed attempt
        List<TestAttempt> completedAttempts = attemptRepository
                .findByTestIdAndStatus(testId, com.oet.attempt.entity.AttemptStatus.COMPLETED);

        Integer maxScore = completedAttempts.stream()
                .map(TestAttempt::getMaxScore)
                .filter(s -> s != null && s > 0)
                .findFirst()
                .orElse(null);

        Double avgPercentage = null;
        if (avgScore != null && maxScore != null && maxScore > 0) {
            avgPercentage = Math.round(avgScore * 100.0 / maxScore * 10) / 10.0;
        }

        return new TestAnalyticsResponse(
                test.getId(),
                test.getTitle(),
                test.getSubTestType(),
                total,
                completed,
                avgScore != null ? Math.round(avgScore * 10.0) / 10.0 : null,
                avgPercentage,
                maxScore
        );
    }

    @Transactional(readOnly = true)
    public UserScoreReport getUserReport(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("User not found: " + userId));

        List<TestAttempt> attempts = attemptRepository
                .findByUserIdOrderByStartedAtDesc(userId, Pageable.unpaged())
                .getContent();

        List<UserScoreReport.AttemptSummary> summaries = attempts.stream()
                .map(a -> new UserScoreReport.AttemptSummary(
                        a.getId(),
                        a.getTest().getId(),
                        a.getTest().getTitle(),
                        a.getTest().getSubTestType(),
                        a.getStatus(),
                        a.getTotalScore(),
                        a.getMaxScore(),
                        a.getTotalScore() != null && a.getMaxScore() != null && a.getMaxScore() > 0
                                ? Math.round(a.getTotalScore() * 100.0 / a.getMaxScore() * 10) / 10.0
                                : null,
                        a.getTimeSpentSeconds(),
                        a.getStartedAt(),
                        a.getCompletedAt()
                ))
                .toList();

        return new UserScoreReport(
                user.getId(),
                user.getFirstName() + " " + user.getLastName(),
                user.getEmail(),
                summaries
        );
    }
}
