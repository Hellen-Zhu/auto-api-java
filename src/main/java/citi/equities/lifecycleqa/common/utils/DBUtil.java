package citi.equities.lifecycleqa.common.utils;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.NullNode;
import citi.equities.lifecycleqa.common.config.InitialConfig;
import citi.equities.lifecycleqa.common.entities.SqlObject;
import citi.equities.lifecycleqa.common.entities.StopCondition;
import citi.equities.lifecycleqa.common.enums.LIFDBStatement;
import net.sf.jsqlparser.expression.LongValue;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.statement.Statement;
import net.sf.jsqlparser.statement.select.PlainSelect;
import net.sf.jsqlparser.statement.select.Select;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

public class DBUtil {
    private static final Logger log = LoggerFactory.getLogger(DBUtil.class);
    private static final ObjectMapper objectMapper = new ObjectMapper();

    public static SqlObject analyzeSqlWithIsSelectRowColumnNumber(String sql) {
        try {
            String sanitizedSql = sanitizeSql(sql);
            Statement statement = CCJSqlParserUtil.parse(sanitizedSql);
            if (statement instanceof Select) {
                Select select = (Select) statement;
                if (select.getSelectBody() instanceof PlainSelect) {
                    PlainSelect selectBody = (PlainSelect) select.getSelectBody();

                    int rowNumber = -1;
                    if (selectBody.getTop() != null) {
                        if (selectBody.getTop().getExpression() instanceof LongValue) {
                            LongValue topExpr = (LongValue) selectBody.getTop().getExpression();
                            if (topExpr.getValue() == 1L) {
                                rowNumber = 1;
                            }
                        }
                    } else if (selectBody.getLimit() != null && selectBody.getLimit().getRowCount() != null) {
                        if (selectBody.getLimit().getRowCount() instanceof LongValue) {
                            LongValue limitExpr = (LongValue) selectBody.getLimit().getRowCount();
                            if (limitExpr.getValue() == 1L) {
                                rowNumber = 1;
                            }
                        }
                    }

                    int columnCount = -1;
                    if (selectBody.getSelectItems() != null && !selectBody.getSelectItems().isEmpty()) {
                        if (selectBody.getSelectItems().size() == 1 &&
                            selectBody.getSelectItems().get(0).toString().trim().equals("*")) {
                            columnCount = -1;
                        } else {
                            columnCount = selectBody.getSelectItems().size();
                        }
                    }

                    return new SqlObject(true, rowNumber, columnCount);
                }
                return new SqlObject(true, -1, -1);
            }
            return new SqlObject(false, -1, -1);
        } catch (Exception e) {
            log.error("Error parsing SQL: {}", e.getMessage());
            log.error("SQL statement: {}", sql);
            return new SqlObject(false, -1, -1);
        }
    }

    public static String buildSqlSessionManagerName(String dbType, String region, String env) {
        log.info("build profileName DbType={}, region={}, env={}", dbType, region, env);
        String endStr;
        switch (dbType.toUpperCase()) {
            case "IGNITE":
                endStr = "IGNITE";
                break;
            case "CFIM":
                endStr = "CFIM";
                break;
            default:
                endStr = "DRMS";
        }
        return (region + env + "-SendStr-" + endStr).toLowerCase();
    }

