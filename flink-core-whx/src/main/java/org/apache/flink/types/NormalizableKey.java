package org.apache.flink.types;

import org.apache.flink.annotation.Public;

/**
 * @author :wanghuxiong
 * @title: NormalizableKey
 * @projectName flink-parent
 * @description: 规范化键的基本接口。
 * 可规范化的键可以创建本身的二进制表示，可以按字节进行比较。
 * 两个规范化键的逐字节比较继续进行直到比较所有字节或相应位置的两个字节不相等。
 * 如果两个对应的字节值不相等，则较低的字节值表示较低的键。
 * 如果两个标准化的键在字节上完全相同，则可能必须查看实际的键来确定哪个键实际上更低。
 * 后者取决于规范化的key是覆盖整个key还是只是key的前缀。如果标准化key的长度小于最大标准化key长度，
 * 则将其视为前缀。
 *
 *
 *
 * 该接口指定了实现规范化的键(normalizable key)需要满足的契约。先来解释一下什么叫作“规范化的键”，规范化的键指一种在二进制表示的方式下可以进行逐字节比较的键。而要使两个规范化的键能够比较，首先对于同一种类型，它们的最大字节长度要是相等的。对于这个条件，通过接口方法getMaxNormalizedKeyLen来定义。它针对一种类型通常都会返回一个常数值。比如对于32位的整型，它会返回常数值4。但一个规范化的键所占用的字节数不一定要跟该类型的最大字节数相等。当它比规定的最大的字节数小时，可以认为它只是该规范化键的一种“前缀”。
 *
 * 两个规范化的键进行比较，但满足两个条件的其中之一后会停止：
 *
 * 所有的字节都比较完成
 * 两个相同位置的字节不相等
 * 关于比较的结果，如果在相同的位置，两个字节的值不相等则值小的一个键被认为其整个键会小于另外一个键
 * ————————————————
 * 版权声明：本文为CSDN博主「vinoYang」的原创文章，遵循CC 4.0 BY-SA版权协议，转载请附上原文出处链接及本声明。
 * 原文链接：https://blog.csdn.net/yanghua_kobe/article/details/51868643
 * @date 2021/7/27 11:49 下午
 */
@Public
public interface NormalizableKey<T> extends Comparable<T>, Key<T> {

    /**
     * 获取数据类型将生成的规范化键的最大长度，以仅通过规范化键确定实例的顺序。
     * {@link * java.lang.Integer}.MAX_VALUE 的值被解释为无限。
     * <p>例如，32 位整数返回 4，而字符串（可能长度不受限制）返回 {@link java.lang.Integer}.MAX_VALUE。
     * @return 规范化键的最大长度。
     */
    int getMaxNormalizedKeyLen();

    void copyNormalizedKey()
}
