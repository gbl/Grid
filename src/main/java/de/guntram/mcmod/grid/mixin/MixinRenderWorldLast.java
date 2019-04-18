package de.guntram.mcmod.grid.mixin;

import de.guntram.mcmod.grid.Grid;
import net.minecraft.client.render.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;


@Mixin(GameRenderer.class)
public abstract class MixinRenderWorldLast {

    @Inject(method="renderCenter(FJ)V",
            at=@At(value="INVOKE_STRING",
                   target="Lnet/minecraft/util/profiler/Profiler;swap(Ljava/lang/String;)V",
                   args= { "ldc=hand" }
            )
    )
    
//    @Inject(method="renderWorld", at=@At("RETURN"))
    
    private void onRenderWorldLast(float partialTicks, long nanoTime, CallbackInfo ci) {
//    private void onRenderWorldLast(CallbackInfo ci) {
        Grid.instance.renderOverlay(partialTicks);
    }
}
