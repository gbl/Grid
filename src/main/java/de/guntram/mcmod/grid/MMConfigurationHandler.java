package de.guntram.mcmod.grid;

import io.github.prospector.modmenu.api.ModMenuApi;

public class MMConfigurationHandler implements ModMenuApi
{
    @Override
    public String getModId() {
        return Grid.MODID;
    }
/*
    @Override
    public Optional<Supplier<Screen>> getConfigScreen(Screen screen) {
        return Optional.of(new GuiModOptions(screen, Grid.MODNAME, ConfigurationProvider.getHandler(Grid.MODNAME)));
    }
*/
}
