package de.guntram.mcmod.grid;

import de.guntram.mcmod.fabrictools.ConfigChangedEvent;
import de.guntram.mcmod.fabrictools.Configuration;
import de.guntram.mcmod.fabrictools.ModConfigurationHandler;
import java.io.File;

public class ConfigurationHandler implements ModConfigurationHandler {

    private static ConfigurationHandler instance;

    private Configuration config;
    private String configFileName;

    public static ConfigurationHandler getInstance() {
        if (instance==null)
            instance=new ConfigurationHandler();
        return instance;
    }
    int blockColor, lineColor, circleColor, spawnNightColor, spawnDayColor, biomeColor;
    int cacheUpdateSeconds;

    public void load(final File configFile) {
        if (config == null) {
            config = new Configuration(configFile);
            configFileName=configFile.getPath();
            loadConfig();
        }
    }

    @Override
    public void onConfigChanged(ConfigChangedEvent.OnConfigChangedEvent event) {
        // System.out.println("OnConfigChanged for "+event.getModID());
        if (event.getModID().equalsIgnoreCase(Grid.MODID)) {
            loadConfig();
        }
    }
    
    private void loadConfig() {
        
        blockColor =        config.getInt("grid.config.blockcolor",     Configuration.CATEGORY_CLIENT, 0x8080ff, 0, 0xffffff, "grid.config.tt.blockcolor");
        lineColor =         config.getInt("grid.config.linecolor",      Configuration.CATEGORY_CLIENT, 0xff8000, 0, 0xffffff, "grid.config.tt.linecolor");
        circleColor =       config.getInt("grid.config.circlecolor",    Configuration.CATEGORY_CLIENT, 0x00e480, 0, 0xffffff, "grid.config.tt.circlecolor");
        spawnNightColor =   config.getInt("grid.config.spawnNightcolor",Configuration.CATEGORY_CLIENT, 0xffff00, 0, 0xffffff, "grid.config.tt.spawnNightcolor");
        spawnDayColor =     config.getInt("grid.config.spawnDaycolor",  Configuration.CATEGORY_CLIENT, 0xff0000, 0, 0xffffff, "grid.config.tt.spawnDaycolor");
        biomeColor =        config.getInt("grid.config.biomecolor",     Configuration.CATEGORY_CLIENT, 0xff00ff, 0, 0xffffff, "grid.config.tt.biomecolor");
        
        cacheUpdateSeconds= config.getInt("grid.config.cacheupdateseconds", Configuration.CATEGORY_CLIENT, 1, 0, 20, "grid.config.tt.cacheupdateseconds");
        
        
        if (config.hasChanged())
            config.save();
    }
    
    @Override
    public Configuration getConfig() {
        return config;
    }
    
    public static String getConfigFileName() {
        return getInstance().configFileName;
    }
    
    public static int getCacheUpdateSeconds() {
        return getInstance().cacheUpdateSeconds;
    }
}