package org.apache.flink.util.clock;

import org.apache.flink.annotation.PublicEvolving;

import java.time.Duration;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * @author wanghx
 * @describe
 * @since 2021/9/11 15:59
 */
@PublicEvolving
public final class ManualClock extends Clock {

    /**
     * nano 精度
     */
    private final AtomicLong currentTime;

    public ManualClock() {
        this(0);
    }

    public ManualClock(long startTime) {
        this.currentTime = new AtomicLong(startTime);
    }

    @Override
    public long absoluteTimeMills() {
        return currentTime.get() / 1_000_000L;
    }

    @Override
    public long relativeTimeMillis() {
        return currentTime.get() / 1_000_000L;
    }

    @Override
    public long relativeTimeNanos() {
        return currentTime.get();
    }

    /**
     * 当前时间前进制定的nano time
     *
     * @param duration
     * @param timeUnit
     */
    public void advanceTime(long duration, TimeUnit timeUnit) {
        currentTime.addAndGet(timeUnit.toNanos(duration));
    }

    /**
     * 将时间提前给定的时间。时间也可以通过提供负面信息而向后移动
     * 此方法执行不溢出检查。
     *
     * @param duration
     */
    public void advanceTime(Duration duration) {
        currentTime.addAndGet(duration.toNanos());
    }
}
