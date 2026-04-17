package com.oet.test.service;

import com.oet.common.exception.NotFoundException;
import com.oet.test.dto.*;
import com.oet.test.entity.*;
import com.oet.test.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class QuestionService {

    private final QuestionRepository questionRepository;
    private final QuestionOptionRepository questionOptionRepository;
    private final CorrectAnswerRepository correctAnswerRepository;
    private final QuestionGroupRepository questionGroupRepository;

    @Transactional
    public QuestionResponse createQuestion(Long groupId, QuestionCreateRequest request) {
        QuestionGroup group = questionGroupRepository.findById(groupId)
                .orElseThrow(() -> new NotFoundException("Question group not found: " + groupId));

        Question question = Question.builder()
                .questionGroup(group)
                .questionNumber(request.questionNumber())
                .questionText(request.questionText())
                .prefixText(request.prefixText())
                .suffixText(request.suffixText())
                .sortOrder(request.sortOrder() != null ? request.sortOrder() : 0)
                .build();

        question = questionRepository.save(question);

        List<QuestionOption> savedOptions = new ArrayList<>();
        if (request.options() != null) {
            for (QuestionOptionRequest optReq : request.options()) {
                QuestionOption option = QuestionOption.builder()
                        .question(question)
                        .optionLabel(optReq.optionLabel())
                        .optionText(optReq.optionText())
                        .sortOrder(optReq.sortOrder() != null ? optReq.sortOrder() : 0)
                        .build();
                savedOptions.add(questionOptionRepository.save(option));
            }
        }

        QuestionOption correctOption = null;
        if (request.correctOptionId() != null) {
            final Long correctOptionId = request.correctOptionId();
            correctOption = savedOptions.stream()
                    .filter(o -> o.getId().equals(correctOptionId))
                    .findFirst()
                    .orElseThrow(() -> new NotFoundException("Correct option not found in saved options"));
        }

        CorrectAnswer correctAnswer = CorrectAnswer.builder()
                .question(question)
                .correctOption(correctOption)
                .correctText(request.correctText())
                .alternativeAnswers(request.alternativeAnswers() != null ? request.alternativeAnswers() : new ArrayList<>())
                .build();
        correctAnswerRepository.save(correctAnswer);

        log.info("Question created: id={}, number={}", question.getId(), question.getQuestionNumber());
        return toResponse(question, savedOptions, correctAnswer);
    }

    @Transactional
    public QuestionResponse updateQuestion(Long questionId, QuestionCreateRequest request) {
        Question question = findById(questionId);
        question.setQuestionNumber(request.questionNumber());
        question.setQuestionText(request.questionText());
        question.setPrefixText(request.prefixText());
        question.setSuffixText(request.suffixText());
        if (request.sortOrder() != null) question.setSortOrder(request.sortOrder());

        CorrectAnswer ca = correctAnswerRepository.findByQuestionId(questionId).orElse(null);
        if (ca != null) {
            ca.setCorrectText(request.correctText());
            if (request.alternativeAnswers() != null) ca.setAlternativeAnswers(request.alternativeAnswers());
        }

        return toResponse(question, question.getOptions(), ca);
    }

    @Transactional(readOnly = true)
    public QuestionResponse getQuestionById(Long questionId) {
        Question question = findById(questionId);
        return toResponse(question, question.getOptions(), question.getCorrectAnswer());
    }

    @Transactional(readOnly = true)
    public List<QuestionResponse> getQuestionsByGroupId(Long groupId) {
        if (!questionGroupRepository.existsById(groupId)) {
            throw new NotFoundException("Question group not found: " + groupId);
        }
        return questionRepository.findByQuestionGroupIdOrderBySortOrderAsc(groupId)
                .stream()
                .map(q -> toResponse(q, q.getOptions(), q.getCorrectAnswer()))
                .toList();
    }

    @Transactional
    public void deleteQuestion(Long questionId) {
        questionRepository.delete(findById(questionId));
    }

    private Question findById(Long id) {
        return questionRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Question not found: " + id));
    }

    private QuestionResponse toResponse(Question q, List<QuestionOption> options, CorrectAnswer ca) {
        List<QuestionOptionResponse> optResponses = options.stream()
                .map(o -> new QuestionOptionResponse(o.getId(), o.getOptionLabel(), o.getOptionText(), o.getSortOrder()))
                .toList();

        CorrectAnswerResponse caResponse = null;
        if (ca != null) {
            Long correctOptionId = ca.getCorrectOption() != null ? ca.getCorrectOption().getId() : null;
            caResponse = new CorrectAnswerResponse(correctOptionId, ca.getCorrectText(), ca.getAlternativeAnswers());
        }

        return new QuestionResponse(q.getId(), q.getQuestionNumber(), q.getQuestionText(),
                q.getPrefixText(), q.getSuffixText(), q.getSortOrder(), optResponses, caResponse);
    }
}
