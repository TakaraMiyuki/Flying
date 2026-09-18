package com.example.flyingenchant;

import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;

/**
 * 飞翔状态效果：纯标记效果，本身不产生任何周期作用。
 * 持有效果的玩家在疾跑状态下按跳跃键即可向空中突进
 * （客户端轮询检测按键，服务端 {@code FlyingEnchantMod#handleEffectDashRequest} 校验并执行）。
 * 通过 /effect give、数据包或其他模组施加；除玩家以外的生物被免疫
 * （见 {@code FlyingEnchantMod} 中的 MobEffectEvent.Applicable 守卫）。
 */
public final class FlyingEffect extends MobEffect {
    public FlyingEffect() {
        super(MobEffectCategory.BENEFICIAL, 0xBFE7FF);
    }
}
