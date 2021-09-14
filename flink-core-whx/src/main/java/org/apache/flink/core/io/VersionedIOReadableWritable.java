package org.apache.flink.core.io;

import org.apache.flink.annotation.Internal;
import org.apache.flink.core.memory.DataInputView;
import org.apache.flink.core.memory.DataOutputView;

import java.io.IOException;
import java.util.Arrays;

/**
 * @author wanghx
 * @describe 这是 {@link IOReadableWritable} 的抽象基类，它允许区分序列化版本。
 * 具体的子类通常应该覆盖 {@link write(DataOutputView)} 和 {@link read(DataInputView)}，
 * 从而调用 super 以确保版本检查
 * @since 2021/9/14 17:43
 */
@Internal
public abstract class VersionedIOReadableWritable implements IOReadableWritable, Versioned {

    private int readVersion = Integer.MIN_VALUE;

    @Override
    public void write(DataOutputView out) throws IOException {
        out.writeInt(getVersion());
    }

    @Override
    public void read(DataInputView in) throws IOException {
        this.readVersion = in.readInt();
        resolveVersionRead(readVersion);
    }

    /**
     * Returns the found serialization version. If this instance was not read from serialized bytes
     * but simply instantiated, then the current version is returned.
     *
     * @return the read serialization version, or the current version if the instance was not read
     *     from bytes.
     */
    public int getReadVersion() {
        return (readVersion == Integer.MIN_VALUE) ? getVersion() : readVersion;
    }

    /**
     * Returns the compatible version values.
     *
     * <p>By default, the base implementation recognizes only the current version (identified by
     * {@link #getVersion()}) as compatible. This method can be used as a hook and may be overridden
     * to identify more compatible versions.
     *
     * @return an array of integers representing the compatible version values.
     */
    public int[] getCompatibleVersions() {
        return new int[] {getVersion()};
    }

    private void resolveVersionRead(int readVersion) throws VersionMismatchException {

        int[] compatibleVersions = getCompatibleVersions();
        for (int compatibleVersion : compatibleVersions) {
            if (compatibleVersion == readVersion) {
                return;
            }
        }

        throw new VersionMismatchException(
                "Incompatible version: found "
                        + readVersion
                        + ", compatible versions are "
                        + Arrays.toString(compatibleVersions));
    }
}
