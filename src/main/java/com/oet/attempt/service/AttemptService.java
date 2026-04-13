package com.oet.attempt.service;

import com.oet.attempt.dto.*;
import com.oet.attempt.entity.AttemptAnswer;
import com.oet.attempt.entity.AttemptStatus;
import com.oet.attempt.entity.TestAttempt;
import com.oet.attempt.repository.AttemptAnswerRepository;
import com.oet.attempt.repository.TestAttemptRepository;
import com.oet.common.exception.BusinessException;
import com.oet.common.exception.NotFoundException;
import com.oet.test.entity.*;
import com.oet.test.repository.CorrectAnswerRepository;
import com.oet.test.repository.QuestionOptionRepository;
import com.oet.test.repository.QuestionRepository;
import com.oet.test.repository.TestRepository;
import com.oet.test.service.TestService;
import com.oet.user.entity.User;
import com.oet.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class AttemptService {

    private final TestAttemptRepository attemptRepository;
    private final AttemptAnswerRepository answerRepository;
    private final TestRepository testRepository;
    private final QuestionRepository questionRepository;
    private final QuestionOptionRepository questionOptionRepository;
    private final CorrectAnswerRepository correctAnswerRepository;
    private final UserRepository userRepository;
    private final GradingService gradingService;
    private final TestService testService;

    @Transactional
    public StartAttemptResponse startAttempt(Long testId, String userEmail) {
        User user = findUserByEmail(userEmail);
        OetTest test = testRepository.findById(testId)
                .orElseThrow(() -> new NotFoundException("Test not found: " + testId));

        if (!test.isPublished()) {
            throw new BusinessException("Test is not published");
        }

        // Check for existing in-progress attempt
        boolean hasActive = attemptRepository
                .findByUserIdOrderByStartedAtDesc(user.getId(), Pageable.ofSize(1))
                .stream()
                .anyMatch(a -> a.getTest().getId().equals(testId) && a.getStatus() == AttemptStatus.IN_PROGRESS);

        if (hasActive) {
            throw new BusinessException("You already have an active attempt for this test");
        }

        TestAttempt attempt = TestAttempt.builder()
                .user(user)
                .test(test)
                .status(AttemptStatus.IN_PROGRESS)
                .build();

        TestAttempt saved = attemptRepository.save(attempt);
        log.info("Attempt started: userId={}, testId={}, attemptId={}", user.getId(), testId, saved.getId());

        return new StartAttemptResponse(
                saved.getId(),
                saved.getStartedAt(),
                test.getTotalTimeLimitMinutes(),
                testService.toDetail(test, false)
        );
    }

    @Transactional(readOnly = true)
    public StartAttemptResponse resumeAttempt(Long attemptId, String userEmail) {
        User user = findUserByEmail(userEmail);
        TestAttempt attempt = findAttemptForUser(attemptId, user.getId());

        if (attempt.getStatus() != AttemptStatus.IN_PROGRESS) {
            throw new BusinessException("Attempt is no longer in progress");
        }

        return new StartAttemptResponse(
                attempt.getId(),
                attempt.getStartedAt(),
                attempt.getTest().getTotalTimeLimitMinutes(),
                testService.toDetail(attempt.getTest(), false)
        );
    }

    @Transactional
    public void saveAnswer(Long attemptId, SaveAnswerRequest request, String userEmail) {
        User user = findUserByEmail(userEmail);
        TestAttempt attempt = findAttemptForUser(attemptId, user.getId());

        if (attempt.getStatus() != AttemptStatus.IN_PROGRESS) {
            throw new BusinessException("Attempt is not in progress");
        }

        Question question = questionRepository.findById(request.questionId())
                .orElseThrow(() -> new NotFoundException("Question not found: " + request.questionId()));

        AttemptAnswer answer = answerRepository
                .findByAttemptIdAndQuestionId(attemptId, request.questionId())
                .orElseGet(() -> AttemptAnswer.builder()
                        .attempt(attempt)
                        .question(question)
                        .build());

        if (request.selectedOptionId() != null) {
            QuestionOption option = questionOptionRepository.findById(request.selectedOptionId())
                    .orElseThrow(() -> new NotFoundException("Option not found: " + request.selectedOptionId()));
            answer.setSelectedOption(option);
            answer.setAnswerText(null);
        } else {
            answer.setAnswerText(request.answerText());
            answer.setSelectedOption(null);
        }

        answerRepository.save(answer);
    }

    @Transactional
    public SubmitAttemptResponse submitAttempt(Long attemptId, String userEmail) {
        User user = findUserByEmail(userEmail);
        TestAttempt attempt = findAttemptForUser(attemptId, user.getId());

        if (attempt.getStatus() != AttemptStatus.IN_PROGRESS) {
            throw new BusinessException("Attempt is not in progress");
        }

        return gradeAndComplete(attempt, AttemptStatus.COMPLETED);
    }

    @Transactional
    public SubmitAttemptResponse timeoutAttempt(Long attemptId) {
        TestAttempt attempt = attemptRepository.findById(attemptId)
                .orElseThrow(() -> new NotFoundException("Attempt not found: " + attemptId));

        if (attempt.getStatus() != AttemptStatus.IN_PROGRESS) {
            return buildSubmitResponse(attempt);
        }

        return gradeAndComplete(attempt, AttemptStatus.TIMED_OUT);
    }

    @Transactional(readOnly = true)
    public AttemptResultResponse getResults(Long attemptId, String userEmail) {
        User user = findUserByEmail(userEmail);
        TestAttempt attempt = findAttemptForUser(attemptId, user.getId());

        if (attempt.getStatus() == AttemptStatus.IN_PROGRESS) {
            throw new BusinessException("Attempt is still in progress");
        }

        List<AttemptAnswer> answers = answerRepository.findByAttemptIdWithDetails(attemptId);

        List<AttemptAnswerResult> answerResults = answers.stream()
                .map(this::toAnswerResult)
                .toList();

        int timeSpent = attempt.getTimeSpentSeconds() != null ? attempt.getTimeSpentSeconds() : 0;

        return new AttemptResultResponse(
                attempt.getId(),
                attempt.getTest().getTitle(),
                attempt.getStatus(),
                attempt.getTotalScore(),
                attempt.getMaxScore(),
                attempt.getTotalScore() != null && attempt.getMaxScore() != null && attempt.getMaxScore() > 0
                        ? Math.round(attempt.getTotalScore() * 100.0 / attempt.getMaxScore() * 10) / 10.0
                        : 0.0,
                timeSpent,
                attempt.getStartedAt(),
                attempt.getCompletedAt(),
                answerResults
        );
    }

    @Transactional(readOnly = true)
    public Page<AttemptHistoryResponse> getHistory(String userEmail, Pageable pageable) {
        User user = findUserByEmail(userEmail);
        return attemptRepository.findByUserIdOrderByStartedAtDesc(user.getId(), pageable)
                .map(this::toHistoryResponse);
    }

    // ── Private helpers ───────────────────────────────────────────────────

    private SubmitAttemptResponse gradeAndComplete(TestAttempt attempt, AttemptStatus finalStatus) {
        List<Question> questions = questionRepository.findAllByTestIdWithCorrectAnswers(attempt.getTest().getId());
        List<AttemptAnswer> answers = answerRepository.findByAttemptIdWithDetails(attempt.getId());

        int totalScore = 0;
        int maxScore = questions.size();

        for (AttemptAnswer answer : answers) {
            Question question = questions.stream()
                    .filter(q -> q.getId().equals(answer.getQuestion().getId()))
                    .findFirst()
                    .orElse(null);

            if (question != null) {
                boolean correct = gradingService.grade(answer, question);
                answer.setCorrect(correct);
                if (correct) totalScore++;
            }
        }

        answerRepository.saveAll(answers);

        LocalDateTime completedAt = LocalDateTime.now();
        int timeSpent = (int) ChronoUnit.SECONDS.between(attempt.getStartedAt(), completedAt);
        double percentage = maxScore > 0 ? Math.round(totalScore * 100.0 / maxScore * 10) / 10.0 : 0.0;

        attempt.setStatus(finalStatus);
        attempt.setCompletedAt(completedAt);
        attempt.setTotalScore(totalScore);
        attempt.setMaxScore(maxScore);
        attempt.setTimeSpentSeconds(timeSpent);
        attemptRepository.save(attempt);

        log.info("Attempt {} completed: score={}/{}, status={}", attempt.getId(), totalScore, maxScore, finalStatus);

        return new SubmitAttemptResponse(
                attempt.getId(), finalStatus, totalScore, maxScore, percentage, timeSpent, completedAt
        );
    }

    private SubmitAttemptResponse buildSubmitResponse(TestAttempt attempt) {
        double percentage = attempt.getTotalScore() != null && attempt.getMaxScore() != null && attempt.getMaxScore() > 0
                ? Math.round(attempt.getTotalScore() * 100.0 / attempt.getMaxScore() * 10) / 10.0
                : 0.0;
        return new SubmitAttemptResponse(
                attempt.getId(), attempt.getStatus(), attempt.getTotalScore(),
                attempt.getMaxScore(), percentage, attempt.getTimeSpentSeconds(), attempt.getCompletedAt()
        );
    }

    private AttemptAnswerResult toAnswerResult(AttemptAnswer answer) {
        Question q = answer.getQuestion();
        CorrectAnswer ca = q.getCorrectAnswer();

        Long selectedOptionId = answer.getSelectedOption() != null ? answer.getSelectedOption().getId() : null;
        Long correctOptionId = ca != null && ca.getCorrectOption() != null ? ca.getCorrectOption().getId() : null;
        String correctText = ca != null ? ca.getCorrectText() : null;

        return new AttemptAnswerResult(
                q.getId(),
                q.getQuestionNumber(),
                q.getQuestionText(),
                selectedOptionId,
                answer.getAnswerText(),
                correctOptionId,
                correctText,
                Boolean.TRUE.equals(answer.getCorrect())
        );
    }

    private AttemptHistoryResponse toHistoryResponse(TestAttempt attempt) {
        return new AttemptHistoryResponse(
                attempt.getId(),
                attempt.getTest().getId(),
                attempt.getTest().getTitle(),
                attempt.getTest().getSubTestType(),
                attempt.getStatus(),
                attempt.getTotalScore(),
                attempt.getMaxScore(),
                attempt.getTotalScore() != null && attempt.getMaxScore() != null && attempt.getMaxScore() > 0
                        ? Math.round(attempt.getTotalScore() * 100.0 / attempt.getMaxScore() * 10) / 10.0
                        : null,
                attempt.getStartedAt(),
                attempt.getCompletedAt()
        );
    }

    private TestAttempt findAttemptForUser(Long attemptId, Long userId) {
        return attemptRepository.findByIdAndUserId(attemptId, userId)
                .orElseThrow(() -> new NotFoundException("Attempt not found: " + attemptId));
    }

    private User findUserByEmail(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new NotFoundException("User not found: " + email));
    }
}
