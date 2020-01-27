package de.guntram.mcmod.grid;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.brigadier.CommandDispatcher;
import static com.mojang.brigadier.arguments.IntegerArgumentType.getInteger;
import static com.mojang.brigadier.arguments.IntegerArgumentType.integer;
import static com.mojang.brigadier.arguments.StringArgumentType.getString;
import static com.mojang.brigadier.arguments.StringArgumentType.string;
import static io.github.cottonmc.clientcommands.ArgumentBuilders.argument;
import static io.github.cottonmc.clientcommands.ArgumentBuilders.literal;
import io.github.cottonmc.clientcommands.ClientCommandPlugin;
import io.github.cottonmc.clientcommands.CottonClientCommandSource;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.keybinding.FabricKeyBinding;
import net.fabricmc.fabric.api.client.keybinding.KeyBindingRegistry;
import net.fabricmc.fabric.api.event.client.ClientTickCallback;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.EntityType;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.resource.language.I18n;
import net.minecraft.client.util.InputUtil;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.world.SpawnHelper;
import net.minecraft.entity.SpawnRestriction;
import net.minecraft.text.LiteralText;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.LightType;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_B;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_C;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_L;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_Y;

public class Grid implements ClientModInitializer, ClientCommandPlugin
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
    private boolean showGrid=false;
    private boolean isBlocks=true;
    private boolean isCircles=false;
    private boolean showSpawns=false;
    private Pattern showBiomes=null;
    private Logger LOGGER;

    FabricKeyBinding showHide, gridHere, gridFixY, gridSpawns;
    
    private boolean dump;
    private long lastDumpTime, thisDumpTime;

    @Override
    public void onInitializeClient() {
        instance=this;
        setKeyBindings();
        LOGGER = LogManager.getLogger(MODNAME);
    }
    
    public void renderOverlay(float partialTicks, MatrixStack matrices) {
        if (!showGrid && !showSpawns && showBiomes == null)
            return;

        RenderSystem.pushMatrix();
        RenderSystem.multMatrix(matrices.peek().getModel());
        Entity player = MinecraftClient.getInstance().getCameraEntity();
        double cameraX = player.lastRenderX + (player.getX() - player.lastRenderX) * (double)partialTicks;
        double cameraY = player.lastRenderY + (player.getY() - player.lastRenderY) * (double)partialTicks + player.getEyeHeight(player.getPose());
        double cameraZ = player.lastRenderZ + (player.getZ() - player.lastRenderZ) * (double)partialTicks;        

        RenderSystem.disableTexture();
        RenderSystem.disableBlend();
        RenderSystem.lineWidth(1.0f);
        RenderSystem.enableAlphaTest();
        RenderSystem.enableDepthTest();

        Tessellator tessellator=Tessellator.getInstance();
        BufferBuilder bufferBuilder = tessellator.getBuffer();
        // bufferBuilder.setOffset(-cameraX, -cameraY, -cameraZ);
        RenderSystem.translated(-cameraX, -cameraY, -cameraZ);
        bufferBuilder.begin(3, VertexFormats.POSITION_COLOR);
        
        int playerX=(int) Math.floor(player.getX());
        int playerZ=(int) Math.floor(player.getZ());
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
        
        if (showGrid) {
            float tempy=((float)(fixY==-1 ? player.lastRenderY + (player.getY() - player.lastRenderY) * (double)partialTicks : fixY));
            final float y;
            if (player.getY()+player.getEyeHeight(player.getPose()) > tempy) {
                y=tempy+0.05f;
            } else {
                y=tempy-0.05f;
            }
            
            LOGGER.debug(() -> { return "camera: "+cameraX+" "+cameraY+" "+cameraZ+", y="+y; });
                
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
            showSpawns(bufferBuilder, player, baseX, baseZ);
        }
        
        if (showBiomes!=null) {
            showBiomes(bufferBuilder, player, baseX, baseZ);
        }
        
        tessellator.draw();

        RenderSystem.translated(0, 0, 0);
        
        RenderSystem.lineWidth(1.0f);
        RenderSystem.enableBlend();
        RenderSystem.enableTexture();
        RenderSystem.popMatrix();
    }
    
    private void showSpawns(BufferBuilder bufferBuilder, Entity player, int baseX, int baseZ) {
        int miny=(int)(player.getY())-16;
        int maxy=(int)(player.getY())+2;
        if (miny<0) { miny=0; }
        if (maxy>255) { maxy=255; }
        for (int x=baseX-distance; x<=baseX+distance; x++) {
            for (int z=baseZ-distance; z<=baseZ+distance; z++) {
                for (int y=miny; y<=maxy; y++) {
                    BlockPos pos=new BlockPos(x, y, z);
                    int spawnmode;
                    if (SpawnHelper.canSpawn(SpawnRestriction.Location.ON_GROUND, player.world, pos, EntityType.COD)) {
                        if (player.world.getLightLevel(LightType.BLOCK, pos)>=lightLevel)
                            continue;
                        else if (player.world.getLightLevel(LightType.SKY, pos)>=lightLevel)
                            cross(bufferBuilder, x, y, z, 1.0f, 1.0f, 0.0f, false );
                        else
                            cross(bufferBuilder, x, y, z, 1.0f, 0.0f, 0.0f, true );
                    }
                }
            }
        }
    }
    
    private void showBiomes(BufferBuilder bufferBuilder, Entity player, int baseX, int baseZ) {
        int miny=(int)(player.getY())-16;
        int maxy=(int)(player.getY());
        if (miny<0) { miny=0; }
        if (maxy>255) { maxy=255; }
        for (int x=baseX-distance; x<=baseX+distance; x++) {
            for (int z=baseZ-distance; z<=baseZ+distance; z++) {
                if (showBiomes.matcher(player.world.getBiome(new BlockPos(x, 1, z)).getName().asFormattedString()).find()) {
                    int y;
                    if (fixY == -1) {
                        y=(int)(player.getY());
                        while (y>=miny && isAir(player.world.getBlockState(new BlockPos(x, y, z)).getBlock())) {
                            y--;
                        }
                    } else {
                        y=fixY-1;
                    }
                    diamond(bufferBuilder, x, y+1, z, 1.0f, 0.0f, 1.0f);
                }
            }
        }
    }
    
    static private boolean isAir(Block block) {
        return block == Blocks.AIR || block == Blocks.CAVE_AIR || block == Blocks.VOID_AIR;
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
    
    private void cross(BufferBuilder b, int x, int y, int z, float red, float green, float blue, boolean twoLegs) {
        b.vertex(x+0.3f, y+0.05f, z+0.3f).color(red, green, blue, 0.0f).next();
        b.vertex(x+0.3f, y+0.05f, z+0.3f).color(red, green, blue, 1.0f).next();
        b.vertex(x+0.7f, y+0.05f, z+0.7f).color(red, green, blue, 1.0f).next();
        b.vertex(x+0.7f, y+0.05f, z+0.7f).color(red, green, blue, 0.0f).next();
        if (twoLegs) {
            b.vertex(x+0.3f, y+0.05f, z+0.7f).color(red, green, blue, 0.0f).next();
            b.vertex(x+0.3f, y+0.05f, z+0.7f).color(red, green, blue, 1.0f).next();
            b.vertex(x+0.7f, y+0.05f, z+0.3f).color(red, green, blue, 1.0f).next();
            b.vertex(x+0.7f, y+0.05f, z+0.3f).color(red, green, blue, 0.0f).next();
        }
    }
    
    private void diamond(BufferBuilder b, int x, int y, int z, float red, float green, float blue) {
        b.vertex(x+0.3f, y+0.05f, z+0.5f).color(red, green, blue, 0.0f).next();
        b.vertex(x+0.3f, y+0.05f, z+0.5f).color(red, green, blue, 1.0f).next();
        b.vertex(x+0.5f, y+0.05f, z+0.3f).color(red, green, blue, 1.0f).next();
        b.vertex(x+0.7f, y+0.05f, z+0.5f).color(red, green, blue, 1.0f).next();
        b.vertex(x+0.5f, y+0.05f, z+0.7f).color(red, green, blue, 1.0f).next();
        b.vertex(x+0.3f, y+0.05f, z+0.5f).color(red, green, blue, 1.0f).next();
        b.vertex(x+0.3f, y+0.05f, z+0.5f).color(red, green, blue, 0.0f).next();
    }
    
    private void cmdShow(ClientPlayerEntity sender) {
        showGrid = true;
        sender.addChatMessage(new LiteralText(I18n.translate("msg.gridshown", (Object[]) null)), false);
    }
    
    private void cmdHide(ClientPlayerEntity sender) {
        showGrid = false;
        sender.addChatMessage(new LiteralText(I18n.translate("msg.gridhidden", (Object[]) null)), false);
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

            sender.addChatMessage(new LiteralText(I18n.translate("msg.spawnshidden")), false);
            showSpawns=false;
        } else {
            sender.addChatMessage(new LiteralText(I18n.translate("msg.spawnsshown", level)), false);
            showSpawns=true;
        }
    }
    
    private void cmdLines(ClientPlayerEntity sender) {
        showGrid = true; isBlocks = false;
        sender.addChatMessage(new LiteralText(I18n.translate("msg.gridlines", (Object[]) null)), false);
    }
    
    private void cmdBlocks(ClientPlayerEntity sender) {
        showGrid = true; isBlocks = true;
        sender.addChatMessage(new LiteralText(I18n.translate("msg.gridblocks", (Object[]) null)), false);
    }
    
    private void cmdCircles(ClientPlayerEntity sender) {
        if (isCircles) {
            isCircles = false;
            sender.addChatMessage(new LiteralText(I18n.translate("msg.gridnomorecircles", (Object[]) null)), false);
        } else {
            isCircles = true;
            showGrid = true;
            sender.addChatMessage(new LiteralText(I18n.translate("msg.gridcircles", (Object[]) null)), false);
        }
    }
    
    private void cmdHere(ClientPlayerEntity sender) {
        int playerX=(int) Math.floor(sender.getX());
        int playerZ=(int) Math.floor(sender.getZ());
        int playerXShift=Math.floorMod(playerX, gridX);
        int playerZShift=Math.floorMod(playerZ, gridZ);                
        offsetX=playerXShift;
        offsetZ=playerZShift;
        showGrid=true;
        sender.addChatMessage(new LiteralText(I18n.translate("msg.gridaligned", (Object[]) null)), false);
    }
    
    private void cmdFixy(ClientPlayerEntity sender) {
        if (fixY==-1) {
            cmdFixy(sender, (int)Math.floor(sender.getY()));
        } else {
            fixY=-1;
            sender.addChatMessage(new LiteralText(I18n.translate("msg.gridheightfloat")), false);
        }
    }

    private void cmdFixy(ClientPlayerEntity sender, int level) {
            fixY=level;
            sender.addChatMessage(new LiteralText(I18n.translate("msg.gridheightfixed", fixY)), false);
    }
    
    private void cmdChunks(ClientPlayerEntity sender) {
        offsetX=offsetZ=0;
        gridX=gridZ=16;
        showGrid=true;
        sender.addChatMessage(new LiteralText(I18n.translate("msg.gridchunks")), false);
    }
    
    private void cmdDistance(ClientPlayerEntity sender, int distance) {
        this.distance=distance;
        sender.addChatMessage(new LiteralText(I18n.translate("msg.griddistance", distance)), false);
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
            showGrid=true;
        	sender.addChatMessage(new LiteralText(I18n.translate("msg.gridpattern", gridX, gridZ)), false);
        } else {
            sender.addChatMessage(new LiteralText(I18n.translate("msg.gridcoordspositive")), false);
        }
    }
    
    private void cmdBiome(ClientPlayerEntity sender, String biome) {
        if (biome == null  || biome.isEmpty()) {
            showBiomes = null;
        } else {
            try {
                this.showBiomes=Pattern.compile(biome, Pattern.CASE_INSENSITIVE);
                sender.addChatMessage(new LiteralText(I18n.translate("msg.biomesearching", biome)), false);
            } catch (PatternSyntaxException ex) {
                showBiomes = null;
                sender.addChatMessage(new LiteralText(I18n.translate("msg.biomepatternbad", biome)), false);
            }
        }
    }

    @Override
    public void registerCommands(CommandDispatcher<CottonClientCommandSource> cd) {
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
                .then(
                    literal("biome").then(
					    argument("pattern", string()).executes(c->{
							instance.cmdBiome(MinecraftClient.getInstance().player, ""+getString(c, "pattern"));
                            return 1;
						})
					).executes(c->{
                        instance.cmdBiome(MinecraftClient.getInstance().player, null);
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
        ClientTickCallback.EVENT.register(e->processKeyBinds());
    }

    public void processKeyBinds() {
        ClientPlayerEntity player = MinecraftClient.getInstance().player;
        if (showHide.wasPressed()) {
            showGrid=!showGrid;
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
