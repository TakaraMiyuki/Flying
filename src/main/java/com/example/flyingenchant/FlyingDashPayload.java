package com.example.flyingenchant;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * 客户端 → 服务端：请求执行一次"飞翔"突进（附魔版与效果版统一入口）。
 * 携带客户端的疾跑标志——服务端的疾跑镜像会经 moved-wrongly 回退/碰撞脱同步，
 * 不可信，故由客户端上送；效果通道据此把关，伪造该标志仅能绕过"疾跑"这一体验门槛，
 * 真正的安全门（效果持有、gamerule、冷却、饱食度、跃起窗口）全部由服务端自行校验。
 * 通道优先级（附魔优先 → 效果）由服务端在 {@code FlyingEnchantMod#handleDashRequest} 仲裁。
 * TYPE id 沿用旧命名空间 xingyue:flying_dash（协议版本号随结构变更为 2）。
 */
public record FlyingDashPayload(boolean sprinting) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<FlyingDashPayload> TYPE =
        new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath("xingyue", "flying_dash"));
    public static final StreamCodec<FriendlyByteBuf, FlyingDashPayload> STREAM_CODEC =
        StreamCodec.composite(ByteBufCodecs.BOOL, FlyingDashPayload::sprinting, FlyingDashPayload::new);

    @Override
    public CustomPacketPayload.Type<FlyingDashPayload> type() {
        return TYPE;
    }
}
