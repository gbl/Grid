package de.guntram.mcmod.grid;

import com.mojang.blaze3d.matrix.MatrixStack;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.brigadier.CommandDispatcher;
import static com.mojang.brigadier.arguments.IntegerArgumentType.getInteger;
import static com.mojang.brigadier.arguments.IntegerArgumentType.integer;
import static com.mojang.brigadier.arguments.StringArgumentType.getString;
import static com.mojang.brigadier.arguments.StringArgumentType.string;
import static com.mojang.brigadier.builder.LiteralArgumentBuilder.literal;
import static com.mojang.brigadier.builder.RequiredArgumentBuilder.argument;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import de.guntram.mcmod.GBForgetools.ConfigurationProvider;
import java.awt.Color;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.player.ClientPlayerEntity;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.Matrix4f;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.client.resources.I18n;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.StringTextComponent;
import net.minecraft.world.LightType;
import net.minecraftforge.client.event.ClientChatEvent;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.client.registry.ClientRegistry;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_B;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_C;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_L;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_Y;

@Mod("grid")

public class Grid
{
    static final String MODID="grid";
    static final String MODNAME="Grid";
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

    KeyBinding showHide, gridHere, gridFixY, gridSpawns;
    
    private boolean dump;
    private long lastDumpTime, thisDumpTime;
    
    CommandDispatcher cd;
    
    public Grid() {
        IEventBus bus = FMLJavaModLoadingContext.get().getModEventBus();
        bus.addListener(this::init);
    }
    
    public void init(final FMLCommonSetupEvent event) {
        cd = new CommandDispatcher();
        registerCommands(cd);
        MinecraftForge.EVENT_BUS.register(this);
        onInitializeClient();
    }

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
    
    @SubscribeEvent
    public void onRender(RenderWorldLastEvent e) {
        renderOverlay(e.getPartialTicks(), e.getMatrixStack());
    }
    
    public void renderOverlay(float partialTicks, MatrixStack stack) {
        if (!showGrid && !showSpawns && showBiomes == null)
            return;

        Entity player = Minecraft.getInstance().getRenderViewEntity();
        double cameraX = player.lastTickPosX + (player.getPosX() - player.lastTickPosX) * (double)partialTicks;
        double cameraY = player.lastTickPosY + (player.getPosY() - player.lastTickPosY) * (double)partialTicks + player.getEyeHeight(player.getPose());
        double cameraZ = player.lastTickPosZ + (player.getPosZ() - player.lastTickPosZ) * (double)partialTicks;        
        stack.push();
        stack.translate(-cameraX, -cameraY, -cameraZ);

        RenderSystem.disableTexture();
        RenderSystem.disableBlend();
        RenderSystem.enableAlphaTest();
        RenderSystem.enableDepthTest();

        Tessellator tessellator=Tessellator.getInstance();
        BufferBuilder bufferBuilder = tessellator.getBuffer();
        
        int playerX=(int) Math.floor(player.getPosX());
        int playerZ=(int) Math.floor(player.getPosZ());
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
            float tempy=((float)(fixY==-1 ? player.lastTickPosY + (player.getPosY() - player.lastTickPosY) * (double)partialTicks : fixY));
            final float y;
            if (player.getPosY()+player.getEyeHeight(player.getPose()) > tempy) {
                y=tempy+0.05f;
            } else {
                y=tempy-0.05f;
            }
                
            RenderSystem.lineWidth(3.0f);
            stack.push();
            stack.translate(offsetX, 0, offsetZ);
            bufferBuilder.begin(3, DefaultVertexFormats.POSITION_COLOR);
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
            bufferBuilder.begin(3, DefaultVertexFormats.POSITION_COLOR);
            showSpawns(bufferBuilder, stack, player, baseX, baseZ);
            tessellator.draw();
        }
        
        if (showBiomes!=null) {
            RenderSystem.lineWidth(1.0f);
            bufferBuilder.begin(3, DefaultVertexFormats.POSITION_COLOR);
            showBiomes(bufferBuilder, stack, player, baseX, baseZ);
            tessellator.draw();
        }

        stack.pop();
        
