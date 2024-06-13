package com.mogudiandian.mybatis.actual.sql.interceptor;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.google.common.base.Stopwatch;
import com.mogudiandian.mybatis.actual.sql.properties.ActualSqlProperties;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.time.FastDateFormat;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.ibatis.cache.CacheKey;
import org.apache.ibatis.executor.Executor;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.mapping.ParameterMapping;
import org.apache.ibatis.mapping.SqlCommandType;
import org.apache.ibatis.plugin.*;
import org.apache.ibatis.reflection.MetaObject;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.ResultHandler;
import org.apache.ibatis.session.RowBounds;
import org.apache.ibatis.type.TypeHandlerRegistry;

import java.sql.SQLException;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

/**
 * Mybatis拦截器，用来打印真正执行的SQL以及执行时间
 * Mybatis支持对 Executor/StatementHandler/ParameterHandler/ResultSetHandler 对象进行拦截
 * 执行顺序：Executor.method -> Plugin.invoke -> Interceptor.intercept -> Invocation.proceed -> method.invoke
 *
 * @author Joshua Sun
 */
@Slf4j
@Intercepts({
        @Signature(type = Executor.class, method = "query", args = {MappedStatement.class, Object.class, RowBounds.class, ResultHandler.class}),
        @Signature(type = Executor.class, method = "query", args = {MappedStatement.class, Object.class, RowBounds.class, ResultHandler.class, CacheKey.class, BoundSql.class}),
        @Signature(type = Executor.class, method = "update", args = {MappedStatement.class, Object.class})
})
public class ActualSqlInterceptor implements Interceptor {

    /**
     * 配置参数
     */
    private ActualSqlProperties actualSqlProperties;

    /**
     * 时间格式化
     */
    private static final FastDateFormat NORMAL_DATE_TIME_FORMAT = FastDateFormat.getInstance("yyyy-MM-dd HH:mm:ss");

    /**
     * 零时间，如果一个date只有日期没有时间，用正则将时间部分清空
     */
    private static final Pattern ZERO_TIME_PATTERN = Pattern.compile(" 00:00:00");

    /**
     * 匹配换行
     */
    private static final Pattern MULTILINE_PATTERN = Pattern.compile("[\\s\n ]+");

    /**
     * SQL的结束符号和SQL中的占位符
     */
    private static final char SQL_DELIMITER = ';', QUESTION_MARK = '?';

    /**
     * 单引号和转义后的引号
     */
    private static final String SINGLE_QUOTE = "'", ESCAPED_SINGLE_QUOTE = "''";

    /**
     * 空格
     */
    private static final String SPACE = " ";

    /**
     * 拦截mybatis内部的query和update方法
     */
    private static final String METHOD_QUERY = "query", METHOD_UPDATE = "update";

    /**
     * Executor的query方法有4个和6个两种
     */
    private static final int PARAMETER_COUNT_4 = 4, PARAMETER_COUNT_6 = 6;

    /**
     * 拦截器的拦截方法
     * @param invocation 调用的上下文
     * @return SQL执行返回的结果
     * @throws SQLException 整个拦截器链都可能抛Throwable的异常，所以不处理直接向上抛
     */
    @Override
    public Object intercept(Invocation invocation) throws Throwable {
        if (!actualSqlProperties.isEnabled()) {
            return invocation.proceed();
        }

        Object[] args = invocation.getArgs();

        MappedStatement mappedStatement = (MappedStatement) args[0];
        Object parameter = args[1];

        Executor executor = (Executor) invocation.getTarget();

        Object result = null;

        BoundSql boundSql = null;

        Stopwatch stopwatch = Stopwatch.createStarted();

        try {
            // 拦截到query方法，由于逻辑关系，只会进入一次
            if (METHOD_QUERY.equals(invocation.getMethod().getName())) {
                RowBounds rowBounds = (RowBounds) args[2];
                ResultHandler<?> resultHandler = (ResultHandler<?>) args[3];

                CacheKey cacheKey = null;

                // 拦截到的是4个参数的query方法
                if (args.length == PARAMETER_COUNT_4) {
                    boundSql = mappedStatement.getBoundSql(parameter);
                    cacheKey = executor.createCacheKey(mappedStatement, parameter, rowBounds, boundSql);
                } else if (args.length == PARAMETER_COUNT_6) {
                    // 拦截到的是6个参数的query方法
                    cacheKey = (CacheKey) args[4];
                    boundSql = (BoundSql) args[5];
                }
                result = executor.query(mappedStatement, parameter, rowBounds, resultHandler, cacheKey, boundSql);
            } else if (METHOD_UPDATE.equals(invocation.getMethod().getName())) {
                // 拦截到update方法
                boundSql = mappedStatement.getBoundSql(parameter);
                result = executor.update(mappedStatement, parameter);
            }
        } finally {
            if (boundSql != null) {
                long elapsed = stopwatch.stop().elapsed(TimeUnit.MILLISECONDS);
                String sql = getSql(mappedStatement.getConfiguration(), boundSql);
                formatSqlLog(mappedStatement.getSqlCommandType(), mappedStatement.getId(), sql, elapsed, result);
            }
        }
        return result;
    }

