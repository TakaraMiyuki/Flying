package com.example.flyingenchant;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.Codec;

import org.slf4j.Logger;

import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.gamerules.GameRule;
import net.minecraft.world.level.gamerules.GameRuleCategory;
import net.minecraft.world.level.gamerules.GameRuleType;
import net.minecraft.world.level.gamerules.GameRuleTypeVisitor;
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
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.AnvilUpdateEvent;
import net.neoforged.neoforge.event.entity.living.MobEffectEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.minecraft.world.flag.FeatureFlagSet;

/**
 * 飞翔（flying）附魔模组。独立模组，可单独安装；与星月模组（xingyue）共存时联动。
 * 附魔 id 保持 {@code xingyue:flying}（存档兼容），适用物品走共享 item tag
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
 *
 * <p>此外提供一个同名的<b>状态效果</b> {@code flying_enchant:flying}（附魔版的独立通道，两者并存）：
 * 持有该效果的玩家在<b>疾跑状态下按跳跃键</b>即可向前上方突进（水平 ≈2 格、抬高 ≈2 格），
 * 每次消耗 1 点饥饿、突进时同样整体重置垂直速度（继承附魔版的"发动时重置下坠"特性）。
 * 效果通过 /effect give、数据包或其他模组施加，持续时长由施加者决定；本模组不主动施加。
 * 同样受 gamerule {@code flying_enchant:flying_dash} 管辖与饱食度门槛限制；每玩家另有 10 刻冷却防连按刷屏。
 * 除玩家以外的所有生物免疫该效果（MobEffectEvent.Applicable 守卫返回 DO_NOT_APPLY）。</p>
 */
@Mod(FlyingEnchantMod.MODID)
public final class FlyingEnchantMod {
    public static final String MODID = "flying_enchant";
    /** 临时诊断开关：定位线上状态同步问题用，问题闭环后关闭。 */
    static final boolean DEBUG_DASH = true;
    private static final Logger LOGGER = LogUtils.getLogger();
    /** 飞翔附魔 id 沿用旧命名空间（存档兼容：已附魔的物品不受拆分影响）。 */
    public static final ResourceKey<Enchantment> FLYING_KEY =
        ResourceKey.create(Registries.ENCHANTMENT, Identifier.fromNamespaceAndPath("xingyue", "flying"));
    public static final ResourceKey<Enchantment> WIND_BURST_KEY =
        ResourceKey.create(Registries.ENCHANTMENT, Identifier.withDefaultNamespace("wind_burst"));
    /** 飞翔适用物品的共享 tag：两个模组各提供一份同名文件（数据包 tag 自动合并）。 */
    public static final TagKey<Item> FLYING_ENCHANTABLE =
        TagKey.create(Registries.ITEM, Identifier.fromNamespaceAndPath("xingyue", "enchantable/flying"));

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

    /** mod 总线注册器：飞翔状态效果（id {@code flying_enchant:flying}，本模组自有内容）。 */
    private static final DeferredRegister<MobEffect> MOB_EFFECTS =
        DeferredRegister.create(Registries.MOB_EFFECT, MODID);
    /** 飞翔状态效果：纯标记效果，突进逻辑在 {@link #handleEffectDashRequest}。 */
    public static final DeferredHolder<MobEffect, FlyingEffect> FLYING_EFFECT =
        MOB_EFFECTS.register("flying", FlyingEffect::new);
    /** 效果版突进水平初速：≈2 格（空中阻力 0.91/刻）。 */
    public static final double EFFECT_DASH_SPEED = 0.30;
    /** 效果版突进向上初速：≈2 格高（整体重置垂直速度，与附魔版同机制）。 */
    public static final double EFFECT_DASH_UP = 0.55;
    /** 效果版每次突进消耗的饥饿值（点）。 */
    public static final int EFFECT_DASH_HUNGER_COST = 1;
    /** 效果版突进冷却（刻），防疾跑连按跳跃刷屏。 */
    public static final int EFFECT_DASH_COOLDOWN_TICKS = 10;

    /** 跃起判定：上一刻 Y 速度上限（下坠/静止区间）。 */
    public static final double LAUNCH_LAST_Y_MAX = 0.02;
    /** 跃起判定：本刻 Y 速度下限（须高于普通跳跃的 0.42）。 */
    public static final double LAUNCH_Y_MIN = 0.5;

