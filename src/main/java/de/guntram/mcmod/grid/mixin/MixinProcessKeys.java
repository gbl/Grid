package de.guntram.mcmod.grid.mixin;

import de.guntram.mcmod.grid.Grid;
import net.minecraft.client.MinecraftClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MinecraftClient.class)
public class MixinProcessKeys {
    @Inject(method="method_1508", at=@At("HEAD"))
    public void onProcessKeybinds(CallbackInfo ci) {
        Grid.instance.processKeybinds();
    }
}
