package com.mx_wj.networkHelper.network.easy;

import com.mx_wj.networkHelper.network.core.info.IPacket;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fml.unsafe.UnsafeHacks;
import net.minecraftforge.network.NetworkEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 一个“懒人包”基类，它使用 VarHandle 来高性能地自动处理字段的序列化和反序列化。
 * 继承此类后，您只需要定义字段并实现 process() 方法即可。
 * 支持的字段类型: int, String, boolean, long, float, double, byte[],
 * CompoundTag, ItemStack, BlockPos, ResourceLocation, Component, UUID
 * 注意: 字段不能是 final 的。
 */
public abstract class EasyPacket implements IPacket {
    private static final Logger LOGGER = LogManager.getLogger();
    private static final Map<Class<?>, List<VarHandle>> varHandleCache = new ConcurrentHashMap<>();
    private static final MethodHandles.Lookup IMPL_LOOKUP;

    static {
        MethodHandles.Lookup found;
        try {
            // 这是您建议的“不安全技巧”。我们通过反射访问 MethodHandles.Lookup 中私有的 IMPL_LOOKUP 字段，
            // 该字段拥有最高权限，可以绕过所有可见性和安全检查。

            Field field = MethodHandles.Lookup.class.getDeclaredField("IMPL_LOOKUP");
            found = EasyUnsafeHacks.getStaticField(field);
        } catch (Exception e) {
            // 这在未来的 Java 版本中可能会失败，所以我们记录日志并回退到功能较弱的 lookup。
            LOGGER.warn("Could not get IMPL_LOOKUP, falling back to standard MethodHandles.lookup(). Some private fields might be inaccessible.", e);
            found = MethodHandles.lookup();
        }
        IMPL_LOOKUP = found;
    }

    /**
     * 默认构造函数，用于发送数据包。
     */
    public EasyPacket() {
    }

    /**
     * 用于解码的构造函数（由 PacketManager 内部调用）。
     * 它会自动从字节缓冲区中读取数据并填充子类的字段。
     * @param buf 字节缓冲区
     */
    public EasyPacket(FriendlyByteBuf buf) {
        try {
            List<VarHandle> varHandles = getVarHandles(this.getClass());
            for (VarHandle varHandle : varHandles) {
                Object value = readField(buf, varHandle.varType());
                varHandle.set(this, value);
            }
        } catch (Throwable e) {
            LOGGER.error("Failed to deserialize EasyPacket " + this.getClass().getSimpleName(), e);
            throw new RuntimeException("Packet Deserialization failed for " + this.getClass().getSimpleName(), e);
        }
    }

    @Override
    public final void encode(FriendlyByteBuf buf) {
        try {
            List<VarHandle> varHandles = getVarHandles(this.getClass());
            for (VarHandle varHandle : varHandles) {
                writeField(buf, varHandle.get(this));
            }
        } catch (Throwable e) {
            LOGGER.error("Failed to serialize EasyPacket " + this.getClass().getSimpleName(), e);
            throw new RuntimeException("Packet Serialization failed for " + this.getClass().getSimpleName(), e);
        }
    }

    @Override
    public final void execute(NetworkEvent.Context context) {
        this.process(context);
    }

    /**
     * 数据包的核心逻辑，由子类实现。
     * 此方法在主线程上调用，此时所有字段都已被自动反序列化。
     * @param context 网络事件上下文
     */
    public abstract void process(NetworkEvent.Context context);

    private static List<VarHandle> getVarHandles(Class<?> clazz) {
        return varHandleCache.computeIfAbsent(clazz, key -> {
            List<VarHandle> handles = new ArrayList<>();
            for (Field field : key.getDeclaredFields()) {
                if (Modifier.isTransient(field.getModifiers()) || Modifier.isStatic(field.getModifiers())) {
                    continue;
                }
                try {
                    // 使用万能的 IMPL_LOOKUP 绕过所有访问检查。
                    // 这比在每个字段上调用 setAccessible(true) 更简洁、更直接。
                    handles.add(IMPL_LOOKUP.unreflectVarHandle(field));
                } catch (IllegalAccessException e) {
                    // 使用 IMPL_LOOKUP 时，这几乎不可能发生，但以防万一我们还是处理它。
                    LOGGER.error("Failed to create VarHandle for field {} using IMPL_LOOKUP", field.getName(), e);
                    throw new RuntimeException("Failed to create VarHandle for field: " + field.getName(), e);
                }
            }
            return handles;
        });
    }


    private static void writeField(FriendlyByteBuf buf, Object value) {
        if (value instanceof Integer) buf.writeInt((Integer) value);
        else if (value instanceof String) buf.writeUtf((String) value);
        else if (value instanceof Boolean) buf.writeBoolean((Boolean) value);
        else if (value instanceof Long) buf.writeLong((Long) value);
        else if (value instanceof Float) buf.writeFloat((Float) value);
        else if (value instanceof Double) buf.writeDouble((Double) value);
        else if (value instanceof byte[]) buf.writeByteArray((byte[]) value);
        else if (value instanceof CompoundTag) buf.writeNbt((CompoundTag) value);
        else if (value instanceof ItemStack) buf.writeItem((ItemStack) value);
        else if (value instanceof BlockPos) buf.writeBlockPos((BlockPos) value);
        else if (value instanceof ResourceLocation) buf.writeResourceLocation((ResourceLocation) value);
        else if (value instanceof Component) buf.writeComponent((Component) value);
        else if (value instanceof UUID) buf.writeUUID((UUID) value);
        else {
            throw new IllegalArgumentException("EasyPacket does not support automatic serialization for this field type: " + (value != null ? value.getClass().getName() : "null"));
        }
    }

    private static Object readField(FriendlyByteBuf buf, Class<?> type) {
        if (type == int.class || type == Integer.class) return buf.readInt();
        if (type == String.class) return buf.readUtf();
        if (type == boolean.class || type == Boolean.class) return buf.readBoolean();
        if (type == long.class || type == Long.class) return buf.readLong();
        if (type == float.class || type == Float.class) return buf.readFloat();
        if (type == double.class || type == Double.class) return buf.readDouble();
        if (type == byte[].class) return buf.readByteArray();
        if (type == CompoundTag.class) return buf.readNbt();
        if (type == ItemStack.class) return buf.readItem();
        if (type == BlockPos.class) return buf.readBlockPos();
        if (type == ResourceLocation.class) return buf.readResourceLocation();
        if (type == Component.class) return buf.readComponent();
        if (type == UUID.class) return buf.readUUID();
        else {
            throw new IllegalArgumentException("EasyPacket does not support automatic deserialization for this field type: " + type.getName());
        }
    }
}

