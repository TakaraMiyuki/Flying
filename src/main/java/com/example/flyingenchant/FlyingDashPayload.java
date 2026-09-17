package com.example.flyingenchant;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * 客户端 → 服务端：请求执行一次"飞翔"空中突进。
 * 无携带数据（0 字节）——跃起窗口、附魔等级、饱食度等全部由服务端自行校验，
 * 防止伪造发包绕过消耗与限制。TYPE id 沿用旧命名空间 xingyue:flying_dash（存档/协议兼容）。
 */
public record FlyingDashPayload() implements CustomPacketPayload {
    public static final FlyingDashPayload INSTANCE = new FlyingDashPayload();
    public static final CustomPacketPayload.Type<FlyingDashPayload> TYPE =
        new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath("xingyue", "flying_dash"));
    public static final StreamCodec<FriendlyByteBuf, FlyingDashPayload> STREAM_CODEC =
        StreamCodec.unit(INSTANCE);

    @Override
    public CustomPacketPayload.Type<FlyingDashPayload> type() {
        return TYPE;
    }
}