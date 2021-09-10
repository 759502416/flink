package org.apache.flink.core.memory;

import org.apache.flink.annotation.Internal;
import org.apache.flink.annotation.VisibleForTesting;
import org.apache.flink.util.Preconditions;

import sun.misc.Unsafe;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.nio.BufferOverflowException;
import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.ReadOnlyBufferException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.function.Function;

import static org.apache.flink.core.memory.MemoryUtils.getByteBufferAddress;

/**
 * @author :wanghuxiong
 * @title: MemorySegment
 * @projectName flink-parent
 * @description: 这个类代表了 Flink 管理的一块内存
 *         <p>
 *         内存可以是是 on-heap、off-heap direct 或 off-heap unsafe. 都是由这个类进行处理
 *         <p>
 *         这个类在概念上实现了与 Java 的 {@link java.nio.ByteBuffer} 类似的目的。我们出于各种原因添加这个专门的类：
 *         <p>
 *         1、它提供了额外的 字节 compare、swap 以及copy 方法
 *         2、它使用折叠检查进行范围检查和内存段处理
 *         3、它为批量put/get方法提供绝对定位方法，以保证线程安全使用。
 *         4、它提供显式的大端/小端访问方法，而不是在内部使用字节顺序去访问
 *         5、它透明有效地在on-heap和off-heap变体之间移动数据
 *         <p>
 *         我们大量使用原生指令支持的操作，以实现高效率。多字节类型（int、long、float、double、...）
 *         使用“unsafe”native commands读取和写入
 *         <p>
 *         效率注意事项：
 *         为了获得最佳效率，我们没有将不同内存类型的实现与继承分开，以避免在调用抽象方法时寻找具体的实现的开销
 * @date 2021/7/28 11:40 下午
 */
@Internal
public class MemorySegment {

    /*** 激活多个 free segment 检查的系统属性，用于测试 */
    public static final String CHECK_MULTIPLE_FREE_PROPERTY =
            "flink.tests.check-segment-multiple-free";

    private static final boolean checkMultipleFree =
            System.getProperties().containsKey(CHECK_MULTIPLE_FREE_PROPERTY);

    private static final Unsafe UNSAFE = MemoryUtils.UNSAFE;

    private static final long BYTE_ARRAY_BASE_OFFSET = UNSAFE.arrayBaseOffset(byte[].class);

    /** 标记字节顺序的常量。因为这是一个布尔常量，所以 JIT 编译器可以很好地利用它来积极地消除不适用的代码路径。 **/
    private static final boolean LITTLE_ENDIAN =
            (ByteOrder.nativeOrder() == ByteOrder.LITTLE_ENDIAN);

    /**
     * The heap byte array object relative to which we access the memory.
     *
     * <p>非<tt>null<tt>，如果内存在堆上，并且<tt>null<tt>，
     * 如果内存不在堆上。如果我们有这个缓冲区，我们决不能使这个引用无效，
     * 否则memory segment 将指向堆外的未定义地址，并可能在乱序执行的情况下导致segment错误。
     */
    @Nullable
    private final byte[] heapMemory;

    /**
     * 包装堆外内存的直接字节缓冲区。该内存段持有对该缓冲区的引用，因此只要该内存段存在，内存就不会被释放
     */
    @Nullable
    private ByteBuffer offHeadBuffer;

    /**
     * 数据的地址，相对于堆内存字节数组。如果堆内存字节数组为<tt>null<tt>，这将成为堆外的绝对内存地址。
     */
    private long address;

    /**
     * 在最后一个可寻址字节之后一个字节的地址，即 <tt>address + size<tt>，而该段未被处理。
     */
    private final long addressLimit;

    /** THe size in bytes of the memory segment. **/
    private final int size;

    /** Optional owner of the memory segment */
    @Nullable
    private final Object owner;

    @Nullable
    private Runnable cleaner;

    /**
     * Wrapping is not allowed when the underlying memory is unsafe. Unsafe memory can be actively
     * released, without reference counting. Therefore, access from wrapped buffers, which may not
     * be aware of the releasing of memory, could be risky.
     */
    private final boolean allowWrap;

    private final AtomicBoolean isFreedAtomic;