    /**
     * plugin方法是拦截器用于封装目标对象的
     * 通过该方法可以返回目标对象本身，也可以返回一个代理对象
     * @param target 目标对象
     * @return 如果对象是Executor类型，返回代理类，否则返回本身
     */
    @Override
    public Object plugin(Object target) {
        if (target instanceof Executor) {
            return Plugin.wrap(target, this);
        }
        return target;
    }

    @Override
    public void setProperties(Properties properties) {
        if (properties != null) {
            actualSqlProperties = (ActualSqlProperties) properties.get("");
        }
    }

    /**
     * 获取完整拼接好参数的SQL
     * @param configuration Mybatis的配置
     * @param boundSql SQL及参数
     * @return 拼接后的SQL
     */
    private String getSql(Configuration configuration, BoundSql boundSql) {
        String sql = boundSql.getSql();
        if (StringUtils.isBlank(sql)) {
            return "";
        }
        sql = beautifySql(sql);

        /*
         * parameterObject表示SQL的参数
         * 如果是@Param配置的参数，属性为参数名以及param1, param2, ...
         * 如果是list，属性为collection和list
         */
        Object parameterObject = boundSql.getParameterObject();

        // parameterMappings表示参数#{}和javaType&jdbcType等属性的映射
        List<ParameterMapping> parameterMappings = boundSql.getParameterMappings();

        // 填充占位符，不支持存储过程的调用，也没这种需求，暂不考虑
        if (parameterObject != null && !parameterMappings.isEmpty()) {
            TypeHandlerRegistry typeHandlerRegistry = configuration.getTypeHandlerRegistry();
            if (typeHandlerRegistry.hasTypeHandler(parameterObject.getClass())) {
                sql = replacePlaceholder(sql, parameterObject, 0).getLeft();
            } else {
                MetaObject metaObject = configuration.newMetaObject(parameterObject);
                int start = 0;
                for (ParameterMapping parameterMapping : parameterMappings) {
                    // property表示参数名称，也就是@Param注解里指定的，foreach中为__frch_标签中配置的item属性_0
                    String property = parameterMapping.getProperty();
                    // 参数中是否配置了这个属性，比如用@Param配置的
                    if (metaObject.hasGetter(property)) {
                        Object value = metaObject.getValue(property);
                        Pair<String, Integer> pair = replacePlaceholder(sql, value, start);
                        sql = pair.getLeft();
                        start = pair.getRight();
                    } else if (boundSql.hasAdditionalParameter(property)) {
                        // 额外参数 比如foreach中的动态参数
                        Object value = boundSql.getAdditionalParameter(property);
                        Pair<String, Integer> pair = replacePlaceholder(sql, value, start);
                        sql = pair.getLeft();
                        start = pair.getRight();
                    } else if (StringUtils.contains(property, '.')) {
                        // 兼容fluent-mybatis这种多级的情况 ew[0].wrapperData.parameters.variable_1_1
                        String[] segments = StringUtils.split(property, '.');
                        MetaObject minMeta = metaObject;
                        String segment = null;
                        // 递归每一段 如果没有找到对应的getter则结束掉 将loopMeta置空 如果全都有 则循环结束后的minMeta为倒数第二段对象 segment为最后一段的数据
                        for (int i = 0, len = segments.length; i < len; i++) {
                            segment = segments[i];
                            if (!minMeta.hasGetter(segment)) {
                                minMeta = null;
                                break;
                            }
                            // 只有最后一段不需要递归赋值
                            if (i < len - 1) {
                                minMeta = minMeta.metaObjectForProperty(segment);
                            }
                        }
                        // 处理最后一段数据
                        if (minMeta != null) {
                            Object value = minMeta.getValue(segment);
                            Pair<String, Integer> pair = replacePlaceholder(sql, value, start);
                            sql = pair.getLeft();
                            start = pair.getRight();
                        }
                    }
                }
            }
        }
        return sql;
    }

