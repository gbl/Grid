package de.guntram.mcmod.grid;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.brigadier.CommandDispatcher;
import static com.mojang.brigadier.arguments.IntegerArgumentType.getInteger;
import static com.mojang.brigadier.arguments.IntegerArgumentType.integer;
import de.guntram.mcmod.fabrictools.KeyBindingHandler;
import de.guntram.mcmod.fabrictools.KeyBindingManager;
import static io.github.cottonmc.clientcommands.ArgumentBuilders.argument;
import static io.github.cottonmc.clientcommands.ArgumentBuilders.literal;
import io.github.cottonmc.clientcommands.ClientCommandPlugin;
import io.github.cottonmc.clientcommands.ClientCommands;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.keybinding.FabricKeyBinding;
import net.fabricmc.fabric.api.client.keybinding.KeyBindingRegistry;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.EntityType;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.resource.language.I18n;
import net.minecraft.client.util.InputUtil;
import net.minecraft.entity.Entity;
import net.minecraft.world.SpawnHelper;
import net.minecraft.entity.SpawnRestriction;
import net.minecraft.server.command.CommandSource;
import net.minecraft.text.StringTextComponent;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.LightType;
import static org.lwjgl.glfw.GLFW.*;

public class Grid implements ClientModInitializer, KeyBindingHandler, ClientCommandPlugin
{
    static final String MODID="grid";
    static final String MODNAME="Grid";
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

    FabricKeyBinding showHide, gridHere, gridFixY, gridSpawns;
    
    private boolean dump;
    private long lastDumpTime, thisDumpTime;

    @Override
    public void onInitializeClient() {
        instance=this;
        setKeyBindings();
    }
    
    public void renderOverlay(float partialTicks) {
        if (!visible && !showSpawns)
            return;

        Entity entityplayer = MinecraftClient.getInstance().getCameraEntity();
        double cameraX = entityplayer.prevRenderX + (entityplayer.x - entityplayer.prevRenderX) * (double)partialTicks;
        double cameraY = entityplayer.prevRenderY + (entityplayer.y - entityplayer.prevRenderY) * (double)partialTicks + entityplayer.getEyeHeight(entityplayer.getPose());
        double cameraZ = entityplayer.prevRenderZ + (entityplayer.z - entityplayer.prevRenderZ) * (double)partialTicks;        

        GlStateManager.disableTexture();
        GlStateManager.disableBlend();
        GlStateManager.lineWidth(1.0f);

        Tessellator tessellator=Tessellator.getInstance();
        BufferBuilder bufferBuilder = tessellator.getBufferBuilder();
        bufferBuilder.setOffset(-cameraX, -cameraY, -cameraZ);
        bufferBuilder.begin(3, VertexFormats.POSITION_COLOR);
        
        int playerX=(int) Math.floor(entityplayer.x);
        int playerZ=(int) Math.floor(entityplayer.z);
        int playerXShift=Math.floorMod(playerX, gridX);
        int playerZShift=Math.floorMod(playerZ, gridZ);
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
            float y=((float)(fixY==-1 ? entityplayer.y : fixY));
            if (entityplayer.y+entityplayer.getEyeHeight(entityplayer.getPose()) > y) {
                y+=0.05f;
            } else {
                y-=0.05f;
            }
                
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
            int miny=(int)(entityplayer.y)-16;
            int maxy=(int)(entityplayer.y)+2;
            if (miny<0) { miny=0; }
            if (maxy>255) { maxy=255; }
            for (int x=baseX-distance; x<=baseX+distance; x++) {
                for (int z=baseZ-distance; z<=baseZ+distance; z++) {
                    for (int y=miny; y<=maxy; y++) {
                        BlockPos pos=new BlockPos(x, y, z);
                        int spawnmode;
                        if (SpawnHelper.canSpawn(SpawnRestriction.Location.ON_GROUND, entityplayer.world, pos, EntityType.COD)) {
                            if (entityplayer.world.getLightLevel(LightType.BLOCK, pos)>=lightLevel)
                                continue;
                            else if (entityplayer.world.getLightLevel(LightType.SKY, pos)>=lightLevel)
                                cross(bufferBuilder, x, y, z, 1.0f, 1.0f, 0.0f );
                            else
                                cross(bufferBuilder, x, y, z, 1.0f, 0.0f, 0.0f );
                        }
                    }
                }
            }
        }
        
        tessellator.draw();
        bufferBuilder.setOffset(0, 0, 0);
        
