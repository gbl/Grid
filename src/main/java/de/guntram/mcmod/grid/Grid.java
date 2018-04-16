package de.guntram.mcmod.grid;

import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.Mod.EventHandler;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;

@Mod(modid = Grid.MODID, 
        version = Grid.VERSION,
	clientSideOnly = true, 
	guiFactory = "de.guntram.mcmod.grid.GuiFactory",
	acceptedMinecraftVersions = "[1.12]",
	updateJSON = "https://raw.githubusercontent.com/gbl/Grid/master/versioncheck.json"
)

public class Grid
{
    static final String MODID="grid";
    static final String VERSION="@VERSION@";
    @EventHandler
    public void init(FMLInitializationEvent event)
    {
        MinecraftForge.EVENT_BUS.register(this);
    }

    @EventHandler
    public void preInit(final FMLPreInitializationEvent event) {
        ConfigurationHandler confHandler = ConfigurationHandler.getInstance();
        confHandler.load(event.getSuggestedConfigurationFile());
        MinecraftForge.EVENT_BUS.register(confHandler);
    }
}