    /**
     * mod 总线注册器：服务端开关 gamerule（Modrinth 审核合规：为玩家提供机动能力的模组必须可由服务端禁用）。
     * 管理员可用 {@code /gamerule flying_enchant:flying_dash false} 全服禁用突进。
     * <p>26.2 的 gamerule 是注册表驱动（{@code minecraft:game_rule}），必须在冻结前的注册阶段
     * 经 DeferredRegister 注册；mod 构造期直接调 {@code GameRules.registerBoolean} 会因注册表
     * 已冻结而崩溃，且 id 必须全小写。挂在 minecraft 命名空间之外的 {@code flying_enchant:flying_dash}。</p>
     */
    private static final DeferredRegister<GameRule<?>> GAME_RULES =
        DeferredRegister.create(Registries.GAME_RULE, MODID);
    public static final DeferredHolder<GameRule<?>, GameRule<Boolean>> FLYING_DASH_ENABLED =
        GAME_RULES.register("flying_dash", () -> new GameRule<>(
            GameRuleCategory.MISC, GameRuleType.BOOL, BoolArgumentType.bool(),
            GameRuleTypeVisitor::visitBoolean, Codec.BOOL, b -> b ? 1 : 0,
            true, FeatureFlagSet.of()));

    /** 服务端每刻记录的玩家 Y 速度，用于跃起反转检测。 */
    private static final Map<UUID, Double> LAST_Y_VEL = new HashMap<>();
    /** 处于跃起窗口（可突进）的玩家。 */
    private static final Set<UUID> LAUNCHED = new HashSet<>();
    /** 本窗口内已突进过的玩家。 */
    private static final Set<UUID> DASH_USED = new HashSet<>();
    /** 效果版突进冷却截止刻（游戏刻，按玩家维度记录）。 */
    private static final Map<UUID, Long> EFFECT_DASH_COOLDOWN_UNTIL = new HashMap<>();

    public FlyingEnchantMod(IEventBus modEventBus) {
        // mod bus：状态效果、gamerule、网络包注册
        MOB_EFFECTS.register(modEventBus);
        GAME_RULES.register(modEventBus);
        modEventBus.addListener(this::onRegisterPayloads);
        // 游戏总线：跃起检测（突进窗口）、铁砧风爆前置否决、退出清理、非玩家免疫飞翔效果
        NeoForge.EVENT_BUS.addListener(FlyingEnchantMod::onPlayerTick);
        NeoForge.EVENT_BUS.addListener(FlyingEnchantMod::onAnvilUpdate);
        NeoForge.EVENT_BUS.addListener(FlyingEnchantMod::onPlayerLoggedOut);
        NeoForge.EVENT_BUS.addListener(FlyingEnchantMod::onEffectApplicable);
    }

    // mod bus：注册服务端 payload 处理器
    private void onRegisterPayloads(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("1");
        registrar.playToServer(FlyingDashPayload.TYPE, FlyingDashPayload.STREAM_CODEC,
            (payload, context) -> handleDashRequest(context.player()));
        registrar.playToServer(FlyingEffectDashPayload.TYPE, FlyingEffectDashPayload.STREAM_CODEC,
            (payload, context) -> handleEffectDashRequest(context.player()));
    }

    // 游戏总线：除玩家以外的所有生物免疫飞翔效果
    static void onEffectApplicable(MobEffectEvent.Applicable event) {
        if (!(event.getEntity() instanceof Player)
            && event.getEffectInstance().getEffect().is(FLYING_EFFECT)) {
            event.setResult(MobEffectEvent.Applicable.Result.DO_NOT_APPLY);
        }
    }

