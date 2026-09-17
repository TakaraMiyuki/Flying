package com.example.examplemod;

import org.slf4j.Logger;

import com.mojang.logging.LogUtils;

import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Rarity;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.ModContainer;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

// The value here should match an entry in the META-INF/neoforge.mods.toml file
@Mod(ExampleMod.MODID)
public class ExampleMod {
    // Define mod id in a common place for everything to reference
    public static final String MODID = "examplemod";
    // Directly reference a slf4j logger
    public static final Logger LOGGER = LogUtils.getLogger();
    // Create a Deferred Register to hold Items which will all be registered under the "examplemod" namespace
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(MODID);

    // 星月粒子类型：xingyue_burst = 星月粒子1（迸发）、xingyue_orbit = 星月粒子2（环绕）
    // overrideLimiter=true：无视游戏"粒子细节"设置强制渲染，保证特效观感
    public static final DeferredRegister<ParticleType<?>> PARTICLE_TYPES = DeferredRegister.create(Registries.PARTICLE_TYPE, MODID);
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> XINGYUE_BURST =
        PARTICLE_TYPES.register("xingyue_burst", () -> new SimpleParticleType(true));
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> XINGYUE_ORBIT =
        PARTICLE_TYPES.register("xingyue_orbit", () -> new SimpleParticleType(true));

    // 星月：原创武器（剑），长按右键蓄力到 45 刻爆发（范围伤害+击退+跃起免摔），与盾牌共持时不可蓄力
    public static final DeferredItem<XingyueItem> XINGYUE = ITEMS.registerItem("xingyue",
        properties -> new XingyueItem(properties
            .sword(XingyueItem.MATERIAL, 3.0F, -2.4F)
            .rarity(Rarity.RARE)));

    // The constructor for the mod class is the first code that is run when your mod is loaded.
    // FML will recognize some parameter types like IEventBus or ModContainer and pass them in automatically.
    public ExampleMod(IEventBus modEventBus, ModContainer modContainer) {
        // Register the Deferred Register to the mod event bus so items get registered
        ITEMS.register(modEventBus);
        // Register the Deferred Register to the mod event bus so particle types get registered
        PARTICLE_TYPES.register(modEventBus);

        // Register the item to the combat creative tab
        modEventBus.addListener(this::addCreative);
    }

    // Add Xingyue to the combat tab
    private void addCreative(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.COMBAT) {
            event.accept(XINGYUE);
        }
    }
}