    MemorySegment(@Nonnull byte[] buffer, @Nullable Object owner) {
        this.heapMemory = buffer;
        this.offHeadBuffer = null;
        this.size = buffer.length;
        this.address = BYTE_ARRAY_BASE_OFFSET;
        this.addressLimit = this.address + this.size;
        this.owner = owner;
        this.allowWrap = true;
        this.cleaner = null;
        this.isFreedAtomic = new AtomicBoolean(false);
    }


    MemorySegment(@Nonnull ByteBuffer buffer, @Nullable Object owner) {
        this(buffer, owner, true, null);
    }

    MemorySegment(
            @Nonnull ByteBuffer buffer,
            @Nullable Object owner,
            boolean allowWrap,
            @Nullable Runnable cleaner
    ) {
        this.heapMemory = null;
        this.offHeadBuffer = buffer;
        this.size = buffer.capacity();
        this.address = MemoryUtils.getByteBufferAddress(buffer);
        this.addressLimit = this.address + this.size;
        this.owner = owner;
        this.allowWrap = allowWrap;
        this.cleaner = cleaner;
        this.isFreedAtomic = new AtomicBoolean(false);
    }

    // ------------------------------------------------------------------------
    // Memory Segment Operations
    // ------------------------------------------------------------------------

    /**
     * Gets the size of the memory segment, in bytes.
     *
     * @return The size of the memory segment
     */
    public int size() {
        return size;
    }

    @VisibleForTesting
    public boolean isFreed() {
        return address > addressLimit;
    }

    /**
     * Frees this memory segment.
     * 调用此操作后，将无法对内存段进行进一步的操作，并且将失败。实际内存（堆或堆外）只会在此内存段对象被垃圾回收后才会释放。
     */
    public void free() {
        if (isFreedAtomic.getAndSet(true)) {
            // the segment has already been freed
            if (checkMultipleFree) {
                throw new IllegalStateException("MemorySegment can be freed only once!");
            }
        }else {
            // this ensures we can place no more data and trigger
            // the checks for the freed segment
            // 这确保我们不能放置更多数据并触发对freed segment 的检查
            address = addressLimit + 1;
            offHeadBuffer = null; // to enable GC of unsafe memory
            if (cleaner != null) {
                cleaner.run();
                cleaner = null;
            }
        }
    }

    public boolean isOffHeap() {
        return heapMemory == null;
    }

    /**
     * Returns the byte array of on-heap memory segments
     * @return
     */
    public byte[] getArray() {
        if (heapMemory != null) {
            return heapMemory;
        }else {
            throw new IllegalStateException("Memory segment does not represent head memory");
        }
    }

    /**
     * Returns the memory address of off-heap memory segments.
     *
     * @return 堆外的绝对内存地址
     */
    public long getAddress() {
        if (heapMemory == null) {
            return address;
        } else {
            throw new IllegalStateException("Memory segment does not represent off heap memory");
        }
    }

    /**
     * 将位于 <tt>offset<tt> 和 <tt>offset + length<tt> 之间的底层内存块包装在 NIO ByteBuffer 中。
     * ByteBuffer 将完整段作为容量，偏移量和长度参数设置缓冲区的位置和限制。
     *
     * @param offset 这个memory segment的偏移量
     * @param length 包装buffer的长度
     * @return
     */
    public ByteBuffer wrap(int offset, int length) {
        if (!allowWrap) {
            throw new UnsupportedOperationException(
                    "Wrap is not supported by this segment. This usually indicates that the underlying memory is unsafe,"
                            + "thus transferring of ownership is not allowed "
            );
        }
        return wrapInternal(offset, length);
    }

    private ByteBuffer wrapInternal(int offset, int length) {
        if (address <=  addressLimit) {
            if (heapMemory != null) {
                return ByteBuffer.wrap(heapMemory, offset, length);
            } else {
                try {
                    ByteBuffer wrapper = Preconditions.checkNotNull(offHeadBuffer).duplicate();
                    wrapper.limit(offset + length);
                    wrapper.position(offset);
                    return wrapper;
                } catch (IllegalArgumentException e) {
                    throw new IndexOutOfBoundsException();
                }
            }
        } else {
            throw new IllegalStateException("segment has been freed");
        }
    }


    @Nullable
    public Object getOwner() {
        return owner;
    }

