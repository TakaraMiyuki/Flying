package com.example.examplemod;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.AnvilUpdateEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/**
 * 飞翔（flying）附魔：仅作用于星月与重锤（重锤须先附魔风爆才能获得，见 {@link #onAnvilUpdate}）。
 * 效果：处于"被弹起后的腾空窗口"（星月特殊攻击跃起，或风爆下坠攻击弹跳）时按跳跃键，
 * 向视线前上方突进一段距离（一级 ≈2 格 / 二级 ≈3 格，抬高约 0.5 格）。
 * 每次腾空限一次；消耗 1 点武器耐久与 2 点饥饿（不受等级影响，创造豁免），
 * 饱食度不高于疾跑阈值（6）时不可用。突进时脚下泛起白色风爆粒子并播放风爆音效。
 * 弹起窗口的判定依据：{@code LivingEntity.isIgnoringFallDamageFromCurrentImpulse()}——
 * 星月的 {@code launchPlayer} 与原版重锤风爆（{@code MaceItem.hurtEnemy}）都会设置该状态，落地即失效。
 */
public final class FlyingEnchant {
    public static final ResourceKey<Enchantment> FLYING_KEY =
        ResourceKey.create(Registries.ENCHANTMENT, Identifier.fromNamespaceAndPath(ExampleMod.MODID, "flying"));
    public static final ResourceKey<Enchantment> WIND_BURST_KEY =
        ResourceKey.create(Registries.ENCHANTMENT, Identifier.withDefaultNamespace("wind_burst"));

    /** 突进水平初速：一级 ≈2 格、二级 ≈3 格（空中阻力 0.91/刻，前约 10 刻的滑行位移）。 */
    public static final double DASH_SPEED_BASE = 0.30;
    public static final double DASH_SPEED_PER_LEVEL = 0.15;
    /** 突进向上初速：约抬高 0.5 格。 */
    public static final double DASH_UP = 0.24;
    /** 每次突进消耗的饥饿值（点）。 */
    public static final int DASH_HUNGER_COST = 2;
    /** 疾跑所需饱食度阈值（须严格大于才可突进）。 */
    public static final int SPRINT_FOOD_THRESHOLD = 6;

    /** 本次腾空已突进过的玩家，落地时清除（见 {@link #onPlayerTick}）。 */
    private static final Set<UUID> DASHED_THIS_FLIGHT = new HashSet<>();

    private FlyingEnchant() {
    }

    // mod bus：注册服务端 payload 处理器
    public static void onRegisterPayloads(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("1");
        registrar.playToServer(XingyueDashPayload.TYPE, XingyueDashPayload.STREAM_CODEC,
            (payload, context) -> handleDashRequest(context.player()));
    }

    // 游戏总线：玩家落地即恢复突进资格
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        Player player = event.getEntity();
        if (!player.level().isClientSide() && player.onGround()) {
            DASHED_THIS_FLIGHT.remove(player.getUUID());
        }
    }

    // 游戏总线：重锤必须已附魔风爆，铁砧才允许为其应用飞翔附魔书
    public static void onAnvilUpdate(AnvilUpdateEvent event) {
        ItemStack left = event.getLeft();
        ItemStack right = event.getRight();
        if (!left.is(Items.MACE) || right.isEmpty()) {
            return;
        }
        ItemEnchantments stored = right.get(DataComponents.STORED_ENCHANTMENTS);
        if (stored == null || !left.is(Items.MACE)) {
            return;
        }
        var registry = event.getPlayer().registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
        Holder<Enchantment> flying = registry.getOrThrow(FLYING_KEY);
        if (stored.getLevel(flying) > 0) {
            Holder<Enchantment> windBurst = registry.getOrThrow(WIND_BURST_KEY);
            if (left.getEnchantmentLevel(windBurst) <= 0) {
                event.setCanceled(true);
            }
        }
    }

    // 服务端：全量校验后执行突进（客户端请求不携带任何数据，伪造发包无法绕过限制）
    private static void handleDashRequest(Player basePlayer) {
        if (!(basePlayer instanceof ServerPlayer player) || !(player.level() instanceof ServerLevel level)) {
            return;
        }
        if (player.onGround() || !player.isIgnoringFallDamageFromCurrentImpulse()) {
            return; // 必须处于"被星月爆发/风爆弹起后的腾空窗口"
        }
        if (DASHED_THIS_FLIGHT.contains(player.getUUID())) {
            return; // 每次腾空限一次
        }
        ItemStack weapon = player.getMainHandItem();
        int flyingLevel = weapon.getEnchantmentLevel(enchantmentHolder(player, FLYING_KEY));
        if (flyingLevel <= 0) {
            return; // 持有物必须附魔飞翔
        }
        if (!player.hasInfiniteMaterials() && player.getFoodData().getFoodLevel() <= SPRINT_FOOD_THRESHOLD) {
            return; // 饱食度不足（与疾跑同门槛）
        }

        // 前上方突进：视线水平方向（归一化，俯视不减距）× 等级速度 + 固定向上分量，保留爆发残余动量
        Vec3 look = player.getLookAngle();
        Vec3 horizontal = new Vec3(look.x, 0.0, look.z);
        horizontal = horizontal.lengthSqr() > 1.0E-4
            ? horizontal.normalize()
            : Vec3.directionFromRotation(0.0F, player.getYRot());
        double speed = DASH_SPEED_BASE + DASH_SPEED_PER_LEVEL * (flyingLevel - 1);
        player.addDeltaMovement(horizontal.scale(speed).add(0.0, DASH_UP, 0.0));
        player.applyPostImpulseGraceTime(10);
        player.connection.send(new ClientboundSetEntityMotionPacket(player));

        // 特效：脚下白色风爆粒子 + 风爆音效
        for (int i = 0; i < 10; i++) {
            double angle = i * (Math.PI * 2 / 10.0);
            level.sendParticles(ParticleTypes.GUST,
                player.getX() + Math.cos(angle) * 0.8, player.getY() + 0.1, player.getZ() + Math.sin(angle) * 0.8,
                1, 0.0, 0.0, 0.0, 0.0);
        }
        level.playSound(null, player.getX(), player.getY(), player.getZ(),
            SoundEvents.BREEZE_WIND_CHARGE_BURST, SoundSource.PLAYERS, 1.0F, 1.0F);

        // 消耗：1 点武器耐久（创造自动豁免）+ 2 点饥饿（仅生存）
        weapon.hurtAndBreak(1, player, InteractionHand.MAIN_HAND);
        if (!player.hasInfiniteMaterials()) {
            player.getFoodData().setFoodLevel(Math.max(0, player.getFoodData().getFoodLevel() - DASH_HUNGER_COST));
        }
        DASHED_THIS_FLIGHT.add(player.getUUID());
    }

    private static Holder<Enchantment> enchantmentHolder(ServerPlayer player, ResourceKey<Enchantment> key) {
        return player.level().registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(key);
    }
}
