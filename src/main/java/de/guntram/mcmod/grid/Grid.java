package de.guntram.mcmod.grid;

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
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.options.KeyBinding;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.resource.language.I18n;
import net.minecraft.client.util.InputUtil;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnRestriction;
import net.minecraft.text.LiteralText;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Matrix4f;
import net.minecraft.util.registry.MutableRegistry;
import net.minecraft.util.registry.Registry;
import net.minecraft.world.LightType;
import net.minecraft.world.SpawnHelper;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.chunk.ChunkSection;
import net.minecraft.world.chunk.WorldChunk;
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
    
    private float[] blockColor      = colorToRgb(0x8080ff);
    private float[] lineColor       = colorToRgb(0xff8000);
    private float[] circleColor     = colorToRgb(0x00e480);
    private float[] spawnNightColor = colorToRgb(0xffff00);
    private float[] spawnDayColor   = colorToRgb(0xff0000);
    private float[] biomeColor      = colorToRgb(0xff00ff);

    KeyBinding showHide, gridHere, gridFixY, gridSpawns;
    
    private boolean dump;
    private long lastDumpTime, thisDumpTime;
    
    private int[][] biomeCache;
    private int[][] spawnCache;
    int biomeUpdateX, spawnUpdateX;

    @Override
    public void onInitializeClient() {
        instance=this;
        ConfigurationHandler confHandler = ConfigurationHandler.getInstance();
        ConfigurationProvider.register(MODNAME, confHandler);
        confHandler.load(ConfigurationProvider.getSuggestedFile(MODID));
        
        blockColor =        colorToRgb(confHandler.blockColor);
        lineColor  =        colorToRgb(confHandler.lineColor);
        circleColor=        colorToRgb(confHandler.circleColor);
        spawnNightColor =   colorToRgb(confHandler.spawnNightColor);
        spawnDayColor =     colorToRgb(confHandler.spawnDayColor);
        biomeColor =        colorToRgb(confHandler.biomeColor);
        
        biomeCache = new int[256][];
        spawnCache = new int[256][];
        for (int i=0; i<256; i++) {
            spawnCache[i]=new int[256];
            biomeCache[i]=new int[256];
        }

        setKeyBindings();
        LOGGER = LogManager.getLogger(MODNAME);
    }
    
    private float[] colorToRgb(int color) {
        float[] result = new float[3];
        result[0] = ((color>>16)&0xff) / 255f;
        result[1] = ((color>> 8)&0xff) / 255f;
        result[2] = ((color>> 0)&0xff) / 255f;
        return result;
    }
    
    public void renderOverlay(float partialTicks, MatrixStack stack, VertexConsumer consumer, double cameraX, double cameraY, double cameraZ) {
        
        if (!showGrid && !showSpawns && showBiomes == null)
            return;

        Entity player = MinecraftClient.getInstance().getCameraEntity();
        stack.push();
        stack.translate(-cameraX, -cameraY, -cameraZ);

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
                
            stack.push();
            stack.translate(offsetX, 0, offsetZ);
            int circRadSquare=(gridX/2)*(gridX/2);
            if (isBlocks) {
                for (int x=baseX-sizeX; x<=baseX+sizeX; x+=gridX) {
                    for (int z=baseZ-sizeZ; z<=baseZ+sizeZ; z+=gridZ) {
                        if (isHexes) {
                            if (gridX >= gridZ) {
                                drawXTriangleVertex(consumer, stack, x, y, z,                   true,  blockColor[0], blockColor[1], blockColor[2]);       //  dot itself
                                drawXTriangleVertex(consumer, stack, x-gridZ/4f, y, z-gridZ/2f, false, blockColor[0], blockColor[1], blockColor[2]);       // left bottom of myself red
                                drawXTriangleVertex(consumer, stack, x+gridX/2f-gridZ/4f, y, z, false, blockColor[0], blockColor[1], blockColor[2]);       // node right orange
                                drawXTriangleVertex(consumer, stack, x+gridX/2f, y, z-gridZ/2f, true,  blockColor[0], blockColor[1], blockColor[2]);       // right bottom of right node yellowgreenish
                            } else {
                                drawYTriangleVertex(consumer, stack, x, y, z,                   true,  blockColor[0], blockColor[1], blockColor[2]);       //  dot itself
                                drawYTriangleVertex(consumer, stack, x-gridX/2f, y, z-gridX/4f, false, blockColor[0], blockColor[1], blockColor[2]);       // left bottom of myself red
                                drawYTriangleVertex(consumer, stack, x, y, z+gridZ/2f-gridX/4f, false, blockColor[0], blockColor[1], blockColor[2]);       // node right orange
                                drawYTriangleVertex(consumer, stack, x-gridX/2f, y, z+gridZ/2f, true,  blockColor[0], blockColor[1], blockColor[2]);       // right bottom of right node yellowgreenish
                            }
                        } else {
                            drawSquare(consumer, stack, x, y, z, blockColor[0], blockColor[1], blockColor[2]);
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
                                    drawCircleSegment(consumer, stack, x, dx, dz, y, z, dz, dx, circleColor[0], circleColor[1], circleColor[2]);
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
                                drawCircleSegment(consumer, stack, x, dx, nextx, y, z, dz, nextz, circleColor[0], circleColor[1], circleColor[2]);
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
                                drawLine(consumer, stack, x+0.5f, x+0.5f-gridZ/4f,                     y, y, z+0.5f, z+0.5f-gridZ/2f,          lineColor[0], lineColor[1], lineColor[2]);   // to LB
                                drawLine(consumer, stack, x+0.5f, x+0.5f-gridZ/4f,                     y, y, z+0.5f, z+0.5f+gridZ/2f,          lineColor[0], lineColor[1], lineColor[2]);   // to LT
                                drawLine(consumer, stack, x+0.5f, x+0.5f+gridX/2f-gridZ/4f,            y, y, z+0.5f, z+0.5f,                   lineColor[0], lineColor[1], lineColor[2]);   // to R
                                drawLine(consumer, stack, x+0.5f+gridX/2f-gridZ/4f, x+0.5f+gridX/2f,   y, y, z+0.5f, z+0.5f-gridZ/2f,          lineColor[0], lineColor[1], lineColor[2]);   // to RB
                                drawLine(consumer, stack, x+0.5f+gridX/2f-gridZ/4f, x+0.5f+gridX/2f,   y, y, z+0.5f, z+0.5f+gridZ/2f,          lineColor[0], lineColor[1], lineColor[2]);   // to RT
                                
                                drawLine(consumer, stack, x+0.5f+gridX/2f, x+0.5f+gridX-gridZ/4f,      y, y, z+0.5f+gridZ/2f, z+0.5f+gridZ/2f, lineColor[0], lineColor[1], lineColor[2]);   // left stump out
                                drawLine(consumer, stack, x+0.5f-gridZ/4f, x+0.5f-gridX/2f,            y, y, z+0.5f+gridZ/2f, z+0.5f+gridZ/2f, lineColor[0], lineColor[1], lineColor[2]);   // right stump out
                            } else {
                                drawLine(consumer, stack, x+0.5f, x+0.5f-gridX/2f,          y, y, z+0.5f, z+0.5f-gridX/4f,                     lineColor[0], lineColor[1], lineColor[2]);   // to LT
                                drawLine(consumer, stack, x+0.5f, x+0.5f+gridX/2f,          y, y, z+0.5f, z+0.5f-gridX/4f,                     lineColor[0], lineColor[1], lineColor[2]);   // to RT
                                drawLine(consumer, stack, x+0.5f, x+0.5f,                   y, y, z+0.5f, z+0.5f+gridZ/2f-gridX/4f,            lineColor[0], lineColor[1], lineColor[2]);   // to B
                                drawLine(consumer, stack, x+0.5f, x+0.5f-gridX/2f,          y, y, z+0.5f+gridZ/2f-gridX/4f, z+0.5f+gridZ/2f,   lineColor[0], lineColor[1], lineColor[2]);   // to LB
                                drawLine(consumer, stack, x+0.5f, x+0.5f+gridX/2f,          y, y, z+0.5f+gridZ/2f-gridX/4f, z+0.5f+gridZ/2f,   lineColor[0], lineColor[1], lineColor[2]);   // to RB
                                
                                drawLine(consumer, stack, x+0.5f+gridX/2f, x+0.5f+gridX/2f, y, y, z+0.5f+gridZ/2f, z+0.5f+gridZ-gridX/4f,      lineColor[0], lineColor[1], lineColor[2]);   // stump up
                                drawLine(consumer, stack, x+0.5f+gridX/2f, x+0.5f+gridX/2f, y, y, z+0.5f-gridX/4f, z+0.5f-gridZ/2f,            lineColor[0], lineColor[1], lineColor[2]);   // stump down
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
                                drawCircleSegment(consumer, stack, x, dx, nextx, y, z, dz, nextz, circleColor[0], circleColor[1], circleColor[2]);
                                dx=nextx;
                                dz=nextz;
                                if (nextz<nextx)
                                    break;
                            }
                            dump=false;
                        }   
                    }
                } else {
                    drawLineGrid(consumer, stack, baseX, baseZ, y, sizeX, sizeZ);                        
                }
            }
            stack.pop();
        }
        
        if (showSpawns) {
            showSpawns(consumer, stack, player, player.getBlockPos().getX(), player.getBlockPos().getZ());
        }
        
        if (showBiomes!=null) {
            showBiomes(consumer, stack, player, player.getBlockPos().getX(), player.getBlockPos().getZ());
        }

        stack.pop();
    }
    
    private void showSpawns(VertexConsumer consumer, MatrixStack stack, Entity player, int baseX, int baseZ) {
        int miny=(int)(player.getY())-16;
        int maxy=(int)(player.getY())+2;
        if (miny<0) { miny=0; }
        if (maxy>255) { maxy=255; }

        WorldChunk cachedChunk = null;

        spawnUpdateX++;
        if (spawnUpdateX < (baseX-distance) || spawnUpdateX > baseX+distance) {
            spawnUpdateX = baseX-distance;
        }
        boolean alwaysUpdate = !ConfigurationHandler.getUseCache();

        for (int x=baseX-distance; x<=baseX+distance; x++) {
            for (int z=baseZ-distance; z<=baseZ+distance; z++) {
                
                int display = 0;
                if (alwaysUpdate || x == spawnUpdateX) {
                    if (cachedChunk == null || cachedChunk.getPos().x != (x>>4) || cachedChunk.getPos().z != (z>>4)) {
                        cachedChunk=player.world.getChunk(x>>4, z>>4);
                    }
                    for (int y=maxy; y>=miny; y--) {
                        BlockState state;
                        BlockPos pos=new BlockPos(x, y, z);

                        ChunkSection section = cachedChunk.getSectionArray()[y>>4];
                        if (section == null || section.isEmpty()) {
                            //if (x==baseX && z==baseZ) System.out.println("section is empty for "+y);
                            continue;
                        } else {
                            state = section.getBlockState(x & 15, y & 15, z & 15);
                        }
                        if (state.isSolidBlock(player.world, pos)) {
                            if (y != maxy) {
                                BlockPos up = pos.up();
                                if (SpawnHelper.canSpawn(SpawnRestriction.Location.ON_GROUND, player.world, up, EntityType.COD)) {
                                    if (player.world.getLightLevel(LightType.BLOCK, up)>=lightLevel)
                                        display = 0x0000 | y;
                                    else if (player.world.getLightLevel(LightType.SKY, up)>=lightLevel)
                                        display = 0x1000 | y;
                                    else
                                        display = 0x2000 | y;
                                }
                            }
                            break;
                        }
                    }
                    spawnCache[x&0xff][z&0xff] = display;
                } else {
                    display = spawnCache[x&0xff][z&0xff];
                }

                if ((display & 0xf000) == 0x1000) {
                    drawCross(consumer, stack, x, (display&0xfff)+1.05f, z, spawnNightColor[0], spawnNightColor[1], spawnNightColor[2], false );
                } else if ((display & 0xf000) == 0x2000) {
                    drawCross(consumer, stack, x, (display&0xfff)+1.05f, z, spawnDayColor[0], spawnDayColor[1], spawnDayColor[2], true );
                }
            }
        }
    }
    
    private void showBiomes(VertexConsumer consumer, MatrixStack stack, Entity player, int baseX, int baseZ) {
        int miny=(int)(player.getY())-16;
        int maxy=(int)(player.getY());
        if (miny<0) { miny=0; }
        if (maxy>255) { maxy=255; }
        MutableRegistry<Biome> registry = player.world.getRegistryManager().get(Registry.BIOME_KEY);
        
        biomeUpdateX++;
        if (biomeUpdateX < (baseX-distance) || biomeUpdateX > baseX+distance) {
            biomeUpdateX = baseX-distance;
        }
        boolean alwaysUpdate = !ConfigurationHandler.getUseCache();
        
        for (int x=baseX-distance; x<=baseX+distance; x++) {
            for (int z=baseZ-distance; z<=baseZ+distance; z++) {
                int display = 0;
                if (alwaysUpdate || x == biomeUpdateX) {
                    boolean match = showBiomes.matcher(registry.getId(player.world.getBiome(new BlockPos(x, 64, z))).getPath()).find();
                    if (match) {
                        int y=(int)(player.getY());
                        while (y>=miny && isAir(player.world.getBlockState(new BlockPos(x, y, z)).getBlock())) {
                            y--;
                        }
                        display = 0x1000 + y;
                    } else {
                        display = 0;
                    }
                    biomeCache[x&0xff][z&0xff] = display;
                } else {
                    display = biomeCache[x&0xff][z&0xff];
                }
                if ((display & 0x1000) != 0) {
                    int y;
                    if (fixY == -1) {
                        y=display & 0xfff;
                    } else {
                        y=fixY-1;
                    }
                    drawDiamond(consumer, stack, x, y+1, z, biomeColor[0], biomeColor[1], biomeColor[2]);
                }
            }
        }
    }
    
    static private boolean isAir(Block block) {
        return block == Blocks.AIR || block == Blocks.CAVE_AIR || block == Blocks.VOID_AIR;
    }
    
    private void drawLineGrid(VertexConsumer consumer, MatrixStack stack, int baseX, int baseZ, float y, int sizeX, int sizeZ) {
        for (int x=baseX-sizeX; x<=baseX+sizeX; x+=gridX) {
            drawLine(consumer, stack, x, x, y, y, baseZ-distance, baseZ+distance, lineColor[0], lineColor[1], lineColor[2]);
        }
        for (int z=baseZ-sizeZ; z<=baseZ+sizeZ; z+=gridZ) {
            drawLine(consumer, stack, baseX-distance, baseX+distance, y, y, z, z, lineColor[0], lineColor[1], lineColor[2]);
        }
    }
    
    private void drawSquare(VertexConsumer consumer, MatrixStack stack, float x, float y, float z, float r, float g, float b) {
        drawLine (consumer, stack, x+0.3f, x+0.7f, y, y, z+0.3f, z+0.3f, r, g, b);
        drawLine (consumer, stack, x+0.7f, x+0.7f, y, y, z+0.3f, z+0.7f, r, g, b);
        drawLine (consumer, stack, x+0.7f, x+0.3f, y, y, z+0.7f, z+0.7f, r, g, b);
        drawLine (consumer, stack, x+0.3f, x+0.3f, y, y, z+0.7f, z+0.3f, r, g, b);
    }
    
    private void drawXTriangleVertex(VertexConsumer consumer, MatrixStack stack, float x, float y, float z, boolean inverted, float r, float g, float b) {
        float xMult = (inverted ? 1 : -1);
        drawLine(consumer, stack, x+0.5f, x+0.5f+ 0.5f*xMult, y, y, z+0.5f, z+0.5f, r, g, b);
        drawLine(consumer, stack, x+0.5f, x+0.5f-0.25f*xMult, y, y, z+0.5f, z+1.0f, r, g, b);
        drawLine(consumer, stack, x+0.5f, x+0.5f-0.25f*xMult, y, y, z+0.5f, z+0.0f, r, g, b);
    }

    private void drawYTriangleVertex(VertexConsumer consumer, MatrixStack stack, float x, float y, float z, boolean inverted, float r, float g, float b) {
        float xMult = (inverted ? 1 : -1);
        drawLine(consumer, stack, x+0.5f, x+0.5f, y, y, z+0.5f, z+0.5f+ 0.5f*xMult, r, g, b);        
        drawLine(consumer, stack, x+0.5f, x+1.0f, y, y, z+0.5f, z+0.5f-0.25f*xMult, r, g, b);
        drawLine(consumer, stack, x+0.5f, x+0.0f, y, y, z+0.5f, z+0.5f-0.25f*xMult, r, g, b);
    }

    
    private void drawCircleSegment(VertexConsumer consumer, MatrixStack stack, float xc, float x1, float x2, float y, float zc, float z1, float z2, float red, float green, float blue) {
        drawLine(consumer, stack, xc+x1+0.5f, xc+x2+0.5f, y, y, zc+z1+0.5f, zc+z2+0.5f, red, green, blue);
        drawLine(consumer, stack, xc-x1+0.5f, xc-x2+0.5f, y, y, zc+z1+0.5f, zc+z2+0.5f, red, green, blue);
        drawLine(consumer, stack, xc+x1+0.5f, xc+x2+0.5f, y, y, zc-z1+0.5f, zc-z2+0.5f, red, green, blue);
        drawLine(consumer, stack, xc-x1+0.5f, xc-x2+0.5f, y, y, zc-z1+0.5f, zc-z2+0.5f, red, green, blue);
        drawLine(consumer, stack, xc+z1+0.5f, xc+z2+0.5f, y, y, zc+x1+0.5f, zc+x2+0.5f, red, green, blue);
        drawLine(consumer, stack, xc-z1+0.5f, xc-z2+0.5f, y, y, zc+x1+0.5f, zc+x2+0.5f, red, green, blue);
        drawLine(consumer, stack, xc+z1+0.5f, xc+z2+0.5f, y, y, zc-x1+0.5f, zc-x2+0.5f, red, green, blue);
        drawLine(consumer, stack, xc-z1+0.5f, xc-z2+0.5f, y, y, zc-x1+0.5f, zc-x2+0.5f, red, green, blue);
    }
    
    private void drawLine(VertexConsumer consumer, MatrixStack stack, float x1, float x2, float y1, float y2, float z1, float z2, float red, float green, float blue) {
        if (dump) {
            System.out.println("line from "+x1+","+y1+","+z1+" to "+x2+","+y2+","+z2);
        }
        Matrix4f model = stack.peek().getModel();
        consumer.vertex(model, x1, y1, z1).color(red, green, blue, 1.0f).next();
        consumer.vertex(model, x2, y2, z2).color(red, green, blue, 1.0f).next();
    }
    
    private void drawCross(VertexConsumer consumer, MatrixStack stack, float x, float y, float z, float red, float green, float blue, boolean twoLegs) {
        drawLine(consumer, stack, x+0.3f, x+0.7f, y, y, z+0.3f, z+0.7f, red, green, blue);
        if (twoLegs) {
            drawLine(consumer, stack, x+0.3f, x+0.7f, y, y, z+0.7f, z+0.3f, red, green, blue);
        }
    }
    
    private void drawDiamond(VertexConsumer consumer, MatrixStack stack, int x, int y, int z, float red, float green, float blue) {
        Matrix4f model = stack.peek().getModel();
        float x1 = x+0.3f;
        float x2 = x+0.5f;
        float x3 = x+0.7f;
        float z1 = z+0.3f;
        float z2 = z+0.5f;
        float z3 = z+0.7f;
        float y1 = y+0.05f;

        consumer.vertex(model, x1, y1, z2).color(red, green, blue, 1.0f).next();
        consumer.vertex(model, x2, y1, z1).color(red, green, blue, 1.0f).next();
        
        consumer.vertex(model, x2, y1, z1).color(red, green, blue, 1.0f).next();
        consumer.vertex(model, x3, y1, z2).color(red, green, blue, 1.0f).next();
        
        consumer.vertex(model, x3, y1, z2).color(red, green, blue, 1.0f).next();
        consumer.vertex(model, x2, y1, z3).color(red, green, blue, 1.0f).next();
        
        consumer.vertex(model, x2, y1, z3).color(red, green, blue, 1.0f).next();
        consumer.vertex(model, x1, y1, z2).color(red, green, blue, 1.0f).next();
    }
    
    private void cmdShow(ClientPlayerEntity sender) {
        showGrid = true;
        sender.sendMessage(new LiteralText(I18n.translate("msg.gridshown", (Object[]) null)), false);
    }
    
    private void cmdHide(ClientPlayerEntity sender) {
        showGrid = false;
        sender.sendMessage(new LiteralText(I18n.translate("msg.gridhidden", (Object[]) null)), false);
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

            sender.sendMessage(new LiteralText(I18n.translate("msg.spawnshidden")), false);
            showSpawns=false;
        } else {
            sender.sendMessage(new LiteralText(I18n.translate("msg.spawnsshown", level)), false);
            showSpawns=true;
        }
    }
    
    private void cmdLines(ClientPlayerEntity sender) {
        showGrid = true; isBlocks = false;
        sender.sendMessage(new LiteralText(I18n.translate("msg.gridlines", (Object[]) null)), false);
    }
    
    private void cmdBlocks(ClientPlayerEntity sender) {
        showGrid = true; isBlocks = true;
        sender.sendMessage(new LiteralText(I18n.translate("msg.gridblocks", (Object[]) null)), false);
    }
    
    private void cmdCircles(ClientPlayerEntity sender) {
        if (isCircles) {
            isCircles = false;
            sender.sendMessage(new LiteralText(I18n.translate("msg.gridnomorecircles", (Object[]) null)), false);
        } else {
            isCircles = true;
            isHexes = false;
            showGrid = true;
            sender.sendMessage(new LiteralText(I18n.translate("msg.gridcircles", (Object[]) null)), false);
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
        sender.sendMessage(new LiteralText(I18n.translate("msg.gridaligned", (Object[]) null)), false);
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
            sender.sendMessage(new LiteralText(I18n.translate("msg.gridheightfloat")), false);
        }
    }

    private void cmdFixy(ClientPlayerEntity sender, int level) {
            fixY=level;
            sender.sendMessage(new LiteralText(I18n.translate("msg.gridheightfixed", fixY)), false);
    }
    
    private void cmdChunks(ClientPlayerEntity sender) {
        offsetX=offsetZ=0;
        gridX=gridZ=16;
        showGrid=true;
        sender.sendMessage(new LiteralText(I18n.translate("msg.gridchunks")), false);
    }
    
    private void cmdDistance(ClientPlayerEntity sender, int distance) {
        this.distance=distance;
        sender.sendMessage(new LiteralText(I18n.translate("msg.griddistance", distance)), false);
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
        	sender.sendMessage(new LiteralText(I18n.translate("msg.gridpattern", gridX, gridZ)), false);
        } else {
            sender.sendMessage(new LiteralText(I18n.translate("msg.gridcoordspositive")), false);
        }
    }
    
    private void cmdBiome(ClientPlayerEntity sender, String biome) {
        if (biome == null  || biome.isEmpty()) {
            showBiomes = null;
        } else {
            try {
                this.showBiomes=Pattern.compile(biome, Pattern.CASE_INSENSITIVE);
                sender.sendMessage(new LiteralText(I18n.translate("msg.biomesearching", biome)), false);
            } catch (PatternSyntaxException ex) {
                showBiomes = null;
                sender.sendMessage(new LiteralText(I18n.translate("msg.biomepatternbad", biome)), false);
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
        KeyBindingHelper.registerKeyBinding(showHide = new KeyBinding("key.grid.showhide", InputUtil.Type.KEYSYM, GLFW_KEY_B, category));
        KeyBindingHelper.registerKeyBinding(gridHere = new KeyBinding("key.grid.here", InputUtil.Type.KEYSYM, GLFW_KEY_C, category));
        KeyBindingHelper.registerKeyBinding(gridFixY = new KeyBinding("key.grid.fixy", InputUtil.Type.KEYSYM, GLFW_KEY_Y, category));
        KeyBindingHelper.registerKeyBinding(gridSpawns = new KeyBinding("key.grid.spawns", InputUtil.Type.KEYSYM, GLFW_KEY_L, category));
        ClientTickEvents.END_CLIENT_TICK.register(e->processKeyBinds());
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
