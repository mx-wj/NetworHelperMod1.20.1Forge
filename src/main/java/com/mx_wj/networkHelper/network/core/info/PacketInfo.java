package com.mx_wj.networkHelper.network.core.info;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 网络包信息注解。
 * <p>
 * 用于标记一个 Packet 类的元数据，例如它的传输方向。
 */
@Retention(RetentionPolicy.RUNTIME) // 确保注解在运行时可见，以便反射可以读取
@Target(ElementType.TYPE)           // 只能用于类
public @interface PacketInfo {

    /**
     * 定义该网络包的传输方向。
     */
    PacketDirection direction();

    /**
     * 定义网络包的传输方向。
     * <p>
     * 用于在注册时限制数据包的流向，增强稳定性和安全性。
     */

}

