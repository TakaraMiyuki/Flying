package com.example.flyingenchant;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * 客户端 → 服务端：请求执行一次"飞翔效果"突进（疾跑状态下按跳跃键触发）。
 * 与附魔版的 {@link FlyingDashPayload} 相互独立：不要求跃起窗口、附魔或特定武器，
 * 改为校验玩家持有飞翔状态效果。无携带数据，全部条件由服务端自行校验，
 * 防止伪造发包绕过消耗与限制。
 */
public record FlyingEffectDashPayload() implements CustomPacketPayload {
    public static final FlyingEffectDashPayload INSTANCE = new FlyingEffectDashPayload();
    public static final CustomPacketPayload.Type<FlyingEffectDashPayload> TYPE =
        new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath("flying_enchant", "flying_effect_dash"));
    public static final StreamCodec<FriendlyByteBuf, FlyingEffectDashPayload> STREAM_CODEC =
        StreamCodec.unit(INSTANCE);

    @Override
    public CustomPacketPayload.Type<FlyingEffectDashPayload> type() {
        return TYPE;
    }
}
