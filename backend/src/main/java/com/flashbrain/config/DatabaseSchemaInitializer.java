package com.flashbrain.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Component
@Slf4j
public class DatabaseSchemaInitializer implements ApplicationRunner {

    private final JdbcTemplate jdbcTemplate;

    public DatabaseSchemaInitializer(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void run(ApplicationArguments args) {
        initializeSchema();
    }

    void initializeSchema() {
        for (TableDefinition table : tableDefinitions()) {
            ensureTable(table);
        }
    }

    private void ensureTable(TableDefinition table) {
        if (!tableExists(table.getName())) {
            log.info("Creating missing table: {}", table.getName());
            jdbcTemplate.execute(table.getCreateSql());
            return;
        }

        Map<String, ExistingColumn> existingColumns = loadExistingColumns(table.getName());
        for (ColumnDefinition column : table.getColumns().values()) {
            ExistingColumn existing = existingColumns.get(column.getName());
            if (existing == null) {
                log.info("Adding missing column: {}.{}", table.getName(), column.getName());
                jdbcTemplate.execute("ALTER TABLE `" + table.getName() + "` ADD COLUMN " + column.getAddSql());
                continue;
            }
            if (!normalizeColumnType(existing.getColumnType()).equals(normalizeColumnType(column.getExpectedType()))) {
                log.warn("Column type mismatch for {}.{}: database={}, expected={}. Automatic type changes are disabled.",
                        table.getName(), column.getName(), existing.getColumnType(), column.getExpectedType());
            }
        }
    }

    private boolean tableExists(String tableName) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = DATABASE() AND table_name = ?",
                Integer.class,
                tableName
        );
        return count != null && count > 0;
    }

    private Map<String, ExistingColumn> loadExistingColumns(String tableName) {
        List<ExistingColumn> columns = jdbcTemplate.query(
                "SELECT column_name, column_type FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = ?",
                (rs, rowNum) -> new ExistingColumn(
                        rs.getString("column_name").toLowerCase(Locale.ROOT),
                        rs.getString("column_type").toLowerCase(Locale.ROOT)
                ),
                tableName
        );
        Map<String, ExistingColumn> byName = new LinkedHashMap<>();
        for (ExistingColumn column : columns) {
            byName.put(column.getName(), column);
        }
        return byName;
    }

    public static List<TableDefinition> tableDefinitions() {
        List<TableDefinition> tables = new ArrayList<>();

        tables.add(new TableDefinition(
                "subject",
                "CREATE TABLE `subject` ("
                        + "`id` BIGINT NOT NULL AUTO_INCREMENT,"
                        + "`name` VARCHAR(255) NULL,"
                        + "`parent_id` BIGINT NULL,"
                        + "`icon` VARCHAR(255) NULL,"
                        + "`is_deleted` TINYINT(1) NULL DEFAULT 0,"
                        + "PRIMARY KEY (`id`)"
                        + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci",
                columns(
                        column("id", "bigint", "`id` BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY"),
                        column("name", "varchar(255)", "`name` VARCHAR(255) NULL"),
                        column("parent_id", "bigint", "`parent_id` BIGINT NULL"),
                        column("icon", "varchar(255)", "`icon` VARCHAR(255) NULL"),
                        column("is_deleted", "tinyint(1)", "`is_deleted` TINYINT(1) NULL DEFAULT 0")
                )
        ));

        tables.add(new TableDefinition(
                "snippet",
                "CREATE TABLE `snippet` ("
                        + "`id` BIGINT NOT NULL AUTO_INCREMENT,"
                        + "`subject_id` BIGINT NULL,"
                        + "`title` VARCHAR(255) NULL,"
                        + "`image_path` VARCHAR(255) NULL,"
                        + "`ocr_text` LONGTEXT NULL,"
                        + "`note_content` LONGTEXT NULL,"
                        + "`sort_order` DOUBLE NULL,"
                        + "`is_pinned` TINYINT(1) NULL DEFAULT 0,"
                        + "`is_mastered` TINYINT(1) NULL DEFAULT 0,"
                        + "PRIMARY KEY (`id`)"
                        + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci",
                columns(
                        column("id", "bigint", "`id` BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY"),
                        column("subject_id", "bigint", "`subject_id` BIGINT NULL"),
                        column("title", "varchar(255)", "`title` VARCHAR(255) NULL"),
                        column("image_path", "varchar(255)", "`image_path` VARCHAR(255) NULL"),
                        column("ocr_text", "longtext", "`ocr_text` LONGTEXT NULL"),
                        column("note_content", "longtext", "`note_content` LONGTEXT NULL"),
                        column("sort_order", "double", "`sort_order` DOUBLE NULL"),
                        column("is_pinned", "tinyint(1)", "`is_pinned` TINYINT(1) NULL DEFAULT 0"),
                        column("is_mastered", "tinyint(1)", "`is_mastered` TINYINT(1) NULL DEFAULT 0")
                )
        ));

        tables.add(new TableDefinition(
                "snippet_image",
                "CREATE TABLE `snippet_image` ("
                        + "`id` BIGINT NOT NULL AUTO_INCREMENT,"
                        + "`snippet_id` BIGINT NULL,"
                        + "`original_filename` VARCHAR(255) NULL,"
                        + "`stored_filename` VARCHAR(255) NULL,"
                        + "`url` VARCHAR(255) NULL,"
                        + "`created_at` DATETIME NULL,"
                        + "PRIMARY KEY (`id`)"
                        + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci",
                columns(
                        column("id", "bigint", "`id` BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY"),
                        column("snippet_id", "bigint", "`snippet_id` BIGINT NULL"),
                        column("original_filename", "varchar(255)", "`original_filename` VARCHAR(255) NULL"),
                        column("stored_filename", "varchar(255)", "`stored_filename` VARCHAR(255) NULL"),
                        column("url", "varchar(255)", "`url` VARCHAR(255) NULL"),
                        column("created_at", "datetime", "`created_at` DATETIME NULL")
                )
        ));

        return Collections.unmodifiableList(tables);
    }

    static String normalizeColumnType(String columnType) {
        String normalized = columnType.toLowerCase(Locale.ROOT);
        if (normalized.matches("bigint\\(\\d+\\)")) {
            return "bigint";
        }
        if (normalized.matches("int\\(\\d+\\)")) {
            return "int";
        }
        return normalized;
    }

    private static Map<String, ColumnDefinition> columns(ColumnDefinition... columns) {
        Map<String, ColumnDefinition> map = new LinkedHashMap<>();
        for (ColumnDefinition column : columns) {
            map.put(column.getName(), column);
        }
        return Collections.unmodifiableMap(map);
    }

    private static ColumnDefinition column(String name, String expectedType, String addSql) {
        return new ColumnDefinition(name, expectedType, addSql);
    }

    public static class TableDefinition {
        private final String name;
        private final String createSql;
        private final Map<String, ColumnDefinition> columns;

        TableDefinition(String name, String createSql, Map<String, ColumnDefinition> columns) {
            this.name = name;
            this.createSql = createSql;
            this.columns = columns;
        }

        public String getName() {
            return name;
        }

        public String getCreateSql() {
            return createSql;
        }

        public Map<String, ColumnDefinition> getColumns() {
            return columns;
        }
    }

    public static class ColumnDefinition {
        private final String name;
        private final String expectedType;
        private final String addSql;

        ColumnDefinition(String name, String expectedType, String addSql) {
            this.name = name;
            this.expectedType = expectedType;
            this.addSql = addSql;
        }

        public String getName() {
            return name;
        }

        public String getExpectedType() {
            return expectedType;
        }

        public String getAddSql() {
            return addSql;
        }
    }

    private static class ExistingColumn {
        private final String name;
        private final String columnType;

        ExistingColumn(String name, String columnType) {
            this.name = name;
            this.columnType = columnType;
        }

        String getName() {
            return name;
        }

        String getColumnType() {
            return columnType;
        }
    }
}