    /**
     * 美化SQL
     * @param sql SQL语句
     * @return 美化后的SQL
     */
    private String beautifySql(String sql) {
        return MULTILINE_PATTERN.matcher(sql).replaceAll(SPACE);
    }

    /**
     * 格式化SQL日志
     * @param sqlCommandType SQL类型(insert/update/...)
     * @param sqlId mapper的方法名
     * @param sql SQL语句
     * @param elapsed SQL耗时
     * @param result 执行结果
     */
    private void formatSqlLog(SqlCommandType sqlCommandType, String sqlId, String sql, long elapsed, Object result) {
        if (actualSqlProperties.isShowMethod()) {
            actualSqlProperties.getLogLevel().log(log, "Mapper method ===> {}", sqlId);
        }
        if (actualSqlProperties.isShowSql()) {
            sql = sql.trim();
            // SQL结尾，如果SQL中没有分号将自动打印出分号
            Object endLine;
            if (sql.length() > 0 && sql.charAt(sql.length() - 1) != SQL_DELIMITER) {
                endLine = SQL_DELIMITER;
            } else {
                endLine = "";
            }
            actualSqlProperties.getLogLevel().log(log, "SQL ===> {}{}", sql, endLine);
        }
        if (actualSqlProperties.isShowElapsed()) {
            actualSqlProperties.getLogLevel().log(log, "Time Elapsed ===> {} ms", elapsed);
        }
        if (actualSqlProperties.isShowRows()) {
            switch (sqlCommandType) {
                case INSERT:
                case UPDATE:
                case DELETE:
                    actualSqlProperties.getLogLevel().log(log, "Effect Count ===> {}", result);
                    break;
                case SELECT:
                    int count;
                    if (result instanceof Collection) {
                        count = ((Collection<?>) result).size();
                    } else {
                        count = 1;
                    }
                    actualSqlProperties.getLogLevel().log(log, "Record Count ===> {}", count);
                    break;
            }
        }
    }

    /**
     * 填充占位符
     * @param sql SQL语句
     * @param parameterObject 参数
     * @param start 从哪个index开始替换
     * @return 替换后的SQL和替换后的index
     */
    private Pair<String, Integer> replacePlaceholder(String sql, Object parameterObject, int start) {
        String result;
        if (parameterObject instanceof String || parameterObject instanceof JSONObject || parameterObject instanceof JSONArray) {
            // String类型要加单引号
            result = SINGLE_QUOTE + StringUtils.replace(String.valueOf(parameterObject), SINGLE_QUOTE, ESCAPED_SINGLE_QUOTE) + SINGLE_QUOTE;
        } else if (parameterObject instanceof Date) {
            // Date类型要转成单引号的yyyy-MM-dd HH:mm:ss或yyyy-MM-dd
            String str = NORMAL_DATE_TIME_FORMAT.format((Date) parameterObject);
            result = SINGLE_QUOTE + ZERO_TIME_PATTERN.matcher(str).replaceFirst("") + SINGLE_QUOTE;
        } else {
            // 其它类型的暂时不需要处理
            result = String.valueOf(parameterObject);
        }
        int i = sql.indexOf(QUESTION_MARK, start);
        if (i < 0) {
            return Pair.of(sql, start);
        }
        return Pair.of(sql.substring(0, i) + result + sql.substring(i + 1), i + result.length());
    }
}
