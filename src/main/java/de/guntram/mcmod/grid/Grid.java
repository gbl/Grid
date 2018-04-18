package de.guntram.mcmod.grid;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.client.resources.I18n;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommand;
import net.minecraft.command.ICommandSender;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentString;
import net.minecraftforge.client.ClientCommandHandler;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.client.registry.ClientRegistry;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.Mod.EventHandler;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.InputEvent;
import org.lwjgl.input.Keyboard;

@Mod(modid = Grid.MODID, 
        version = Grid.VERSION,
	clientSideOnly = true, 
	guiFactory = "de.guntram.mcmod.grid.GuiFactory",
	acceptedMinecraftVersions = "[1.12]",
	updateJSON = "https://raw.githubusercontent.com/gbl/Grid/master/versioncheck.json"
)

public class Grid implements ICommand
{
    static final String MODID="grid";
    static final String VERSION="@VERSION@";
    
    private int gridX=16;
    private int gridZ=16;
    private int fixY=-1;
    private int offsetX=0;
    private int offsetZ=0;
    private int distance=30;
    private boolean visible=false;
    private boolean isBlocks=true;

    KeyBinding showHide, gridHere, gridFixY;

    
    private boolean dump;
    private long lastDumpTime, thisDumpTime;
    
    @EventHandler
    public void init(FMLInitializationEvent event)
    {
        MinecraftForge.EVENT_BUS.register(this);
        ClientCommandHandler.instance.registerCommand(this);
        ClientRegistry.registerKeyBinding(showHide = new KeyBinding("key.showhide", Keyboard.KEY_B, "key.categories.grid"));
        ClientRegistry.registerKeyBinding(gridHere = new KeyBinding("key.gridhere", Keyboard.KEY_H, "key.categories.grid"));
        ClientRegistry.registerKeyBinding(gridFixY = new KeyBinding("key.gridfixy", Keyboard.KEY_Y, "key.categories.grid"));
    }

    @EventHandler
    public void preInit(final FMLPreInitializationEvent event) {
        ConfigurationHandler confHandler = ConfigurationHandler.getInstance();
        confHandler.load(event.getSuggestedConfigurationFile());
        MinecraftForge.EVENT_BUS.register(confHandler);
    }
    
    @SubscribeEvent(priority = EventPriority.NORMAL)
    public void onRender(final RenderWorldLastEvent event) {
        if (!visible)
            return;

        float partialTicks = event.getPartialTicks();
        EntityPlayerSP entityplayer = Minecraft.getMinecraft().player;
        double cameraX = entityplayer.lastTickPosX + (entityplayer.posX - entityplayer.lastTickPosX) * (double)partialTicks;
        double cameraY = entityplayer.lastTickPosY + (entityplayer.posY - entityplayer.lastTickPosY) * (double)partialTicks;
        double cameraZ = entityplayer.lastTickPosZ + (entityplayer.posZ - entityplayer.lastTickPosZ) * (double)partialTicks;        

        GlStateManager.disableTexture2D();
        GlStateManager.disableBlend();
        GlStateManager.glLineWidth(1.0f);

        Tessellator tessellator=Tessellator.getInstance();
        BufferBuilder bufferBuilder = tessellator.getBuffer();
        bufferBuilder.setTranslation(-cameraX, -cameraY, -cameraZ);
        bufferBuilder.begin(3, DefaultVertexFormats.POSITION_COLOR);
        
        int playerX=(int) Math.floor(entityplayer.posX);
        int playerZ=(int) Math.floor(entityplayer.posZ);
        int playerXShift=Math.floorMod(playerX, gridX);
        int playerZShift=Math.floorMod(playerZ, gridZ);
        int baseX=playerX-playerXShift;
        int baseZ=playerZ-playerZShift;
        int sizeX=(distance/gridX)*gridX;
        int sizeZ=(distance/gridZ)*gridZ;
        float y=((float)(fixY==-1 ? entityplayer.posY : fixY)+0.05f);

        thisDumpTime=System.currentTimeMillis();
        dump=false;
        //if (thisDumpTime > lastDumpTime + 50000) {
            //dump=true;
            //lastDumpTime=thisDumpTime;
        //}
        
        if (isBlocks) {
            GlStateManager.glLineWidth(3.0f);
            for (int x=baseX-sizeX; x<=baseX+sizeX; x+=gridX) {
                for (int z=baseZ-sizeZ; z<=baseZ+sizeZ; z+=gridZ) {
                    line (bufferBuilder, x+0.3f, x+0.7f, y, y, z+0.3f, z+0.3f, 0.0f, 0.5f, 1.0f);
                    line (bufferBuilder, x+0.7f, x+0.7f, y, y, z+0.3f, z+0.7f, 0.0f, 0.5f, 1.0f);
                    line (bufferBuilder, x+0.7f, x+0.3f, y, y, z+0.7f, z+0.7f, 0.0f, 0.5f, 1.0f);
                    line (bufferBuilder, x+0.3f, x+0.3f, y, y, z+0.7f, z+0.3f, 0.0f, 0.5f, 1.0f);
                }
            }
        } else {
            for (int x=baseX-sizeX; x<=baseX+sizeX; x+=gridX) {
                line(bufferBuilder, x, x, y, y, baseZ-distance, baseZ+distance, 1.0f, 0.5f, 0.0f);
            }
            for (int z=baseZ-sizeZ; z<=baseZ+sizeZ; z+=gridZ) {
                line(bufferBuilder, baseX-distance, baseX+distance, y, y, z, z, 1.0f, 0.5f, 0.0f);
            }
        }
        
//        Sphere sphere=new Sphere();
//        sphere.draw(1.0f, 10, 10);
        
        tessellator.draw();
        bufferBuilder.setTranslation(0, 0, 0);
        
        GlStateManager.glLineWidth(1.0f);
        GlStateManager.enableBlend();
        GlStateManager.enableTexture2D();
    }
    
