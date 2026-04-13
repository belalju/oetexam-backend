package com.oet.test.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "text_passages")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TextPassage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "test_part_id", nullable = false)
    private TestPart testPart;

    @Column(nullable = false, length = 50)
    private String label;

    @Column(columnDefinition = "TEXT")
    private String content;

    @Column(name = "audio_file_url", length = 500)
    private String audioFileUrl;

    @Column(name = "audio_duration_seconds")
    private Integer audioDurationSeconds;

    @Column(name = "sort_order", nullable = false)
    @Builder.Default
    private Integer sortOrder = 0;
}
