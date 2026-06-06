package com.flashbrain.config;

import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class DatabaseSchemaInitializerTest {

    @Test
    void shouldDefineAllApplicationTables() {
        Map<String, DatabaseSchemaInitializer.TableDefinition> tables = DatabaseSchemaInitializer.tableDefinitions()
                .stream()
                .collect(Collectors.toMap(DatabaseSchemaInitializer.TableDefinition::getName, table -> table));

        assertThat(tables.keySet()).containsExactlyInAnyOrder("subject", "snippet", "snippet_image");
    }

    @Test
    void shouldDefineSnippetLongTextColumns() {
        DatabaseSchemaInitializer.TableDefinition snippet = DatabaseSchemaInitializer.tableDefinitions()
                .stream()
                .filter(table -> table.getName().equals("snippet"))
                .findFirst()
                .orElseThrow(() -> new AssertionError("snippet table definition missing"));

        assertThat(snippet.getColumns()).containsKeys("ocr_text", "note_content");
        assertThat(snippet.getColumns().get("ocr_text").getExpectedType()).isEqualTo("longtext");
        assertThat(snippet.getColumns().get("note_content").getExpectedType()).isEqualTo("longtext");
    }

    @Test
    void shouldDefineBooleanColumnsAsTinyint() {
        DatabaseSchemaInitializer.TableDefinition subject = DatabaseSchemaInitializer.tableDefinitions()
                .stream()
                .filter(table -> table.getName().equals("subject"))
                .findFirst()
                .orElseThrow(() -> new AssertionError("subject table definition missing"));

        DatabaseSchemaInitializer.TableDefinition snippet = DatabaseSchemaInitializer.tableDefinitions()
                .stream()
                .filter(table -> table.getName().equals("snippet"))
                .findFirst()
                .orElseThrow(() -> new AssertionError("snippet table definition missing"));

        assertThat(subject.getColumns().get("is_deleted").getExpectedType()).isEqualTo("tinyint(1)");
        assertThat(snippet.getColumns().get("is_pinned").getExpectedType()).isEqualTo("tinyint(1)");
        assertThat(snippet.getColumns().get("is_mastered").getExpectedType()).isEqualTo("tinyint(1)");
    }
    @Test
    void shouldNormalizeMySqlIntegerDisplayWidths() {
        assertThat(DatabaseSchemaInitializer.normalizeColumnType("bigint(20)")).isEqualTo("bigint");
        assertThat(DatabaseSchemaInitializer.normalizeColumnType("int(11)")).isEqualTo("int");
        assertThat(DatabaseSchemaInitializer.normalizeColumnType("tinyint(1)")).isEqualTo("tinyint(1)");
    }
}
