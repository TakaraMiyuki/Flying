package com.example.examplemod;

import net.minecraft.client.Minecraft;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterParticleProvidersEvent;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;

// This class will not load on dedicated servers. Accessing client side code from here is safe.
@Mod(value = ExampleMod.MODID, dist = Dist.CLIENT)
// You can use EventBusSubscriber to automatically register all static methods in the class annotated with @SubscribeEvent
@EventBusSubscriber(modid = ExampleMod.MODID, value = Dist.CLIENT)
public class ExampleModClient {
    public ExampleModClient(ModContainer container) {
        // Allows NeoForge to create a config screen for this mod's configs.
        // The config screen is accessed by going to the Mods screen > clicking on your mod > clicking on config.
        // Do not forget to add translations for your config options to the en_us.json file.
        container.registerExtensionPoint(IConfigScreenFactory.class, ConfigurationScreen::new);
    }

    @SubscribeEvent
    static void onClientSetup(FMLClientSetupEvent event) {
        // Some client setup code
        ExampleMod.LOGGER.info("HELLO FROM CLIENT SETUP");
        ExampleMod.LOGGER.info("MINECRAFT NAME >> {}", Minecraft.getInstance().getUser().getName());
    }

    @SubscribeEvent
    static void onRegisterParticles(RegisterParticleProvidersEvent event) {
        // 星月粒子2（环绕）：小光点，缓慢上浮、渐隐；数量与分布的“逐渐增多”由生成端按蓄力进度控制
        event.registerSpriteSet(ExampleMod.XINGYUE_ORBIT.get(), sprites ->
            (type, level, x, y, z, velocityX, velocityY, velocityZ, random) ->
                new XingyueParticle(level, x, y, z, velocityX, velocityY, velocityZ,
                    sprites.get(random), 8 + random.nextInt(6), 0.10F + random.nextFloat() * 0.08F));
        // 星月粒子1（迸发）：四芒星光斑，大尺寸、径向高速飞散、渐隐
        event.registerSpriteSet(ExampleMod.XINGYUE_BURST.get(), sprites ->
            (type, level, x, y, z, velocityX, velocityY, velocityZ, random) ->
                new XingyueParticle(level, x, y, z, velocityX, velocityY, velocityZ,
                    sprites.get(random), 14 + random.nextInt(9), 0.30F + random.nextFloat() * 0.25F));
    }

    // 飞翔附魔：腾空中按跳跃键 → 请求服务端突进。
    // 这里只做最便宜的客户端预筛（腾空 + 主手是星月/重锤）；弹起窗口、附魔等级、
    // 饱食度、每次腾空一次等全部条件由服务端校验（弹起状态不同步到客户端，客户端无法预判）。
    @SubscribeEvent
    static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        var player = mc.player;
        if (player == null || mc.gui.screen() != null || player.isSpectator()) {
            return;
        }
        ItemStack mainHand = player.getMainHandItem();
        if (!(mainHand.getItem() instanceof XingyueItem) && !mainHand.is(Items.MACE)) {
            return;
        }
        while (mc.options.keyJump.consumeClick()) {
            if (!player.onGround()) {
                ClientPacketDistributor.sendToServer(XingyueDashPayload.INSTANCE);
            }
        }
    }
}
