package com.mx_wj.networkHelper.network.core;

import com.mx_wj.networkHelper.network.core.info.IPacket;
import net.minecraft.network.FriendlyByteBuf;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

import java.lang.reflect.Constructor;
import java.util.function.Function;

/**
 * ASM 工具类 (V8 - 终极优化版)
 * <p>
 * 封装了所有与动态字节码生成相关的底层操作。
 * 采用了最直接的方式实现 Function 接口，不再生成多余的桥接方法。
 */
public final class ASMUtil {

    private ASMUtil() {}

    @SuppressWarnings("unchecked")
    public static <T extends IPacket> Function<FriendlyByteBuf, T> createDecoder(Class<T> packetClass) {
        try {
            // 确保目标构造函数存在
            final Constructor<T> targetConstructor;
            try {
                targetConstructor = packetClass.getConstructor(FriendlyByteBuf.class);
            } catch (NoSuchMethodException e) {
                throw new IllegalArgumentException("Packet class " + packetClass.getSimpleName() + " must have a public constructor that accepts a single FriendlyByteBuf argument.", e);
            }

            final String packetClassName = Type.getInternalName(packetClass);
            final String decoderClassName = packetClassName + "$$Decoder";
            final String friendlyByteBufName = Type.getInternalName(FriendlyByteBuf.class);

            // --- 开始 ASM 字节码生成 ---
            ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS);
            cw.visit(Opcodes.V17, Opcodes.ACC_PUBLIC | Opcodes.ACC_FINAL, decoderClassName, null, "java/lang/Object", new String[]{"java/util/function/Function"});

            // 创建默认的无参构造函数
            MethodVisitor mv = cw.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null);
            mv.visitCode();
            mv.visitVarInsn(Opcodes.ALOAD, 0);
            mv.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
            mv.visitInsn(Opcodes.RETURN);
            mv.visitMaxs(1, 1);
            mv.visitEnd();

            // 直接实现 apply(Object) 方法，内部完成所有逻辑
            mv = cw.visitMethod(Opcodes.ACC_PUBLIC, "apply", "(Ljava/lang/Object;)Ljava/lang/Object;", null, null);
            mv.visitCode();
            mv.visitTypeInsn(Opcodes.NEW, packetClassName); // new YourPacket()
            mv.visitInsn(Opcodes.DUP);
            mv.visitVarInsn(Opcodes.ALOAD, 1); // 加载 Object 参数
            mv.visitTypeInsn(Opcodes.CHECKCAST, friendlyByteBufName); // 将其强制转换为 FriendlyByteBuf
            mv.visitMethodInsn(Opcodes.INVOKESPECIAL, packetClassName, "<init>", "(L" + friendlyByteBufName + ";)V", false); // 调用构造函数
            mv.visitInsn(Opcodes.ARETURN); // 返回 new 出来的实例
            mv.visitMaxs(3, 2);
            mv.visitEnd();

            cw.visitEnd();
            // --- ASM 字节码生成结束 ---

            byte[] bytecode = cw.toByteArray();
            DynamicClassLoader classLoader = new DynamicClassLoader();
            Class<?> decoderImplClass = classLoader.defineClass(decoderClassName.replace('/', '.'), bytecode);

            return (Function<FriendlyByteBuf, T>) decoderImplClass.getConstructor().newInstance();

        } catch (Exception e) {
            throw new RuntimeException("Failed to create decoder for packet: " + packetClass.getSimpleName(), e);
        }
    }

    private static class DynamicClassLoader extends ClassLoader {
        public DynamicClassLoader() {
            super(FriendlyByteBuf.class.getClassLoader());
        }

        public Class<?> defineClass(String name, byte[] b) {
            return defineClass(name, b, 0, b.length);
        }
    }
}
