package com.mx_wj.networkHelper.network.core.info;

public enum PacketDirection {
    /**
     * 从客户端发送到服务端 (C -> S)
     */
    CLIENT_TO_SERVER,

    /**
     * 从服务端发送到客户端 (S -> C)
     */
    SERVER_TO_CLIENT,

    /**
     * 双向均可发送 (C <-> S)
     * <p>
     * <b>警告:</b> 不推荐使用。请尽可能明确指定数据包的流向。
     * 仅在确实需要双向通信且逻辑清晰时使用。
     */
    BIDIRECTIONAL
}
