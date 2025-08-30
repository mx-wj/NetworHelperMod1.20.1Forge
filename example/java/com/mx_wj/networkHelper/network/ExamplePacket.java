package com.mx_wj.networkHelper.network;

import com.mx_wj.networkHelper.network.core.info.IPacket;
import com.mx_wj.networkHelper.network.core.info.PacketDirection;
import com.mx_wj.networkHelper.network.core.info.PacketInfo;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

@PacketInfo(direction = PacketDirection.CLIENT_TO_SERVER)
public class ExamplePacket implements IPacket {

    private final int number;
    private final String message;

    // 发送时使用的构造函数
    public ExamplePacket(int number, String message) {
        this.number = number;
        this.message = message;
    }

    // 解码时使用的构造函数 (必须有)
    public ExamplePacket(FriendlyByteBuf buf) {
        this.number = buf.readInt();
        this.message = buf.readUtf();
    }

    @Override
    public void encode(FriendlyByteBuf buf) {
        buf.writeInt(number);
        buf.writeUtf(message);
    }

    @Override
    public void execute(NetworkEvent.Context context) {
        ServerPlayer player = context.getSender();
        if (player != null) {
            player.sendSystemMessage(Component.literal("Received from client: " + number + " and '" + message + "'"));
        }
    }
}

