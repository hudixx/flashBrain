package com.flashbrain.es;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;

import java.time.LocalDateTime;

@Data
@Document(indexName = "snippet")
public class SnippetDoc {

    @Id
    private String id;

    @Field(type = FieldType.Keyword)
    private String subjectId;

    @Field(type = FieldType.Keyword)
    private String userId;

    @Field(type = FieldType.Text, analyzer = "ik_max_word")
    private String title;

    @Field(type = FieldType.Text, analyzer = "ik_max_word")
    private String ocrText;

    @Field(type = FieldType.Text, analyzer = "ik_max_word")
    private String noteContent;

    @Field(type = FieldType.Double)
    private Double sortOrder;

    @Field(type = FieldType.Boolean)
    private Boolean isPinned;

    @Field(type = FieldType.Boolean)
    private Boolean isMastered;

    @Field(type = FieldType.Boolean)
    private Boolean isDeleted;

    @Field(type = FieldType.Date)
    private LocalDateTime deletedAt;
}
