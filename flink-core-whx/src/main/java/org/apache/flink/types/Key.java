package org.apache.flink.types;

import org.apache.flink.annotation.PublicEvolving;

/**
 * @author :wanghuxiong
 * @title: Key
 * @projectName flink-parent
 * @description: 这个接口必须由所有作为键的数据类型来实现。键用于建立值之间的关系。
 * 键必须始终 {@link java.lang.Comparable} 到其他相同类型的键。
 * 此外，键必须实现正确的 {@link * java.lang.Object#hashCode()} 方法和
 * {@link java.lang.Object#equals(Object)} 方法以确保键上的分组正常工作。
 * @date 2021/7/27 11:50 下午
 */
@Deprecated
@PublicEvolving
public interface Key<T> extends Value, Comparable<T> {

    /**
     *
     * @return
     */
    @Override
    int hashCode();

    /**
     *
     * @param other
     * @return
     */
    @Override
    boolean equals(Object other);
}
