package de.guntram.mcmod.grid.mixin;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import de.guntram.mcmod.grid.Grid;
import net.minecraft.client.entity.EntityPlayerSP;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(EntityPlayerSP.class)
public class MixinLocalCommand {
    @Inject(method="sendChatMessage", at=@At("HEAD"), cancellable=true)
    private void handleLocalCommand(String message, CallbackInfo cir) {
        if (message.startsWith("/")) {
            try {
                Grid.instance.dispatchLocalCommand(message.substring(1));
                cir.cancel();
            } catch (CommandSyntaxException ex) {
                // Don't do anything, it wasn't intended to be our command
            }
        }
    }
}