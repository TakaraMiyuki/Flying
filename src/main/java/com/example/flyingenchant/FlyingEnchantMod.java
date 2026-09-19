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
import net.minecraft.world.effect.MobEffectInstance;
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
 * 持有该效果的玩家在<b>疾跑状态下按跳跃键</b>即可向前上方突进，空中不限次数（每 10 刻冷却），
 * 突进时整体重置垂直速度（继承附魔版的"发动时重置下坠"特性）。
 * 等级（/effect 的 amplifier）：一级 ≈水平2格/上2格，二级 ≈水平5格/上3格（更高线性外推）；
 * 每次消耗 0.5 点饥饿（半点累积器，攒满 2 个半点扣 1 点）。疾跑标志由客户端随请求包上送
 * （服务端镜像会脱同步，不可信）。
 * <b>双持仲裁</b>：同时拥有附魔与效果时，跃起窗口内的请求优先走附魔通道（消耗其一次机会），
 * 附魔不可用后落到效果通道；风爆跃起重置附魔状态后附魔通道重新优先。
 * 效果通过 /effect give、数据包或其他模组施加，持续时长由施加者决定；本模组不主动施加。
 * 同样受 gamerule {@code flying_enchant:flying_dash} 管辖与饱食度门槛限制。
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
    /** 效果版突进水平初速：一级（amplifier 0）≈2 格（空中阻力 0.91/刻）。 */
    public static final double EFFECT_DASH_SPEED_BASE = 0.30;
    /** 效果版每级水平初速增量：二级（amplifier 1）≈5 格。 */
    public static final double EFFECT_DASH_SPEED_PER_LEVEL = 0.43;
    /** 效果版突进向上初速：一级 ≈2 格高（整体重置垂直速度，与附魔版同机制）。 */
    public static final double EFFECT_DASH_UP_BASE = 0.55;
    /** 效果版每级向上初速增量：二级 ≈3 格高。 */
    public static final double EFFECT_DASH_UP_PER_LEVEL = 0.17;
    /** 效果版每次突进消耗的饥饿值（半点）：0.5 点，攒满 2 个半点扣 1 点。 */
    public static final int EFFECT_DASH_HUNGER_HALF_COST = 1;
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
    /** 效果版饥饿扣费的半点累积器：每次 0.5 点，攒满 2 个半点扣 1 点（FoodData 只支持整数）。 */
    private static final Map<UUID, Integer> EFFECT_DASH_HUNGER_HALF = new HashMap<>();

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
        // 版本 2：包新增 sprinting 标志（1.x 旧客户端混装会被明确拒绝而非误读）
        PayloadRegistrar registrar = event.registrar("2");
        registrar.playToServer(FlyingDashPayload.TYPE, FlyingDashPayload.STREAM_CODEC,
            (payload, context) -> handleDashRequest(context.player(), payload.sprinting()));
    }

    // 游戏总线：除玩家以外的所有生物免疫飞翔效果
    static void onEffectApplicable(MobEffectEvent.Applicable event) {
        if (!(event.getEntity() instanceof Player)
            && event.getEffectInstance().getEffect().is(FLYING_EFFECT)) {
            event.setResult(MobEffectEvent.Applicable.Result.DO_NOT_APPLY);
        }
    }

    /**
     * 软依赖 API：供其他模组（如星月）显式宣告"该玩家刚完成一次跃起"，直接开放其突进窗口
     * （窗口持续到落地，与速度反转检测的窗口语义一致，覆盖整个跃起过程）。
     * 速度反转检测依赖每刻采样的先后顺序，对一些发射时机（如蓄力释放瞬间玩家正在上升）
     * 不可靠——由发射方主动宣告则 deterministically 开窗。
     * 调用方经反射查找本类（{@code Class.forName}），未安装飞翔时调用方静默跳过，
     * 不构成编译依赖。
     */
    public static void notifyLaunch(Player player) {
        if (player.level() instanceof ServerLevel) {
            UUID uuid = player.getUUID();
            LAUNCHED.add(uuid);
            DASH_USED.remove(uuid);
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
        EFFECT_DASH_HUNGER_HALF.remove(uuid);
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

    // 服务端：统一突进仲裁（附魔优先 → 效果）。全量校验均在服务端，伪造发包无法绕过限制。
    // 效果通道的疾跑门槛采用客户端上送的标志：服务端的疾跑镜像会经 moved-wrongly 回退/碰撞
    // 脱同步，不可信；伪造该标志仅能绕过"疾跑"这一体验性门槛，安全门仍全部在服务端强制。
    private static void handleDashRequest(Player basePlayer, boolean clientSprinting) {
        if (!(basePlayer instanceof ServerPlayer player) || !(player.level() instanceof ServerLevel level)) {
            return;
        }
        UUID uuid = player.getUUID();
        if (!level.getGameRules().get(FLYING_DASH_ENABLED.value())) {
            reject(player, "gamerule flying_dash disabled");
            return;
        }
        if (!player.hasInfiniteMaterials() && player.getFoodData().getFoodLevel() <= SPRINT_FOOD_THRESHOLD) {
            reject(player, "food below sprint threshold");
            return;
        }

        // 优先级 1：附魔通道——跃起窗口内、本次跃起未用、主手为适用物品且已附魔飞翔
        if (!player.onGround() && LAUNCHED.contains(uuid) && !DASH_USED.contains(uuid)) {
            ItemStack weapon = player.getMainHandItem();
            int flyingLevel = weapon.getEnchantmentLevel(enchantmentHolder(player, FLYING_KEY));
            if (flyingLevel > 0 && weapon.is(FLYING_ENCHANTABLE)) {
                dashByEnchant(player, level, uuid, weapon, flyingLevel);
                return;
            }
            if (DEBUG_DASH) {
                LOGGER.info("[FlyingDash] enchant channel skipped for {}: weapon={} flyingLevel={} inTag={}",
                    player.getName().getString(), weapon.getItem(), flyingLevel, weapon.is(FLYING_ENCHANTABLE));
            }
        } else if (DEBUG_DASH) {
            // 落到效果通道前的诊断：说明附魔通道为何未接管（窗口未开/本次跃起已用）
            LOGGER.info("[FlyingDash] enchant channel not taken for {}: onGround={} launched={} dashUsed={}",
                player.getName().getString(), player.onGround(), LAUNCHED.contains(uuid), DASH_USED.contains(uuid));
        }

        // 优先级 2：效果通道——疾跑（客户端标志）+ 持有效果 + 10 刻冷却；空中不限次数
        if (!clientSprinting) {
            reject(player, "not sprinting (client flag)");
            return;
        }
        if (!player.hasEffect(FLYING_EFFECT)) {
            reject(player, "no flying effect");
            return;
        }
        long now = level.getGameTime();
        Long until = EFFECT_DASH_COOLDOWN_UNTIL.get(uuid);
        if (until != null && now < until) {
            reject(player, "cooldown (" + (until - now) + " ticks left)");
            return;
        }
        dashByEffect(player, level, uuid, now);
    }

    // 附魔通道：等级速度前上方突进；垂直分量取"当前速度与突进上抬的较大值"——
    // 下坠时重置为上抬（下坠负速度不得抵消突进），上升时不截断跃起弧线
    // （蓄力跃起/风爆弹跳的上升段发动飞翔，跳跃高度不受损）。消耗 1 耐久 + 2 饥饿；本次跃起标记已用
    private static void dashByEnchant(ServerPlayer player, ServerLevel level, UUID uuid, ItemStack weapon, int flyingLevel) {
        Vec3 horizontal = horizontalLook(player);
        double speed = DASH_SPEED_BASE + DASH_SPEED_PER_LEVEL * (flyingLevel - 1);
        double up = Math.max(player.getDeltaMovement().y, DASH_UP_BASE + DASH_UP_PER_LEVEL * (flyingLevel - 1));
        player.setDeltaMovement(player.getDeltaMovement().add(horizontal.scale(speed)).with(Direction.Axis.Y, up));
        player.applyPostImpulseGraceTime(10);
        player.connection.send(new ClientboundSetEntityMotionPacket(player));
        dashFX(level, player);
        weapon.hurtAndBreak(1, player, InteractionHand.MAIN_HAND);
        if (!player.hasInfiniteMaterials()) {
            player.getFoodData().setFoodLevel(Math.max(0, player.getFoodData().getFoodLevel() - DASH_HUNGER_COST));
        }
        DASH_USED.add(uuid);
        if (DEBUG_DASH) {
            LOGGER.info("[FlyingDash] enchant dash lv{} for {}", flyingLevel, player.getName().getString());
        }
    }

    // 效果通道：固定速度前上方突进；垂直分量同附魔通道取较大值（下坠重置、上升不截断）；
    // 消耗 0.5 饥饿（半点累积器）；10 刻冷却。等级（amplifier）缩放突进距离：
    // 一级 ≈水平2格/上2格，二级 ≈水平5格/上3格，更高等级线性外推。
    private static void dashByEffect(ServerPlayer player, ServerLevel level, UUID uuid, long now) {
        MobEffectInstance effect = player.getEffect(FLYING_EFFECT);
        int amplifier = effect == null ? 0 : effect.getAmplifier();
        double speed = EFFECT_DASH_SPEED_BASE + EFFECT_DASH_SPEED_PER_LEVEL * amplifier;
        double up = Math.max(player.getDeltaMovement().y, EFFECT_DASH_UP_BASE + EFFECT_DASH_UP_PER_LEVEL * amplifier);
        Vec3 horizontal = horizontalLook(player);
        player.setDeltaMovement(player.getDeltaMovement().add(horizontal.scale(speed))
            .with(Direction.Axis.Y, up));
        // 宽限须覆盖整个飞行段（二级上3格+下落 ≈20 刻）直至落地，否则宽限外的滞空段会
        // 触发服务端 moved-wrongly 校验（残差 >0.25 格）→ 瞬移回退 + 双方速度清零
        player.applyPostImpulseGraceTime(25);
        player.connection.send(new ClientboundSetEntityMotionPacket(player));
        dashFX(level, player);
        if (!player.hasInfiniteMaterials()) {
            int halves = EFFECT_DASH_HUNGER_HALF.merge(uuid, EFFECT_DASH_HUNGER_HALF_COST, Integer::sum);
            while (halves >= 2) {
                halves -= 2;
                player.getFoodData().setFoodLevel(Math.max(0, player.getFoodData().getFoodLevel() - 1));
            }
            EFFECT_DASH_HUNGER_HALF.put(uuid, halves);
        }
        EFFECT_DASH_COOLDOWN_UNTIL.put(uuid, now + EFFECT_DASH_COOLDOWN_TICKS);
        if (DEBUG_DASH) {
            LOGGER.info("[FlyingDash] effect dash amp{} for {} (speed={}, up={}, sprint={}, ground={})",
                amplifier, player.getName().getString(), speed, up, player.isSprinting(), player.onGround());
        }
    }

    // 视线水平归一化方向（俯视不减距；完全垂直时退化为面朝方向）
    private static Vec3 horizontalLook(ServerPlayer player) {
        Vec3 look = player.getLookAngle();
        Vec3 horizontal = new Vec3(look.x, 0.0, look.z);
        return horizontal.lengthSqr() > 1.0E-4
            ? horizontal.normalize()
            : Vec3.directionFromRotation(0.0F, player.getYRot());
    }

    // 特效：脚下少量白色风爆粒子 + 风爆音效
    private static void dashFX(ServerLevel level, ServerPlayer player) {
        for (int i = 0; i < 6; i++) {
            double angle = i * (Math.PI * 2 / 6.0);
            level.sendParticles(ParticleTypes.SMALL_GUST,
                player.getX() + Math.cos(angle) * 0.8, player.getY() + 0.1, player.getZ() + Math.sin(angle) * 0.8,
                1, 0.0, 0.0, 0.0, 0.0);
        }
        level.playSound(null, player.getX(), player.getY(), player.getZ(),
            SoundEvents.BREEZE_WIND_CHARGE_BURST, SoundSource.PLAYERS, 1.0F, 1.0F);
    }

    private static void reject(ServerPlayer player, String reason) {
        if (DEBUG_DASH) {
            LOGGER.info("[FlyingDash] rejected for {}: {}", player.getName().getString(), reason);
        }
    }

    private static Holder<Enchantment> enchantmentHolder(ServerPlayer player, ResourceKey<Enchantment> key) {
        return player.level().registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(key);
    }
}