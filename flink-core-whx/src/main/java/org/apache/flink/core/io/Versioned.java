package org.apache.flink.core.io;

import org.apache.flink.annotation.PublicEvolving;

/**
 * @author wanghx
 * @describe 该接口由提供版本号的类实现。版本号可用于区分不断发展的类。
 * @since 2021/9/14 17:39
 */
@PublicEvolving
public interface Versioned {

    /**
     * Returns the version number of the object. Versions numbers can be used to differentiate
     * evolving classes.
     */
    int getVersion();
}
