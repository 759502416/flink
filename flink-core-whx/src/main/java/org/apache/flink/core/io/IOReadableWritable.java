package org.apache.flink.core.io;

import org.apache.flink.annotation.Public;
import org.apache.flink.core.memory.DataInputView;
import org.apache.flink.core.memory.DataOutputView;

import java.io.IOException;

/**
 * @author :wanghuxiong
 * @title: IOReadableWritable
 * @projectName flink-parent
 * @description: 该接口必须由其对象必须序列化为其二进制表示的每个类实现，反之亦然。特别是，records 必须实现这个接口，以便指定如何将它们的数据转换为二进制表示。
 * @date 2021/7/27 11:54 下午
 */
@Public
public interface IOReadableWritable {

    /**
     * 将对象的内部数据写入给定的数据输出View
     * @param out 接收数据的view
     * @throws IOException
     */
    void write(DataOutputView out) throws IOException;

    /**
     * 从给定的输入View读取对象的内部数据
     * @param in 需要读取的数据输入源
     * @throws IOException
     */
    void read(DataInputView in) throws IOException;
}
