package com.example.flyingenchant;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

/**
 * 客户端侧：按跳跃键 → 请求服务端突进。两条独立通道（并存）：
 * <ul>
 * <li><b>附魔版</b>：主手为飞翔适用物品时，空中按跳跃 → {@link FlyingDashPayload}；</li>
 * <li><b>效果版</b>：持有飞翔状态效果且正在疾跑时按跳跃（不限空中）→ {@link FlyingEffectDashPayload}，
 * 优先于附魔版。</li>
 * </ul>
 * 这里只做最便宜的客户端预筛；跃起窗口、效果持有、附魔等级、饱食度、冷却、
 * gamerule 等全部条件由服务端校验（跃起状态不同步到客户端，客户端无法预判）。
 */
@EventBusSubscriber(modid = FlyingEnchantMod.MODID, value = Dist.CLIENT)
public final class FlyingEnchantClient {
    private FlyingEnchantClient() {
    }

    @SubscribeEvent
    static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        Player player = mc.player;
        if (player == null || mc.gui.screen() != null || player.isSpectator()) {
            return;
        }
        boolean effectReady = player.hasEffect(FlyingEnchantMod.FLYING_EFFECT) && player.isSprinting();
        boolean enchantReady = player.getMainHandItem().is(FlyingEnchantMod.FLYING_ENCHANTABLE);
        if (!effectReady && !enchantReady) {
            return;
        }
        while (mc.options.keyJump.consumeClick()) {
            if (effectReady) {
                ClientPacketDistributor.sendToServer(FlyingEffectDashPayload.INSTANCE);
            } else if (!player.onGround()) {
                ClientPacketDistributor.sendToServer(FlyingDashPayload.INSTANCE);
            }
        }
    }
}