    // ------------------------------------------------------------------------
    //                    Random Access get() and put() methods
    // ------------------------------------------------------------------------

    // ------------------------------------------------------------------------
    // Notes on the implementation: We try to collapse as many checks as
    // possible. We need to obey the following rules to make this safe
    // against segfaults:
    //
    //  - Grab mutable fields onto the stack before checking and using. This
    //    guards us against concurrent modifications which invalidate the
    //    pointers
    //  - Use subtractions for range checks, as they are tolerant
    // ------------------------------------------------------------------------

    /**
     * Reads the byte at the given position
     * @param index The position form which the byte will be read
     * @return The byte at the given position
     * @throws IndexOutOfBoundsException Thrown, if the index is negative,or larger or equal to the
     *      size of the memory segment.
     */
    public byte get(int index) {
        final long pos = address + index;
        if (index >= 0 && pos < addressLimit) {
            return UNSAFE.getByte(heapMemory,pos);
        } else if (address > addressLimit) {
            throw new IllegalStateException("segment has been freed");
        } else {
            // index is in fact invalid
            throw new IndexOutOfBoundsException();
        }
    }

    /**
     * Writes the given byte into this buffer at the given position
     *
     * @param index The index at which the byte will be written.
     * @param b The byte value to be written
     * @throws IndexOutOfBoundsException Thrown,if the index is negative, or larger or equal to the
     *      size of the memory segment
     */
    public void put(int index, byte b) {
        final long pos = address + index;
        if (index >= 0 && pos < addressLimit) {
            UNSAFE.putByte(heapMemory, pos, b);
        }else if (address > addressLimit) {
            throw new IllegalStateException("segment has been freed");
        }else {
            // index is in fact invalid
            throw new IndexOutOfBoundsException();
        }
    }

    /**
     * 批量获取方法。将 dst.length 内存从指定位置复制到目标内存。
     * @param index 将读取第一个字节的位置。
     * @param dst 目标内存
     */
    public void get(int index, byte[] dst) {
        get(index, dst, 0, dst.length);
    }

    public void put(int index, byte[] src) {
        put(index,src,0,src.length);
    }


    public void get(int index, byte[] dst, int offset, int length) {
       // check the byte array offset and length and the status
        if ((offset | length | (offset + length) | (dst.length - (offset + length))) < 0) {
            throw new IndexOutOfBoundsException();
        }

        final long pos = address + index;
        if (index >= 0 && pos <= addressLimit -length) {
            final long arrayAddress = BYTE_ARRAY_BASE_OFFSET + offset;
            UNSAFE.copyMemory(heapMemory, pos, dst, arrayAddress, length);
        } else if (address > addressLimit) {
            throw new IllegalStateException("segment has been freed");
        } else {
            throw new IndexOutOfBoundsException(
                    String.format(
                            "pos: %d,length: %d, index: %d,offset: %d",
                            pos, length, index, offset));
        }
    }

    public void put(int index, byte[] src, int offset, int length) {
        // check the byte array offset and length
        if ((offset | length | (offset + length) | (src.length - (offset + length))) < 0) {
            throw new IndexOutOfBoundsException();
        }
        final long pos = address + index;

        if (index >= 0 && pos <= addressLimit - length) {
            final long arrayAddress = BYTE_ARRAY_BASE_OFFSET + offset;
            UNSAFE.copyMemory(src,arrayAddress,heapMemory, pos,length);
        } else if (address > addressLimit) {
            throw new IllegalStateException("segment has been freed");
        } else {
            // index is in fact invalid
            throw new IndexOutOfBoundsException();
        }
    }

    /**
     * Reads one byte at the given position and returns its boolean representation
     * @param index
     * @return
     */
    public boolean getBoolean(int index) {
        return get(index) != 0;
    }

    public void putBoolean(int index, boolean value) {
        put(index,(byte) (value ? 1 : 0));
    }

    @SuppressWarnings("restriction")
    public char getChar(int index) {
        final long pos = address + index;
        if (index >= 0 && pos <=  addressLimit -2) {
            return UNSAFE.getChar(heapMemory, pos);
        } else if (address > addressLimit) {
            throw new IllegalStateException("This segment has been freed.");
        } else {
            // index is in face invalid
            throw new IndexOutOfBoundsException();
        }
    }

