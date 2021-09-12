package org.apache.flink.util.clock;

/**
 * 一个可以访问时间的时钟。这个时钟返回两种时间：
 *
 * <h3> Absolute Time 绝对时间</h3>
 *
 * <p>这是指真实世界的挂钟时间，通常源自系统时钟。受时钟漂移和不准确的影响，如果调整系统时钟，可能会发生跳跃。绝对时间的行为类似于 {@link SystemcurrentTimeMillis()}。
 *
 * <h3>Relative Time 相对时间</h3>
 * 此时间的前进速度与绝对时间相同，但时间戳只能
 * 被相对地提及。时间戳没有绝对意义，也不可能有绝对意义
 * 跨JVM进程比较。时间戳的来源不受调整的影响
 * 系统时钟，所以它永远不会跳。相对时间的行为类似于{@link System#nanoTime()}。
 * @author wanghx
 * @describe
 * @since 2021/9/11 15:21
 */
public abstract class Clock {

    /**
     * Gets the current absolute time, in milliseconds.
     * @return
     */
    public abstract long absoluteTimeMills();

    /**
     * Gets the current relative time, in milliseconds
     * @return
     */
    public abstract long relativeTimeMillis();

    /**
     * Gets the current relative time, in nanoseconds
     * @return
     */
    public abstract long relativeTimeNanos();
}
