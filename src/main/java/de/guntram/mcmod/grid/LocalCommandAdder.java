package org.dimdev.rift.listener.client;

import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.server.command.CommandSource;

public interface LocalCommandAdder {
    void registerLocalCommands(CommandDispatcher<CommandSource> dispatcher);
}