        GlStateManager.lineWidth(1.0f);
        GlStateManager.enableBlend();
        GlStateManager.enableTexture();
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
        b.vertex(x1+offsetX, y1, z1+offsetZ).color(red, green, blue, 0.0f).next();
        b.vertex(x1+offsetX, y1, z1+offsetZ).color(red, green, blue, 1.0f).next();
        b.vertex(x2+offsetX, y2, z2+offsetZ).color(red, green, blue, 1.0f).next();
        b.vertex(x2+offsetX, y2, z2+offsetZ).color(red, green, blue, 0.0f).next();
    }
    
    private void cross(BufferBuilder b, int x, int y, int z, float red, float green, float blue) {
        b.vertex(x+0.3f, y+0.05f, z+0.3f).color(red, green, blue, 0.0f).next();
        b.vertex(x+0.3f, y+0.05f, z+0.3f).color(red, green, blue, 1.0f).next();
        b.vertex(x+0.7f, y+0.05f, z+0.7f).color(red, green, blue, 1.0f).next();
        b.vertex(x+0.7f, y+0.05f, z+0.7f).color(red, green, blue, 0.0f).next();
    }
    
    private void cmdShow(ClientPlayerEntity sender) {
        visible = true;
        sender.addChatMessage(new StringTextComponent(I18n.translate("msg.gridshown", (Object[]) null)), false);
    }
    
    private void cmdHide(ClientPlayerEntity sender) {
        visible = false;
        sender.addChatMessage(new StringTextComponent(I18n.translate("msg.gridhidden", (Object[]) null)), false);
    }
    
    private void cmdSpawns(ClientPlayerEntity sender, String newLevel) {
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

            sender.addChatMessage(new StringTextComponent(I18n.translate("msg.spawnshidden")), false);
            showSpawns=false;
        } else {
            sender.addChatMessage(new StringTextComponent(I18n.translate("msg.spawnsshown", level)), false);
            showSpawns=true;
        }
    }
    
    private void cmdLines(ClientPlayerEntity sender) {
        visible = true; isBlocks = false;
        sender.addChatMessage(new StringTextComponent(I18n.translate("msg.gridlines", (Object[]) null)), false);
    }
    
    private void cmdBlocks(ClientPlayerEntity sender) {
        visible = true; isBlocks = true;
        sender.addChatMessage(new StringTextComponent(I18n.translate("msg.gridblocks", (Object[]) null)), false);
    }
    
    private void cmdCircles(ClientPlayerEntity sender) {
        if (isCircles) {
            isCircles = false;
            sender.addChatMessage(new StringTextComponent(I18n.translate("msg.gridnomorecircles", (Object[]) null)), false);
        } else {
            isCircles = true;
            visible = true;
            sender.addChatMessage(new StringTextComponent(I18n.translate("msg.gridcircles", (Object[]) null)), false);
        }
    }
    
    private void cmdHere(ClientPlayerEntity sender) {
        int playerX=(int) Math.floor(sender.x);
        int playerZ=(int) Math.floor(sender.z);
        int playerXShift=Math.floorMod(playerX, gridX);
        int playerZShift=Math.floorMod(playerZ, gridZ);                
        offsetX=playerXShift;
        offsetZ=playerZShift;
        visible=true;
        sender.addChatMessage(new StringTextComponent(I18n.translate("msg.gridaligned", (Object[]) null)), false);
    }
    
    private void cmdFixy(ClientPlayerEntity sender) {
        if (fixY==-1) {
            cmdFixy(sender, (int)Math.floor(sender.y));
        } else {
            fixY=-1;
            sender.addChatMessage(new StringTextComponent(I18n.translate("msg.gridheightfloat")), false);
        }
    }

    private void cmdFixy(ClientPlayerEntity sender, int level) {
            fixY=level;
            sender.addChatMessage(new StringTextComponent(I18n.translate("msg.gridheightfixed", fixY)), false);
    }
    
    private void cmdChunks(ClientPlayerEntity sender) {
        offsetX=offsetZ=0;
        gridX=gridZ=16;
        visible=true;
        sender.addChatMessage(new StringTextComponent(I18n.translate("msg.gridchunks")), false);
    }
    
    private void cmdDistance(ClientPlayerEntity sender, int distance) {
        this.distance=distance;
        sender.addChatMessage(new StringTextComponent(I18n.translate("msg.griddistance", distance)), false);
    }
    
    private void cmdX(ClientPlayerEntity sender, int coord) {
        cmdXZ(sender, coord, gridZ);
    }

    private void cmdZ(ClientPlayerEntity sender, int coord) {
        cmdXZ(sender, gridX, coord);
    }
    
    private void cmdXZ(ClientPlayerEntity sender, int newX, int newZ) {
        if (newX>0 && newZ>0) {
            gridX=newX;
            gridZ=newZ;
            visible=true;
        	sender.addChatMessage(new StringTextComponent(I18n.translate("msg.gridpattern", gridX, gridZ)), false);
        } else {
            sender.addChatMessage(new StringTextComponent(I18n.translate("msg.gridcoordspositive")), false);
        }
    }

    @Override
    public void registerCommands(CommandDispatcher<CommandSource> cd) {
        cd.register(
            literal("grid")
                .then(
                    literal("show").executes(c->{
                        instance.cmdShow(MinecraftClient.getInstance().player);
                        return 1;
                    })
                )
                .then(
                    literal("hide").executes(c->{
                        instance.cmdHide(MinecraftClient.getInstance().player);
                        return 1;
                    })
                )
                .then(
                    literal("lines").executes(c->{
                        instance.cmdLines(MinecraftClient.getInstance().player);
                        return 1;
                    })
                )
                .then(
                    literal("blocks").executes(c->{
                        instance.cmdBlocks(MinecraftClient.getInstance().player);
                        return 1;
                    })
                )
                .then(
                    literal("circles").executes(c->{
                        instance.cmdCircles(MinecraftClient.getInstance().player);
                        return 1;
                    })
                )
                .then(
                    literal("here").executes(c->{
                        instance.cmdHere(MinecraftClient.getInstance().player);
                        return 1;
                    })
                )
                .then(
                    literal("fixy").then(
                        argument("y", integer()).executes(c->{
                            instance.cmdFixy(MinecraftClient.getInstance().player, getInteger(c, "y"));
                            return 1;
                        })
                    ).executes(c->{
                        instance.cmdFixy(MinecraftClient.getInstance().player);
                        return 1;
                    })
                )
                .then(
                    literal("chunks").executes(c->{
                        instance.cmdChunks(MinecraftClient.getInstance().player);
                        return 1;
                    })
                )
                .then(
                    literal("spawns").then(
					    argument("lightlevel", integer()).executes(c->{
							instance.cmdSpawns(MinecraftClient.getInstance().player, ""+getInteger(c, "lightlevel"));
                            return 1;
						})
					).executes(c->{
                        instance.cmdSpawns(MinecraftClient.getInstance().player, null);
                        return 1;
                    })
                )
                .then(
                    literal("distance").then (
                        argument("distance", integer()).executes(c->{
                            instance.cmdDistance(MinecraftClient.getInstance().player, getInteger(c, "distance"));
                            return 1;
                        })
                    )
                )
                .then(
                    argument("x", integer()).then (
                        argument("z", integer()).executes(c->{
                            instance.cmdXZ(MinecraftClient.getInstance().player, getInteger(c, "x"), getInteger(c, "z"));
                            return 1;
                        })
                    ).executes(c->{
                        instance.cmdXZ(MinecraftClient.getInstance().player, getInteger(c, "x"), getInteger(c, "x"));
                        return 1;
                    })
                )
        );
    }

    public void setKeyBindings() {
        final String category="key.categories.grid";
        KeyBindingRegistry.INSTANCE.addCategory(category);
        KeyBindingRegistry.INSTANCE.register(
            showHide=FabricKeyBinding.Builder
                .create(new Identifier("grid:showhide"), InputUtil.Type.KEYSYM, GLFW_KEY_B, category)
                .build());
        KeyBindingRegistry.INSTANCE.register(
            gridHere=FabricKeyBinding.Builder
                .create(new Identifier("grid:here"), InputUtil.Type.KEYSYM, GLFW_KEY_C, category)
                .build());
        KeyBindingRegistry.INSTANCE.register(
            gridFixY=FabricKeyBinding.Builder
                .create(new Identifier("grid:fixy"), InputUtil.Type.KEYSYM, GLFW_KEY_Y, category)
                .build());
        KeyBindingRegistry.INSTANCE.register(
            gridSpawns=FabricKeyBinding.Builder
                .create(new Identifier("grid:spawns"), InputUtil.Type.KEYSYM, GLFW_KEY_L, category)
                .build());
        KeyBindingManager.register(this);
    }

    @Override
    public void processKeyBinds() {
        ClientPlayerEntity player = MinecraftClient.getInstance().player;
        if (showHide.wasPressed()) {
            visible=!visible;
        }
        if (gridFixY.wasPressed()) {
            cmdFixy(player);
        }
        if (gridHere.wasPressed()) {
            cmdHere(player);
        }
        if (gridSpawns.wasPressed()) {
            cmdSpawns(player, null);
        }
    }
}
