package org.apache.flink.core.memory;

import org.apache.flink.annotation.Public;

import java.io.DataOutput;
import java.io.IOException;

/**
 * @author :wanghuxiong
 * @title: DataOutputView
 * @projectName flink-parent
 * @description: 该接口定义了一些内存的视图，可用于将内容顺序写入内存。
 *                  该视图通常由一个或多个 {@link *org.apache.flink.core.memory.MemorySegment} 支持。
 * @date 2021/7/27 11:57 下午
 */
@Public
public interface DataOutputView extends DataOutput {

    /**
     * 跳过 {@code numBytes} 字节内存。如果某个程序读取了被跳过的内存，结果是不确定的。
     * @param numBytes 跳过的字节数
     * @throws IOException
     */
    void skipBytesToWrite(int numBytes) throws IOException;

    /**
     * 从源View copy{@code numBytes} 字节
     * @param source 需要copy的数据源
     * @param numBytes 需要copy的字节数
     * @throws IOException
     */
    void write(DataInputView source, int numBytes) throws IOException;
}
