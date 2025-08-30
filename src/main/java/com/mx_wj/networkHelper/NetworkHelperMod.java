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
    // Directly reference a slf4j logger
    public static final Logger LOGGER = LogUtils.getLogger();

    public NetworkHelperMod() {
        FMLJavaModLoadingContext.get().getModEventBus().addListener(this::onCommonSetup);
    }

    public void onCommonSetup(FMLCommonSetupEvent event) {
        for(ModFileScanData modFileScanData : ModList.get().getAllScanData()){
            boolean isMod = true;
            for(IModFileInfo modFileInfo : modFileScanData.getIModInfoData()){
                for(IModInfo modInfo : modFileInfo.getMods()){
                    String modID = modInfo.getModId();
                    if(modID.equals("minecraft") || modID.equals("forge")) isMod = false;
                }
            }
            if(!isMod) continue;
            for(ModFileScanData.ClassData classData : modFileScanData.getClasses()){
                boolean isPacket = false;
                for(Type type : classData.interfaces()){
                    if(type.getClassName().equals(IPacket.class.getName())){
                        isPacket = true;
                    }
                }
                if(!isPacket){
                    if(classData.parent().getClassName().equals(EasyPacket.class.getName())){
                        isPacket = true;
                    }
                }
                if(isPacket){
                    try {
                        Class<?> packetClass = Class.forName(classData.clazz().getClassName());
                        if(packetClass.getAnnotation(PacketInfo.class) != null){
                            PacketManager.addToRegister(packetClass);
                        }
                    } catch (Throwable t) {
                        LOGGER.error("Error loading packet class: ", t);
                    }
                }
            }
        }
        event.enqueueWork(PacketManager::registerPackets);
    }
}
