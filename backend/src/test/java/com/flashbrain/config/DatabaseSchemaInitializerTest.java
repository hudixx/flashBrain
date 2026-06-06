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

        assertThat(tables.keySet()).containsExactlyInAnyOrder("app_user", "subject", "snippet", "snippet_image");
    }

    @Test
    void shouldDefineUserTableAndIndexes() {
        DatabaseSchemaInitializer.TableDefinition user = table("app_user");

        assertThat(user.getColumns()).containsKeys("username", "email", "password_hash", "enabled", "created_at", "updated_at");
        assertThat(user.getColumns().get("password_hash").getExpectedType()).isEqualTo("varchar(100)");
        assertThat(user.getColumns().get("enabled").getExpectedType()).isEqualTo("tinyint(1)");
        assertThat(user.getIndexes()).containsKeys("uk_app_user_username", "uk_app_user_email");
    }

    @Test
    void shouldDefineOwnerColumnsAndIndexes() {
        DatabaseSchemaInitializer.TableDefinition subject = table("subject");
        DatabaseSchemaInitializer.TableDefinition snippet = table("snippet");

        assertThat(subject.getColumns()).containsKey("user_id");
        assertThat(subject.getColumns().get("user_id").getExpectedType()).isEqualTo("bigint");
        assertThat(subject.getIndexes()).containsKey("idx_subject_user_id");

        assertThat(snippet.getColumns()).containsKey("user_id");
        assertThat(snippet.getColumns().get("user_id").getExpectedType()).isEqualTo("bigint");
        assertThat(snippet.getIndexes()).containsKeys("idx_snippet_user_subject", "idx_snippet_user_id");
    }

    @Test
    void shouldDefineSnippetLongTextColumns() {
        DatabaseSchemaInitializer.TableDefinition snippet = table("snippet");

        assertThat(snippet.getColumns()).containsKeys("ocr_text", "note_content");
        assertThat(snippet.getColumns().get("ocr_text").getExpectedType()).isEqualTo("longtext");
        assertThat(snippet.getColumns().get("note_content").getExpectedType()).isEqualTo("longtext");
    }

    @Test
    void shouldDefineBooleanColumnsAsTinyint() {
        DatabaseSchemaInitializer.TableDefinition subject = table("subject");
        DatabaseSchemaInitializer.TableDefinition snippet = table("snippet");

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

    private DatabaseSchemaInitializer.TableDefinition table(String name) {
        return DatabaseSchemaInitializer.tableDefinitions()
                .stream()
                .filter(table -> table.getName().equals(name))
                .findFirst()
                .orElseThrow(() -> new AssertionError(name + " table definition missing"));
    }
}
