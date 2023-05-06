# mybatis-actual-sql-spring-boot-starter

基于 Spring Boot 和 Mybatis 拦截器，用于打印 Mybatis 执行时`实际带参数`的 SQL

Mybatis 默认 debug 日志中可以将 Prepared-SQL 和参数，在排查问题时不方便，尤其是测试/预发环境，因此开发了这个项目

## 使用说明

- 1、引用项目（已发布至中央仓库）

```xml
<dependency>
    <groupId>com.mogudiandian</groupId>
    <artifactId>mybatis-actual-sql-spring-boot-starter</artifactId>
    <version>LATEST</version>
</dependency>
```

- 2、logback配置文件（下面两种二选一，推荐使用第二种，spring 接管 logback 配置）

```xml
<!-- 这个是 logback.xml 的写法，请注意不要在生产环境配置 -->
<logger name="com.mogudiandian.mybatis.actual.sql" level="TRACE" />
```

```xml
<!-- 这个是 logback-spring.xml 的写法，推荐使用这个区分环境 -->
<springProfile name="dev,test,pre">
    <logger name="com.mogudiandian.mybatis.actual.sql" level="TRACE" />
</springProfile>
```

## 依赖三方库

| 依赖                          | 版本号           | 说明  |
|-----------------------------|---------------|-----|
| spring-boot                 | 2.3.4.RELEASE |     |
| mybatis-spring-boot-starter | 2.1.3         |     |
| fastjson                    | 1.2.73        |     |
| commons-lang3               | 3.11          |     |
| guava                       | 29.0-jre      |     |
| slf4j                       | 1.7.30        |     |
