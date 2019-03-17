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
import net.minecraft.command.ICommand;
import net.minecraft.command.ICommandSender;
import net.minecraft.entity.EntityLiving;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.world.EnumSkyBlock;
import net.minecraft.world.WorldEntitySpawner;
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
    private int lightLevel=8;
    private boolean visible=false;
    private boolean isBlocks=true;
    private boolean isCircles=false;
    private boolean showSpawns=false;

    KeyBinding showHide, gridHere, gridFixY, gridSpawns;

    
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
        ClientRegistry.registerKeyBinding(gridSpawns = new KeyBinding("key.gridspawns", Keyboard.KEY_L, "key.categories.grid"));
    }

    @EventHandler
    public void preInit(final FMLPreInitializationEvent event) {
        ConfigurationHandler confHandler = ConfigurationHandler.getInstance();
        confHandler.load(event.getSuggestedConfigurationFile());
        MinecraftForge.EVENT_BUS.register(confHandler);
    }
    
    @SubscribeEvent(priority = EventPriority.NORMAL)
    public void onRender(final RenderWorldLastEvent event) {
        if (!visible && !showSpawns)
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
        if (playerXShift > gridX/2) { playerXShift -= gridX; }
        if (playerZShift > gridZ/2) { playerZShift -= gridZ; }
        int baseX=playerX-playerXShift;
        int baseZ=playerZ-playerZShift;
        int sizeX=(distance/gridX)*gridX;
        int sizeZ=(distance/gridZ)*gridZ;

        thisDumpTime=System.currentTimeMillis();
        dump=false;
        if (thisDumpTime > lastDumpTime + 50000) {
            dump=false;         // set this to true to get line info from time to time
            lastDumpTime=thisDumpTime;
        }
        
        if (visible) {
            float y=((float)(fixY==-1 ? entityplayer.posY : fixY)+0.05f);
            int circRadSquare=(gridX/2)*(gridX/2);
            if (isBlocks) {
                GlStateManager.glLineWidth(3.0f);
                for (int x=baseX-sizeX; x<=baseX+sizeX; x+=gridX) {
                    for (int z=baseZ-sizeZ; z<=baseZ+sizeZ; z+=gridZ) {
                        line (bufferBuilder, x+0.3f, x+0.7f, y, y, z+0.3f, z+0.3f, 0.0f, 0.5f, 1.0f);
                        line (bufferBuilder, x+0.7f, x+0.7f, y, y, z+0.3f, z+0.7f, 0.0f, 0.5f, 1.0f);
                        line (bufferBuilder, x+0.7f, x+0.3f, y, y, z+0.7f, z+0.7f, 0.0f, 0.5f, 1.0f);
                        line (bufferBuilder, x+0.3f, x+0.3f, y, y, z+0.7f, z+0.3f, 0.0f, 0.5f, 1.0f);
                        
                        if (isCircles) {
                            int dx=0;
                            int dz=gridX/2;
                            for (;;) {
                                int nextx=dx+1;
                                int nextz=dz;
                                int toomuch=(nextx*nextx)+(nextz*nextz)-circRadSquare;
                                if (nextz>0 && (nextz-1)*(nextz-1)+(nextx*nextx)>circRadSquare-toomuch)
                                    nextz--;
                                if (nextz<nextx) {
                                    circleSegment(bufferBuilder, x, dx, dz, y, z, dz, dx, 0.0f, 0.9f, 0.5f);
                                    break;
                                }

                                if (dump) {
                                    System.out.println("circle line from "+dx+"/"+dz+" to "+nextx+"/"+nextz+
                                            ", (x/2)^2="+((gridX/2.0)*(gridX/2.0))+
                                            ", dist is "+(nextx*nextx+nextz*nextz)+
                                            ", one higher dist is "+(nextx*nextx+((nextz+1)*(nextz+1)))+
                                            ", one lower dist is "+(nextx*nextx+((nextz-1)*(nextz-1)))
                                            );
                                }
                                circleSegment(bufferBuilder, x, dx, nextx, y, z, dz, nextz, 0.0f, 0.9f, 0.5f);
                                dx=nextx;
                                dz=nextz;
                            }
                        }
                        dump=false;
                    }
                }
            } else {
                if (!isCircles) {
                    for (int x=baseX-sizeX; x<=baseX+sizeX; x+=gridX) {
                        line(bufferBuilder, x, x, y, y, baseZ-distance, baseZ+distance, 1.0f, 0.5f, 0.0f);
                    }
                    for (int z=baseZ-sizeZ; z<=baseZ+sizeZ; z+=gridZ) {
                        line(bufferBuilder, baseX-distance, baseX+distance, y, y, z, z, 1.0f, 0.5f, 0.0f);
                    }
                } else {
                    for (int x=baseX-sizeX; x<=baseX+sizeX; x+=gridX) {
                        for (int z=baseZ-sizeZ; z<=baseZ+sizeZ; z+=gridZ) {
                            float dx=0;
                            float dz=gridX/2.0f;
                            for (float nextx=0.1f; nextx<gridX; nextx+=0.1f) {
                                float nextz=(float)(Math.sqrt(gridX*gridX/4.0-nextx*nextx));
                                circleSegment(bufferBuilder, x, dx, nextx, y, z, dz, nextz, 0.0f, 0.9f, 0.5f);
                                dx=nextx;
                                dz=nextz;
                                if (nextz<nextx)
                                    break;
                            }
                            dump=false;
                        }   
                    }
                }
            }
        }
        
        if (showSpawns) {
            GlStateManager.glLineWidth(2.0f);
            int miny=(int)(entityplayer.posY)-16;
            int maxy=(int)(entityplayer.posY)+2;
            if (miny<0) { miny=0; }
            if (maxy>255) { maxy=255; }
            for (int x=playerX-distance; x<=playerX+distance; x++) {
                for (int z=playerZ-distance; z<=playerZ+distance; z++) {
                    for (int y=miny; y<=maxy; y++) {
                        BlockPos pos=new BlockPos(x, y, z);
                        int spawnmode;
                        if (WorldEntitySpawner.canCreatureTypeSpawnAtLocation(EntityLiving.SpawnPlacementType.ON_GROUND, entityplayer.world, pos)) {
                            if (entityplayer.world.getLightFor(EnumSkyBlock.BLOCK, pos)>=lightLevel)
                                continue;
                            else if (entityplayer.world.getLightFor(EnumSkyBlock.SKY, pos)>=lightLevel)
                                cross(bufferBuilder, x, y, z, 1.0f, 1.0f, 0.0f );
                            else
                                cross(bufferBuilder, x, y, z, 1.0f, 0.0f, 0.0f );
                        }
                    }
                }
            }
        }
        
        tessellator.draw();
        bufferBuilder.setTranslation(0, 0, 0);
        
        GlStateManager.glLineWidth(1.0f);
        GlStateManager.enableBlend();
        GlStateManager.enableTexture2D();
    }
    
    private void circleSegment(BufferBuilder b, float xc, float x1, float x2, float y, float zc, float z1, float z2, float red, float green, float blue) {
        line(b, xc+x1+0.5f, xc+x2+0.5f, y, y, zc+z1+0.5f, zc+z2+0.5f, red, green, blue);
        line(b, xc-x1+0.5f, xc-x2+0.5f, y, y, zc+z1+0.5f, zc+z2+0.5f, red, green, blue);
        line(b, xc+x1+0.5f, xc+x2+0.5f, y, y, zc-z1+0.5f, zc-z2+0.5f, red, green, blue);
        line(b, xc-x1+0.5f, xc-x2+0.5f, y, y, zc-z1+0.5f, zc-z2+0.5f, red, green, blue);
        line(b, xc+z1+0.5f, xc+z2+0.5f, y, y, zc+x1+0.5f, zc+x2+0.5f, red, green, blue);
        line(b, xc-z1+0.5f, xc-z2+0.5f, y, y, zc+x1+0.5f, zc+x2+0.5f, red, green, blue);
        line(b, xc+z1+0.5f, xc+z2+0.5f, y, y, zc-x1+0.5f, zc-x2+0.5f, red, green, blue);
        line(b, xc-z1+0.5f, xc-z2+0.5f, y, y, zc-x1+0.5f, zc-x2+0.5f, red, green, blue);
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
    
	    private void cross(BufferBuilder b, int x, int y, int z, float red, float green, float blue) {
        b.pos(x+0.3f, y+0.05f, z+0.3f).color(red, green, blue, 0.0f).endVertex();
        b.pos(x+0.3f, y+0.05f, z+0.3f).color(red, green, blue, 1.0f).endVertex();
        b.pos(x+0.7f, y+0.05f, z+0.7f).color(red, green, blue, 1.0f).endVertex();
        b.pos(x+0.7f, y+0.05f, z+0.7f).color(red, green, blue, 0.0f).endVertex();
	}
        
    private void cmdShow(EntityPlayerSP sender) {
        visible = true;
        sender.sendMessage(new TextComponentString(I18n.format("msg.gridshown", (Object[]) null)));
    }
    
    private void cmdHide(EntityPlayerSP sender) {
        visible = false;
        sender.sendMessage(new TextComponentString(I18n.format("msg.gridhidden", (Object[]) null)));
    }
    
    private void cmdSpawns(EntityPlayerSP sender, String newLevel) {
        int level=8;
        try {
            level=Integer.parseInt(newLevel);
        } catch (NumberFormatException | NullPointerException ex) {
            ;
        }
        if (level<=0 || level>15)
            level=8;
        this.lightLevel=level;
        if (showSpawns && newLevel==null) {
            sender.sendMessage(new TextComponentString(I18n.format("msg.spawnshidden")));
            showSpawns=false;
        } else {
            sender.sendMessage(new TextComponentString(I18n.format("msg.spawnsshown", level)));
            showSpawns=true;
        }
    }
    
    private void cmdLines(EntityPlayerSP sender) {
        visible = true; isBlocks = false;
        sender.sendMessage(new TextComponentString(I18n.format("msg.gridlines", (Object[]) null)));
    }
    
    private void cmdBlocks(EntityPlayerSP sender) {
        visible = true; isBlocks = true;
        sender.sendMessage(new TextComponentString(I18n.format("msg.gridblocks", (Object[]) null)));
    }
    
    private void cmdCircles(EntityPlayerSP sender) {
        if (isCircles) {
            isCircles = false;
            sender.sendMessage(new TextComponentString(I18n.format("msg.gridnomorecircles", (Object[]) null)));
        } else {
            isCircles = true;
            visible = true;
            sender.sendMessage(new TextComponentString(I18n.format("msg.gridcircles", (Object[]) null)));
        }
    }
    
    private void cmdHere(EntityPlayerSP sender) {
        int playerX=(int) Math.floor(sender.posX);
        int playerZ=(int) Math.floor(sender.posZ);
        int playerXShift=Math.floorMod(playerX, gridX);
        int playerZShift=Math.floorMod(playerZ, gridZ);                
        offsetX=playerXShift;
        offsetZ=playerZShift;
        visible=true;
        sender.sendMessage(new TextComponentString(I18n.format("msg.gridaligned", (Object[]) null)));
    }
    
    private void cmdFixy(EntityPlayerSP sender) {
        if (fixY==-1) {
            fixY=(int) Math.floor(sender.posY);
            sender.sendMessage(new TextComponentString(I18n.format("msg.gridheightfixed", fixY)));
        } else {
            fixY=-1;
            sender.sendMessage(new TextComponentString(I18n.format("msg.gridheightfloat")));
        }
    }
    
    private void cmdChunks(EntityPlayerSP sender) {
        offsetX=offsetZ=0;
        gridX=gridZ=16;
        visible=true;
        sender.sendMessage(new TextComponentString(I18n.format("msg.gridchunks")));
    }
    
    private void cmdDistance(EntityPlayerSP sender, int distance) {
        this.distance=distance;
        sender.sendMessage(new TextComponentString(I18n.format("msg.griddistance", distance)));
    }
    
    private void cmdX(EntityPlayerSP sender, int coord) {
        cmdXZ(sender, coord, gridZ);
    }

    private void cmdZ(EntityPlayerSP sender, int coord) {
        cmdXZ(sender, gridX, coord);
    }
    
    private void cmdXZ(EntityPlayerSP sender, int newX, int newZ) {
        if (newX>0 && newZ>0) {
            gridX=newX;
            gridZ=newZ;
            visible=true;
            sender.sendMessage(new TextComponentString(I18n.format("msg.gridpattern", gridX, gridZ)));
        } else {
            sender.sendMessage(new TextComponentString(I18n.format("msg.gridcoordspositive")));
        }
    }
	
    @Override
    public String getName() {
        return "grid";
    }

    @Override
    public String getUsage(ICommandSender sender) {
        return "/grid <size> | /grid <xsize> <ysize> | /grid show | /grid hide | /grid lines | /grid blocks | /grid circles | /grid spawns | /grid here | /grid fixy | /grid chunk";
    }

    @Override
    public List<String> getAliases() {
        return new ArrayList<>();
    }

    @Override
    public void execute(MinecraftServer server, ICommandSender sender, String[] args) {
        EntityPlayerSP player = Minecraft.getMinecraft().player;
        if (args.length==0) {
            visible=!visible;
        } else {
            if (args[0].equals("show")) {
                cmdShow(player);
            } else if (args[0].equals("hide")) {
                cmdHide(player);
            } else if (args[0].equals("lines")) { 
                cmdLines(player);
            } else if (args[0].equals("blocks")) {
                cmdBlocks(player);
            } else if (args[0].equals("circles")) {
                cmdCircles(player);
            } else if (args[0].equals("here")) {
                cmdHere(player);
            } else if (args[0].equals("fixy")) {
                cmdFixy(player);
            } else if (args[0].equals("chunk") || args[0].equals("chunks")) {
                cmdChunks(player);
            } else if (args[0].equals("spawns")) {
                cmdSpawns(player, args.length==2 ? args[1] : null);
            } else if (args[0].equals("distance")) {
                if (args.length==2) {
                    try {
                        cmdDistance(player, Integer.parseInt(args[1]));
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
                cmdXZ(player, newX, newZ);
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
    
    @SubscribeEvent
    public void keyPressed(final InputEvent.KeyInputEvent e) {
        EntityPlayerSP player = Minecraft.getMinecraft().player;
        if (showHide.isPressed()) {
            visible=!visible;
        }
        if (gridFixY.isPressed()) {
            cmdFixy(player);
        }
        if (gridHere.isPressed()) {
            cmdHere(player);
        }
        if (gridSpawns.isPressed()) {
            cmdSpawns(player, null);
        }
    }
}
