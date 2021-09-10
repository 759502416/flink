package org.apache.flink.types;

import org.apache.flink.core.io.IOReadableWritable;

import java.io.Serializable;

/**
 * @author :wanghuxiong
 * @title: Value
 * @projectName flink-parent
 * @description: 可序列化值的基本接口
 * @date 2021/7/27 11:52 下午
 */
public interface Value extends IOReadableWritable, Serializable{
}
