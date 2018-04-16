package de.guntram.mcmod.grid;

import net.minecraft.client.gui.GuiScreen;
import net.minecraftforge.common.config.ConfigElement;
import static net.minecraftforge.common.config.Configuration.CATEGORY_CLIENT;

public class GuiConfig extends net.minecraftforge.fml.client.config.GuiConfig {
    public GuiConfig(GuiScreen parent) {
        super(parent,
                new ConfigElement(ConfigurationHandler.getConfig().getCategory(CATEGORY_CLIENT)).getChildElements(),
                Grid.MODID,
                false,
                false,
                "Grid config");
    }
}
