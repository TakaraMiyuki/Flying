package com.example.examplemod;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.RegisterParticleProvidersEvent;

// This class will not load on dedicated servers. Accessing client side code from here is safe.
@Mod(value = ExampleMod.MODID, dist = Dist.CLIENT)
// You can use EventBusSubscriber to automatically register all static methods in the class annotated with @SubscribeEvent
@EventBusSubscriber(modid = ExampleMod.MODID, value = Dist.CLIENT)
public class ExampleModClient {

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
}