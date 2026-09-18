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
 * 客户端侧：按跳跃键 → 向服务端发送统一的突进请求（{@link FlyingDashPayload}），
 * 由服务端仲裁通道优先级（附魔优先 → 效果）。这里只做轻量预筛：
 * <ul>
 * <li>候选：持有飞翔效果 <b>或</b> 主手为飞翔适用物品，否则不发包；</li>
 * <li>时机：疾跑中 <b>或</b> 空中按下的跳跃键（地面非疾跑的按键不请求，防走路误触）。</li>
 * </ul>
 * 通道门槛不在客户端判定——附魔版的"本次跃起是否已用"状态只在服务端存在，
 * 效果版的疾跑标志随包上送（服务端镜像会经 moved-wrongly 回退/碰撞脱同步，不可信）。
 * 防滥用（走路空中连按）由服务端拒绝 sprinting=false 的效果请求；防刷屏由服务端
 * 10 刻冷却与附魔版"每次跃起一次"约束。26.2 原版跳跃走 keyJump.isDown()，
 * 与此处的 consumeClick() 互不影响。
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
        boolean candidate = player.hasEffect(FlyingEnchantMod.FLYING_EFFECT)
            || player.getMainHandItem().is(FlyingEnchantMod.FLYING_ENCHANTABLE);
        if (!candidate) {
            return;
        }
        while (mc.options.keyJump.consumeClick()) {
            if (player.isSprinting() || !player.onGround()) {
                boolean sprinting = player.isSprinting();
                if (DEBUG_DASH) {
                    LOGGER.info("[FlyingDash] sending request (sprint={}, ground={}, yVel={})",
                        sprinting, player.onGround(), player.getDeltaMovement().y);
                }
                ClientPacketDistributor.sendToServer(new FlyingDashPayload(sprinting));
            }
        }
    }
}