    public char getCharLittleEndian(int index) {
        if (LITTLE_ENDIAN) {
            return getChar(index);
        } else {
            return Character.reverseBytes(getChar(index));
        }
    }

    public char getCharBigEndian(int index) {
        if (LITTLE_ENDIAN) {
            return Character.reverseBytes(getChar(index));
        } else {
            return getChar(index);
        }
    }

    @SuppressWarnings("restriction")
    public void putChar(int index,char value) {
        final long pos = address + index;
        if (index >= 0 && pos <= addressLimit -2) {
            UNSAFE.putChar(heapMemory,pos, value);
        } else if (address > addressLimit) {
            throw new IllegalStateException("segment has benn freed");
        } else {
            // index is in fact invalid
            throw new IndexOutOfBoundsException();
        }
    }

    public void putCharLittleEndian(int index, char value) {
        if (LITTLE_ENDIAN) {
            putChar(index, value);
        } else {
            putChar(index, Character.reverseBytes(value));
        }
    }

    public void putCharBigEndian(int index, char value) {
        if (LITTLE_ENDIAN) {
            putChar(index, Character.reverseBytes(value));
        } else {
            putChar(index, value);
        }
    }

    public short getShort(int index) {
        final long pos = address + index;
        if (index >= 0 && pos <= addressLimit - 2) {
            return UNSAFE.getShort(heapMemory, pos);
        } else if (address > addressLimit) {
            throw new IllegalStateException("segment has been freed");
        } else {
            // index is in fact invalid
            throw new IndexOutOfBoundsException();
        }
    }

    public short getShortLittleEndian(int index) {
        if (LITTLE_ENDIAN) {
            return getShort(index);
        } else {
            return Short.reverseBytes(getShort(index));
        }
    }

    public short getShortBigEndian(int index) {
        if (LITTLE_ENDIAN) {
            return Short.reverseBytes(getShort(index));
        } else {
            return getShort(index);
        }
    }

    public void putShort(int index, short value) {
        final long pos = address + index;
        if (index >= 0 && pos <= addressLimit - 2) {
            UNSAFE.putShort(heapMemory, pos, value);
        } else if (address > addressLimit) {
            throw new IllegalStateException("segment has been freed");
        } else {
            // index is in fact invalid
            throw new IndexOutOfBoundsException();
        }
    }

    public void putShortLittleEndian(int index, short value) {
        if (LITTLE_ENDIAN) {
            putShort(index, value);
        } else {
            putShort(index, Short.reverseBytes(value));
        }
    }

    public void putShortBigEndian(int index, short value) {
        if (LITTLE_ENDIAN) {
            putShort(index, Short.reverseBytes(value));
        } else {
            putShort(index, value);
        }
    }


    public int getInt(int index) {
        final long pos = address + index;
        if (index >= 0 && pos <= addressLimit - 4) {
            return UNSAFE.getInt(heapMemory, pos);
        } else if (address > addressLimit) {
            throw new IllegalStateException("segment has been freed");
        } else {
            // index is in fact invalid
            throw new IndexOutOfBoundsException();
        }
    }

    public int getIntLittleEndian(int index) {
        if (LITTLE_ENDIAN) {
            return getInt(index);
        } else {
            return Integer.reverseBytes(getInt(index));
        }
    }

    public int getIntBigEndian(int index) {
        if (LITTLE_ENDIAN) {
            return Integer.reverseBytes(getInt(index));
        } else {
            return getInt(index);
        }
    }

    public void putInt(int index, int value) {
        final long pos = address + index;
        if (index >= 0 && pos <= addressLimit - 4) {
            UNSAFE.putInt(heapMemory, pos, value);
        } else if (address > addressLimit) {
            throw new IllegalStateException("segment has been freed");
        } else {
            // index is in fact invalid
            throw new IndexOutOfBoundsException();
        }
    }

    public void putIntLittleEndian(int index, int value) {
        if (LITTLE_ENDIAN) {
            putInt(index, value);
        } else {
            putInt(index, Integer.reverseBytes(value));
        }
    }

