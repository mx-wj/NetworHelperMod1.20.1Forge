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
 * 一个为 Forge 1.20.1 设计的、简化的、集中式的网络数据包管理器。
 * <p>
 * 这个管理器的设计初衷是能被多个模组同时使用。它封装了创建 {@link SimpleChannel} 和
 * 注册单个数据包的繁琐样板代码。模组可以使用 {@link #addToRegister(String, Class)}
 * 方法将其数据包加入队列，然后只需调用一次 {@link #registerPackets()} 即可完成所有注册工作。
 */
public class PacketManager {

    /**
     * 一个 Map，用于存储等待注册的数据包类，键为它们对应的模组ID (modID)。
     * 这允许多个模组在最终注册流程开始前，将它们各自的数据包添加进来。
     */
    private static final Map<String, List<Class<? extends IPacket>>> toRegisterPackets = new HashMap<>();

    /**
     * 注册后，为每个模组ID缓存创建的 {@link SimpleChannel}。
     */
    private static final Map<String, SimpleChannel> channels = new HashMap<>();

    /**
     * 网络通道的协议版本号。Forge 使用它来确保客户端和服务端具有兼容的网络设置。
     */
    private static final String PROTOCOL_VERSION = "1";

    /**
     * 为特定的模组将一个数据包类添加到注册队列中。
     * 在你的模组初始化阶段，应该为每一个需要注册的数据包调用此方法。
     *
     * @param modID       注册该数据包的模组ID。
     * @param packetClass 数据包的类，该类必须实现 {@link IPacket} 接口。
     */
    @SuppressWarnings("all")
    public static void addToRegister(String modID, Class<?> packetClass) {
        toRegisterPackets.computeIfAbsent(modID, k -> new ArrayList<>()).add((Class<? extends IPacket>) packetClass);
    }

    /**
     * 注册所有通过 {@link #addToRegister} 添加到队列中的数据包。
     * 此方法会遍历每个模组，为其创建一个专用的 {@link SimpleChannel}，
     * 然后注册其队列中的所有数据包。
     * <p>
     * 这个方法通常应该在模组加载过程中的某个时刻被调用一次，最好是在所有数据包都已入队之后。
     */
    public static void registerPackets() {
        for (Map.Entry<String, List<Class<? extends IPacket>>> entry : toRegisterPackets.entrySet()) {
            String modID = entry.getKey();
            List<Class<? extends IPacket>> packetClasses = entry.getValue();

            // 为该模组创建一个新的通信通道。
            SimpleChannel simpleChannel = NetworkRegistry.newSimpleChannel(
                    new ResourceLocation(modID, "main"),
                    () -> PROTOCOL_VERSION,
                    PROTOCOL_VERSION::equals, // 服务端接受连接时的版本检查
                    PROTOCOL_VERSION::equals  // 客户端接受连接时的版本检查
            );

            channels.put(modID, simpleChannel);
            NetworkHelperMod.LOGGER.info("Registering packets for mod '{}'...", modID);

            int packetId = 0;
            for (Class<? extends IPacket> packetClass : packetClasses) {
                // 这里的泛型 <T> 会被编译器自动推断。
                registerPacket(simpleChannel, packetClass, packetId);
                packetId++;
                NetworkHelperMod.LOGGER.info(" -> Registered packet: {}", packetClass.getSimpleName());
            }
            NetworkHelperMod.LOGGER.info("Finished registration for mod '{}'. Total packets: {}", modID, packetId);
        }
    }

    /**
     * 处理单个数据包类的注册逻辑。
     * 它会读取 {@link PacketInfo} 注解来确定网络方向，并据此注册数据包。
     *
     * @param simpleChannel 要在其上注册数据包的通道。
     * @param packetClass   要注册的数据包的类。
     * @param packetId      此数据包在该通道上的唯一区分ID。
     * @param <T>           数据包的类型，实现了 {@link IPacket} 接口。
     */
    private static <T extends IPacket> void registerPacket(SimpleChannel simpleChannel, Class<T> packetClass, int packetId) {
        PacketInfo info = packetClass.getAnnotation(PacketInfo.class);
        if (info == null) {
            throw new IllegalArgumentException("Packet class " + packetClass.getSimpleName() + " is missing the @PacketInfo annotation!");
        }

        PacketDirection direction = info.direction();
        // 为了性能，解码器是使用 ASM 动态生成的。
        Function<FriendlyByteBuf, T> decoder = ASMUtil.createDecoder(packetClass);

        switch (direction) {
            case CLIENT_TO_SERVER ->
                    registerInternal(simpleChannel, packetClass, packetId, decoder, Optional.of(NetworkDirection.PLAY_TO_SERVER));

            case SERVER_TO_CLIENT ->
                    registerInternal(simpleChannel, packetClass, packetId, decoder, Optional.of(NetworkDirection.PLAY_TO_CLIENT));

            case BIDIRECTIONAL -> {
                // 对于双向数据包，我们为两个方向都进行注册，使用相同的ID。
                // Forge 的 SimpleChannel 支持这种做法，它会根据数据包的发送源来决定路由。
                registerInternal(simpleChannel, packetClass, packetId, decoder, Optional.of(NetworkDirection.PLAY_TO_SERVER));
                registerInternal(simpleChannel, packetClass, packetId, decoder, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
            }
        }
    }

    /**
     * 真正调用 Forge 的 {@link SimpleChannel#registerMessage} 的核心内部方法。
     * 它封装了消息处理逻辑，以确保数据包在主游戏线程上被处理。
     *
     * @param simpleChannel 要注册的通道。
     * @param packetClass   数据包的类。
     * @param packetId      此数据包的唯一ID。
     * @param decoder       用于从缓冲区解码数据包的函数。
     * @param direction     本次注册对应的网络方向。
     * @param <T>           数据包的类型。
     */
    private static <T extends IPacket> void registerInternal(SimpleChannel simpleChannel, Class<T> packetClass, int packetId, Function<FriendlyByteBuf, T> decoder, Optional<NetworkDirection> direction) {
        simpleChannel.registerMessage(packetId,
                packetClass,
                IPacket::encode, // 编码方法引用
                decoder,         // 解码方法引用
                (packet, context) -> {
                    // 这个 lambda 表达式（消费者）负责处理接收到的数据包。
                    // 我们将数据包的执行逻辑放入任务队列，以确保它在主游戏线程上运行，
                    // 这对于与游戏世界的大多数交互来说是至关重要的（线程安全）。
                    context.get().enqueueWork(() -> packet.execute(context.get()));
                    context.get().setPacketHandled(true);
                },
                direction
        );
    }

    /**
     * 从客户端向服务端发送一个数据包。
     *
     * @param modID  应使用哪个模组的通道。
     * @param packet 要发送的数据包实例。
     * @throws IllegalArgumentException 如果没有为给定的 modID 找到注册的通道。
     */
    public static void sendToServer(String modID, IPacket packet) {
        SimpleChannel channel = getChannel(modID);
        if (channel == null) {
            throw new IllegalArgumentException("No channel found for modID: " + modID);
        }
        channel.sendToServer(packet);
    }

    /**
     * 从服务端向一个特定的玩家发送数据包。
     *
     * @param modID  应使用哪个模组的通道。
     * @param player 接收数据包的玩家。
     * @param packet 要发送的数据包实例。
     * @throws IllegalArgumentException 如果没有为给定的 modID 找到注册的通道。
     */
    public static void sendToPlayer(String modID, ServerPlayer player, IPacket packet) {
        SimpleChannel channel = getChannel(modID);
        if (channel == null) {
            throw new IllegalArgumentException("No channel found for modID: " + modID);
        }
        channel.send(PacketDistributor.PLAYER.with(() -> player), packet);
    }

    /**
     * 从服务端向所有当前连接的玩家广播一个数据包。
     *
     * @param modID  应使用哪个模组的通道。
     * @param packet 要发送的数据包实例。
     * @throws IllegalArgumentException 如果没有为给定的 modID 找到注册的通道。
     */
    public static void sendToAllPlayers(String modID, IPacket packet) {
        SimpleChannel channel = getChannel(modID);
        if (channel == null) {
            throw new IllegalArgumentException("No channel found for modID: " + modID);
        }
        channel.send(PacketDistributor.ALL.noArg(), packet);
    }

    /**
     * 获取与给定模组ID关联的 {@link SimpleChannel}。
     * 这在处理一些此帮助类未覆盖的高级网络场景时可能会很有用。
     *
     * @param modID 模组的ID。
     * @return 对应的 {@link SimpleChannel}，如果未找到则返回 {@code null}。
     */
    public static SimpleChannel getChannel(String modID) {
        return channels.get(modID);
    }
}