    // 游戏总线：每刻采样垂直速度做跃起检测 + 落地关窗
    static void onPlayerTick(PlayerTickEvent.Post event) {
        Player player = event.getEntity();
        if (!(player.level() instanceof ServerLevel level)) {
            return;
        }
        UUID uuid = player.getUUID();
        if (!level.getGameRules().get(FLYING_DASH_ENABLED.value())) {
            LAST_Y_VEL.remove(uuid);
            return;
        }
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
        EFFECT_DASH_COOLDOWN_UNTIL.remove(uuid);
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
        if (!level.getGameRules().get(FLYING_DASH_ENABLED.value())) {
            return; // 服务器管理员禁用了空中突进
        }
        ItemStack weapon = player.getMainHandItem();
        int flyingLevel = weapon.getEnchantmentLevel(enchantmentHolder(player, FLYING_KEY));
        if (flyingLevel <= 0 || !weapon.is(FLYING_ENCHANTABLE)) {
            return; // 主手必须为飞翔适用物品（由共享 tag 决定）且已附魔飞翔
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

    // 服务端：效果版突进（疾跑起跳 / 空中接续）；与附魔版独立，不校验跃起窗口/附魔/tag。
    // 不校验 isSprinting：疾跑标志是客户端单向同步的镜像，moved-wrongly 回退/碰撞会让其
    // 脱同步（客户端视觉仍在疾跑而服务端标志为 false），硬校验会导致突进间歇性失效。
    // 疾跑门槛由客户端预筛执行；服务端保留安全门：效果持有、gamerule、冷却、饱食度。
    private static void handleEffectDashRequest(Player basePlayer) {
        if (!(basePlayer instanceof ServerPlayer player) || !(player.level() instanceof ServerLevel level)) {
            return;
        }
        if (!player.hasEffect(FLYING_EFFECT)) {
            reject(player, "no flying effect");
            return;
        }
        if (!level.getGameRules().get(FLYING_DASH_ENABLED.value())) {
            reject(player, "gamerule flying_dash disabled");
            return;
        }
        UUID uuid = player.getUUID();
        long now = level.getGameTime();
        Long until = EFFECT_DASH_COOLDOWN_UNTIL.get(uuid);
        if (until != null && now < until) {
            reject(player, "cooldown (" + (until - now) + " ticks left)");
            return;
        }
        if (!player.hasInfiniteMaterials() && player.getFoodData().getFoodLevel() <= SPRINT_FOOD_THRESHOLD) {
            reject(player, "food below sprint threshold");
            return;
        }

        // 前上方突进：视线水平方向 × 固定速度，垂直分量整体重置为上抬（继承附魔版"发动时重置下坠速度"特性）
        Vec3 look = player.getLookAngle();
        Vec3 horizontal = new Vec3(look.x, 0.0, look.z);
        horizontal = horizontal.lengthSqr() > 1.0E-4
            ? horizontal.normalize()
            : Vec3.directionFromRotation(0.0F, player.getYRot());
        player.setDeltaMovement(player.getDeltaMovement().add(horizontal.scale(EFFECT_DASH_SPEED))
            .with(Direction.Axis.Y, EFFECT_DASH_UP));
        // 宽限须覆盖整个飞行段（上2格+下落 ≈14 刻）直至落地，否则宽限外的滞空段会
        // 触发服务端 moved-wrongly 校验（残差 >0.25 格）→ 瞬移回退 + 双方速度清零
        player.applyPostImpulseGraceTime(20);
        player.connection.send(new ClientboundSetEntityMotionPacket(player));

        // 特效：脚下少量白色风爆粒子 + 风爆音效（与附魔版同款）
        for (int i = 0; i < 6; i++) {
            double angle = i * (Math.PI * 2 / 6.0);
            level.sendParticles(ParticleTypes.SMALL_GUST,
                player.getX() + Math.cos(angle) * 0.8, player.getY() + 0.1, player.getZ() + Math.sin(angle) * 0.8,
                1, 0.0, 0.0, 0.0, 0.0);
        }
        level.playSound(null, player.getX(), player.getY(), player.getZ(),
            SoundEvents.BREEZE_WIND_CHARGE_BURST, SoundSource.PLAYERS, 1.0F, 1.0F);

        // 消耗：仅 1 点饥饿（效果不绑定武器，无耐久消耗；创造豁免）；进入 10 刻冷却
        if (!player.hasInfiniteMaterials()) {
            player.getFoodData().setFoodLevel(
                Math.max(0, player.getFoodData().getFoodLevel() - EFFECT_DASH_HUNGER_COST));
        }
        EFFECT_DASH_COOLDOWN_UNTIL.put(uuid, now + EFFECT_DASH_COOLDOWN_TICKS);
        if (DEBUG_DASH) {
            LOGGER.info("[FlyingEffectDash] dash executed for {} (sprint={}, ground={})",
                player.getName().getString(), player.isSprinting(), player.onGround());
        }
    }

    private static void reject(ServerPlayer player, String reason) {
        if (DEBUG_DASH) {
            LOGGER.info("[FlyingEffectDash] rejected for {}: {}", player.getName().getString(), reason);
        }
    }

    private static Holder<Enchantment> enchantmentHolder(ServerPlayer player, ResourceKey<Enchantment> key) {
        return player.level().registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(key);
    }
}