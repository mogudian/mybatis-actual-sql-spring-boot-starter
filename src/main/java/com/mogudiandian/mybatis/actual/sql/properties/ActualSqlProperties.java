package com.mogudiandian.mybatis.actual.sql.properties;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 真实SQL日志的配置集合
 *
 * @author Joshua Sun
 */
@ConfigurationProperties(prefix = "mybatis.actual.sql")
@Getter
@Setter
public class ActualSqlProperties {

    /**
     * 是否启用
     */
    private boolean enabled = true;

    /**
     * 是否展示方法名
     */
    private boolean showMethod = true;

    /**
     * 是否展示SQL
     */
    private boolean showSql = true;

    /**
     * 是否展示执行耗时
     */
    private boolean showElapsed = true;

    /**
     * 是否展示结果行数
     */
    private boolean showRows = true;

    /**
     * 日志级别
     */
    private LogLevel logLevel = LogLevel.TRACE;

}
