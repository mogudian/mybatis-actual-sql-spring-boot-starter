package com.mogudiandian.mybatis.actual.sql.configuration;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 真实SQL日志的配置集合
 *
 * @author sunbo
 */
@ConfigurationProperties(prefix = "mybatis.actual.sql")
@Getter
@Setter
public class ActualSqlProperties {

    /**
     * 是否展示方法名
     */
    public boolean showMethod = true;

    /**
     * 是否展示SQL
     */
    public boolean showSql = true;

    /**
     * 是否展示执行耗时
     */
    public boolean showElapsed = true;

    /**
     * 是否展示结果行数
     */
    public boolean showRows = true;

}