    public void putIntBigEndian(int index, int value) {
        if (LITTLE_ENDIAN) {
            putInt(index, Integer.reverseBytes(value));
        } else {
            putInt(index, value);
        }
    }


    public long getLong(int index) {
        final long pos = address + index;
        if (index >= 0 && pos <= addressLimit - 8) {
            return UNSAFE.getLong(heapMemory, pos);
        } else if (address > addressLimit) {
            throw new IllegalStateException("segment has been freed");
        } else {
            // index is in fact invalid
            throw new IndexOutOfBoundsException();
        }
    }

    public long getLongLittleEndian(int index) {
        if (LITTLE_ENDIAN) {
            return getLong(index);
        } else {
            return Long.reverseBytes(getLong(index));
        }
    }

    public long getLongBigEndian(int index) {
        if (LITTLE_ENDIAN) {
            return Long.reverseBytes(getLong(index));
        } else {
            return getLong(index);
        }
    }

    public void putLong(int index, long value) {
        final long pos = address + index;
        if (index >= 0 && pos <= addressLimit - 8) {
            UNSAFE.putLong(heapMemory, pos, value);
        } else if (address > addressLimit) {
            throw new IllegalStateException("segment has been freed");
        } else {
            // index is in fact invalid
            throw new IndexOutOfBoundsException();
        }
    }

    public void putLongLittleEndian(int index, long value) {
        if (LITTLE_ENDIAN) {
            putLong(index, value);
        } else {
            putLong(index, Long.reverseBytes(value));
        }
    }

    public void putLongBigEndian(int index, long value) {
        if (LITTLE_ENDIAN) {
            putLong(index, Long.reverseBytes(value));
        } else {
            putLong(index, value);
        }
    }

    public float getFloat(int index) {
        return Float.intBitsToFloat(getInt(index));
    }

    public float getFloatLittleEndian(int index) {
        return Float.intBitsToFloat(getIntLittleEndian(index));
    }

    public float getFloatBigEndian(int index) {
        return Float.intBitsToFloat(getIntBigEndian(index));
    }

    public void putFloat(int index, float value) {
        putInt(index, Float.floatToRawIntBits(value));
    }

    public void putFloatLittleEndian(int index, float value) {
        putIntLittleEndian(index, Float.floatToRawIntBits(value));
    }

    public void putFloatBigEndian(int index, float value) {
        putIntBigEndian(index, Float.floatToRawIntBits(value));
    }

    public double getDouble(int index) {
        return Double.longBitsToDouble(getLong(index));
    }

    public double getDoubleLittleEndian(int index) {
        return Double.longBitsToDouble(getLongLittleEndian(index));
    }

    public double getDoubleBigEndian(int index) {
        return Double.longBitsToDouble(getLongBigEndian(index));
    }

    public void putDouble(int index, double value) {
        putLong(index, Double.doubleToRawLongBits(value));
    }

    public void putDoubleLittleEndian(int index, double value) {
        putLongLittleEndian(index, Double.doubleToRawLongBits(value));
    }

    public void putDoubleBigEndian(int index, double value) {
        putLongBigEndian(index, Double.doubleToRawLongBits(value));
    }

    // ------------------------------------------------------------------------------------
    //                     Bulk Read and Write Methods
    // ------------------------------------------------------------------------------------
    public void get(DataOutput out, int offset, int length) throws IOException {
        if (address <= addressLimit) {
            if (heapMemory  != null) {
                out.write(heapMemory,offset,length);
            }else {
                while (length >= 8) {
                    out.writeLong(getLongBigEndian(offset));
                    offset += 8;
                    length -= 8;
                }

                while (length > 0) {
                    out.writeByte(get(offset));
                    offset++;
                    length--;
                }
            }
        } else {
            throw new IllegalStateException("segment has been freed");
        }
    }

    /**
     * Bulk put method,Copies length memory from the given DataInput to the memory starting at
     * position offset
     * @param in
     * @param offset
     * @param length
     * @throws IOException
     */
    public void put(DataInput in, int offset, int length) throws IOException {
        if (address <= addressLimit) {
            if(heapMemory != null) {
                in.readFully(heapMemory,offset,length);
            } else {
                while(length >= 8) {
                    putLongBigEndian(offset,in.readLong());
                    offset += 8;
                    length -= 8;
                }
                while (length > 0) {
                    put(offset,in.readByte());
                    offset++;
                    length--;
                }
            }
        } else {
            throw new IllegalStateException("segment has been freed");
        }
    }


