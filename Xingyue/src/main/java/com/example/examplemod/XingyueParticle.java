package com.example.examplemod;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.SingleQuadParticle;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;

/**
 * 星月武器粒子：环绕（星月粒子2，小尺寸、缓慢上浮）与迸发（星月粒子1，大尺寸、径向飞散）
 * 共用实现，差异由生成时传入的速度、寿命与尺寸决定。全程渐隐、缩小。
 * 仅被客户端专属的 {@link ExampleModClient} 引用，服务端不会加载。
 */
public class XingyueParticle extends SingleQuadParticle {
    private final float initialQuadSize;

    public XingyueParticle(ClientLevel level, double x, double y, double z,
            double velocityX, double velocityY, double velocityZ,
            TextureAtlasSprite sprite, int lifetime, float quadSize) {
        super(level, x, y, z, velocityX, velocityY, velocityZ, sprite);
        this.initialQuadSize = quadSize;
        this.quadSize = quadSize;
        this.lifetime = lifetime;
        this.friction = 0.92F;
        this.gravity = 0.0F;
        this.hasPhysics = false;
    }

    @Override
    public SingleQuadParticle.Layer getLayer() {
        return SingleQuadParticle.Layer.TRANSLUCENT;
    }

    @Override
    public void tick() {
        super.tick();
        this.alpha = 1.0F - (float) this.age / this.lifetime;
    }

    @Override
    public float getQuadSize(float partialTick) {
        float ageFraction = (this.age + partialTick) / this.lifetime;
        return this.initialQuadSize * (1.0F - 0.5F * ageFraction);
    }
}
