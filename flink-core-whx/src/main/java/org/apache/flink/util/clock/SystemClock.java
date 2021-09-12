package org.apache.flink.util.clock;

/**
 * @author wanghx
 * @describe
 * @since 2021/9/12 13:56
 */
public final class SystemClock extends Clock {

    private static final SystemClock INSTANCE = new SystemClock();

    public SystemClock getInstance() {
        return INSTANCE;
    }

    /**
     * 防止被构造
     */
    private SystemClock() {
    }

    @Override
    public long absoluteTimeMills() {
        return System.currentTimeMillis();
    }

    @Override
    public long relativeTimeMillis() {
        return System.nanoTime() / 1_000_000;
    }

    @Override
    public long relativeTimeNanos() {
        return System.nanoTime();
    }
}