    public void get(int offset,ByteBuffer target, int numBytes) {
        // check the byte array offset and length
        if ((offset | numBytes | (offset + numBytes)) < 0) {
            throw new IndexOutOfBoundsException();
        }
        if (target.isReadOnly()) {
            throw new ReadOnlyBufferException();
        }

        final int targetOffset = target.position();
        final int remaining = target.remaining();

        if (remaining < numBytes) {
            throw new BufferOverflowException();
        }

        if (target.isDirect()) {
            // copy to the target memory directly
            final long targetPointer = getByteBufferAddress(target) + targetOffset;
            final long sourcePointer = address + offset;

            if (sourcePointer <= addressLimit - numBytes) {
                UNSAFE.copyMemory(heapMemory, sourcePointer, null, targetPointer, numBytes);
                target.position(targetOffset + numBytes);
            } else if (address > addressLimit) {
                throw new IllegalStateException("segment has been freed");
            } else {
                throw new IndexOutOfBoundsException();
            }
        } else if (target.hasArray()) {
            // move directly into the byte array
            get(offset, target.array(), targetOffset + target.arrayOffset(), numBytes);

            // this must be after the get() call to ensue that the byte buffer is not
            // modified in case the call fails
            target.position(targetOffset + numBytes);
        } else {
            // other types of byte buffers
            throw new IllegalArgumentException(
                    "The target buffer is not direct, and has no array.");
        }
    }

    public void put(int offset,ByteBuffer source, int numBytes) {
        // check the byte array offset and length
        if ((offset | numBytes | (offset + numBytes)) < 0) {
            throw new IndexOutOfBoundsException();
        }

        final int sourceOffset = source.position();
        final int remaining = source.remaining();

        if (remaining < numBytes) {
            throw new BufferUnderflowException();
        }

        if (source.isDirect()) {
            // copy to the target memory directly
            final long sourcePointer = getByteBufferAddress(source) + sourceOffset;
            final long targetPointer = address + offset;

            if (targetPointer <= addressLimit - numBytes) {
                UNSAFE.copyMemory(null, sourcePointer, heapMemory, targetPointer, numBytes);
                source.position(sourceOffset + numBytes);
            } else if (address > addressLimit) {
                throw new IllegalStateException("segment has been freed");
            } else {
                throw new IndexOutOfBoundsException();
            }
        } else if (source.hasArray()) {
            // move directly into the byte array
            put(offset, source.array(), sourceOffset + source.arrayOffset(), numBytes);

            // this must be after the get() call to ensue that the byte buffer is not
            // modified in case the call fails
            source.position(sourceOffset + numBytes);
        } else {
            // other types of byte buffers
            for (int i = 0; i < numBytes; i++) {
                put(offset++, source.get());
            }
        }
    }


    /**
     * Bulk copy method. Copies {@code numBytes} bytes from this memory segment, starting at
     * position {@code offset} to the target memory segment. The bytes will be put into the target
     * segment starting at position {@code targetOffset}.
     *
     * @param offset The position where the bytes are started to be read from in this memory
     *     segment.
     * @param target The memory segment to copy the bytes to.
     * @param targetOffset The position in the target memory segment to copy the chunk to.
     * @param numBytes The number of bytes to copy.
     * @throws IndexOutOfBoundsException If either of the offsets is invalid, or the source segment
     *     does not contain the given number of bytes (starting from offset), or the target segment
     *     does not have enough space for the bytes (counting from targetOffset).
     */
    public void copyTo(int offset, MemorySegment target, int targetOffset, int numBytes) {
        final byte[] thisHeapRef = this.heapMemory;
        final byte[] otherHeapRef = target.heapMemory;
        final long thisPointer = this.address + offset;
        final long otherPointer = target.address + targetOffset;

        if ((numBytes | offset | targetOffset) >= 0
                && thisPointer <= this.addressLimit - numBytes
                && otherPointer <= target.addressLimit - numBytes) {
            UNSAFE.copyMemory(thisHeapRef, thisPointer, otherHeapRef, otherPointer, numBytes);
        } else if (this.address > this.addressLimit) {
            throw new IllegalStateException("this memory segment has been freed.");
        } else if (target.address > target.addressLimit) {
            throw new IllegalStateException("target memory segment has been freed.");
        } else {
            throw new IndexOutOfBoundsException(
                    String.format(
                            "offset=%d, targetOffset=%d, numBytes=%d, address=%d, targetAddress=%d",
                            offset, targetOffset, numBytes, this.address, target.address));
        }
    }

