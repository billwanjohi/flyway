/*
 * Copyright 2010-2017 Boxfuse GmbH
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.flywaydb.core.internal.database.snowflake;

import org.flywaydb.core.internal.database.Schema;
import org.flywaydb.core.internal.database.Table;
import org.flywaydb.core.internal.util.jdbc.JdbcTemplate;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * Snowflake implementation of Schema.
 */
public class SnowflakeSchema extends Schema<SnowflakeDatabase> {
    /**
     * Creates a new Snowflake schema.
     *
     * @param jdbcTemplate The Jdbc Template for communicating with the DB.
     * @param database     The database-specific support.
     * @param name         The name of the schema.
     */
    public SnowflakeSchema(JdbcTemplate jdbcTemplate, SnowflakeDatabase database, String name) {
        super(jdbcTemplate, database, name);
    }

    @Override
    protected boolean doExists() throws SQLException {
        return jdbcTemplate.queryForInt("SELECT COUNT(*) FROM INFORMATION_SCHEMA.SCHEMATA WHERE SCHEMA_NAME=?", name) > 0;
    }

    @Override
    protected boolean doEmpty() throws SQLException {
        int objectCount = jdbcTemplate.queryForInt(
                "SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_SCHEMA = ? AND TABLE_TYPE = 'BASE TABLE'",
                name);
        return objectCount == 0;
    }

    @Override
    protected void doCreate() throws SQLException {
        jdbcTemplate.executeStatement("CREATE SCHEMA " + database.quote(name));
    }

    @Override
    protected void doDrop() throws SQLException {
        jdbcTemplate.executeStatement("DROP SCHEMA " + database.quote(name) + " CASCADE");
    }

    @Override
    protected void doClean() throws SQLException {
        for (String statement : generateDropStatementsForViews()) {
            jdbcTemplate.executeStatement(statement);
        }
        for (Table table : allTables()) {
            table.drop();
        }
        for (String statement : generateDropStatementsForStages()) {
            jdbcTemplate.executeStatement(statement);
        }
        for (String statement : generateDropStatementsForFileFormats()) {
            jdbcTemplate.executeStatement(statement);
        }
        for (String statement : generateDropStatementsForSequences()) {
            jdbcTemplate.executeStatement(statement);
        }
    }

    @Override
    protected Table[] doAllTables() throws SQLException {
        List<String> tableNames =
                jdbcTemplate.queryForStringList("SELECT TABLE_NAME FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_SCHEMA = ? AND TABLE_TYPE = 'BASE TABLE'", name);

        Table[] tables = new Table[tableNames.size()];
        for (int i = 0; i < tableNames.size(); i++) {
            tables[i] = new SnowflakeTable(jdbcTemplate, database, this, tableNames.get(i));
        }
        return tables;
    }

    protected List<String> generateDropStatementsForViews() throws SQLException {
        List<String> viewNames =
                jdbcTemplate.queryForStringList("SELECT TABLE_NAME FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_SCHEMA = ? AND TABLE_TYPE = 'VIEW'", name);

        List<String> statements = new ArrayList<String>();
        for (String viewName : viewNames) {
            statements.add("DROP VIEW " + database.quote(name, viewName));
        }
        return statements;
    }

    protected List<String> generateDropStatementsForStages() throws SQLException {
        List<String> stageNames =
                jdbcTemplate.queryForStringList("SELECT STAGE_NAME FROM INFORMATION_SCHEMA.STAGES WHERE STAGE_SCHEMA = ?", this.getName());

        List<String> statements = new ArrayList<String>();
        for (String stageName : stageNames) {
            statements.add("DROP STAGE " + database.quote(name, stageName));
        }
        return statements;
    }

    protected List<String> generateDropStatementsForFileFormats() throws SQLException {
        List<String> fileFormatNames =
                jdbcTemplate.queryForStringList("SELECT FILE_FORMAT_NAME FROM INFORMATION_SCHEMA.FILE_FORMATS WHERE FILE_FORMAT_SCHEMA = ?", this.getName());

        List<String> statements = new ArrayList<String>();
        for (String fileFormatName : fileFormatNames) {
            statements.add("DROP FILE FORMAT " + database.quote(name, fileFormatName));
        }
        return statements;
    }

    protected List<String> generateDropStatementsForSequences() throws SQLException {
        List<String> sequenceNames =
                jdbcTemplate.queryForStringList("SELECT SEQUENCE_NAME FROM INFORMATION_SCHEMA.SEQUENCES WHERE SEQUENCE_SCHEMA = ?", this.getName());

        List<String> statements = new ArrayList<String>();
        for (String sequenceName : sequenceNames) {
            statements.add("DROP SEQUENCE " + database.quote(name, sequenceName));
        }
        return statements;
    }

    @Override
    public Table getTable(String tableName) {
        return new SnowflakeTable(jdbcTemplate, database, this, tableName);
    }
}
