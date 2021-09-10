package org.apache.flink.util;

import org.apache.flink.annotation.Internal;

import javax.annotation.Nullable;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

/**
 * @author :wanghuxiong
 * @title: Preconditions
 * @projectName flink-parent
 * @description: 一组用于验证输入的静态实用方法。
 *         该类仿照Google Guava 的Preconditions 类建模，并部分取自该类的代码。
 *         我们将此代码添加到 Flink 代码库中，以减少外部依赖。
 * @date 2021/7/29 12:00 上午
 */
@Internal
public class Preconditions {

    // ------------------------------------------------------------------------
    //  Null checks
    // ------------------------------------------------------------------------

    /**
     * 确保给定的对象引用不为空。违反时，会抛出一个没有消息的 {@code NullPointerException}。
     *
     * @param reference The object reference
     *
     * @return @code reference 本身
     *
     * @throws NullPointerException Thrown, if the passed reference was null.
     */
    public static <T> T checkNotNull(@Nullable T reference) {
        if (reference == null) {
            throw new NullPointerException();
        }
        return reference;
    }

    /**
     * Ensures that the given object reference is not null. Upon violation,a {@code
     * NullPointException} with the given message is thrown
     *
     * @param reference The object reference
     * @param errorMessage The message for the {@code NullPointerException} that is thrown if the
     *         check fails
     *
     * @return NullPointException Throen,if the passed reference was null.
     */
    public static <T> T checkNotNull(@Nullable T reference, @Nullable String errorMessage) {
        if (reference == null) {
            throw new NullPointerException(String.valueOf(errorMessage));
        }
        return reference;
    }

    public static <T> T checkNotNull(
            T reference,
            @Nullable String errorMessageTemplate,
            @Nullable Object... errorMessageArgs
    ) {
        if (reference == null) {
            throw new NullPointerException(format(errorMessageTemplate, errorMessageArgs));
        }
        return reference;
    }

    /**
     * 检查传入的表达式，如果表达式不成立，则抛出参数异常的Exception
     *
     * @param condition
     */
    public static void checkArgument(boolean condition) {
        if (!condition) {
            throw new IllegalArgumentException();
        }
    }

    public static void checkArgument(boolean condition, @Nullable Object errorMessage) {
        if (!condition) {
            throw new IllegalArgumentException(String.valueOf(errorMessage));
        }
    }

    public static void checkArgument(
            boolean condition,
            @Nullable String errorMessageTemplate,
            @Nullable Object... errorMessageArgs
    ) {
        if (!condition) {
            throw new IllegalArgumentException(format(errorMessageTemplate, errorMessageArgs));
        }
    }

    /**
     * 检查状态表达式是否成立，如果不成立，则抛出状态异常。
     *
     * @param condition
     */
    public static void checkState(boolean condition) {
        if (!condition) {
            throw new IllegalStateException();
        }
    }

    public static void checkState(boolean condition, @Nullable Object errorMessage) {
        if (!condition) {
            throw new IllegalStateException(String.valueOf(errorMessage));
        }
    }

    public static void checkState(
            boolean condition,
            @Nullable String errorMessageTemplate,
            @Nullable Object... errorMessageArgs) {
        if (!condition) {
            throw new IllegalStateException(format(errorMessageTemplate, errorMessageArgs));
        }
    }

    public static void checkElementIndex(int index, int size) {
        checkArgument(size >= 0, "Size was negative");
        if (index < 0 || index >= size) {
            throw new IndexOutOfBoundsException("Index: " + index + ", Size: " + size);
        }
    }

    public static void checkElementIndex(int index, int size, @Nullable String errorMessage) {
        checkArgument(size >= 0, "Size was negative ");
        if (index < 0 || index >= size) {
            throw new IndexOutOfBoundsException(
                    String.valueOf(errorMessage) + " Index: " + index + ", Size: " + size);
        }
    }

    /**
     * Ensures that future has completed normally.
     *
     * @throws IllegalStateException Thrown, if future has not completed or it has completed
     *     exceptionally.
     * @param future
     */
    public static void checkCompletedNormally(CompletableFuture<?> future) {
        checkState(future.isDone());
        if (future.isCompletedExceptionally()) {
            try {
                future.get();
            } catch (InterruptedException | ExecutionException e) {
                throw new IllegalStateException(e);
            }
        }
    }

    /**
     * A simplified formatting method. Similar to {@link String#format(String, Object...)},but with
     * lower overhead (only String parameters, no locale, no format validation).
     * <p>
     * 这个方法是从 Guava Preconditions 类中逐字提取的。
     *
     * @param template 模版
     * @param args 参数
     *
     * @return
     */
    private static String format(@Nullable String template, @Nullable Object... args) {
        final int numArgs = args == null ? 0 : args.length;
        // null -> "null"！！！！ 是真的会变成字符串null
        template = String.valueOf(template);

        // start substituting the arguments into the '%s' placeholders
        StringBuilder builder = new StringBuilder(template.length() + 16 * numArgs);
        int templateStart = 0;
        int i = 0;
        while (i < numArgs) {
            int placeholderStart = template.indexOf("%s", templateStart);
            if (placeholderStart == -1) {
                break;
            }
            builder.append(template.substring(templateStart, placeholderStart));
            builder.append(args[i++]);
            templateStart = placeholderStart + 2;
        }
        builder.append(template.substring(templateStart));

        // if we run out of placeholders,append the extra args in square braces
        if (i < numArgs) {
            builder.append("[");
            builder.append(args[i++]);
            while (i < numArgs) {
                builder.append(", ");
                builder.append(args[i++]);
            }
            builder.append("]");
        }

        return builder.toString();
    }

    // ------------------------------------------------------------------------

    /** Private constructor to prevent instantiation. */
    private Preconditions() {
    }
}