    /**
     * Bulk copy method. Copies {@code numBytes} bytes to target unsafe object and pointer. NOTE:
     * This is an unsafe method, no check here, please be careful.
     *
     * @param offset The position where the bytes are started to be read from in this memory
     *     segment.
     * @param target The unsafe memory to copy the bytes to.
     * @param targetPointer The position in the target unsafe memory to copy the chunk to.
     * @param numBytes The number of bytes to copy.
     * @throws IndexOutOfBoundsException If the source segment does not contain the given number of
     *     bytes (starting from offset).
     */
    public void copyToUnsafe(int offset, Object target, int targetPointer, int numBytes) {
        final long thisPointer = this.address + offset;
        if (thisPointer + numBytes > addressLimit) {
            throw new IndexOutOfBoundsException(
                    String.format(
                            "offset=%d, numBytes=%d, address=%d", offset, numBytes, this.address));
        }
        UNSAFE.copyMemory(this.heapMemory, thisPointer, target, targetPointer, numBytes);
    }

    /**
     * Bulk copy method. Copies {@code numBytes} bytes from source unsafe object and pointer. NOTE:
     * This is an unsafe method, no check here, please be careful.
     *
     * @param offset The position where the bytes are started to be write in this memory segment.
     * @param source The unsafe memory to copy the bytes from.
     * @param sourcePointer The position in the source unsafe memory to copy the chunk from.
     * @param numBytes The number of bytes to copy.
     * @throws IndexOutOfBoundsException If this segment can not contain the given number of bytes
     *     (starting from offset).
     */
    public void copyFromUnsafe(int offset, Object source, int sourcePointer, int numBytes) {
        final long thisPointer = this.address + offset;
        if (thisPointer + numBytes > addressLimit) {
            throw new IndexOutOfBoundsException(
                    String.format(
                            "offset=%d, numBytes=%d, address=%d", offset, numBytes, this.address));
        }
        UNSAFE.copyMemory(source, sourcePointer, this.heapMemory, thisPointer, numBytes);
    }

    // -------------------------------------------------------------------------
    //                      Comparisons & Swapping
    // -------------------------------------------------------------------------

    /**
     * Compares two memory segment regions.
     *
     * @param seg2 Segment to compare this segment with
     * @param offset1 Offset of this segment to start comparing
     * @param offset2 Offset of seg2 to start comparing
     * @param len Length of the compared memory region
     * @return 0 if equal, -1 if seg1 &lt; seg2, 1 otherwise
     */
    public int compare(MemorySegment seg2, int offset1, int offset2, int len) {
        while (len >= 8) {
            long l1 = this.getLongBigEndian(offset1);
            long l2 = seg2.getLongBigEndian(offset2);

            if (l1 != l2) {
                return (l1 < l2) ^ (l1 < 0) ^ (l2 < 0) ? -1 : 1;
            }

            offset1 += 8;
            offset2 += 8;
            len -= 8;
        }
        while (len > 0) {
            int b1 = this.get(offset1) & 0xff;
            int b2 = seg2.get(offset2) & 0xff;
            int cmp = b1 - b2;
            if (cmp != 0) {
                return cmp;
            }
            offset1++;
            offset2++;
            len--;
        }
        return 0;
    }

    /**
     * Compares two memory segment regions with different length.
     *
     * @param seg2 Segment to compare this segment with
     * @param offset1 Offset of this segment to start comparing
     * @param offset2 Offset of seg2 to start comparing
     * @param len1 Length of this memory region to compare
     * @param len2 Length of seg2 to compare
     * @return 0 if equal, -1 if seg1 &lt; seg2, 1 otherwise
     */
    public int compare(MemorySegment seg2, int offset1, int offset2, int len1, int len2) {
        final int minLength = Math.min(len1, len2);
        int c = compare(seg2, offset1, offset2, minLength);
        return c == 0 ? (len1 - len2) : c;
    }

