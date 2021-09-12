package org.apache.flink.util.concurrent;

import java.util.concurrent.CompletionException;
import java.util.function.Consumer;

/**
 * 接口的一个被检查的扩展，它会重新抛出封装在
 *
 * @author wanghx
 * @describe
 * @since 2021/9/12 14:10
 */
public interface FutureConsumerWithException<T, E extends Throwable> extends Consumer<T> {

    void acceptWithException(T value) throws E;

    @Override
    default void accept(T value) {
        try {
            acceptWithException(value);
        } catch (Throwable t) {
            throw new CompletionException(t);
        }
    }
}
