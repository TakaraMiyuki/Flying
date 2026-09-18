package com.example.flyingenchant;

import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

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
 * <li><b>效果版</b>：持有飞翔状态效果时按跳跃 → {@link FlyingEffectDashPayload}。
 * 地面起跳要求正在疾跑；空中（风爆弹跳、突进途中）不要求疾跑——空中疾跑标志不可靠
 * （服务端镜像经 moved-wrongly 回退/碰撞后会脱同步），故空中只校验效果持有。</li>
 * <li><b>附魔版</b>：主手为飞翔适用物品时，空中按跳跃 → {@link FlyingDashPayload}；
 * 效果版不就绪时才走此分支。</li>
 * </ul>
 * 这里只做最便宜的客户端预筛；效果持有、附魔等级、饱食度、冷却、gamerule 等
 * 全部条件由服务端校验。注意 26.2 原版跳跃走 keyJump.isDown()，与此处的
 * consumeClick() 互不影响。
 */
@EventBusSubscriber(modid = FlyingEnchantMod.MODID, value = Dist.CLIENT)
public final class FlyingEnchantClient {
    /** 临时诊断开关：定位线上状态同步问题用，问题闭环后关闭。 */
    static final boolean DEBUG_DASH = true;
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
        // 效果版：地面起跳要求疾跑；空中只要求持有效果
        boolean effectReady = player.hasEffect(FlyingEnchantMod.FLYING_EFFECT)
            && (player.isSprinting() || !player.onGround());
        boolean enchantReady = player.getMainHandItem().is(FlyingEnchantMod.FLYING_ENCHANTABLE);
        if (!effectReady && !enchantReady) {
            return;
        }
        while (mc.options.keyJump.consumeClick()) {
            if (effectReady) {
                if (DEBUG_DASH) {
                    LOGGER.info("[FlyingEffectDash] sending dash request (sprint={}, ground={}, yVel={})",
                        player.isSprinting(), player.onGround(), player.getDeltaMovement().y);
                }
                ClientPacketDistributor.sendToServer(FlyingEffectDashPayload.INSTANCE);
            } else if (!player.onGround()) {
                ClientPacketDistributor.sendToServer(FlyingDashPayload.INSTANCE);
            }
        }
    }
}
