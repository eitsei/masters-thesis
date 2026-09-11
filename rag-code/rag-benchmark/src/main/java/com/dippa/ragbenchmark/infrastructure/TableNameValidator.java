package com.dippa.ragbenchmark.infrastructure;

import java.util.regex.Pattern;

/**
 * Validates identifiers that are interpolated into PostgreSQL table names.
 */
public final class TableNameValidator {

    private static final Pattern TABLE_NAME_PATTERN = Pattern.compile("^[a-zA-Z0-9_]+$");

    private TableNameValidator() {}

    public static String requireValid(String tableName, String description) {
        if (tableName == null || tableName.isBlank() || !TABLE_NAME_PATTERN.matcher(tableName).matches()) {
            throw new IllegalArgumentException("Invalid " + description + " name: " + tableName);
        }
        return tableName;
    }

    public static boolean isValid(String tableName) {
        return tableName != null && !tableName.isBlank() && TABLE_NAME_PATTERN.matcher(tableName).matches();
    }
}
