package com.example.flyingenchant;

import com.mojang.logging.LogUtils;
import org.slf4j.Logger;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

/**
 * 客户端侧：腾空中按跳跃键 → 请求服务端突进。
 * 这里只做最便宜的客户端预筛（腾空 + 主手是飞翔适用物品——由共享 item tag 判定，
 * 因此无需依赖星月模组的任何类）；跃起窗口、附魔等级、饱食度、每次跃起一次等
 * 全部条件由服务端校验（跃起状态不同步到客户端，客户端无法预判）。
 */
@EventBusSubscriber(modid = FlyingEnchantMod.MODID, value = Dist.CLIENT)
public final class FlyingEnchantClient {
    private static final boolean DEBUG = true;
    private static final Logger LOGGER = LogUtils.getLogger();

    private FlyingEnchantClient() {
    }

    @SubscribeEvent
    static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        Player player = mc.player;
        if (player == null || mc.gui.screen() != null || player.isSpectator()) {
            return;
        }
        ItemStack mainHand = player.getMainHandItem();
        if (!mainHand.is(FlyingEnchantMod.FLYING_ENCHANTABLE)) {
            return;
        }
        while (mc.options.keyJump.consumeClick()) {
            if (!player.onGround()) {
                if (DEBUG) LOGGER.info("【飞翔】空中跳跃键触发，发送突进请求");
                ClientPacketDistributor.sendToServer(FlyingDashPayload.INSTANCE);
            } else if (DEBUG) {
                LOGGER.info("【飞翔】跳跃键点击但在地面，不发送");
            }
        }
    }
}