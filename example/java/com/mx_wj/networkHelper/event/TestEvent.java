package com.mx_wj.networkHelper.event;

import net.minecraft.network.chat.PlayerChatMessage;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber
public class TestEvent {
    @SubscribeEvent
    public static void on(PlayerInteractEvent.RightClickBlock event) {
        if(!event.getLevel().isClientSide) return;
        PacketManager.sendToServer(NetworkHelperMod.MODID, new ExamplePacket(1, "Hello World!"));
    }
}
