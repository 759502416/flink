package org.apache.flink.util;

import org.apache.flink.annotation.Public;

/**
 * @author wanghx
 * @describe
 * @since 2021/9/14 17:28
 */
@Public
public class FlinkException extends Exception {

    private static final long serialVersionUID = 450688772469004724L;

    /**
     * Creates a new Exception with the given message and null as the cause.
     *
     * @param message The exception message
     */
    public FlinkException(String message) {
        super(message);
    }

    /**
     * Creates a new exception with a null message and the given cause.
     *
     * @param cause The exception that caused this exception
     */
    public FlinkException(Throwable cause) {
        super(cause);
    }

    /**
     * Creates a new exception with the given message and cause.
     *
     * @param message The exception message
     * @param cause The exception that caused this exception
     */
    public FlinkException(String message, Throwable cause) {
        super(message, cause);
    }
}
