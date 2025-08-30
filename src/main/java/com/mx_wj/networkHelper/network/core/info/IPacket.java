package com.mx_wj.networkHelper.network.core.info;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

/**
 * 网络包接口 (Forge 1.20.1 简化版)
 * <p>
 * 所有网络包都必须实现此接口。
 */
public interface IPacket {

    /**
     * 将包的数据写入字节缓冲。
     *
     * @param buf 要写入的缓冲区
     */
    void encode(FriendlyByteBuf buf);

    /**
     * 在主线程上执行包的逻辑。
     *
     * @param context 网络事件的上下文
     */
    void execute(NetworkEvent.Context context);
}

