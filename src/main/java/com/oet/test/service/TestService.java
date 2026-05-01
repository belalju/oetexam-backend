package com.oet.test.service;

import com.oet.common.exception.BusinessException;
import com.oet.common.exception.NotFoundException;
import com.oet.test.dto.CorrectAnswerResponse;
import com.oet.test.dto.QuestionGroupRequest;
import com.oet.test.dto.QuestionGroupResponse;
import com.oet.test.dto.QuestionOptionResponse;
import com.oet.test.dto.QuestionResponse;
import com.oet.test.dto.TestCreateRequest;
import com.oet.test.dto.TestDetailResponse;
import com.oet.test.dto.TestPartRequest;
import com.oet.test.dto.TestPartResponse;
import com.oet.test.dto.TestSummaryResponse;
import com.oet.test.dto.TextPassageRequest;
import com.oet.test.dto.TextPassageResponse;
import com.oet.test.entity.CorrectAnswer;
import com.oet.test.entity.OetTest;
import com.oet.test.entity.Question;
import com.oet.test.entity.QuestionGroup;
import com.oet.test.entity.QuestionOption;
import com.oet.test.entity.TestPart;
import com.oet.test.entity.TextPassage;
import com.oet.test.repository.QuestionGroupRepository;
import com.oet.test.repository.TestPartRepository;
import com.oet.test.repository.TestRepository;
import com.oet.test.repository.TextPassageRepository;
import com.oet.user.entity.User;
import com.oet.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class TestService {

    private final TestRepository testRepository;
    private final TestPartRepository testPartRepository;
    private final TextPassageRepository textPassageRepository;
    private final QuestionGroupRepository questionGroupRepository;
    private final UserRepository userRepository;

    // ── Admin operations ────────────────────────────────────────────────

    @Transactional
    public TestSummaryResponse createTest(TestCreateRequest request, String creatorEmail) {
        User creator = userRepository.findByEmail(creatorEmail)
                .orElseThrow(() -> new NotFoundException("User not found"));

        OetTest test = OetTest.builder()
                .title(request.title())
                .description(request.description())
                .subTestType(request.subTestType())
                .totalTimeLimitMinutes(request.totalTimeLimitMinutes())
                .createdBy(creator)
                .build();

        OetTest saved = testRepository.save(test);
        log.info("Test created: id={}, title={}", saved.getId(), saved.getTitle());
        return toSummary(saved);
    }

    @Transactional(readOnly = true)
    public Page<TestSummaryResponse> adminListTests(Pageable pageable) {
        return testRepository.findAll(pageable).map(this::toSummary);
    }

    @Transactional(readOnly = true)
    public TestSummaryResponse getTestInformation(Long testId) {
        OetTest oetTest = testRepository.findById(testId)
            .orElseThrow(() -> new NotFoundException("Test not found: " + testId));
        return toSummary(oetTest);
    }

    @Transactional(readOnly = true)
    public TestDetailResponse adminGetTestDetail(Long testId) {
        OetTest test = testRepository.findWithFullDetailById(testId)
                .orElseThrow(() -> new NotFoundException("Test not found: " + testId));
        return toDetail(test, true);
    }

    @Transactional
    public TestSummaryResponse updateTest(Long testId, TestCreateRequest request) {
        OetTest test = findTestById(testId);
        test.setTitle(request.title());
        test.setDescription(request.description());
        test.setSubTestType(request.subTestType());
        test.setTotalTimeLimitMinutes(request.totalTimeLimitMinutes());
        return toSummary(test);
    }

    @Transactional
    public void deleteTest(Long testId) {
        OetTest test = findTestById(testId);
        testRepository.delete(test);
        log.info("Test deleted: id={}", testId);
    }

    @Transactional
    public TestSummaryResponse publishTest(Long testId) {
        OetTest test = findTestById(testId);
        test.setPublished(true);
        return toSummary(test);
    }

    @Transactional
    public TestSummaryResponse unpublishTest(Long testId) {
        OetTest test = findTestById(testId);
        test.setPublished(false);
        return toSummary(test);
    }

    // ── Parts ────────────────────────────────────────────────────────────

    @Transactional
    public TestPartResponse addPart(Long testId, TestPartRequest request) {
        OetTest test = findTestById(testId);
        TestPart part = TestPart.builder()
                .test(test)
                .partLabel(request.partLabel())
                .title(request.title())
                .timeLimitMinutes(request.timeLimitMinutes())
                .instructions(request.instructions())
                .sortOrder(request.sortOrder() != null ? request.sortOrder() : 0)
                .build();
        return toPartResponse(testPartRepository.save(part), true);
    }

    @Transactional
    public TestPartResponse updatePart(Long partId, TestPartRequest request) {
        TestPart part = findPartById(partId);
        part.setTitle(request.title());
        part.setTimeLimitMinutes(request.timeLimitMinutes());
        part.setInstructions(request.instructions());
        if (request.sortOrder() != null) part.setSortOrder(request.sortOrder());
        return toPartResponse(part, true);
    }

    @Transactional
    public void deletePart(Long partId) {
        testPartRepository.delete(findPartById(partId));
    }

    @Transactional(readOnly = true)
    public List<TestPartResponse> getTestPartList(Long testId) {
        List<TestPart> testPartList = testPartRepository.findByTestIdOrderBySortOrderAsc(testId);
        return testPartList.stream().map(part -> toPartResponse(part, false)).toList();
    }

    // ── Passages ─────────────────────────────────────────────────────────

    @Transactional
    public TextPassageResponse addPassage(Long partId, TextPassageRequest request) {
        TestPart part = findPartById(partId);
        TextPassage passage = TextPassage.builder()
                .testPart(part)
                .label(request.label())
                .content(request.content())
                .audioFileUrl(request.audioFileUrl())
                .audioDurationSeconds(request.audioDurationSeconds())
                .sortOrder(request.sortOrder() != null ? request.sortOrder() : 0)
                .build();
        return toPassageResponse(textPassageRepository.save(passage));
    }

    @Transactional
    public TextPassageResponse updatePassage(Long passageId, TextPassageRequest request) {
        TextPassage passage = textPassageRepository.findById(passageId)
                .orElseThrow(() -> new NotFoundException("Passage not found: " + passageId));
        passage.setLabel(request.label());
        passage.setContent(request.content());
        passage.setAudioFileUrl(request.audioFileUrl());
        passage.setAudioDurationSeconds(request.audioDurationSeconds());
        if (request.sortOrder() != null) passage.setSortOrder(request.sortOrder());
        return toPassageResponse(passage);
    }

    @Transactional
    public void deletePassage(Long passageId) {
        textPassageRepository.deleteById(passageId);
    }

    // ── Question Groups ───────────────────────────────────────────────────

    @Transactional
    public QuestionGroupResponse addQuestionGroup(Long partId, QuestionGroupRequest request) {
        TestPart part = findPartById(partId);
        TextPassage passage = null;
        if (request.passageId() != null) {
            passage = textPassageRepository.findById(request.passageId())
                    .orElseThrow(() -> new NotFoundException("Passage not found: " + request.passageId()));
        }

        QuestionGroup group = QuestionGroup.builder()
                .testPart(part)
                .passage(passage)
                .title(request.title())
                .instructions(request.instructions())
                .questionType(request.questionType())
                .sortOrder(request.sortOrder() != null ? request.sortOrder() : 0)
                .build();

        return toGroupResponse(questionGroupRepository.save(group), true);
    }

    @Transactional
    public QuestionGroupResponse updateQuestionGroup(Long groupId, QuestionGroupRequest request) {
        QuestionGroup group = findGroupById(groupId);
        group.setTitle(request.title());
        group.setInstructions(request.instructions());
        if (request.sortOrder() != null) group.setSortOrder(request.sortOrder());
        return toGroupResponse(group, true);
    }

    @Transactional
    public void deleteQuestionGroup(Long groupId) {
        questionGroupRepository.delete(findGroupById(groupId));
    }

    @Transactional(readOnly = true)
    public TestPartResponse getPartById(Long partId) {
        return toPartResponse(findPartById(partId), true);
    }

    @Transactional(readOnly = true)
    public TextPassageResponse getPassageById(Long passageId) {
        TextPassage passage = textPassageRepository.findById(passageId)
                .orElseThrow(() -> new NotFoundException("Passage not found: " + passageId));
        return toPassageResponse(passage);
    }

    @Transactional(readOnly = true)
    public QuestionGroupResponse getQuestionGroupById(Long groupId) {
        return toGroupResponse(findGroupById(groupId), true);
    }

    @Transactional(readOnly = true)
    public List<QuestionGroupResponse> getQuestionGroupsByTestId(Long testId) {
        if (!testRepository.existsById(testId)) {
            throw new NotFoundException("Test not found: " + testId);
        }
        return questionGroupRepository.findByTestPartTestIdOrderBySortOrderAsc(testId)
                .stream()
                .map(g -> toGroupResponse(g, true))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<TextPassageResponse> getPassagesByTestId(Long testId) {
        if (!testRepository.existsById(testId)) {
            throw new NotFoundException("Test not found: " + testId);
        }
        return textPassageRepository.findByTestPartTestIdOrderBySortOrderAsc(testId)
                .stream()
                .map(this::toPassageResponse)
                .toList();
    }

    // ── Applicant operations ──────────────────────────────────────────────

    @Transactional(readOnly = true)
    public Page<TestSummaryResponse> listPublishedTests(Pageable pageable) {
        return testRepository.findAllByPublishedTrue(pageable).map(this::toSummary);
    }

    @Transactional(readOnly = true)
    public TestDetailResponse getTestForApplicant(Long testId) {
        OetTest test = testRepository.findWithFullDetailById(testId)
                .orElseThrow(() -> new NotFoundException("Test not found: " + testId));
        if (!test.isPublished()) {
            throw new BusinessException("Test is not available");
        }
        return toDetail(test, false);
    }

    // ── Private helpers ───────────────────────────────────────────────────

    private OetTest findTestById(Long id) {
        return testRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Test not found: " + id));
    }

    private TestPart findPartById(Long id) {
        return testPartRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Test part not found: " + id));
    }

    private QuestionGroup findGroupById(Long id) {
        return questionGroupRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Question group not found: " + id));
    }

    // ── Mappers ───────────────────────────────────────────────────────────

    private TestSummaryResponse toSummary(OetTest test) {
        String creatorName = test.getCreatedBy().getFirstName() + " " + test.getCreatedBy().getLastName();
        return new TestSummaryResponse(
                test.getId(), test.getTitle(), test.getDescription(),
                test.getSubTestType(), test.getTotalTimeLimitMinutes(),
                test.isPublished(), creatorName, test.getCreatedAt()
        );
    }

    public TestDetailResponse toDetail(OetTest test, boolean includeAnswers) {
        String creatorName = test.getCreatedBy().getFirstName() + " " + test.getCreatedBy().getLastName();
        List<TestPartResponse> parts = test.getParts().stream()
                .map(p -> toPartResponse(p, includeAnswers))
                .toList();
        return new TestDetailResponse(
                test.getId(), test.getTitle(), test.getDescription(),
                test.getSubTestType(), test.getTotalTimeLimitMinutes(),
                test.isPublished(), creatorName, test.getCreatedAt(), parts
        );
    }

    private TestPartResponse toPartResponse(TestPart part, boolean includeAnswers) {
        List<TextPassageResponse> passages = part.getPassages().stream()
                .map(this::toPassageResponse).toList();
        List<QuestionGroupResponse> groups = part.getQuestionGroups().stream()
                .map(g -> toGroupResponse(g, includeAnswers)).toList();
        return new TestPartResponse(part.getId(), part.getPartLabel(), part.getTitle(),
                part.getTimeLimitMinutes(), part.getInstructions(), part.getSortOrder(), passages, groups);
    }

    private TextPassageResponse toPassageResponse(TextPassage p) {
        return new TextPassageResponse(p.getId(), p.getLabel(), p.getContent(),
                p.getAudioFileUrl(), p.getAudioDurationSeconds(), p.getSortOrder());
    }

    private QuestionGroupResponse toGroupResponse(QuestionGroup group, boolean includeAnswers) {
        List<QuestionResponse> questions = group.getQuestions().stream()
                .map(q -> toQuestionResponse(q, includeAnswers)).toList();
        Long passageId = group.getPassage() != null ? group.getPassage().getId() : null;
        return new QuestionGroupResponse(group.getId(), passageId, group.getTitle(),
                group.getInstructions(), group.getQuestionType(), group.getSortOrder(), questions);
    }

    private QuestionResponse toQuestionResponse(Question q, boolean includeAnswers) {
        List<QuestionOptionResponse> options = q.getOptions().stream()
                .map(o -> new QuestionOptionResponse(o.getId(), o.getOptionLabel(), o.getOptionText(), o.getSortOrder()))
                .toList();

        CorrectAnswerResponse correctAnswer = null;
        if (includeAnswers && q.getCorrectAnswer() != null) {
            CorrectAnswer ca = q.getCorrectAnswer();
            Long correctOptionId = ca.getCorrectOption() != null ? ca.getCorrectOption().getId() : null;
            Character optionLabel = ca.getCorrectOption() != null ? ca.getCorrectOption().getOptionLabel() : null;
            correctAnswer = new CorrectAnswerResponse(correctOptionId, optionLabel, ca.getCorrectText(), ca.getAlternativeAnswers());
        }

        return new QuestionResponse(q.getId(), q.getQuestionNumber(), q.getQuestionText(),
                q.getPrefixText(), q.getSuffixText(), q.getSortOrder(), options, correctAnswer);
    }
}