        RenderSystem.enableBlend();
        RenderSystem.enableTexture();
    }
    
    private void showSpawns(BufferBuilder bufferBuilder, MatrixStack stack, Entity player, int baseX, int baseZ) {
        int miny=(int)(player.getPosY())-16;
        int maxy=(int)(player.getPosY())+2;
        if (miny<0) { miny=0; }
        if (maxy>255) { maxy=255; }
        for (int x=baseX-distance; x<=baseX+distance; x++) {
            for (int z=baseZ-distance; z<=baseZ+distance; z++) {
                for (int y=miny; y<=maxy; y++) {
                    BlockPos pos=new BlockPos(x, y, z);
                    int spawnmode;
                    if (fastSpawnCheck && player.world.getBlockState(pos.down()).isOpaqueCube(player.world, pos.down())
                    /* ||  !fastSpawnCheck && SpawnHelper.canSpawn(SpawnRestriction.Location.ON_GROUND, player.world, pos, EntityType.COD )*/) {
                        if (player.world.getLightFor(LightType.BLOCK, pos)>=lightLevel)
                            continue;
                        else if (player.world.getLightFor(LightType.SKY, pos)>=lightLevel)
                            drawCross(bufferBuilder, stack, x, y+0.05f, z, spawnNightColor.getRed()/255f, spawnNightColor.getGreen()/255f, spawnNightColor.getBlue()/255f, false );
                        else
                            drawCross(bufferBuilder, stack, x, y+0.05f, z, spawnDayColor.getRed()/255f, spawnDayColor.getGreen()/255f, spawnDayColor.getBlue()/255f, true );
                    }
                }
            }
        }
    }
    
    private void showBiomes(BufferBuilder bufferBuilder, MatrixStack stack, Entity player, int baseX, int baseZ) {
        int miny=(int)(player.getPosY())-16;
        int maxy=(int)(player.getPosY());
        if (miny<0) { miny=0; }
        if (maxy>255) { maxy=255; }
        for (int x=baseX-distance; x<=baseX+distance; x++) {
            for (int z=baseZ-distance; z<=baseZ+distance; z++) {
                if (showBiomes.matcher(player.world.getBiome(new BlockPos(x, 1, z)).getDisplayName().getString()).find()) {
                    int y;
                    if (fixY == -1) {
                        y=(int)(player.getPosY());
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
        Matrix4f model = stack.getLast().getMatrix();
        b.pos(model, x1, y1, z1).color(red, green, blue, 0.0f).endVertex();
        b.pos(model, x1, y1, z1).color(red, green, blue, 1.0f).endVertex();
        b.pos(model, x2, y2, z2).color(red, green, blue, 1.0f).endVertex();
        b.pos(model, x2, y2, z2).color(red, green, blue, 0.0f).endVertex();
    }
    
    private void drawCross(BufferBuilder b, MatrixStack stack, float x, float y, float z, float red, float green, float blue, boolean twoLegs) {
        drawLine(b, stack, x+0.3f, x+0.7f, y, y, z+0.3f, z+0.7f, red, green, blue);
        if (twoLegs) {
            drawLine(b, stack, x+0.3f, x+0.7f, y, y, z+0.7f, z+0.3f, red, green, blue);
        }
    }
    
    private void drawDiamond(BufferBuilder b, MatrixStack stack, int x, int y, int z, float red, float green, float blue) {
        Matrix4f model = stack.getLast().getMatrix();
        b.pos(model, x+0.3f, y+0.05f, z+0.5f).color(red, green, blue, 0.0f).endVertex();
        b.pos(model, x+0.3f, y+0.05f, z+0.5f).color(red, green, blue, 1.0f).endVertex();
        b.pos(model, x+0.5f, y+0.05f, z+0.3f).color(red, green, blue, 1.0f).endVertex();
        b.pos(model, x+0.7f, y+0.05f, z+0.5f).color(red, green, blue, 1.0f).endVertex();
        b.pos(model, x+0.5f, y+0.05f, z+0.7f).color(red, green, blue, 1.0f).endVertex();
        b.pos(model, x+0.3f, y+0.05f, z+0.5f).color(red, green, blue, 1.0f).endVertex();
        b.pos(model, x+0.3f, y+0.05f, z+0.5f).color(red, green, blue, 0.0f).endVertex();
    }
    
    private void cmdShow(ClientPlayerEntity sender) {
        showGrid = true;
        sender.sendStatusMessage(new StringTextComponent(I18n.format("msg.gridshown", (Object[]) null)), false);
    }
    
    private void cmdHide(ClientPlayerEntity sender) {
        showGrid = false;
        sender.sendStatusMessage(new StringTextComponent(I18n.format("msg.gridhidden", (Object[]) null)), false);
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

            sender.sendStatusMessage(new StringTextComponent(I18n.format("msg.spawnshidden")), false);
            showSpawns=false;
        } else {
            sender.sendStatusMessage(new StringTextComponent(I18n.format("msg.spawnsshown", level)), false);
            showSpawns=true;
        }
    }
    
    private void cmdLines(ClientPlayerEntity sender) {
        showGrid = true; isBlocks = false;
        sender.sendStatusMessage(new StringTextComponent(I18n.format("msg.gridlines", (Object[]) null)), false);
    }
    
    private void cmdBlocks(ClientPlayerEntity sender) {
        showGrid = true; isBlocks = true;
        sender.sendStatusMessage(new StringTextComponent(I18n.format("msg.gridblocks", (Object[]) null)), false);
    }
    
    private void cmdCircles(ClientPlayerEntity sender) {
        if (isCircles) {
            isCircles = false;
            sender.sendStatusMessage(new StringTextComponent(I18n.format("msg.gridnomorecircles", (Object[]) null)), false);
        } else {
            isCircles = true;
            isHexes = false;
            showGrid = true;
            sender.sendStatusMessage(new StringTextComponent(I18n.format("msg.gridcircles", (Object[]) null)), false);
        }
    }
    
    private void cmdHere(ClientPlayerEntity sender) {
        int playerX=(int) Math.floor(sender.getPosX());
        int playerZ=(int) Math.floor(sender.getPosZ());
        int playerXShift=Math.floorMod(playerX, gridX);
        int playerZShift=Math.floorMod(playerZ, gridZ);                
        offsetX=playerXShift;
        offsetZ=playerZShift;
        showGrid=true;
        sender.sendStatusMessage(new StringTextComponent(I18n.format("msg.gridaligned", (Object[]) null)), false);
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
            cmdFixy(sender, (int)Math.floor(sender.getPosY()));
        } else {
            fixY=-1;
            sender.sendStatusMessage(new StringTextComponent(I18n.format("msg.gridheightfloat")), false);
        }
    }

    private void cmdFixy(ClientPlayerEntity sender, int level) {
            fixY=level;
            sender.sendStatusMessage(new StringTextComponent(I18n.format("msg.gridheightfixed", fixY)), false);
    }
    
    private void cmdChunks(ClientPlayerEntity sender) {
        offsetX=offsetZ=0;
        gridX=gridZ=16;
        showGrid=true;
        sender.sendStatusMessage(new StringTextComponent(I18n.format("msg.gridchunks")), false);
    }
    
    private void cmdDistance(ClientPlayerEntity sender, int distance) {
        this.distance=distance;
        sender.sendStatusMessage(new StringTextComponent(I18n.format("msg.griddistance", distance)), false);
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
        	sender.sendStatusMessage(new StringTextComponent(I18n.format("msg.gridpattern", gridX, gridZ)), false);
        } else {
            sender.sendStatusMessage(new StringTextComponent(I18n.format("msg.gridcoordspositive")), false);
        }
    }
    
    private void cmdBiome(ClientPlayerEntity sender, String biome) {
        if (biome == null  || biome.isEmpty()) {
            showBiomes = null;
        } else {
            try {
                this.showBiomes=Pattern.compile(biome, Pattern.CASE_INSENSITIVE);
                sender.sendStatusMessage(new StringTextComponent(I18n.format("msg.biomesearching", biome)), false);
            } catch (PatternSyntaxException ex) {
                showBiomes = null;
                sender.sendStatusMessage(new StringTextComponent(I18n.format("msg.biomepatternbad", biome)), false);
            }
        }
    }

    public void registerCommands(CommandDispatcher cd) {
        cd.register(
            literal("grid")
                .then(
                    literal("show").executes(c->{
                        instance.cmdShow(Minecraft.getInstance().player);
                        return 1;
                    })
                )
                .then(
                    literal("hide").executes(c->{
                        instance.cmdHide(Minecraft.getInstance().player);
                        return 1;
                    })
                )
                .then(
                    literal("lines").executes(c->{
                        instance.cmdLines(Minecraft.getInstance().player);
                        return 1;
                    })
                )
                .then(
                    literal("blocks").executes(c->{
                        instance.cmdBlocks(Minecraft.getInstance().player);
                        return 1;
                    })
                )
                .then(
                    literal("circles").executes(c->{
                        instance.cmdCircles(Minecraft.getInstance().player);
                        return 1;
                    })
                )
                .then(
                    literal("here").executes(c->{
                        instance.cmdHere(Minecraft.getInstance().player);
                        return 1;
                    })
                )
                .then(
                    literal("hex").executes(c->{
                        instance.cmdHex(Minecraft.getInstance().player);
                        return 1;
                    })
                )
                .then(
                    literal("fixy").then(
                        argument("y", integer()).executes(c->{
                            instance.cmdFixy(Minecraft.getInstance().player, getInteger(c, "y"));
                            return 1;
                        })
                    ).executes(c->{
                        instance.cmdFixy(Minecraft.getInstance().player);
                        return 1;
                    })
                )
                .then(
                    literal("chunks").executes(c->{
                        instance.cmdChunks(Minecraft.getInstance().player);
                        return 1;
                    })
                )
                .then(
                    literal("spawns").then(
					    argument("lightlevel", integer()).executes(c->{
							instance.cmdSpawns(Minecraft.getInstance().player, ""+getInteger(c, "lightlevel"));
                            return 1;
						})
					).executes(c->{
                        instance.cmdSpawns(Minecraft.getInstance().player, null);
                        return 1;
                    })
                )
                .then(
                    literal("distance").then (
                        argument("distance", integer()).executes(c->{
                            instance.cmdDistance(Minecraft.getInstance().player, getInteger(c, "distance"));
                            return 1;
                        })
                    )
                )
                .then(
                    argument("x", integer()).then (
                        argument("z", integer()).executes(c->{
                            instance.cmdXZ(Minecraft.getInstance().player, getInteger(c, "x"), getInteger(c, "z"));
                            return 1;
                        })
                    ).executes(c->{
                        instance.cmdXZ(Minecraft.getInstance().player, getInteger(c, "x"), getInteger(c, "x"));
                        return 1;
                    })
                )
                .then(
                    literal("biome").then(
					    argument("pattern", string()).executes(c->{
							instance.cmdBiome(Minecraft.getInstance().player, ""+getString(c, "pattern"));
                            return 1;
						})
					).executes(c->{
                        instance.cmdBiome(Minecraft.getInstance().player, null);
                        return 1;
                    })
                )
        );
    }
    
    @SubscribeEvent
    public void chatSent(final ClientChatEvent e) {
        if (e.getOriginalMessage().startsWith("/grid")) {
            // System.out.println("execute chat "+e.getOriginalMessage().substring(1));
            try {
                cd.execute(e.getOriginalMessage().substring(1), e);
            } catch (CommandSyntaxException ex) {
                Minecraft.getInstance().player.sendStatusMessage(new StringTextComponent(ex.getMessage()), false);
            }
            e.setCanceled(true);
        }
    }

    public void setKeyBindings() {
        final String category="key.categories.grid";
        ClientRegistry.registerKeyBinding(showHide = new KeyBinding("key.showhide", GLFW_KEY_B, "key.categories.grid"));
        ClientRegistry.registerKeyBinding(gridHere = new KeyBinding("key.gridhere", GLFW_KEY_C, "key.categories.grid"));
        ClientRegistry.registerKeyBinding(gridFixY = new KeyBinding("key.gridfixy", GLFW_KEY_Y, "key.categories.grid"));
        ClientRegistry.registerKeyBinding(gridSpawns = new KeyBinding("key.gridspawns", GLFW_KEY_L, "key.categories.grid"));
    }
    
    @SubscribeEvent
    public void keyPressed(final InputEvent.KeyInputEvent e) {
        processKeyBinds();
    }

    public void processKeyBinds() {
        ClientPlayerEntity player = Minecraft.getInstance().player;
        if (showHide.isPressed()) {
            showGrid=!showGrid;
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
