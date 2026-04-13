package com.oet.test.entity;

import io.hypersistence.utils.hibernate.type.json.JsonType;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Type;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "correct_answers")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CorrectAnswer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "question_id", nullable = false)
    private Question question;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "correct_option_id")
    private QuestionOption correctOption;

    @Column(name = "correct_text", length = 500)
    private String correctText;

    @Type(JsonType.class)
    @Column(name = "alternative_answers", columnDefinition = "json")
    @Builder.Default
    private List<String> alternativeAnswers = new ArrayList<>();
}
