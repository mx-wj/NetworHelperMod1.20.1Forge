package com.mx_wj.networkHelper.network.easy;

import com.mx_wj.networkHelper.NetworkHelperMod;
import sun.misc.Unsafe;

import java.lang.reflect.Field;

public class EasyUnsafeHacks {
    private static final Unsafe UNSAFE;

    static {
        Unsafe currentUNSAFE = null;
        try {
            final Field theUnsafe = Unsafe.class.getDeclaredField("theUnsafe");
            theUnsafe.setAccessible(true);
            currentUNSAFE = (Unsafe)theUnsafe.get(null);
        } catch (IllegalAccessException | NoSuchFieldException e) {
            NetworkHelperMod.LOGGER.error("I can't get unsafe instance :( ", e);
        }
        UNSAFE = currentUNSAFE;
    }

    @SuppressWarnings("unchecked")
    public static <T> T getStaticField(Field field){
        if (UNSAFE == null){
            throw new RuntimeException("Unsafe instance is null");
        }
        return (T)UNSAFE.getObject(UNSAFE.staticFieldBase(field), UNSAFE.staticFieldOffset(field));
    }
}
