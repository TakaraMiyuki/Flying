package com.example.flyingenchant;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import net.minecraft.core.Direction;
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
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.event.AnvilUpdateEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/**
 * 飞翔（flying）附魔模组。独立模组，可单独安装；与星月模组（examplemod）共存时联动。
 * 附魔 id 保持 {@code examplemod:flying}（存档兼容），适用物品走共享 item tag
 * {@link #FLYING_ENCHANTABLE}：两个模组的 jar 各提供一份同名 tag（数据包自动合并），
 * 本模组提供重锤，星月模组提供星月——只装其一时对应物品依然可用。
 *
 * <p>效果：处于"跃起窗口"时按跳跃键，向视线前上方突进（一级 ≈3 格 / 二级 ≈4 格，抬高约 0.5 / 1 格）。
 * 每次跃起限一次；消耗 1 点武器耐久与 2 点饥饿（不受等级影响，创造豁免），
 * 饱食度不高于疾跑阈值（6）时不可用。突进时脚下泛起少量白色风爆粒子并播放风爆音效。
 * 跃起窗口由服务端自研检测维护（不依赖原版 impulse 免摔状态——那会在风爆附魔爆炸时被
 * {@code onExplosionHit} 重置为 false，导致第二次跃起后无法突进）：服务端每刻采样玩家垂直速度，
 * "上一刻 ≤ {@link #LAUNCH_LAST_Y_MAX} → 本刻 ≥ {@link #LAUNCH_Y_MIN}"的强烈上升反转即为一次跃起，
 * 覆盖星月爆发（初速 0.9）、重锤风爆弹跳与星月+风爆下坠弹跳；普通跳跃（0.42）不会误判。
 * 窗口在落地时关闭。创造飞行与鞘翅滑翔不触发。</p>
 *
 * <p>本模组与星月模组零编译依赖（双向），联动仅通过共享 item tag 与客户端输入（本模组自己的 payload）。</p>
 */
@Mod(FlyingEnchantMod.MODID)
public final class FlyingEnchantMod {
    public static final String MODID = "flying_enchant";
    /** 飞翔附魔 id 沿用旧命名空间（存档兼容：已附魔的物品不受拆分影响）。 */
    public static final ResourceKey<Enchantment> FLYING_KEY =
        ResourceKey.create(Registries.ENCHANTMENT, Identifier.fromNamespaceAndPath("examplemod", "flying"));
    public static final ResourceKey<Enchantment> WIND_BURST_KEY =
        ResourceKey.create(Registries.ENCHANTMENT, Identifier.withDefaultNamespace("wind_burst"));
    /** 飞翔适用物品的共享 tag：两个模组各提供一份同名文件（数据包 tag 自动合并）。 */
    public static final TagKey<Item> FLYING_ENCHANTABLE =
        TagKey.create(Registries.ITEM, Identifier.fromNamespaceAndPath("examplemod", "enchantable/flying"));

    /** 突进水平初速：一级 ≈3 格、二级 ≈4 格（空中阻力 0.91/刻，前约 10 刻的滑行位移）。 */
    public static final double DASH_SPEED_BASE = 0.44;
    public static final double DASH_SPEED_PER_LEVEL = 0.15;
    /** 突进向上初速：一级约抬高 0.5 格、二级约 1 格。 */
    public static final double DASH_UP_BASE = 0.24;
    public static final double DASH_UP_PER_LEVEL = 0.13;
    /** 每次突进消耗的饥饿值（点）。 */
    public static final int DASH_HUNGER_COST = 2;
    /** 疾跑所需饱食度阈值（须严格大于才可突进）。 */
    public static final int SPRINT_FOOD_THRESHOLD = 6;

    /** 跃起判定：上一刻 Y 速度上限（下坠/静止区间）。 */
    public static final double LAUNCH_LAST_Y_MAX = 0.02;
    /** 跃起判定：本刻 Y 速度下限（须高于普通跳跃的 0.42）。 */
    public static final double LAUNCH_Y_MIN = 0.5;

    /** 服务端每刻记录的玩家 Y 速度，用于跃起反转检测。 */
    private static final Map<UUID, Double> LAST_Y_VEL = new HashMap<>();
    /** 处于跃起窗口（可突进）的玩家。 */
    private static final Set<UUID> LAUNCHED = new HashSet<>();
    /** 本窗口内已突进过的玩家。 */
    private static final Set<UUID> DASH_USED = new HashSet<>();