    /**
     * Swaps bytes between two memory segments, using the given auxiliary buffer.
     *
     * @param tempBuffer The auxiliary buffer in which to put data during triangle swap.
     * @param seg2 Segment to swap bytes with
     * @param offset1 Offset of this segment to start swapping
     * @param offset2 Offset of seg2 to start swapping
     * @param len Length of the swapped memory region
     */
    public void swapBytes(
            byte[] tempBuffer, MemorySegment seg2, int offset1, int offset2, int len) {
        if ((offset1 | offset2 | len | (tempBuffer.length - len)) >= 0) {
            final long thisPos = this.address + offset1;
            final long otherPos = seg2.address + offset2;

            if (thisPos <= this.addressLimit - len && otherPos <= seg2.addressLimit - len) {
                // this -> temp buffer
                UNSAFE.copyMemory(
                        this.heapMemory, thisPos, tempBuffer, BYTE_ARRAY_BASE_OFFSET, len);

                // other -> this
                UNSAFE.copyMemory(seg2.heapMemory, otherPos, this.heapMemory, thisPos, len);

                // temp buffer -> other
                UNSAFE.copyMemory(
                        tempBuffer, BYTE_ARRAY_BASE_OFFSET, seg2.heapMemory, otherPos, len);
                return;
            } else if (this.address > this.addressLimit) {
                throw new IllegalStateException("this memory segment has been freed.");
            } else if (seg2.address > seg2.addressLimit) {
                throw new IllegalStateException("other memory segment has been freed.");
            }
        }

        // index is in fact invalid
        throw new IndexOutOfBoundsException(
                String.format(
                        "offset1=%d, offset2=%d, len=%d, bufferSize=%d, address1=%d, address2=%d",
                        offset1, offset2, len, tempBuffer.length, this.address, seg2.address));
    }

    /**
     * Equals two memory segment regions.
     *
     * @param seg2 Segment to equal this segment with
     * @param offset1 Offset of this segment to start equaling
     * @param offset2 Offset of seg2 to start equaling
     * @param length Length of the equaled memory region
     * @return true if equal, false otherwise
     */
    public boolean equalTo(MemorySegment seg2, int offset1, int offset2, int length) {
        int i = 0;

        // we assume unaligned accesses are supported.
        // Compare 8 bytes at a time.
        while (i <= length - 8) {
            if (getLong(offset1 + i) != seg2.getLong(offset2 + i)) {
                return false;
            }
            i += 8;
        }

        // cover the last (length % 8) elements.
        while (i < length) {
            if (get(offset1 + i) != seg2.get(offset2 + i)) {
                return false;
            }
            i += 1;
        }

        return true;
    }

    /**
     * Get the heap byte array object.
     *
     * @return Return non-null if the memory is on the heap, and return null if the memory if off
     *     the heap.
     */
    public byte[] getHeapMemory() {
        return heapMemory;
    }

    /**
     * Applies the given process function on a {@link ByteBuffer} that represents this entire
     * segment.
     *
     * <p>Note: The {@link ByteBuffer} passed into the process function is temporary and could
     * become invalid after the processing. Thus, the process function should not try to keep any
     * reference of the {@link ByteBuffer}.
     *
     * @param processFunction to be applied to the segment as {@link ByteBuffer}.
     * @return the value that the process function returns.
     */
    public <T> T processAsByteBuffer(Function<ByteBuffer, T> processFunction) {
        return Preconditions.checkNotNull(processFunction).apply(wrapInternal(0, size));
    }

    /**
     * Supplies a {@link ByteBuffer} that represents this entire segment to the given process
     * consumer.
     *
     * <p>Note: The {@link ByteBuffer} passed into the process consumer is temporary and could
     * become invalid after the processing. Thus, the process consumer should not try to keep any
     * reference of the {@link ByteBuffer}.
     *
     * @param processConsumer to accept the segment as {@link ByteBuffer}.
     */
    public void processAsByteBuffer(Consumer<ByteBuffer> processConsumer) {
        Preconditions.checkNotNull(processConsumer).accept(wrapInternal(0, size));
    }

}
