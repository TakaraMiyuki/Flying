package com.example.examplemod;

import java.util.function.Predicate;

import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUseAnimation;
import net.minecraft.world.item.ToolMaterial;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * 星月：原创武器（剑）。
 * 左键攻击同原版剑；长按右键蓄力（弓姿势），到 {@link #FULL_CHARGE_TICK} 刻自动爆发：
 * 星月粒子1 三重迸发、半径 {@link #BURST_RADIUS} 格内实体受 {@link #BURST_DAMAGE} 点伤害，
 * 以玩家实体为基准被水平击退约 3 格、向上击飞约 3 格（落地结算摔落伤害）；
 * 玩家跃起约 4.9 格（可跨越 4 格方块），落地免摔（复用原版风爆的 impulse 免摔机制）。
 * 爆发后进入 {@link #COOLDOWN_TICKS} 刻冷却（物品栏扫表动画）并损耗 1 点耐久（与普攻相同）。
 * 与盾牌共持时无法蓄力，盾牌可正常格挡。
 */
public class XingyueItem extends Item {
    /** 星月材质：1680 耐久，攻击伤害加成 3.0（+3.0 基线 = 总伤害 7），下界合金级采矿与修复。 */
    public static final ToolMaterial MATERIAL = new ToolMaterial(
        BlockTags.INCORRECT_FOR_NETHERITE_TOOL, 1680, 9.0F, 3.0F, 15, ItemTags.NETHERITE_TOOL_MATERIALS);

    public static final int FULL_CHARGE_TICK = 45;
    public static final int CHARGE_START_TICK = 6;
    /** 特殊攻击冷却（游戏刻）。 */
    public static final int COOLDOWN_TICKS = 88;
    /** 爆发范围伤害（固定值，不受攻击力属性影响）。 */
    public static final float BURST_DAMAGE = 4.0F;
    public static final double BURST_RADIUS = 3.0;
    /** 水平击退初速：按玩家实体空中阻力约位移 3 格（含落地小段滑行），按目标击退抗性缩减。 */
    public static final double BURST_KNOCKBACK = 0.4;
    /** 被击飞实体的向上初速：约 1 格腾空。 */
    public static final double BURST_LAUNCH = 0.38;
    /** 玩家跃起初速：按原版重力/阻尼约 4.9 格高，可跨越 4 格方块。 */
    public static final double JUMP_POWER = 0.9;

    public XingyueItem(Properties properties) {
        super(properties);
    }

    // 冷却中无法使用特殊攻击；另一只手持可格挡物品（盾牌）时交出使用权，盾牌正常举盾
    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        InteractionHand otherHand = hand == InteractionHand.MAIN_HAND ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND;
        if (player.getItemInHand(otherHand).has(DataComponents.BLOCKS_ATTACKS)) {
            return InteractionResult.PASS;
        }
        if (player.getCooldowns().isOnCooldown(stack)) {
            return InteractionResult.FAIL;
        }
        player.startUsingItem(hand);
        return InteractionResult.CONSUME;
    }

    @Override
    public int getUseDuration(ItemStack stack, LivingEntity user) {
        return FULL_CHARGE_TICK;
    }

    // 弓的蓄力姿势与动画
    @Override
    public ItemUseAnimation getUseAnimation(ItemStack stack) {
        return ItemUseAnimation.BOW;
    }

    // 蓄力期间：
    // 服务端——每 4 刻播放一次紫水晶鸣响（高音调，契合星月主题），音量与音调随蓄力进度渐强；
    // 客户端——星月粒子2 环绕玩家，数量随进度平方加速增多，环绕半径与高度带扩大，
    // 蓄力后半段额外生成贴地外圈光点，营造能量汇聚感
    @Override
    public void onUseTick(Level level, LivingEntity entity, ItemStack stack, int remaining) {
        int elapsed = getUseDuration(stack, entity) - remaining;
        if (elapsed < CHARGE_START_TICK) {
            return;
        }
        float progress = Math.min(1.0F, (elapsed - CHARGE_START_TICK) / (float) (FULL_CHARGE_TICK - CHARGE_START_TICK));
        if (!level.isClientSide()) {
            if (elapsed % 4 == 0) {
                level.playSound(null, entity.getX(), entity.getY() + 0.5, entity.getZ(),
                    SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS,
                    0.15F + 0.85F * progress, 1.0F + 0.5F * progress);
            }
            return;
        }
        int count = 1 + Math.round(progress * progress * 5.0F);
        double radius = 0.6 + 0.8 * progress;
        double heightBand = 0.6 + 1.2 * progress;
        for (int i = 0; i < count; i++) {
            double angle = (elapsed * 25.0 + i * 360.0 / count) * (Math.PI / 180.0);
            double px = entity.getX() + Math.cos(angle) * radius;
            double pz = entity.getZ() + Math.sin(angle) * radius;
            double py = entity.getY() + 0.2 + level.getRandom().nextDouble() * heightBand;
            level.addParticle(ExampleMod.XINGYUE_ORBIT.get(), px, py, pz, 0.0, 0.01 + 0.04 * progress, 0.0);
        }
        if (progress > 0.7F && level.getRandom().nextBoolean()) {
            double angle = (elapsed * 40.0) * (Math.PI / 180.0);
            double outerRadius = radius + 0.4 + 0.3 * (1.0F - progress);
            level.addParticle(ExampleMod.XINGYUE_ORBIT.get(),
                entity.getX() + Math.cos(angle) * outerRadius, entity.getY() + 0.1,
                entity.getZ() + Math.sin(angle) * outerRadius, 0.0, 0.06, 0.0);
        }
    }

    // 蓄力到 45 刻自动触发；提前松手走默认 releaseUsing（无效果），蓄力取消
    @Override
    public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity entity) {
        if (level instanceof ServerLevel serverLevel && entity instanceof Player player) {
            damageArea(serverLevel, player);
            burstEffects(serverLevel, player);
            launchPlayer(serverLevel, player);
            player.getCooldowns().addCooldown(stack, COOLDOWN_TICKS);
            stack.hurtAndBreak(1, entity, entity.getUsedItemHand());
        }
        return stack;
    }

    private static void damageArea(ServerLevel level, Player player) {
        level.getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(BURST_RADIUS), burstTargets(player))
            .forEach(target -> {
                target.hurtServer(level, player.damageSources().playerAttack(player), BURST_DAMAGE);
                Vec3 direction = target.position().subtract(player.position());
                if (direction.lengthSqr() > 1.0E-4) {
                    double resistanceScale = 1.0 - target.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE);
                    Vec3 knockback = direction.normalize().scale(BURST_KNOCKBACK * resistanceScale);
                    target.push(knockback.x, BURST_LAUNCH * resistanceScale, knockback.z);
                    if (target instanceof ServerPlayer targetPlayer) {
                        targetPlayer.connection.send(new ClientboundSetEntityMotionPacket(targetPlayer));
                    }
                }
            });
    }

    private static Predicate<LivingEntity> burstTargets(Player player) {
        return target -> target != player
            && !target.isSpectator()
            && !player.isAlliedTo(target)
            && !(target instanceof ArmorStand armorStand && armorStand.isMarker())
            && !(target instanceof TamableAnimal animal && animal.isTame() && animal.isOwnedBy(player))
            && !(target instanceof Player other && other.isCreative() && other.getAbilities().flying);
    }

    private static void burstEffects(ServerLevel level, Player player) {
        double centerX = player.getX();
        double centerY = player.getY() + 1.0;
        double centerZ = player.getZ();
        // 主环：星月粒子1 从腰部高速向四周迸发（sendParticles count=0 时以速度模式生成单个粒子）
        for (int i = 0; i < 36; i++) {
            double angle = i * (Math.PI * 2 / 36.0);
            level.sendParticles(ExampleMod.XINGYUE_BURST.get(),
                centerX, centerY, centerZ, 0, Math.cos(angle) * 1.2, 0.15, Math.sin(angle) * 1.2, 0.45);
        }
        // 贴地冲击环：低空向外的光尘，强化范围感
        for (int i = 0; i < 20; i++) {
            double angle = i * (Math.PI * 2 / 20.0);
            level.sendParticles(ExampleMod.XINGYUE_BURST.get(),
                centerX, player.getY() + 0.15, centerZ, 0, Math.cos(angle) * 1.6, 0.05, Math.sin(angle) * 1.6, 0.35);
        }
        // 上升光柱：能量向上喷发
        for (int i = 0; i < 12; i++) {
            double angle = level.getRandom().nextDouble() * Math.PI * 2;
            double dist = level.getRandom().nextDouble() * 1.2;
            level.sendParticles(ExampleMod.XINGYUE_BURST.get(),
                centerX + Math.cos(angle) * dist, player.getY() + 0.2, centerZ + Math.sin(angle) * dist,
                0, 0.0, 0.9 + level.getRandom().nextDouble() * 0.6, 0.0, 0.5);
        }
        // 星月粒子2：环绕光点被迸发出去——移动向量模长 3，速度 1
        for (int i = 0; i < 16; i++) {
            double angle = i * (Math.PI * 2 / 16.0);
            level.sendParticles(ExampleMod.XINGYUE_ORBIT.get(),
                centerX, player.getY() + 0.8, centerZ, 0, Math.cos(angle) * 3.0, 0.0, Math.sin(angle) * 3.0, 1.0);
        }
        // 脚下一圈云雾尘土（踩地板粒子）
        for (int i = 0; i < 14; i++) {
            double angle = i * (Math.PI * 2 / 14.0);
            level.sendParticles(ParticleTypes.CLOUD,
                centerX + Math.cos(angle) * 0.9, player.getY() + 0.1, centerZ + Math.sin(angle) * 0.9,
                1, 0.0, 0.0, 0.0, 0.0);
        }
        level.playSound(null, centerX, centerY, centerZ, SoundEvents.WIND_CHARGE_BURST, SoundSource.PLAYERS, 1.0F, 1.0F);
    }

    // 原版风爆弹跳同款：Y 轴推力 + 记录起跳点，落地结算摔落时以起跳点为基准，故不受摔落伤害。
    // 跃起瞬间播放三叉戟落地（音量1）与 spyglass 展开（音量4）音效，并刷新飞翔突进资格（每次跃起可用一次）
    private static void launchPlayer(ServerLevel level, Player player) {
        player.setDeltaMovement(player.getDeltaMovement().with(Direction.Axis.Y, JUMP_POWER));
        player.resetFallDistance();
        player.setIgnoreFallDamageFromCurrentImpulse(true, player.position());
        if (player instanceof ServerPlayer serverPlayer) {
            serverPlayer.connection.send(new ClientboundSetEntityMotionPacket(serverPlayer));
        }
        FlyingEnchant.onXingyueLaunch(player);
        level.playSound(null, player.getX(), player.getY(), player.getZ(),
            SoundEvents.TRIDENT_HIT_GROUND, SoundSource.PLAYERS, 1.0F, 1.0F);
        level.playSound(null, player.getX(), player.getY(), player.getZ(),
            SoundEvents.SPYGLASS_USE, SoundSource.PLAYERS, 4.0F, 1.0F);
    }
}
