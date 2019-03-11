package de.guntram.mcmod.grid;

import com.mojang.brigadier.CommandDispatcher;
import static com.mojang.brigadier.arguments.IntegerArgumentType.getInteger;
import static com.mojang.brigadier.arguments.IntegerArgumentType.integer;
import static com.mojang.brigadier.arguments.StringArgumentType.getString;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.client.resources.I18n;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.command.CommandSource;
import static net.minecraft.command.Commands.argument;
import static net.minecraft.command.Commands.literal;
import net.minecraft.entity.EntitySpawnPlacementRegistry;
import net.minecraft.entity.EntityType;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.world.EnumLightType;
import net.minecraft.world.WorldEntitySpawner;
import org.dimdev.rift.listener.client.KeyBindingAdder;
import org.dimdev.rift.listener.client.KeybindHandler;
import org.dimdev.rift.listener.client.LocalCommandAdder;
import org.dimdev.riftloader.listener.InitializationListener;
import static org.lwjgl.glfw.GLFW.*;
import org.spongepowered.asm.launch.MixinBootstrap;
import org.spongepowered.asm.mixin.Mixins;

public class Grid implements InitializationListener, LocalCommandAdder, KeybindHandler, KeyBindingAdder
{
    static final String MODID="grid";
    static final String VERSION="@VERSION@";
    public static Grid instance;
    
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

    @Override
    public void onInitialization() {
        MixinBootstrap.init();
        Mixins.addConfiguration("mixins.grid.json");
        Mixins.addConfiguration("mixins.riftpatch-de-guntram.json");
        instance=this;
    }
    
    public void renderOverlay(float partialTicks) {
        if (!visible && !showSpawns)
            return;

        EntityPlayerSP entityplayer = Minecraft.getInstance().player;
        double cameraX = entityplayer.lastTickPosX + (entityplayer.posX - entityplayer.lastTickPosX) * (double)partialTicks;
        double cameraY = entityplayer.lastTickPosY + (entityplayer.posY - entityplayer.lastTickPosY) * (double)partialTicks;
        double cameraZ = entityplayer.lastTickPosZ + (entityplayer.posZ - entityplayer.lastTickPosZ) * (double)partialTicks;        

        GlStateManager.disableTexture2D();
        GlStateManager.disableBlend();
        GlStateManager.lineWidth(1.0f);

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
                GlStateManager.lineWidth(3.0f);
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
            GlStateManager.lineWidth(2.0f);
            int miny=(int)(entityplayer.posY)-16;
            int maxy=(int)(entityplayer.posY)+2;
            if (miny<0) { miny=0; }
            if (maxy>255) { maxy=255; }
            for (int x=playerX-distance; x<=playerX+distance; x++) {
                for (int z=playerZ-distance; z<=playerZ+distance; z++) {
                    for (int y=miny; y<=maxy; y++) {
                        BlockPos pos=new BlockPos(x, y, z);
                        int spawnmode;
                        if (WorldEntitySpawner.canCreatureTypeSpawnAtLocation(EntitySpawnPlacementRegistry.SpawnPlacementType.ON_GROUND, entityplayer.world, pos, EntityType.COD)) {
                            if (entityplayer.world.getLightFor(EnumLightType.BLOCK, pos)>=lightLevel)
                                continue;
                            else if (entityplayer.world.getLightFor(EnumLightType.SKY, pos)>=lightLevel)
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
        
        GlStateManager.lineWidth(1.0f);
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
        gridX=newX;
        gridZ=newZ;
        visible=true;
        sender.sendMessage(new TextComponentString(I18n.format("msg.gridpattern", gridX, gridZ)));
    }

    @Override
    public void registerLocalCommands(CommandDispatcher<CommandSource> cd) {
        cd.register(
            literal("grid")
                .then(
                    literal("show").executes(c->{
                        cmdShow(Minecraft.getInstance().player);
                        return 1;
                    })
                )
                .then(
                    literal("hide").executes(c->{
                        cmdHide(Minecraft.getInstance().player);
                        return 1;
                    })
                )
                .then(
                    literal("lines").executes(c->{
                        cmdLines(Minecraft.getInstance().player);
                        return 1;
                    })
                )
                .then(
                    literal("blocks").executes(c->{
                        cmdBlocks(Minecraft.getInstance().player);
                        return 1;
                    })
                )
                .then(
                    literal("circles").executes(c->{
                        cmdCircles(Minecraft.getInstance().player);
                        return 1;
                    })
                )
                .then(
                    literal("here").executes(c->{
                        cmdHere(Minecraft.getInstance().player);
                        return 1;
                    })
                )
                .then(
                    literal("fixy").executes(c->{
                        cmdFixy(Minecraft.getInstance().player);
                        return 1;
                    })
                )
                .then(
                    literal("chunks").executes(c->{
                        cmdChunks(Minecraft.getInstance().player);
                        return 1;
                    })
                )
                .then(
                    literal("spawns").then(
					    argument("lightlevel", integer()).executes(c->{
							cmdSpawns(Minecraft.getInstance().player, ""+getInteger(c, "lightlevel"));
                            return 1;
						})
					).executes(c->{
                        cmdSpawns(Minecraft.getInstance().player, null);
                        return 1;
                    })
                )
                .then(
                    literal("distance").then (
                        argument("distance", integer()).executes(c->{
                            cmdDistance(Minecraft.getInstance().player, getInteger(c, "distance"));
                            return 1;
                        })
                    )
                )
                .then(
                    argument("x", integer()).then (
                        argument("z", integer()).executes(c->{
                            cmdXZ(Minecraft.getInstance().player, getInteger(c, "x"), getInteger(c, "z"));
                            return 1;
                        })
                    ).executes(c->{
                        cmdXZ(Minecraft.getInstance().player, getInteger(c, "x"), getInteger(c, "x"));
                        return 1;
                    })
                )
        );
    }

    @Override
    public Collection<? extends KeyBinding> getKeyBindings() {
        List<KeyBinding> myBindings=new ArrayList();
        
        myBindings.add(showHide = new KeyBinding("key.showhide", GLFW_KEY_B, "key.categories.grid"));
        myBindings.add(gridHere = new KeyBinding("key.gridhere", GLFW_KEY_C, "key.categories.grid"));
        myBindings.add(gridFixY = new KeyBinding("key.gridfixy", GLFW_KEY_Y, "key.categories.grid"));
        myBindings.add(gridSpawns = new KeyBinding("key.gridspawns", GLFW_KEY_L, "key.categories.grid"));
        return myBindings;
    }

    @Override
    public void processKeybinds() {
        EntityPlayerSP player = Minecraft.getInstance().player;
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
