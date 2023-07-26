package com.mogudiandian.mybatis.actual.sql.properties;

import org.slf4j.Logger;

/**
 * 日志级别
 *
 * @author Joshua Sun
 */
public enum LogLevel {

    /**
     * TRACE级别
     */
    TRACE {
        @Override
        public void log(Logger logger, String format, Object... arguments) {
            logger.trace(format, arguments);
        }
    },

    /**
     * DEBUG级别
     */
    DEBUG {
        @Override
        public void log(Logger logger, String format, Object... arguments) {
            logger.debug(format, arguments);
        }
    },

    /**
     * INFO级别
     */
    INFO {
        @Override
        public void log(Logger logger, String format, Object... arguments) {
            logger.info(format, arguments);
        }
    };

    /**
     * 记录日志
     * @param logger 日志记录器
     * @param format 格式
     * @param arguments 参数
     */
    public abstract void log(Logger logger, String format, Object... arguments);

}
