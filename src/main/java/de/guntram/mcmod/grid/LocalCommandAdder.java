package de.guntram.mcmod.grid;

import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.server.command.CommandSource;

public interface LocalCommandAdder {
    void registerLocalCommands(CommandDispatcher<CommandSource> dispatcher);
}
