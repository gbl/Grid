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
    boolean useCache;

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

    @Override
    public void onConfigChanging(ConfigChangedEvent.OnConfigChangingEvent event) {
        switch (event.getItem()) {
            case "grid.config.colorblock"      : blockColor         = (int) event.getNewValue(); break;
            case "grid.config.colorline"       : lineColor          = (int) event.getNewValue(); break;
            case "grid.config.colorcircle"     : circleColor        = (int) event.getNewValue(); break;
            case "grid.config.colorspawnnight" : spawnNightColor    = (int) event.getNewValue(); break;
            case "grid.config.colorspawnday"   : spawnDayColor      = (int) event.getNewValue(); break;
            case "grid.config.colorbiome"      : biomeColor         = (int) event.getNewValue(); break;
        }
    }
    
    private void loadConfig() {
        
        config.forget("grid.config.blockcolor");
        config.forget("grid.config.linecolor");
        config.forget("grid.config.circlecolor");
        config.forget("grid.config.spawnNightcolor");
        config.forget("grid.config.spawnDaycolor");
        config.forget("grid.config.biomecolor");
        config.forget("grid.config.cacheupdateseconds");
        
        blockColor =        config.getRGB("grid.config.colorblock",     Configuration.CATEGORY_CLIENT, 0x8080ff, "grid.config.tt.colorblock");
        lineColor =         config.getRGB("grid.config.colorline",      Configuration.CATEGORY_CLIENT, 0xff8000, "grid.config.tt.colorline");
        circleColor =       config.getRGB("grid.config.colorcircle",    Configuration.CATEGORY_CLIENT, 0x00e480, "grid.config.tt.colorcircle");
        spawnNightColor =   config.getRGB("grid.config.colorspawnnight",Configuration.CATEGORY_CLIENT, 0xffff00, "grid.config.tt.colorspawnnight");
        spawnDayColor =     config.getRGB("grid.config.colorspawnday",  Configuration.CATEGORY_CLIENT, 0xff0000, "grid.config.tt.colorspawnday");
        biomeColor =        config.getRGB("grid.config.colorbiome",     Configuration.CATEGORY_CLIENT, 0xff00ff, "grid.config.tt.colorbiome");
        
        useCache = config.getBoolean("grid.config.usecache", Configuration.CATEGORY_CLIENT, true, "grid.config.tt.usecache");
        
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
    
    public static boolean getUseCache() {
        return getInstance().useCache;
    }
}