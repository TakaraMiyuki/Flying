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
 * <li><b>效果版</b>：持有飞翔状态效果且<b>正在疾跑</b>时按跳跃 → {@link FlyingEffectDashPayload}。
 * 疾跑是唯一客户端门槛（26.2 支持空中起跑，风爆弹跳后按住疾跑键+前进键即可空中接续）；
 * 不可放宽到"不在地面"——否则正常行走跳跃后空中按键也会突进。
 * 服务端不再校验疾跑镜像（该标志会经 moved-wrongly 回退/碰撞脱同步），
 * 安全门（效果、gamerule、冷却、饱食度）全部在服务端强制。</li>
 * <li><b>附魔版</b>：主手为飞翔适用物品时，空中按跳跃 → {@link FlyingDashPayload}；
 * 效果版不就绪时才走此分支。</li>
 * </ul>
 * 客户端另加与服务器冷却等长的发送节流，防连按/长按刷屏。
 * 注意 26.2 原版跳跃走 keyJump.isDown()，与此处的 consumeClick() 互不影响。
 */
@EventBusSubscriber(modid = FlyingEnchantMod.MODID, value = Dist.CLIENT)
public final class FlyingEnchantClient {
    /** 临时诊断开关：定位线上状态同步问题用，问题闭环后关闭。 */
    static final boolean DEBUG_DASH = true;
    private static final Logger LOGGER = LogUtils.getLogger();

    /** 上次发送效果突进请求的客户端游戏刻，用于与服务器冷却对齐的本地节流。 */
    private static long lastEffectDashSent = Long.MIN_VALUE;

    private FlyingEnchantClient() {
    }

    @SubscribeEvent
    static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        Player player = mc.player;
        if (player == null || mc.gui.screen() != null || player.isSpectator()) {
            return;
        }
        // 效果版：必须正在疾跑（空中起跑后同样成立）；附魔版：主手为适用物品
        boolean effectReady = player.hasEffect(FlyingEnchantMod.FLYING_EFFECT) && player.isSprinting();
        boolean enchantReady = player.getMainHandItem().is(FlyingEnchantMod.FLYING_ENCHANTABLE);
        if (!effectReady && !enchantReady) {
            return;
        }
        long now = player.level().getGameTime();
        while (mc.options.keyJump.consumeClick()) {
            if (effectReady) {
                if (now - lastEffectDashSent < FlyingEnchantMod.EFFECT_DASH_COOLDOWN_TICKS) {
                    if (DEBUG_DASH) {
                        LOGGER.info("[FlyingEffectDash] client-throttled ({} ticks left)",
                            FlyingEnchantMod.EFFECT_DASH_COOLDOWN_TICKS - (now - lastEffectDashSent));
                    }
                    continue;
                }
                lastEffectDashSent = now;
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