    public FlyingEnchantMod(IEventBus modEventBus) {
        modEventBus.addListener(this::onRegisterPayloads);
    }

    // mod bus：注册服务端 payload 处理器
    private void onRegisterPayloads(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("1");
        registrar.playToServer(FlyingDashPayload.TYPE, FlyingDashPayload.STREAM_CODEC,
            (payload, context) -> handleDashRequest(context.player()));
    }

    // 游戏总线：每刻采样垂直速度做跃起检测 + 落地关窗
    static void onPlayerTick(PlayerTickEvent.Post event) {
        Player player = event.getEntity();
        if (player.level().isClientSide()) {
            return;
        }
        UUID uuid = player.getUUID();
        double yVel = player.getDeltaMovement().y;
        Double lastY = LAST_Y_VEL.put(uuid, yVel);
        // 强烈上升反转 = 一次跃起（弹跳发生时玩家可能尚有一刻触地，故先于落地判定）
        boolean launchSpike = !player.getAbilities().flying
            && !player.isFallFlying()
            && lastY != null
            && lastY <= LAUNCH_LAST_Y_MAX
            && yVel >= LAUNCH_Y_MIN;
        if (launchSpike) {
            LAUNCHED.add(uuid);
            DASH_USED.remove(uuid);
        } else if (player.onGround()) {
            LAUNCHED.remove(uuid);
            DASH_USED.remove(uuid);
            LAST_Y_VEL.remove(uuid);
        }
    }

    // 游戏总线：退出时清理状态
    static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        UUID uuid = event.getEntity().getUUID();
        LAST_Y_VEL.remove(uuid);
        LAUNCHED.remove(uuid);
        DASH_USED.remove(uuid);
    }

    // 游戏总线：重锤必须已附魔风爆，铁砧才允许为其应用飞翔附魔书
    static void onAnvilUpdate(AnvilUpdateEvent event) {
        ItemStack left = event.getLeft();
        ItemStack right = event.getRight();
        if (!left.is(Items.MACE) || right.isEmpty()) {
            return;
        }
        ItemEnchantments stored = right.get(DataComponents.STORED_ENCHANTMENTS);
        if (stored == null) {
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
        UUID uuid = player.getUUID();
        if (player.onGround() || !LAUNCHED.contains(uuid)) {
            return; // 必须处于跃起窗口内
        }
        if (DASH_USED.contains(uuid)) {
            return; // 每次跃起限一次
        }
        ItemStack weapon = player.getMainHandItem();
        int flyingLevel = weapon.getEnchantmentLevel(enchantmentHolder(player, FLYING_KEY));
        if (flyingLevel <= 0 || !weapon.is(FLYING_ENCHANTABLE)) {
            return; // 主手必须为飞翔适用物品（星月/重锤，由共享 tag 决定）且已附魔飞翔
        }
        if (!player.hasInfiniteMaterials() && player.getFoodData().getFoodLevel() <= SPRINT_FOOD_THRESHOLD) {
            return; // 饱食度不足（与疾跑同门槛）
        }

        // 前上方突进：视线水平方向（归一化，俯视不减距）× 等级速度，叠加到水平动量上；
        // 垂直分量整体重置为固定上抬——否则爆发下坠阶段的负速度会把突进抵消掉
        Vec3 look = player.getLookAngle();
        Vec3 horizontal = new Vec3(look.x, 0.0, look.z);
        horizontal = horizontal.lengthSqr() > 1.0E-4
            ? horizontal.normalize()
            : Vec3.directionFromRotation(0.0F, player.getYRot());
        double speed = DASH_SPEED_BASE + DASH_SPEED_PER_LEVEL * (flyingLevel - 1);
        double up = DASH_UP_BASE + DASH_UP_PER_LEVEL * (flyingLevel - 1);
        player.setDeltaMovement(player.getDeltaMovement().add(horizontal.scale(speed)).with(Direction.Axis.Y, up));
        player.applyPostImpulseGraceTime(10);
        player.connection.send(new ClientboundSetEntityMotionPacket(player));

        // 特效：脚下少量白色风爆粒子 + 风爆音效
        for (int i = 0; i < 6; i++) {
            double angle = i * (Math.PI * 2 / 6.0);
            level.sendParticles(ParticleTypes.SMALL_GUST,
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
        DASH_USED.add(uuid);
    }

    private static Holder<Enchantment> enchantmentHolder(ServerPlayer player, ResourceKey<Enchantment> key) {
        return player.level().registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(key);
    }
}