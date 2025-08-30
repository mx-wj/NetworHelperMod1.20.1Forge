package com.mx_wj.networkHelper;

import com.mojang.logging.LogUtils;
import com.mx_wj.networkHelper.network.core.PacketManager;
import com.mx_wj.networkHelper.network.core.info.IPacket;
import com.mx_wj.networkHelper.network.core.info.PacketInfo;
import com.mx_wj.networkHelper.network.easy.EasyPacket;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.forgespi.language.IModFileInfo;
import net.minecraftforge.forgespi.language.IModInfo;
import net.minecraftforge.forgespi.language.ModFileScanData;
import org.objectweb.asm.Type;
import org.slf4j.Logger;

@Mod(NetworkHelperMod.MODID)
public class NetworkHelperMod
{
    public static final String MODID = "networkhelper";
    public static final Logger LOGGER = LogUtils.getLogger();

    public NetworkHelperMod() {
        FMLJavaModLoadingContext.get().getModEventBus().addListener(this::onCommonSetup);
    }

    /**
     * 在 FMLCommonSetupEvent 事件期间被调用。
     * 这个方法会自动扫描所有已加载的模组，查找并注册所有实现了 IPacket 或继承了 EasyPacket 的数据包类。
     * @param event FML 通用设置事件
     */
    public void onCommonSetup(FMLCommonSetupEvent event) {
        LOGGER.info("Starting automatic packet scanning for all mods...");
        // 遍历由 Forge 加载的所有模组的扫描数据。
        for(ModFileScanData modFileScanData : ModList.get().getAllScanData()){
            String modID = null;
            boolean isMod = true;

            // 从扫描数据中提取模组信息来确定 modID。
            // 一个 JAR 文件可能包含多个模组，这里我们简单地使用找到的第一个 modID。
            for(IModFileInfo modFileInfo : modFileScanData.getIModInfoData()){
                for(IModInfo modInfo : modFileInfo.getMods()){
                    String currentModID = modInfo.getModId();
                    // 过滤掉 Minecraft 本身和 Forge，因为我们不关心它们内部的类。
                    if(currentModID.equals("minecraft") || currentModID.equals("forge")) {
                        isMod = false;
                    }
                    modID = modID == null ? currentModID : modID;
                }
            }

            // 如果这不是一个有效的、我们关心的模组（例如，是原版或Forge），或者没有找到 modID，则跳过。
            if(!isMod || modID == null) {
                continue;
            }

            int foundPackets = 0;
            // 遍历该模组 JAR 文件中的所有类。
            for(ModFileScanData.ClassData classData : modFileScanData.getClasses()){
                if(isPacket(classData)){
                    // 如果该类被确定为一个数据包...
                    try {
                        // 使用反射加载该类。
                        Class<?> packetClass = Class.forName(classData.clazz().getClassName());
                        // 关键检查：只有带有 @PacketInfo 注解的数据包才会被注册。
                        // 这可以防止意外注册一些不想作为数据包的抽象基类或工具类。
                        if(packetClass.getAnnotation(PacketInfo.class) != null){
                            LOGGER.debug("Found packet '{}', queueing for registration.", packetClass.getSimpleName());
                            PacketManager.addToRegister(modID, packetClass);
                            foundPackets++;
                        }
                    } catch (Throwable t) {
                        LOGGER.error("Error while trying to load and register a potential packet class '{}'", classData.clazz().getClassName(), t);
                    }
                }
            }
            if (foundPackets > 0) {
                LOGGER.info("Finished scanning mod '{}'. Found {} packets to register.", modID, foundPackets);
            }
        }

        // 将 PacketManager::registerPackets 方法加入到主线程的工作队列中。
        // 这确保了所有网络通道和数据包的注册都在合适的时间点执行。
        event.enqueueWork(PacketManager::registerPackets);
        LOGGER.info("All mods scanned. Packet registration has been enqueued.");
    }

    private static boolean isPacket(ModFileScanData.ClassData classData) {
        // 检查该类是否直接实现了 IPacket 接口。
        for(Type type : classData.interfaces()){
            if(type.getClassName().equals(IPacket.class.getName())){
                return true;
            }
        }
        // 如果没有实现 IPacket 接口，再检查它是否继承自 EasyPacket。
        // 这是因为 EasyPacket 本身实现了 IPacket，所以它的子类也算是数据包。
        Type parent = classData.parent();
        if(parent != null && parent.getClassName().equals(EasyPacket.class.getName())){
            return true;
        }
        return false;
    }
}