    private void line(BufferBuilder b, float x1, float x2, float y1, float y2, float z1, float z2, float red, float green, float blue) {
        if (dump) {
            System.out.println("line from "+x1+","+y1+","+z1+" to "+x2+","+y2+","+z2);
        }
        b.pos(x1+offsetX, y1, z1+offsetZ).color(red, green, blue, 0.0f).endVertex();
        b.pos(x1+offsetX, y1, z1+offsetZ).color(red, green, blue, 1.0f).endVertex();
        b.pos(x2+offsetX, y2, z2+offsetZ).color(red, green, blue, 1.0f).endVertex();
        b.pos(x2+offsetX, y2, z2+offsetZ).color(red, green, blue, 0.0f).endVertex();
    }
    
    @SubscribeEvent
    public void keyPressed(final InputEvent.KeyInputEvent e) {
        EntityPlayerSP player = Minecraft.getMinecraft().player;
        if (showHide.isPressed()) {
            this.execute(null, player, new String[0]);
        }
        if (gridFixY.isPressed()) {
            this.execute(null, player, new String[]{"fixy"});
        }
        if (gridHere.isPressed()) {
            this.execute(null, player, new String[]{"here"});
        }
    }
    

    @Override
    public String getName() {
        return "grid";
    }

    @Override
    public String getUsage(ICommandSender sender) {
        return "/grid <size> | /grid <xsize> <ysize> | /grid show | /grid hide | /grid lines | /grid blocks | /grid here | /grid fixy | /grid chunk";
    }

    @Override
    public List<String> getAliases() {
        return new ArrayList<>();
    }

    @Override
    public void execute(MinecraftServer server, ICommandSender sender, String[] args) {
        if (args.length==0) {
            visible=!visible;
        } else {
            if (args[0].equals("show")) {
                visible = true;
                sender.sendMessage(new TextComponentString(I18n.format("msg.gridshown", (Object[]) null)));
            }
            else if (args[0].equals("hide")) {
                visible = false;
                sender.sendMessage(new TextComponentString(I18n.format("msg.gridhidden", (Object[]) null)));
            }
            else if (args[0].equals("lines")) { 
                visible = true; isBlocks = false;
                sender.sendMessage(new TextComponentString(I18n.format("msg.gridlines", (Object[]) null)));
            }
            else if (args[0].equals("blocks")) {
                visible = true; isBlocks = true;
                sender.sendMessage(new TextComponentString(I18n.format("msg.gridblocks", (Object[]) null)));
            }
            else if (args[0].equals("here")) {
                EntityPlayerSP entityplayer = Minecraft.getMinecraft().player;
                int playerX=(int) Math.floor(entityplayer.posX);
                int playerZ=(int) Math.floor(entityplayer.posZ);
                int playerXShift=Math.floorMod(playerX, gridX);
                int playerZShift=Math.floorMod(playerZ, gridZ);                
                offsetX=playerXShift;
                offsetZ=playerZShift;
                visible=true;
                sender.sendMessage(new TextComponentString(I18n.format("msg.gridaligned", (Object[]) null)));
            } else if (args[0].equals("fixy")) {
                if (fixY==-1) {
                    EntityPlayerSP entityplayer = Minecraft.getMinecraft().player;
                    fixY=(int) Math.floor(entityplayer.posY);
                    sender.sendMessage(new TextComponentString(I18n.format("msg.gridheightfixed", fixY)));
                } else {
                    fixY=-1;
                    sender.sendMessage(new TextComponentString(I18n.format("msg.gridheightfloat")));
                }
            } else if (args[0].equals("chunk")) {
                offsetX=offsetZ=0;
                gridX=gridZ=16;
                visible=true;
                sender.sendMessage(new TextComponentString(I18n.format("msg.gridchunks")));
            } else if (args[0].equals("distance")) {
                if (args.length==2) {
                    try {
                        distance=Integer.parseInt(args[1]);
                        sender.sendMessage(new TextComponentString(I18n.format("msg.griddistance", distance)));
                    } catch (NumberFormatException ex) {
                        sender.sendMessage(new TextComponentString(I18n.format("msg.badnumber")));
                    }
                } else {
                    sender.sendMessage(new TextComponentString(I18n.format("msg.griddistusage", distance)));
                }
                
            } else try {
                int newX=Integer.parseInt(args[0]);
                int newZ=newX;
                if (args.length>=2) {
                    newZ=Integer.parseInt(args[1]);
                }
                gridX=newX;
                gridZ=newZ;
                visible=true;
                sender.sendMessage(new TextComponentString(I18n.format("msg.gridpattern", gridX, gridZ)));
            } catch (NumberFormatException ex) {
                sender.sendMessage(new TextComponentString(I18n.format("msg.badnumber")));
            }
        }
    }

    @Override
    public boolean checkPermission(MinecraftServer server, ICommandSender sender) {
        return true;
    }

    @Override
    public List<String> getTabCompletions(MinecraftServer server, ICommandSender sender, String[] args, BlockPos targetPos) {
        return new ArrayList<String>() {
        };
    }

    @Override
    public boolean isUsernameIndex(String[] args, int index) {
        return false;
    }

    @Override
    public int compareTo(ICommand o) {
        return getName().compareTo(o.getName());
    }
}
