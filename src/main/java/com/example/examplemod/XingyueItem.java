package com.example.examplemod;

import java.util.function.Predicate;

import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
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
 * 星月粒子1 迸发、半径 {@link #BURST_RADIUS} 格内实体受伤并被击退，玩家获得 Y 轴推力跃起，
 * 落地免摔（复用原版风爆的 impulse 免摔机制）。与盾牌共持时无法蓄力，盾牌可正常格挡。
 */
public class XingyueItem extends Item {
    /** 星月材质：1680 耐久，攻击伤害加成 3.0（+3.0 基线 = 总伤害 7，tooltip 显示“7 攻击伤害”），下界合金级采矿与修复。 */
    public static final ToolMaterial MATERIAL = new ToolMaterial(
        BlockTags.INCORRECT_FOR_NETHERITE_TOOL, 1680, 9.0F, 3.0F, 15, ItemTags.NETHERITE_TOOL_MATERIALS);

    public static final int FULL_CHARGE_TICK = 45;
    public static final int CHARGE_START_TICK = 6;
    public static final double BURST_RADIUS = 2.0;
    public static final double BURST_KNOCKBACK = 0.9;
    /** 跃起初速：按原版重力/阻尼约跳起 2.1 格。 */
    public static final double JUMP_POWER = 0.56;

    public XingyueItem(Properties properties) {
        super(properties);
    }

    // 另一只手持可格挡物品（盾牌）时交出使用权：主手 PASS 后原版会继续尝试副手，盾牌正常举盾
    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        InteractionHand otherHand = hand == InteractionHand.MAIN_HAND ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND;
        if (player.getItemInHand(otherHand).has(DataComponents.BLOCKS_ATTACKS)) {
            return InteractionResult.PASS;
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

    // 蓄力期间：星月粒子2 环绕玩家，随进度变密、半径变大（客户端每刻在玩家当前位置周围生成）
    @Override
    public void onUseTick(Level level, LivingEntity entity, ItemStack stack, int remaining) {
        if (!level.isClientSide()) {
            return;
        }
        int elapsed = getUseDuration(stack, entity) - remaining;
        if (elapsed < CHARGE_START_TICK) {
            return;
        }
        float progress = Math.min(1.0F, (elapsed - CHARGE_START_TICK) / (float) (FULL_CHARGE_TICK - CHARGE_START_TICK));
        int count = 1 + Math.round(progress * 2.0F);
        double radius = 0.7 + 0.5 * progress;
        for (int i = 0; i < count; i++) {
            double angle = (elapsed * 25.0 + i * 360.0 / count) * (Math.PI / 180.0);
            double px = entity.getX() + Math.cos(angle) * radius;
            double pz = entity.getZ() + Math.sin(angle) * radius;
            double py = entity.getY() + 0.4 + level.getRandom().nextDouble() * (0.4 + 0.8 * progress);
            level.addParticle(ExampleMod.XINGYUE_ORBIT.get(), px, py, pz, 0.0, 0.015 + 0.02 * progress, 0.0);
        }
    }

    // 蓄力到 45 刻自动触发；提前松手走默认 releaseUsing（无效果），蓄力取消
    @Override
    public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity entity) {
        if (level instanceof ServerLevel serverLevel && entity instanceof Player player) {
            damageArea(serverLevel, player);
            burstEffects(serverLevel, player);
            launchPlayer(player);
        }
        return stack;
    }

    private static void damageArea(ServerLevel level, Player player) {
        float damage = (float) player.getAttributeValue(Attributes.ATTACK_DAMAGE);
        level.getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(BURST_RADIUS), burstTargets(player))
            .forEach(target -> {
                target.hurtServer(level, player.damageSources().playerAttack(player), damage);
                Vec3 direction = target.position().subtract(player.position());
                if (direction.lengthSqr() > 1.0E-4) {
                    Vec3 knockback = direction.normalize().scale(BURST_KNOCKBACK);
                    target.push(knockback.x, 0.5, knockback.z);
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
        // 星月粒子1：环形迸发（sendParticles count=0 时以速度模式生成单个粒子）
        for (int i = 0; i < 24; i++) {
            double angle = i * (Math.PI * 2 / 24.0);
            level.sendParticles(ExampleMod.XINGYUE_BURST.get(),
                centerX, centerY, centerZ, 0, Math.cos(angle), 0.1, Math.sin(angle), 0.35);
        }
        level.playSound(null, centerX, centerY, centerZ, SoundEvents.WIND_CHARGE_BURST, SoundSource.PLAYERS, 1.0F, 1.0F);
    }

    // 原版风爆弹跳同款：Y 轴推力 + 记录起跳点，落地结算摔落时以起跳点为基准，故不受摔落伤害
    private static void launchPlayer(Player player) {
        player.setDeltaMovement(player.getDeltaMovement().with(Direction.Axis.Y, JUMP_POWER));
        player.resetFallDistance();
        player.setIgnoreFallDamageFromCurrentImpulse(true, player.position());
        if (player instanceof ServerPlayer serverPlayer) {
            serverPlayer.connection.send(new ClientboundSetEntityMotionPacket(serverPlayer));
        }
    }
}
