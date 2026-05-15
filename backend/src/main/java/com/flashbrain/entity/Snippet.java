package com.flashbrain.entity;

import javax.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Data
@NoArgsConstructor
public class Snippet {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long subjectId;

    private String title;

    private String imagePath;

    @Lob
    private String ocrText;

    @Lob
    private String noteContent;

    private Double sortOrder;

    private Boolean isPinned = false;

    private Boolean isMastered = false;
}
