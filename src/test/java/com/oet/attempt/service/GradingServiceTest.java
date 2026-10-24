package com.oet.attempt.service;

import com.oet.attempt.entity.AttemptAnswer;
import com.oet.test.entity.CorrectAnswer;
import com.oet.test.entity.Question;
import com.oet.test.entity.QuestionGroup;
import com.oet.test.enums.QuestionType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class GradingServiceTest {

    private GradingService gradingService;

    @BeforeEach
    void setUp() {
        gradingService = new GradingService();
    }

    private Question questionOf(QuestionType type, CorrectAnswer correctAnswer) {
        QuestionGroup questionGroup = QuestionGroup.builder()
                .questionType(type)
                .build();
        Question question = Question.builder()
                .questionGroup(questionGroup)
                .build();
        question.setCorrectAnswer(correctAnswer);
        return question;
    }

    private AttemptAnswer answerOf(String text) {
        return AttemptAnswer.builder().answerText(text).build();
    }

    @Test
    void gapFill_exactMatchStillPasses() {
        CorrectAnswer correctAnswer = CorrectAnswer.builder().correctText("apple").build();
        Question question = questionOf(QuestionType.GAP_FILL, correctAnswer);

        assertThat(gradingService.grade(answerOf("apple"), question)).isTrue();
    }

    @Test
    void gapFill_wordEmbeddedInSentencePasses() {
        CorrectAnswer correctAnswer = CorrectAnswer.builder().correctText("apple").build();
        Question question = questionOf(QuestionType.GAP_FILL, correctAnswer);

        assertThat(gradingService.grade(answerOf("i love apple"), question)).isTrue();
    }

    @Test
    void gapFill_partialWordDoesNotFalselyMatch() {
        CorrectAnswer correctAnswer = CorrectAnswer.builder().correctText("apple").build();
        Question question = questionOf(QuestionType.GAP_FILL, correctAnswer);

        assertThat(gradingService.grade(answerOf("pineapple pie"), question)).isFalse();
    }

    @Test
    void gapFill_multiWordAnswerMatchesOutOfOrderInSentence() {
        CorrectAnswer correctAnswer = CorrectAnswer.builder().correctText("heavy suitcase").build();
        Question question = questionOf(QuestionType.GAP_FILL, correctAnswer);

        assertThat(gradingService.grade(answerOf("my suitcase was heavy"), question)).isTrue();
    }

    @Test
    void gapFill_alternativeWithParentheticalExpansionMatchesEmbeddedInSentence() {
        CorrectAnswer correctAnswer = CorrectAnswer.builder()
                .correctText("suitcase")
                .alternativeAnswers(List.of("(a) (heavy) suitcase"))
                .build();
        Question question = questionOf(QuestionType.GAP_FILL, correctAnswer);

        assertThat(gradingService.grade(answerOf("i lost a heavy suitcase at the airport"), question)).isTrue();
    }

    @Test
    void shortAnswer_exactMatchBehaviorUnchanged() {
        CorrectAnswer correctAnswer = CorrectAnswer.builder().correctText("apple").build();
        Question question = questionOf(QuestionType.SHORT_ANSWER, correctAnswer);

        assertThat(gradingService.grade(answerOf("i love apple"), question)).isFalse();
        assertThat(gradingService.grade(answerOf("apple"), question)).isTrue();
    }

    @Test
    void noteCompletion_exactMatchBehaviorUnchanged() {
        CorrectAnswer correctAnswer = CorrectAnswer.builder().correctText("apple").build();
        Question question = questionOf(QuestionType.NOTE_COMPLETION, correctAnswer);

        assertThat(gradingService.grade(answerOf("i love apple"), question)).isFalse();
        assertThat(gradingService.grade(answerOf("apple"), question)).isTrue();
    }
}