    public static JsonNode executeSQL(SqlSessionManager sqlSessionManager, String sql,
                                       StringBuilder errorMessage, int retryNumber,
                                       int intervalSeconds, StopCondition stopCondition) {
        int attempts = 1;
        SqlObject sqlObject = analyzeSqlWithIsSelectRowColumnNumber(sql);

        while (attempts <= retryNumber) {
            log.info("Start to execute sql '{}' with times {}, retryNumber {}, intervalSeconds {}",
                    sql, attempts, retryNumber, intervalSeconds);

            try (SqlSession session = sqlSessionManager.openSession()) {
                List<Object> result = session.selectList(LIFDBStatement.DynamicSQL.name(),
                        Collections.singletonMap("sql", sql));

                if (result == null || result.isEmpty()) {
                    result = new ArrayList<>();
                }

                if (attempts == retryNumber) {
                    log.info("Finish sql '{}' with retryNumber {}, attempts {}, result: {}",
                            sql, retryNumber, attempts, result.toString().substring(0, Math.min(100, result.toString().length())));
                    return buildJsonElementAfterExecute(result, sqlObject, errorMessage);
                } else {
                    if (!result.isEmpty() && stopCondition != null && stopCondition.evaluateForDB(result)) {
                        log.info("Finish sql '{}' with retryNumber {}, attempts {}, meet stopCondition",
                                sql, retryNumber, attempts);
                        return buildJsonElementAfterExecute(result, sqlObject, errorMessage);
                    }
                }
            } catch (Exception e) {
                String errMsg = e.getMessage() + " with sql : '" + sql + "'";
                DataTypeUtil.appendErrorMessage(errorMessage, errMsg);
                log.error(errMsg);
            }

            attempts++;
            if (attempts <= retryNumber) {
                try {
                    Thread.sleep(intervalSeconds * 1000L);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        }

        log.error("All {} attempts failed for sql '{}'", retryNumber, sql);
        return NullNode.getInstance();
    }

    public static JsonNode executeLIF(String statementOrSql, boolean isSql, StringBuilder errorMessage) {
        return executeLIF(statementOrSql, isSql, errorMessage, null);
    }

    public static JsonNode executeIf(String statementOrSql, boolean isSql,
                                       StringBuilder errorMessage, Map<String, String> params) {
        return executeLIF(statementOrSql, isSql, errorMessage, params != null ? new HashMap<>(params) : null);
    }

    public static JsonNode executeLIF(String statementOrSql, boolean isSql,
                                        StringBuilder errorMessage, Map<String, Object> params) {
        SqlSessionManager sqlSessionManager = InitialConfig.getLIFSqlSessionManager();
        log.info("Start to execute {} {}", isSql ? "SQL" : "statement", statementOrSql);

        if (isSql) {
            return executeSQL(sqlSessionManager, statementOrSql, errorMessage, 1, 1, null);
        } else {
            try (SqlSession session = sqlSessionManager.openSession()) {
                List<Object> result = session.selectList(statementOrSql, params);
                if (result == null || result.isEmpty()) {
                    result = new ArrayList<>();
                }
                LIFDBStatement statement = LIFDBStatement.fromString(statementOrSql);
                SqlObject sqlObject = new SqlObject(statement.isSelect(), statement.getRowNumber(), statement.getColumnNumber());
                return buildJsonElementAfterExecute(result, sqlObject, errorMessage);
            } catch (Exception e) {
                String errMsg = e.getMessage() + " with statement: " + statementOrSql;
                DataTypeUtil.appendErrorMessage(errorMessage, errMsg);
                log.error(errMsg);
                return NullNode.getInstance();
            }
        }
    }

    private static String sanitizeSql(String sql) {
        return sql.toLowerCase().trim()
                .replaceAll("[\\x00-\\x09\\x0B\\x0C\\x0E-\\x1F\\x7F]", "")
                .replaceAll("\\s+", " ")
                .replaceAll("--.*$", "")
                .replaceAll("/\\*.*?\\*/", "")
                .replace("set chained off", "");
    }

    private static JsonNode fetchFinalJsonElementFromSelectResultAndRowColumnNumber(
            List<?> selectResult, int rowNumber, int columnNumber, StringBuilder errorMessage) {

        if (selectResult.isEmpty()) {
            log.info("Empty select RESULT");
            return NullNode.getInstance();
        }

        if (rowNumber != selectResult.size() && rowNumber != -1) {
            String errMsg = "Invalid Match between rowNumber: " + rowNumber +
                    " and the size of selectResult: " + selectResult.size();
            DataTypeUtil.appendErrorMessage(errorMessage, errMsg);
            log.error(errMsg);
            return NullNode.getInstance();
        }

        if (selectResult.stream().allMatch(Objects::isNull)) {
            if (columnNumber == 1) {
                return DataTypeUtil.convertToJsonNode(selectResult.get(0));
            }
            return objectMapper.createArrayNode();
        }

        if (rowNumber == 1) {
            Object first = selectResult.get(0);
            if (columnNumber == 1 && first instanceof Map) {
                Collection<?> values = ((Map<?, ?>) first).values();
                return DataTypeUtil.convertToJsonNode(values.iterator().next());
            } else if (columnNumber == 1 && first instanceof List) {
                return DataTypeUtil.convertToJsonNode(((List<?>) first).get(0));
            }
            return DataTypeUtil.convertToJsonNode(first);
        }

        if (columnNumber == 1) {
            ArrayNode arrayNode = objectMapper.createArrayNode();
            for (Object item : selectResult) {
                if (item instanceof Map) {
                    Collection<?> values = ((Map<?, ?>) item).values();
                    arrayNode.add(DataTypeUtil.convertToJsonNode(values.iterator().next()));
                }
            }
            return arrayNode;
        }

        return DataTypeUtil.listToJsonArray((List<?>) selectResult);
    }

    private static JsonNode buildJsonElementAfterExecute(List<Object> variablesDBResult,
                                                          SqlObject sqlObject,
                                                          StringBuilder errorMessage) {
        try {
            if (!sqlObject.isSelect()) {
                return NullNode.getInstance();
            }

            return fetchFinalJsonElementFromSelectResultAndRowColumnNumber(
                    variablesDBResult,
                    sqlObject.getRowNumber(),
                    sqlObject.getColumnNumber(),
                    errorMessage
            );
        } catch (Exception e) {
            String errMsg = "Error executing fetchJsonElementByDynamicSQL: " + e.getMessage();
            errorMessage.append(errMsg);
            log.error(errMsg, e);
            return NullNode.getInstance();
        }
    }
}
