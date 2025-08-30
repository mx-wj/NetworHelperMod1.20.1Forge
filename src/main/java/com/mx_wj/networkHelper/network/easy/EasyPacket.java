package com.mx_wj.networkHelper.network.easy;

import com.mx_wj.networkHelper.network.core.info.IPacket;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
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
 * 一个“懒人包”基类，通过反射自动处理字段的序列化和反序列化。
 * 继承此类后，您只需要在子类中定义要同步的字段，并实现 {@link #process(NetworkEvent.Context)} 方法来处理数据包逻辑即可。
 * <p>
 * <b>支持的字段类型:</b>
 * <ul>
 * <li>int, String, boolean, long, float, double, byte[]</li>
 * <li>{@link CompoundTag}, {@link ItemStack}, {@link BlockPos}, {@link ResourceLocation}, {@link Component}, {@link UUID}</li>
 * </ul>
 * <b>注意:</b>
 * <ul>
 * <li>字段<b>不能</b>是 {@code final} 的。</li>
 * <li>{@code static} 和 {@code transient} 修饰的字段会被自动忽略。</li>
 * </ul>
 */
public abstract class EasyPacket implements IPacket {
    private static final Logger LOGGER = LogManager.getLogger();
    /**
     * 缓存每个数据包类的 VarHandle 列表，避免每次都通过反射获取，提高性能。
     */
    private static final Map<Class<?>, List<VarHandle>> varHandleCache = new ConcurrentHashMap<>();

    /**
     * 一个拥有最高权限的 MethodHandles.Lookup 实例，可以绕过 Java 的所有可见性和安全检查。
     * 这使得我们可以直接访问私有字段，而无需调用 setAccessible(true)，效率更高。
     */
    private static final MethodHandles.Lookup IMPL_LOOKUP;

    static {
        MethodHandles.Lookup found;
        try {
            // "IMPL_LOOKUP" 是 MethodHandles.Lookup 类中的一个私有静态 final 字段。
            // 通过特殊的 Unsafe/Reflection hack 来获取它。
            Field field = MethodHandles.Lookup.class.getDeclaredField("IMPL_LOOKUP");
            // EasyUnsafeHacks 是一个工具类，用于暴力获取字段值。
            found = EasyUnsafeHacks.getStaticField(field);
        } catch (Exception e) {
            // 这种黑科技在未来的 Java 版本中可能会失效。如果失败，我们记录一条警告，
            // 并回退到标准的、功能较弱的 lookup() 方法。这可能导致无法访问某些私有字段。
            LOGGER.warn("Could not get IMPL_LOOKUP, falling back to standard MethodHandles.lookup(). Some private fields might be inaccessible.", e);
            found = MethodHandles.lookup();
        }
        IMPL_LOOKUP = found;
    }

    /**
     * 默认构造函数。当你在代码中 new 一个数据包以发送它时，会调用这个构造函数。
     */
    public EasyPacket() {
    }

    /**
     * 用于解码的构造函数，由 PacketManager 在接收到数据包时内部调用。
     * 它会自动从字节缓冲区中读取数据，并填充子类中定义的所有非静态、非瞬态字段。
     *
     * @param buf 包含数据包数据的字节缓冲区。
     */
    public EasyPacket(FriendlyByteBuf buf) {
        try {
            List<VarHandle> varHandles = getVarHandles(this.getClass());
            for (VarHandle varHandle : varHandles) {
                // 根据字段类型从缓冲区读取数据
                Object value = readField(buf, varHandle.varType());
                // 将读取到的值设置到当前对象的对应字段上
                varHandle.set(this, value);
            }
        } catch (Throwable e) {
            LOGGER.error("Failed to deserialize EasyPacket " + this.getClass().getSimpleName(), e);
            throw new RuntimeException("Packet Deserialization failed for " + this.getClass().getSimpleName(), e);
        }
    }

    /**
     * 将数据包实例中的所有字段编码到字节缓冲区中。
     * 这是一个 final 方法，子类不应覆盖。
     * @param buf 要写入数据的字节缓冲区。
     */
    @Override
    public final void encode(FriendlyByteBuf buf) {
        try {
            List<VarHandle> varHandles = getVarHandles(this.getClass());
            for (VarHandle varHandle : varHandles) {
                // 从当前对象的字段中获取值，并写入到缓冲区
                writeField(buf, varHandle.get(this));
            }
        } catch (Throwable e) {
            LOGGER.error("Failed to serialize EasyPacket " + this.getClass().getSimpleName(), e);
            throw new RuntimeException("Packet Serialization failed for " + this.getClass().getSimpleName(), e);
        }
    }

    /**
     * 执行数据包的核心入口点。
     * 它会在主游戏线程上调用子类实现的 {@link #process} 方法。
     * 这是一个 final 方法，子类不应覆盖。
     * @param context 网络事件的上下文。
     */
    @Override
    public final void execute(NetworkEvent.Context context) {
        this.process(context);
    }

    /**
     * 数据包的核心处理逻辑，由子类实现。
     * 当这个方法被调用时，数据包的所有字段都已经被自动反序列化并填充完毕。
     * 这个方法保证在主游戏线程上执行，因此可以安全地与游戏世界进行交互。
     *
     * @param context 网络事件的上下文，可以用来获取玩家实体等信息。
     */
    public abstract void process(NetworkEvent.Context context);

    /**
     * 获取并缓存一个类的所有可序列化字段的 VarHandle。
     * VarHandle 是对字段的强类型引用，比传统的反射性能更好。
     *
     * @param clazz 要分析的数据包类。
     * @return 该类的 VarHandle 列表。
     */
    private static List<VarHandle> getVarHandles(Class<?> clazz) {
        return varHandleCache.computeIfAbsent(clazz, key -> {
            List<VarHandle> handles = new ArrayList<>();
            for (Field field : key.getDeclaredFields()) {
                // 忽略 transient (瞬态) 和 static (静态) 字段
                if (Modifier.isTransient(field.getModifiers()) || Modifier.isStatic(field.getModifiers())) {
                    continue;
                }
                try {
                    // 使用万能的 IMPL_LOOKUP 来创建 VarHandle，它可以无视 private 等访问修饰符。
                    // 这比在每个字段上调用 field.setAccessible(true) 更高效、更直接。
                    handles.add(IMPL_LOOKUP.unreflectVarHandle(field));
                } catch (IllegalAccessException e) {
                    // 理论上，使用 IMPL_LOOKUP 时这几乎不可能发生，但为了代码健壮性我们还是处理它。
                    LOGGER.error("Failed to create VarHandle for field {} using IMPL_LOOKUP", field.getName(), e);
                    throw new RuntimeException("Failed to create VarHandle for field: " + field.getName(), e);
                }
            }
            return handles;
        });
    }

    /**
     * 根据值的类型，将其写入到字节缓冲区。
     * @param buf   字节缓冲区。
     * @param value 要写入的对象值。
     */
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

    /**
     * 根据字段类型，从字节缓冲区中读取相应的数据。
     * @param buf  字节缓冲区。
     * @param type 目标字段的 Class 类型。
     * @return 从缓冲区读取并转换后的对象。
     */
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

