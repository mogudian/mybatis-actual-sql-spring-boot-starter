package com.mogudiandian.mybatis.actual.sql.configuration;

import org.springframework.context.annotation.Import;

import java.lang.annotation.*;

/**
 * 启用SQL日志功能
 * @author sunbo
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Import(ActualSqlConfiguration.class)
public @interface EnableActualSql {

}
