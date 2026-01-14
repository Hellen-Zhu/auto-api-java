package citi.equities.lifecycleqa.common.utils;

import org.apache.ibatis.executor.Executor;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.mapping.ParameterMapping;
import org.apache.ibatis.plugin.*;
import org.apache.ibatis.reflection.MetaObject;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.ResultHandler;
import org.apache.ibatis.session.RowBounds;
import org.apache.ibatis.type.TypeHandlerRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.text.DateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Properties;

/**
 * MyBatis SQL 日志拦截器
 * 用于打印实际执行的 SQL 语句（包含参数替换后的完整 SQL）
 */
@Intercepts({
    @Signature(type = Executor.class, method = "update", args = {MappedStatement.class, Object.class}),
    @Signature(type = Executor.class, method = "query", args = {MappedStatement.class, Object.class, RowBounds.class, ResultHandler.class})
})
public class SqlLoggingInterceptor implements Interceptor {
    private static final Logger log = LoggerFactory.getLogger(SqlLoggingInterceptor.class);

    @Override
    public Object intercept(Invocation invocation) throws Throwable {
        try {
            MappedStatement mappedStatement = (MappedStatement) invocation.getArgs()[0];
            Object parameter = invocation.getArgs().length > 1 ? invocation.getArgs()[1] : null;
            
            BoundSql boundSql = mappedStatement.getBoundSql(parameter);
            Configuration configuration = mappedStatement.getConfiguration();
            
            // 获取完整的 SQL 语句（参数替换后）
            String sql = getSql(configuration, boundSql);
            
            // 打印 SQL 信息
            log.info("========== 执行 SQL Statement ==========");
            log.info("Statement ID: {}", mappedStatement.getId());
            log.info("SQL: {}", sql);
            if (parameter != null) {
                log.info("参数对象: {}", parameter);
            }
            log.info("==========================================");
            
        } catch (Exception e) {
            log.warn("SQL 日志拦截器执行异常: {}", e.getMessage());
        }
        
        return invocation.proceed();
    }

    /**
     * 获取完整的 SQL 语句（参数替换后）
     * 注意：对于使用 ${} 语法的参数，MyBatis 会在 BoundSql 中直接替换，所以这里主要处理 #{} 的参数
     */
    private String getSql(Configuration configuration, BoundSql boundSql) {
        // 获取原始 SQL（对于 ${} 语法，参数已经替换；对于 #{} 语法，还是占位符 ?）
        String sql = boundSql.getSql();
        
        // 格式化 SQL（压缩多余空格）
        sql = sql.replaceAll("[\\s]+", " ").trim();
        
        // 处理 #{} 语法的参数（预编译参数，在 BoundSql 中显示为 ?）
        List<ParameterMapping> parameterMappings = boundSql.getParameterMappings();
        if (parameterMappings != null && !parameterMappings.isEmpty()) {
            Object parameterObject = boundSql.getParameterObject();
            TypeHandlerRegistry typeHandlerRegistry = configuration.getTypeHandlerRegistry();
            
            // 如果参数对象有对应的 TypeHandler，直接处理
            if (parameterObject != null && typeHandlerRegistry.hasTypeHandler(parameterObject.getClass())) {
                sql = sql.replaceFirst("\\?", getParameterValue(parameterObject));
            } else if (parameterObject != null) {
                // 处理 Map 或其他复杂对象
                MetaObject metaObject = configuration.newMetaObject(parameterObject);
                for (ParameterMapping parameterMapping : parameterMappings) {
                    String propertyName = parameterMapping.getProperty();
                    Object value = null;
                    
                    // 尝试从参数对象中获取值
                    if (metaObject.hasGetter(propertyName)) {
                        value = metaObject.getValue(propertyName);
                    } else if (boundSql.hasAdditionalParameter(propertyName)) {
                        value = boundSql.getAdditionalParameter(propertyName);
                    } else if (parameterObject instanceof java.util.Map) {
                        // 如果是 Map，直接从 Map 中获取
                        value = ((java.util.Map<?, ?>) parameterObject).get(propertyName);
                    }
                    
                    if (value != null) {
                        sql = sql.replaceFirst("\\?", getParameterValue(value));
                    } else {
                        sql = sql.replaceFirst("\\?", "?");
                    }
                }
            }
        }
        
        return sql;
    }

    /**
     * 获取参数值的字符串表示
     */
    private String getParameterValue(Object obj) {
        String value = null;
        if (obj instanceof String) {
            value = "'" + obj.toString() + "'";
        } else if (obj instanceof Date) {
            DateFormat formatter = DateFormat.getDateTimeInstance(DateFormat.DEFAULT, DateFormat.DEFAULT, Locale.CHINA);
            value = "'" + formatter.format((Date) obj) + "'";
        } else {
            if (obj != null) {
                value = obj.toString();
            } else {
                value = "null";
            }
        }
        return value;
    }

    @Override
    public Object plugin(Object target) {
        return Plugin.wrap(target, this);
    }

    @Override
    public void setProperties(Properties properties) {
        // 可以在这里配置属性
    }
}
