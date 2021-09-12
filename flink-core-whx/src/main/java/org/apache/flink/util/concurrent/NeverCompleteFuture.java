package org.apache.flink.util.concurrent;

import org.apache.flink.annotation.Internal;

import javax.annotation.Nonnull;

import java.util.concurrent.CancellationException;
import java.util.concurrent.Delayed;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * @author wanghx
 * @describe 永不完成的future
 * @since 2021/9/12 14:15
 */
@Internal
public final class NeverCompleteFuture implements ScheduledFuture<Object> {

    private final Object lock = new Object();

    private final long delayMllis;

    private volatile boolean canceled;

    public NeverCompleteFuture(long delayMllis) {
        this.delayMllis = delayMllis;
    }

    @Override
    public long getDelay(@Nonnull TimeUnit timeUnit) {
        return timeUnit.convert(delayMllis, TimeUnit.MILLISECONDS);
    }

    @Override
    public int compareTo(@Nonnull Delayed delayed) {
        long otherMillis = delayed.getDelay(TimeUnit.MILLISECONDS);
        return Long.compare(this.delayMllis, otherMillis);
    }

    @Override
    public boolean cancel(boolean mayInterruptIfRunning) {
        synchronized (lock) {
            canceled = true;
            lock.notifyAll();
        }
        return true;
    }

    @Override
    public boolean isCancelled() {
        return canceled;
    }

    @Override
    public boolean isDone() {
        return false;
    }

    @Override
    public Object get() throws InterruptedException, ExecutionException {
        synchronized (lock) {
            while (!canceled) {
                lock.wait();
            }
        }
        throw new CancellationException();
    }

    @Override
    public Object get(
            long timeout,
            TimeUnit timeUnit) throws InterruptedException, ExecutionException, TimeoutException {
        synchronized (lock) {
            while (!canceled) {
                timeUnit.timedWait(lock, timeout);
            }

            if (canceled) {
                throw new CancellationException();
            } else {
                throw new TimeoutException();
            }
        }
    }
}
