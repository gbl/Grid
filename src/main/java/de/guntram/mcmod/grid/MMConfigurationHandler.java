package de.guntram.mcmod.grid;

import de.guntram.mcmod.grid.Grid;
import de.guntram.mcmod.fabrictools.ConfigurationProvider;
import de.guntram.mcmod.fabrictools.GuiModOptions;
import io.github.prospector.modmenu.api.ModMenuApi;
import java.util.Optional;
import java.util.function.Supplier;
import net.minecraft.client.gui.Screen;

public class MMConfigurationHandler implements ModMenuApi
{
    @Override
    public String getModId() {
        return Grid.MODID;
    }

    @Override
    public Optional<Supplier<Screen>> getConfigScreen(Screen screen) {
        return Optional.of(new GuiModOptions(screen, Grid.MODNAME, ConfigurationProvider.getHandler(Grid.MODNAME)));
    }
}
