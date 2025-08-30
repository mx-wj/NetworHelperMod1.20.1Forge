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

import java.util.*;
import java.util.function.Function;

/**
 * 傻瓜式网络包管理器 (Forge 1.20.1 版本)
 */
public class PacketManager {
    private static final Map<String, List<Class<? extends IPacket>>> toRegisterPackets = new HashMap<>();
    private static final Map<String, SimpleChannel> channels = new HashMap<>();

    private static final String PROTOCOL_VERSION = "1";

    @SuppressWarnings("all")
    public static void addToRegister(String modID, Class<?> packetClass) {
        toRegisterPackets.computeIfAbsent(modID, k -> new ArrayList<>()).add((Class<? extends IPacket>) packetClass);
    }

    public static void registerPackets() {
        for (Map.Entry<String, List<Class<? extends IPacket>>> entry : toRegisterPackets.entrySet()) {
            String modID = entry.getKey();
            List<Class<? extends IPacket>> packetClasses = entry.getValue();
            SimpleChannel simpleChannel = NetworkRegistry.newSimpleChannel(
                    new ResourceLocation(modID, "main"),
                    () -> PROTOCOL_VERSION,
                    PROTOCOL_VERSION::equals,
                    PROTOCOL_VERSION::equals
            );
            channels.put(modID, simpleChannel);
            NetworkHelperMod.LOGGER.info("Registering packets for mod '{}'...", modID);
            int packetId = 0;
            for(Class<? extends IPacket> packetClass : packetClasses){
                registerPacket(simpleChannel, packetClass, packetId);
                packetId++;
                NetworkHelperMod.LOGGER.info(" -> Registered packet: {}", packetClass.getSimpleName());
            }
            NetworkHelperMod.LOGGER.info("Finished registration for mod '{}'. Total packets: {}", modID, packetId);
        }
    }

    private static <T extends IPacket> void registerPacket(SimpleChannel simpleChannel, Class<T> packetClass, int packetId) {
        PacketInfo info = packetClass.getAnnotation(PacketInfo.class);
        if (info == null) {
            throw new IllegalArgumentException("Packet class " + packetClass.getSimpleName() + " is missing the @PacketInfo annotation!");
        }
        PacketDirection direction = info.direction();
        Function<FriendlyByteBuf, T> decoder = ASMUtil.createDecoder(packetClass);

        switch (direction) {
            case CLIENT_TO_SERVER ->
                    registerInternal(simpleChannel, packetClass, packetId, decoder, Optional.of(NetworkDirection.PLAY_TO_SERVER));

            case SERVER_TO_CLIENT ->
                    registerInternal(simpleChannel, packetClass, packetId, decoder, Optional.of(NetworkDirection.PLAY_TO_CLIENT));

            case BIDIRECTIONAL -> {
                // 双向包分别注册两个方向
                registerInternal(simpleChannel, packetClass, packetId, decoder, Optional.of(NetworkDirection.PLAY_TO_SERVER));
                registerInternal(simpleChannel, packetClass, packetId, decoder, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
            }
        }
    }

    /**
     * Forge 1.20.1 的内部注册方法
     */
    private static <T extends IPacket> void registerInternal(SimpleChannel simpleChannel, Class<T> packetClass, int packetId, Function<FriendlyByteBuf, T> decoder, Optional<NetworkDirection> direction) {
        simpleChannel.registerMessage(packetId,
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

    public static void sendToServer(String modID, IPacket packet) {
        SimpleChannel channel = channels.get(modID);
        if (channel == null) {
            throw new IllegalArgumentException("No channel found for modID: " + modID);
        }
        channel.sendToServer(packet);
    }
    public static void sendToPlayer(String modID, ServerPlayer player, IPacket packet) {
        SimpleChannel channel = channels.get(modID);
        if (channel == null) {
            throw new IllegalArgumentException("No channel found for modID: " + modID);
        }
        channel.send(PacketDistributor.PLAYER.with(() -> player), packet);
    }
    public static void sendToAllPlayers(String modID, IPacket packet) {
        SimpleChannel channel = channels.get(modID);
        if (channel == null) {
            throw new IllegalArgumentException("No channel found for modID: " + modID);
        }
        channel.send(PacketDistributor.ALL.noArg(), packet);
    }

    public static SimpleChannel getChannel(String modID) {
        return channels.get(modID);
    }
}
