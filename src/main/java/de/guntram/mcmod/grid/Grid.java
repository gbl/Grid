package de.guntram.mcmod.grid;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.brigadier.CommandDispatcher;
import static com.mojang.brigadier.arguments.IntegerArgumentType.getInteger;
import static com.mojang.brigadier.arguments.IntegerArgumentType.integer;
import static com.mojang.brigadier.arguments.StringArgumentType.getString;
import static com.mojang.brigadier.arguments.StringArgumentType.string;
import de.guntram.mcmod.fabrictools.ConfigurationProvider;
import static io.github.cottonmc.clientcommands.ArgumentBuilders.argument;
import static io.github.cottonmc.clientcommands.ArgumentBuilders.literal;
import io.github.cottonmc.clientcommands.ClientCommandPlugin;
import io.github.cottonmc.clientcommands.CottonClientCommandSource;
import java.awt.Color;
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
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.resource.language.I18n;
import net.minecraft.client.util.InputUtil;
import net.minecraft.client.util.math.Matrix4f;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnRestriction;
import net.minecraft.text.LiteralText;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.LightType;
import net.minecraft.world.SpawnHelper;
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
    private boolean isHexes = false;
    private boolean showSpawns=false;
    private Pattern showBiomes=null;
    private Logger LOGGER;
    
    private boolean fastSpawnCheck = true;
    
    
    private Color blockColor = new Color(0x8080ff);
    private Color lineColor = new Color(0xff8000);
    private Color circleColor = new Color(0x00e480);
    private Color spawnNightColor = new Color(0xffff00);
    private Color spawnDayColor = new Color(0xff0000);
    private Color biomeColor = new Color(0xff00ff);

    FabricKeyBinding showHide, gridHere, gridFixY, gridSpawns;
    
    private boolean dump;
    private long lastDumpTime, thisDumpTime;

    @Override
    public void onInitializeClient() {
        instance=this;
        ConfigurationHandler confHandler = ConfigurationHandler.getInstance();
        confHandler.load(ConfigurationProvider.getSuggestedFile(MODID));
        
        blockColor = new Color(confHandler.blockColor);
        lineColor  = new Color(confHandler.lineColor);
        circleColor= new Color(confHandler.circleColor);
        spawnNightColor = new Color(confHandler.spawnNightColor);
        spawnDayColor = new Color(confHandler.spawnDayColor);
        biomeColor = new Color(confHandler.biomeColor);

        setKeyBindings();
        LOGGER = LogManager.getLogger(MODNAME);
    }
    
    public void renderOverlay(float partialTicks, MatrixStack stack) {
        if (!showGrid && !showSpawns && showBiomes == null)
            return;

        Entity player = MinecraftClient.getInstance().getCameraEntity();
        double cameraX = player.lastRenderX + (player.getX() - player.lastRenderX) * (double)partialTicks;
        double cameraY = player.lastRenderY + (player.getY() - player.lastRenderY) * (double)partialTicks + player.getEyeHeight(player.getPose());
        double cameraZ = player.lastRenderZ + (player.getZ() - player.lastRenderZ) * (double)partialTicks;        
        stack.push();
        stack.translate(-cameraX, -cameraY, -cameraZ);

        RenderSystem.disableTexture();
        RenderSystem.disableBlend();
        RenderSystem.enableAlphaTest();
        RenderSystem.enableDepthTest();

        Tessellator tessellator=Tessellator.getInstance();
        BufferBuilder bufferBuilder = tessellator.getBuffer();
        
        int playerX=(int) Math.floor(player.getX());
        int playerZ=(int) Math.floor(player.getZ());
        int playerXShift=Math.floorMod(playerX, gridX);
        int playerZShift=Math.floorMod(playerZ, gridZ);
        int baseX=playerX-playerXShift;
        int baseZ=playerZ-playerZShift;
        int sizeX=Math.max((distance/gridX)*gridX, 2*gridX);
        int sizeZ=Math.max((distance/gridZ)*gridZ, 2*gridZ);
        if (playerXShift > sizeX/2) { baseX += gridX; }
        if (playerZShift > sizeZ/2) { baseZ += gridZ; }

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
                
            RenderSystem.lineWidth(3.0f);
            stack.push();
            stack.translate(offsetX, 0, offsetZ);
            bufferBuilder.begin(3, VertexFormats.POSITION_COLOR);
            int circRadSquare=(gridX/2)*(gridX/2);
            if (isBlocks) {
                for (int x=baseX-sizeX; x<=baseX+sizeX; x+=gridX) {
                    for (int z=baseZ-sizeZ; z<=baseZ+sizeZ; z+=gridZ) {
                        if (isHexes) {
                            if (gridX >= gridZ) {
                                drawXTriangleVertex(bufferBuilder, stack, x, y, z,                   true,  blockColor.getRed()/255f, blockColor.getGreen()/255f, blockColor.getBlue()/255f);       //  dot itself
                                drawXTriangleVertex(bufferBuilder, stack, x-gridZ/4f, y, z-gridZ/2f, false, blockColor.getRed()/255f, blockColor.getGreen()/255f, blockColor.getBlue()/255f);       // left bottom of myself red
                                drawXTriangleVertex(bufferBuilder, stack, x+gridX/2f-gridZ/4f, y, z, false, blockColor.getRed()/255f, blockColor.getGreen()/255f, blockColor.getBlue()/255f);       // node right orange
                                drawXTriangleVertex(bufferBuilder, stack, x+gridX/2f, y, z-gridZ/2f, true,  blockColor.getRed()/255f, blockColor.getGreen()/255f, blockColor.getBlue()/255f);       // right bottom of right node yellowgreenish
                            } else {
                                drawYTriangleVertex(bufferBuilder, stack, x, y, z,                   true,  blockColor.getRed()/255f, blockColor.getGreen()/255f, blockColor.getBlue()/255f);       //  dot itself
                                drawYTriangleVertex(bufferBuilder, stack, x-gridX/2f, y, z-gridX/4f, false, blockColor.getRed()/255f, blockColor.getGreen()/255f, blockColor.getBlue()/255f);       // left bottom of myself red
                                drawYTriangleVertex(bufferBuilder, stack, x, y, z+gridZ/2f-gridX/4f, false, blockColor.getRed()/255f, blockColor.getGreen()/255f, blockColor.getBlue()/255f);       // node right orange
                                drawYTriangleVertex(bufferBuilder, stack, x-gridX/2f, y, z+gridZ/2f, true,  blockColor.getRed()/255f, blockColor.getGreen()/255f, blockColor.getBlue()/255f);       // right bottom of right node yellowgreenish
                            }
                        } else {
                            drawSquare(bufferBuilder, stack, x, y, z, blockColor.getRed()/255f, blockColor.getGreen()/255f, blockColor.getBlue()/255f);
                        }
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
                                    drawCircleSegment(bufferBuilder, stack, x, dx, dz, y, z, dz, dx, circleColor.getRed()/255f, circleColor.getGreen()/255f, circleColor.getBlue()/255f);
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
                                drawCircleSegment(bufferBuilder, stack, x, dx, nextx, y, z, dz, nextz, circleColor.getRed()/255f, circleColor.getGreen()/255f, circleColor.getBlue()/255f);
                                dx=nextx;
                                dz=nextz;
                            }
                        }
                        dump=false;
                    }
                }
            } else {
                if (isHexes) {
                    for (int x=baseX-sizeX; x<=baseX+sizeX; x+=gridX) {
                        for (int z=baseZ-sizeZ; z<=baseZ+sizeZ; z+=gridZ) {
                            if (gridX >= gridZ) {
                                drawLine(bufferBuilder, stack, x+0.5f, x+0.5f-gridZ/4f,                     y, y, z+0.5f, z+0.5f-gridZ/2f,          lineColor.getRed()/255f, lineColor.getGreen()/255f, lineColor.getBlue()/255f);   // to LB
                                drawLine(bufferBuilder, stack, x+0.5f, x+0.5f-gridZ/4f,                     y, y, z+0.5f, z+0.5f+gridZ/2f,          lineColor.getRed()/255f, lineColor.getGreen()/255f, lineColor.getBlue()/255f);   // to LT
                                drawLine(bufferBuilder, stack, x+0.5f, x+0.5f+gridX/2f-gridZ/4f,            y, y, z+0.5f, z+0.5f,                   lineColor.getRed()/255f, lineColor.getGreen()/255f, lineColor.getBlue()/255f);   // to R
                                drawLine(bufferBuilder, stack, x+0.5f+gridX/2f-gridZ/4f, x+0.5f+gridX/2f,   y, y, z+0.5f, z+0.5f-gridZ/2f,          lineColor.getRed()/255f, lineColor.getGreen()/255f, lineColor.getBlue()/255f);   // to RB
                                drawLine(bufferBuilder, stack, x+0.5f+gridX/2f-gridZ/4f, x+0.5f+gridX/2f,   y, y, z+0.5f, z+0.5f+gridZ/2f,          lineColor.getRed()/255f, lineColor.getGreen()/255f, lineColor.getBlue()/255f);   // to RT
                                
                                drawLine(bufferBuilder, stack, x+0.5f+gridX/2f, x+0.5f+gridX-gridZ/4f,      y, y, z+0.5f+gridZ/2f, z+0.5f+gridZ/2f, lineColor.getRed()/255f, lineColor.getGreen()/255f, lineColor.getBlue()/255f);   // left stump out
                                drawLine(bufferBuilder, stack, x+0.5f-gridZ/4f, x+0.5f-gridX/2f,            y, y, z+0.5f+gridZ/2f, z+0.5f+gridZ/2f, lineColor.getRed()/255f, lineColor.getGreen()/255f, lineColor.getBlue()/255f);   // right stump out
                            } else {
                                drawLine(bufferBuilder, stack, x+0.5f, x+0.5f-gridX/2f,          y, y, z+0.5f, z+0.5f-gridX/4f,                     lineColor.getRed()/255f, lineColor.getGreen()/255f, lineColor.getBlue()/255f);   // to LT
                                drawLine(bufferBuilder, stack, x+0.5f, x+0.5f+gridX/2f,          y, y, z+0.5f, z+0.5f-gridX/4f,                     lineColor.getRed()/255f, lineColor.getGreen()/255f, lineColor.getBlue()/255f);   // to RT
                                drawLine(bufferBuilder, stack, x+0.5f, x+0.5f,                   y, y, z+0.5f, z+0.5f+gridZ/2f-gridX/4f,            lineColor.getRed()/255f, lineColor.getGreen()/255f, lineColor.getBlue()/255f);   // to B
                                drawLine(bufferBuilder, stack, x+0.5f, x+0.5f-gridX/2f,          y, y, z+0.5f+gridZ/2f-gridX/4f, z+0.5f+gridZ/2f,   lineColor.getRed()/255f, lineColor.getGreen()/255f, lineColor.getBlue()/255f);   // to LB
                                drawLine(bufferBuilder, stack, x+0.5f, x+0.5f+gridX/2f,          y, y, z+0.5f+gridZ/2f-gridX/4f, z+0.5f+gridZ/2f,   lineColor.getRed()/255f, lineColor.getGreen()/255f, lineColor.getBlue()/255f);   // to RB
                                
                                drawLine(bufferBuilder, stack, x+0.5f+gridX/2f, x+0.5f+gridX/2f, y, y, z+0.5f+gridZ/2f, z+0.5f+gridZ-gridX/4f,      lineColor.getRed()/255f, lineColor.getGreen()/255f, lineColor.getBlue()/255f);   // stump up
                                drawLine(bufferBuilder, stack, x+0.5f+gridX/2f, x+0.5f+gridX/2f, y, y, z+0.5f-gridX/4f, z+0.5f-gridZ/2f,            lineColor.getRed()/255f, lineColor.getGreen()/255f, lineColor.getBlue()/255f);   // stump down
                            }
                        }
                    }
                } else if (isCircles) {
                    for (int x=baseX-sizeX; x<=baseX+sizeX; x+=gridX) {
                        for (int z=baseZ-sizeZ; z<=baseZ+sizeZ; z+=gridZ) {
                            float dx=0;
                            float dz=gridX/2.0f;
                            for (float nextx=0.1f; nextx<gridX; nextx+=0.1f) {
                                float nextz=(float)(Math.sqrt(gridX*gridX/4.0-nextx*nextx));
                                drawCircleSegment(bufferBuilder, stack, x, dx, nextx, y, z, dz, nextz, circleColor.getRed()/255f, circleColor.getGreen()/255f, circleColor.getBlue()/255f);
                                dx=nextx;
                                dz=nextz;
                                if (nextz<nextx)
                                    break;
                            }
                            dump=false;
                        }   
                    }
                } else {
                    drawLineGrid(bufferBuilder, stack, baseX, baseZ, y, sizeX, sizeZ);                        
                }
            }
            tessellator.draw();
            stack.pop();
        }
        
        if (showSpawns) {
            RenderSystem.lineWidth(1.0f);
            bufferBuilder.begin(3, VertexFormats.POSITION_COLOR);
            showSpawns(bufferBuilder, stack, player, baseX, baseZ);
            tessellator.draw();
        }
        
        if (showBiomes!=null) {
            RenderSystem.lineWidth(1.0f);
            bufferBuilder.begin(3, VertexFormats.POSITION_COLOR);
            showBiomes(bufferBuilder, stack, player, baseX, baseZ);
            tessellator.draw();
        }

        stack.pop();
        
        RenderSystem.enableBlend();
        RenderSystem.enableTexture();
    }
    
    private void showSpawns(BufferBuilder bufferBuilder, MatrixStack stack, Entity player, int baseX, int baseZ) {
        int miny=(int)(player.getY())-16;
        int maxy=(int)(player.getY())+2;
        if (miny<0) { miny=0; }
        if (maxy>255) { maxy=255; }
        for (int x=baseX-distance; x<=baseX+distance; x++) {
            for (int z=baseZ-distance; z<=baseZ+distance; z++) {
                for (int y=miny; y<=maxy; y++) {
                    BlockPos pos=new BlockPos(x, y, z);
                    int spawnmode;
                    if (fastSpawnCheck && player.world.getBlockState(pos.down()).isFullCube(player.world, pos.down())
                    ||  !fastSpawnCheck && SpawnHelper.canSpawn(SpawnRestriction.Location.ON_GROUND, player.world, pos, EntityType.COD)) {
                        if (player.world.getLightLevel(LightType.BLOCK, pos)>=lightLevel)
                            continue;
                        else if (player.world.getLightLevel(LightType.SKY, pos)>=lightLevel)
                            drawCross(bufferBuilder, stack, x, y+0.05f, z, spawnNightColor.getRed()/255f, spawnNightColor.getGreen()/255f, spawnNightColor.getBlue()/255f, false );
                        else
                            drawCross(bufferBuilder, stack, x, y+0.05f, z, spawnDayColor.getRed()/255f, spawnDayColor.getGreen()/255f, spawnDayColor.getBlue()/255f, true );
                    }
                }
            }
        }
    }
    
    private void showBiomes(BufferBuilder bufferBuilder, MatrixStack stack, Entity player, int baseX, int baseZ) {
        int miny=(int)(player.getY())-16;
        int maxy=(int)(player.getY());
        if (miny<0) { miny=0; }
        if (maxy>255) { maxy=255; }
        for (int x=baseX-distance; x<=baseX+distance; x++) {
            for (int z=baseZ-distance; z<=baseZ+distance; z++) {
                if (showBiomes.matcher(player.world.getBiome(new BlockPos(x, 1, z)).getName().getString()).find()) {
                    int y;
                    if (fixY == -1) {
                        y=(int)(player.getY());
                        while (y>=miny && isAir(player.world.getBlockState(new BlockPos(x, y, z)).getBlock())) {
                            y--;
                        }
                    } else {
                        y=fixY-1;
                    }
                    drawDiamond(bufferBuilder, stack, x, y+1, z, biomeColor.getRed()/255f, biomeColor.getGreen()/255f, biomeColor.getBlue()/255f);
                }
            }
        }
    }
    
    static private boolean isAir(Block block) {
        return block == Blocks.AIR || block == Blocks.CAVE_AIR || block == Blocks.VOID_AIR;
    }
    
    private void drawLineGrid(BufferBuilder bufferBuilder, MatrixStack stack, int baseX, int baseZ, float y, int sizeX, int sizeZ) {
        for (int x=baseX-sizeX; x<=baseX+sizeX; x+=gridX) {
            drawLine(bufferBuilder, stack, x, x, y, y, baseZ-distance, baseZ+distance, lineColor.getRed()/255f, lineColor.getGreen()/255f, lineColor.getBlue()/255f);
        }
        for (int z=baseZ-sizeZ; z<=baseZ+sizeZ; z+=gridZ) {
            drawLine(bufferBuilder, stack, baseX-distance, baseX+distance, y, y, z, z, lineColor.getRed()/255f, lineColor.getGreen()/255f, lineColor.getBlue()/255f);
        }
    }
    
    private void drawSquare(BufferBuilder bufferBuilder, MatrixStack stack, float x, float y, float z, float r, float g, float b) {
        drawLine (bufferBuilder, stack, x+0.3f, x+0.7f, y, y, z+0.3f, z+0.3f, r, g, b);
        drawLine (bufferBuilder, stack, x+0.7f, x+0.7f, y, y, z+0.3f, z+0.7f, r, g, b);
        drawLine (bufferBuilder, stack, x+0.7f, x+0.3f, y, y, z+0.7f, z+0.7f, r, g, b);
        drawLine (bufferBuilder, stack, x+0.3f, x+0.3f, y, y, z+0.7f, z+0.3f, r, g, b);
    }
    
    private void drawXTriangleVertex(BufferBuilder builder, MatrixStack stack, float x, float y, float z, boolean inverted, float r, float g, float b) {
        float xMult = (inverted ? 1 : -1);
        drawLine(builder, stack, x+0.5f, x+0.5f+ 0.5f*xMult, y, y, z+0.5f, z+0.5f, r, g, b);
        drawLine(builder, stack, x+0.5f, x+0.5f-0.25f*xMult, y, y, z+0.5f, z+1.0f, r, g, b);
        drawLine(builder, stack, x+0.5f, x+0.5f-0.25f*xMult, y, y, z+0.5f, z+0.0f, r, g, b);
    }

    private void drawYTriangleVertex(BufferBuilder builder, MatrixStack stack, float x, float y, float z, boolean inverted, float r, float g, float b) {
        float xMult = (inverted ? 1 : -1);
        drawLine(builder, stack, x+0.5f, x+0.5f, y, y, z+0.5f, z+0.5f+ 0.5f*xMult, r, g, b);        
        drawLine(builder, stack, x+0.5f, x+1.0f, y, y, z+0.5f, z+0.5f-0.25f*xMult, r, g, b);
        drawLine(builder, stack, x+0.5f, x+0.0f, y, y, z+0.5f, z+0.5f-0.25f*xMult, r, g, b);
    }

    
    private void drawCircleSegment(BufferBuilder b, MatrixStack stack, float xc, float x1, float x2, float y, float zc, float z1, float z2, float red, float green, float blue) {
        drawLine(b, stack, xc+x1+0.5f, xc+x2+0.5f, y, y, zc+z1+0.5f, zc+z2+0.5f, red, green, blue);
        drawLine(b, stack, xc-x1+0.5f, xc-x2+0.5f, y, y, zc+z1+0.5f, zc+z2+0.5f, red, green, blue);
        drawLine(b, stack, xc+x1+0.5f, xc+x2+0.5f, y, y, zc-z1+0.5f, zc-z2+0.5f, red, green, blue);
        drawLine(b, stack, xc-x1+0.5f, xc-x2+0.5f, y, y, zc-z1+0.5f, zc-z2+0.5f, red, green, blue);
        drawLine(b, stack, xc+z1+0.5f, xc+z2+0.5f, y, y, zc+x1+0.5f, zc+x2+0.5f, red, green, blue);
        drawLine(b, stack, xc-z1+0.5f, xc-z2+0.5f, y, y, zc+x1+0.5f, zc+x2+0.5f, red, green, blue);
        drawLine(b, stack, xc+z1+0.5f, xc+z2+0.5f, y, y, zc-x1+0.5f, zc-x2+0.5f, red, green, blue);
        drawLine(b, stack, xc-z1+0.5f, xc-z2+0.5f, y, y, zc-x1+0.5f, zc-x2+0.5f, red, green, blue);
    }
    
    private void drawLine(BufferBuilder b, MatrixStack stack, float x1, float x2, float y1, float y2, float z1, float z2, float red, float green, float blue) {
        if (dump) {
            System.out.println("line from "+x1+","+y1+","+z1+" to "+x2+","+y2+","+z2);
        }
        Matrix4f model = stack.peek().getModel();
        b.vertex(model, x1, y1, z1).color(red, green, blue, 0.0f).next();
        b.vertex(model, x1, y1, z1).color(red, green, blue, 1.0f).next();
        b.vertex(model, x2, y2, z2).color(red, green, blue, 1.0f).next();
        b.vertex(model, x2, y2, z2).color(red, green, blue, 0.0f).next();
    }
    
    private void drawCross(BufferBuilder b, MatrixStack stack, float x, float y, float z, float red, float green, float blue, boolean twoLegs) {
        drawLine(b, stack, x+0.3f, x+0.7f, y, y, z+0.3f, z+0.7f, red, green, blue);
        if (twoLegs) {
            drawLine(b, stack, x+0.3f, x+0.7f, y, y, z+0.7f, z+0.3f, red, green, blue);
        }
    }
    
    private void drawDiamond(BufferBuilder b, MatrixStack stack, int x, int y, int z, float red, float green, float blue) {
        Matrix4f model = stack.peek().getModel();
        b.vertex(model, x+0.3f, y+0.05f, z+0.5f).color(red, green, blue, 0.0f).next();
        b.vertex(model, x+0.3f, y+0.05f, z+0.5f).color(red, green, blue, 1.0f).next();
        b.vertex(model, x+0.5f, y+0.05f, z+0.3f).color(red, green, blue, 1.0f).next();
        b.vertex(model, x+0.7f, y+0.05f, z+0.5f).color(red, green, blue, 1.0f).next();
        b.vertex(model, x+0.5f, y+0.05f, z+0.7f).color(red, green, blue, 1.0f).next();
        b.vertex(model, x+0.3f, y+0.05f, z+0.5f).color(red, green, blue, 1.0f).next();
        b.vertex(model, x+0.3f, y+0.05f, z+0.5f).color(red, green, blue, 0.0f).next();
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
            isHexes = false;
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
    
    private void cmdHex(ClientPlayerEntity sender) {
        if (isHexes) {
            isHexes = false;
        } else {
            isHexes = true;
            isCircles = false;
            showGrid = true;
        }
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
                    literal("hex").executes(c->{
                        instance.cmdHex(MinecraftClient.getInstance().player);
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
