package org.apache.flink.core.io;

import org.apache.flink.annotation.PublicEvolving;

import java.io.IOException;

/**
 * @author wanghx
 * @describe 此异常表示在序列化期间发现了不兼容的版本。
 * @since 2021/9/14 17:47
 */
@PublicEvolving
public class VersionMismatchException extends IOException {

    private static final long serialVersionUID = 7024258967585372438L;

    public VersionMismatchException() {}

    public VersionMismatchException(String message) {
        super(message);
    }

    public VersionMismatchException(String message, Throwable cause) {
        super(message, cause);
    }

    public VersionMismatchException(Throwable cause) {
        super(cause);
    }
}
