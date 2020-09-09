package de.guntram.mcmod.grid.mixin;

import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.util.math.MatrixStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;


@Mixin(GameRenderer.class)
public abstract class MixinRenderWorldLast {

    @Inject(method="renderWorld(FJLnet/minecraft/client/util/math/MatrixStack;)V",
            at=@At(value="INVOKE_STRING",
                   target="Lnet/minecraft/util/profiler/Profiler;swap(Ljava/lang/String;)V",
                   args= { "ldc=hand" }
            )
    )
    
//    @Inject(method="renderWorld", at=@At("RETURN"))
    
    private void onRenderWorldLast(float partialTicks, long nanoTime, MatrixStack stack, CallbackInfo ci) {
        // Grid.instance.renderOverlay(partialTicks, stack);
    }
}
