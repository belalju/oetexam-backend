package com.oet.test.entity;

import com.oet.test.enums.PartLabel;
import jakarta.persistence.*;
import lombok.*;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "test_parts", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"test_id", "part_label"})
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TestPart {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "test_id", nullable = false)
    private OetTest test;

    @Enumerated(EnumType.STRING)
    @Column(name = "part_label", nullable = false, length = 10)
    private PartLabel partLabel;

    @Column(name = "time_limit_minutes")
    private Integer timeLimitMinutes;

    @Column(columnDefinition = "TEXT")
    private String instructions;

    @Column(name = "sort_order", nullable = false)
    @Builder.Default
    private Integer sortOrder = 0;

    @OneToMany(mappedBy = "testPart", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("sortOrder ASC")
    @Builder.Default
    private List<TextPassage> passages = new ArrayList<>();

    @OneToMany(mappedBy = "testPart", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("sortOrder ASC")
    @Builder.Default
    private List<QuestionGroup> questionGroups = new ArrayList<>();
}
