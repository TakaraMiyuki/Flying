package com.example.examplemod;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * 客户端 → 服务端：请求执行一次"飞翔"空中突进。
 * 无携带数据（0 字节）——腾空窗口、附魔等级、饱食度等全部由服务端自行校验，
 * 防止伪造发包绕过消耗与限制。
 */
public record XingyueDashPayload() implements CustomPacketPayload {
    public static final XingyueDashPayload INSTANCE = new XingyueDashPayload();
    public static final CustomPacketPayload.Type<XingyueDashPayload> TYPE =
        new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(ExampleMod.MODID, "flying_dash"));
    public static final StreamCodec<FriendlyByteBuf, XingyueDashPayload> STREAM_CODEC =
        StreamCodec.unit(INSTANCE);

    @Override
    public CustomPacketPayload.Type<XingyueDashPayload> type() {
        return TYPE;
    }
}
