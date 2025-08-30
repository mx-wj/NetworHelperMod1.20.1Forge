package com.mx_wj.networkHelper.network;

import com.mx_wj.networkHelper.network.core.info.PacketDirection;
import com.mx_wj.networkHelper.network.core.info.PacketInfo;
import com.mx_wj.networkHelper.network.easy.EasyPacket;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

@PacketInfo(direction = PacketDirection.CLIENT_TO_SERVER)
public class ExamplePacket2 extends EasyPacket {
    private int number;
    private String message;

    // 发送时使用的构造函数
    public ExamplePacket2(int number, String message) {
        this.number = number;
        this.message = message;
    }

    // 解码时使用的构造函数 (必须有)
    public ExamplePacket2(FriendlyByteBuf buf) {
        super(buf);
    }

    @Override
    public void process(NetworkEvent.Context context) {
        ServerPlayer player = context.getSender();
        if (player != null) {
            player.sendSystemMessage(Component.literal("Received from client: " + number + " and '" + message + "'"));
        }
    }
}

