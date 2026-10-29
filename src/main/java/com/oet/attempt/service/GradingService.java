package com.oet.attempt.service;

import com.oet.attempt.entity.AttemptAnswer;
import com.oet.test.entity.CorrectAnswer;
import com.oet.test.entity.Question;
import com.oet.test.enums.QuestionType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
public class GradingService {

    /**
     * Grades a single answer against the correct answer.
     * Returns true if the answer is correct.
     */
    public boolean grade(AttemptAnswer answer, Question question) {
        CorrectAnswer correctAnswer = question.getCorrectAnswer();
        if (correctAnswer == null) {
            return false;
        }

        QuestionType type = question.getQuestionGroup().getQuestionType();

        return switch (type) {
            case MCQ_3, MCQ_4 -> gradeMcq(answer, correctAnswer);
            case TEXT_MATCHING -> gradeTextMatching(answer, correctAnswer);
            case GAP_FILL -> gradeGapFill(answer, correctAnswer);
            case SHORT_ANSWER, NOTE_COMPLETION -> gradeTextAnswer(answer, correctAnswer);
        };
    }

    private boolean gradeMcq(AttemptAnswer answer, CorrectAnswer correctAnswer) {
        if (answer.getSelectedOption() == null || correctAnswer.getCorrectOption() == null) {
            return false;
        }
        return answer.getSelectedOption().getId().equals(correctAnswer.getCorrectOption().getId());
    }

    private boolean gradeTextMatching(AttemptAnswer answer, CorrectAnswer correctAnswer) {
        // TEXT_MATCHING may use selected option or text (A/B/C/D)
        if (correctAnswer.getCorrectOption() != null && answer.getSelectedOption() != null) {
            return answer.getSelectedOption().getId().equals(correctAnswer.getCorrectOption().getId());
        }
        if (correctAnswer.getCorrectText() != null && answer.getAnswerText() != null) {
            return normalize(answer.getAnswerText()).equals(normalize(correctAnswer.getCorrectText()));
        }
        return false;
    }

    private boolean gradeTextAnswer(AttemptAnswer answer, CorrectAnswer correctAnswer) {
        if (answer.getAnswerText() == null || answer.getAnswerText().isBlank()) {
            return false;
        }

        String submitted = normalize(answer.getAnswerText());
        Set<String> submittedWords = tokenize(answer.getAnswerText());

        // Check against primary correct text
        if (correctAnswer.getCorrectText() != null
                && matchesTextAnswer(submitted, submittedWords, correctAnswer.getCorrectText())) {
            return true;
        }

        // Check against all expanded alternatives
        List<String> alternatives = expandAlternatives(correctAnswer.getAlternativeAnswers());
        for (String alt : alternatives) {
            if (matchesTextAnswer(submitted, submittedWords, alt)) {
                return true;
            }
        }

        return false;
    }

    /**
     * Correct if the submitted answer equals the expected one after normalization,
     * or contains every word of it (punctuation ignored), e.g. "94 or higher" matches "94%".
     */
    private boolean matchesTextAnswer(String submitted, Set<String> submittedWords, String expected) {
        return submitted.equals(normalize(expected)) || containsAllWords(submittedWords, expected);
    }

    /**
     * Grades GAP_FILL answers leniently: correct if every word of the correct
     * text (or of any expanded alternative) appears anywhere in the submitted
     * answer, using whole-word matching. This allows a full-sentence answer
     * (e.g. "i love apple") to be accepted when the correct answer is just the
     * key word/phrase (e.g. "apple").
     */
    private boolean gradeGapFill(AttemptAnswer answer, CorrectAnswer correctAnswer) {
        if (answer.getAnswerText() == null || answer.getAnswerText().isBlank()) {
            return false;
        }

        Set<String> submittedWords = tokenize(answer.getAnswerText());

        if (correctAnswer.getCorrectText() != null && containsAllWords(submittedWords, correctAnswer.getCorrectText())) {
            return true;
        }

        for (String alt : expandAlternatives(correctAnswer.getAlternativeAnswers())) {
            if (containsAllWords(submittedWords, alt)) {
                return true;
            }
        }

        return false;
    }

    private boolean containsAllWords(Set<String> submittedWords, String phrase) {
        Set<String> phraseWords = tokenize(phrase);
        return !phraseWords.isEmpty() && submittedWords.containsAll(phraseWords);
    }

    private Set<String> tokenize(String text) {
        String normalized = normalize(text);
        if (normalized.isEmpty()) {
            return Set.of();
        }
        return Arrays.stream(normalized.split("[^a-z0-9]+"))
                .filter(w -> !w.isBlank())
                .collect(Collectors.toSet());
    }

    /**
     * Expands alternatives with optional parenthetical parts.
     * E.g. "(a) (heavy) suitcase" expands to:
     * "suitcase", "a suitcase", "heavy suitcase", "a heavy suitcase"
     */
    List<String> expandAlternatives(List<String> rawAlternatives) {
        if (rawAlternatives == null || rawAlternatives.isEmpty()) {
            return List.of();
        }

        List<String> expanded = new ArrayList<>();
        for (String raw : rawAlternatives) {
            expanded.addAll(expandParenthetical(raw));
        }
        return expanded;
    }

    private List<String> expandParenthetical(String template) {
        // Extract all parenthetical groups in order
        // e.g. "(a) (heavy) suitcase" → optionals=["a","heavy"], base="suitcase"
        List<String> optionals = new ArrayList<>();
        String remaining = template;

        java.util.regex.Pattern p = java.util.regex.Pattern.compile("\\(([^)]+)\\)\\s*");
        java.util.regex.Matcher m = p.matcher(remaining);
        StringBuilder baseBuilder = new StringBuilder();
        int lastEnd = 0;
        while (m.find()) {
            baseBuilder.append(remaining, lastEnd, m.start());
            optionals.add(m.group(1).trim());
            lastEnd = m.end();
        }
        baseBuilder.append(remaining.substring(lastEnd));
        String base = baseBuilder.toString().trim();

        // Generate all 2^n combinations of optional words
        int count = 1 << optionals.size();
        List<String> results = new ArrayList<>();
        for (int mask = 0; mask < count; mask++) {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < optionals.size(); i++) {
                if ((mask & (1 << i)) != 0) {
                    sb.append(optionals.get(i)).append(" ");
                }
            }
            sb.append(base);
            String candidate = sb.toString().trim().replaceAll("\\s+", " ");
            if (!candidate.isBlank()) {
                results.add(candidate);
            }
        }
        return results;
    }

    /**
     * Normalizes text for comparison: trim, lowercase, collapse whitespace.
     */
    private String normalize(String text) {
        if (text == null) return "";
        return text.trim().toLowerCase().replaceAll("\\s+", " ");
    }
}
