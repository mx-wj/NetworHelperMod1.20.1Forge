package com.mx_wj.networkHelper.network.core;

import com.mx_wj.networkHelper.NetworkHelperMod;
import com.mx_wj.networkHelper.network.core.info.IPacket;
import com.mx_wj.networkHelper.network.core.info.PacketDirection;
import com.mx_wj.networkHelper.network.core.info.PacketInfo;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

/**
 * 傻瓜式网络包管理器 (Forge 1.20.1 版本)
 */
public class PacketManager {
    private static final List<Class<? extends IPacket>> toRegisterPackets = new ArrayList<>();

    private static final String PROTOCOL_VERSION = "1";
    private static int packetId = 0;

    public static final SimpleChannel INSTANCE = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(NetworkHelperMod.MODID, "main"),
            () -> PROTOCOL_VERSION,
            PROTOCOL_VERSION::equals,
            PROTOCOL_VERSION::equals
    );

    public static void registerPackets() {
        for (Class<? extends IPacket> packetClass : toRegisterPackets) {
            registerPacket(packetClass);
            NetworkHelperMod.LOGGER.info("Registered packet:{}", packetClass.getSimpleName());
        }
    }

    @SuppressWarnings("all")
    public static void addToRegister(Class<?> packetClass) {
        toRegisterPackets.add((Class<? extends IPacket>) packetClass);
    }

    private static <T extends IPacket> void registerPacket(Class<T> packetClass) {
        PacketInfo info = packetClass.getAnnotation(PacketInfo.class);
        if (info == null) {
            throw new IllegalArgumentException("Packet class " + packetClass.getSimpleName() + " is missing the @PacketInfo annotation!");
        }
        PacketDirection direction = info.direction();
        Function<FriendlyByteBuf, T> decoder = ASMUtil.createDecoder(packetClass);

        switch (direction) {
            case CLIENT_TO_SERVER ->
                    registerInternal(packetClass, decoder, Optional.of(NetworkDirection.PLAY_TO_SERVER));

            case SERVER_TO_CLIENT ->
                    registerInternal(packetClass, decoder, Optional.of(NetworkDirection.PLAY_TO_CLIENT));

            case BIDIRECTIONAL -> {
                // 双向包分别注册两个方向
                registerInternal(packetClass, decoder, Optional.of(NetworkDirection.PLAY_TO_SERVER));
                registerInternal(packetClass, decoder, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
            }
        }
    }

    /**
     * Forge 1.20.1 的内部注册方法
     */
    private static <T extends IPacket> void registerInternal(Class<T> packetClass, Function<FriendlyByteBuf, T> decoder, Optional<NetworkDirection> direction) {
        INSTANCE.registerMessage(packetId++,
                packetClass,
                IPacket::encode,
                decoder,
                (packet, context) -> {
                    // 将上下文(context)的处理逻辑封装在此处，让 IPacket 的实现更干净
                    context.get().enqueueWork(() -> packet.execute(context.get()));
                    context.get().setPacketHandled(true);
                },
                direction
        );
    }


    // --- 发送方法保持不变 ---
    public static void sendToServer(IPacket packet) { INSTANCE.sendToServer(packet); }
    public static void sendToPlayer(ServerPlayer player, IPacket packet) { INSTANCE.send(PacketDistributor.PLAYER.with(() -> player), packet); }
    public static void sendToAllPlayers(IPacket packet) { INSTANCE.send(PacketDistributor.ALL.noArg(), packet); }
}
