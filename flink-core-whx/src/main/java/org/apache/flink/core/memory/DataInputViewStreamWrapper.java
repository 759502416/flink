package org.apache.flink.core.memory;

import org.apache.flink.annotation.PublicEvolving;

import java.io.DataInputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;

/**
 * @author wanghx
 * @describe Utility class that turns an {@link InputStream} into a {@link DataInputView}.
 * @since 2021/9/14 17:15
 */
@PublicEvolving
public class DataInputViewStreamWrapper extends DataInputStream implements DataInputView {

    public DataInputViewStreamWrapper(InputStream in) {
        super(in);
    }

    @Override
    public void skipBytesToRead(int numBytes) throws IOException {
        if (skipBytes(numBytes) != numBytes) {
            throw new EOFException("Could not skip " + numBytes + " bytes.");
        }
    }
}
