package org.apache.flink.core.memory;

import java.io.DataInput;
import java.io.IOException;

/**
 * @author :wanghuxiong
 * @title: DataInputView
 * @projectName flink-parent
 * @description: 该接口定义了一些内存的视图，可用于顺序读取内存的内容，
 *               该接口一般由多个 org.apache.flink.core.memory.MemorySegment 组成
 * @date 2021/7/28 12:00 上午
 */
public interface DataInputView extends DataInput {

    /**
     * 跳过 {@code numBytes} 字节的内存。与 {@link #skipBytes(int)} 方法相反，
     * 此方法总是跳过所需的字节数或抛出 {@link java.io.EOFException}。
     * @param numBytes 需要跳过的字节数
     * @throws IOException
     */
    void skipBytesToRead(int numBytes) throws IOException;

    /**
     * 读取最多 {@code len} 个字节的内存并将其存储到 {@code b} 中，
     * 从偏移量 * {@code off} 开始。如果没有更多数据，则返回读取字节数或 -1。
     * @param b 读取后store的数据
     * @param off 开始的读取的偏移量
     * @param len 读取的偏移量长度
     * @return
     * @throws IOException
     */
    int read(byte[] b,int off, int len) throws IOException;

    /**
     * 尝试填充给定的字节数组 {@code b}。如果没有更多数据，则返回实际读取字节数或 -1
     * @param b 字节数组来存储数据
     * @return
     * @throws IOException
     */
    int read(byte[] b) throws IOException;
}
