package de.guntram.mcmod.grid;

import com.mojang.brigadier.CommandDispatcher;
import static com.mojang.brigadier.arguments.IntegerArgumentType.getInteger;
import static com.mojang.brigadier.arguments.IntegerArgumentType.integer;
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
import net.minecraft.util.text.TextComponentString;
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
    private boolean visible=false;
    private boolean isBlocks=true;

    KeyBinding showHide, gridHere, gridFixY;
    
    private boolean dump;
    private long lastDumpTime, thisDumpTime;

    @Override
    public void onInitialization() {
        MixinBootstrap.init();
        Mixins.addConfiguration("mixins.grid.json");
        instance=this;
    }
    
    public void renderOverlay(float partialTicks) {
        if (!visible)
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
        int baseX=playerX-playerXShift;
        int baseZ=playerZ-playerZShift;
        int sizeX=(distance/gridX)*gridX;
        int sizeZ=(distance/gridZ)*gridZ;
        float y=((float)(fixY==-1 ? entityplayer.posY : fixY)+0.05f);

        thisDumpTime=System.currentTimeMillis();
        dump=false;
//        if (thisDumpTime > lastDumpTime + 50000) {
//            dump=true;
//            lastDumpTime=thisDumpTime;
//        }
        
        if (isBlocks) {
            GlStateManager.lineWidth(3.0f);
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
        
        tessellator.draw();
        bufferBuilder.setTranslation(0, 0, 0);
        
        GlStateManager.lineWidth(1.0f);
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
    
    private void cmdShow(EntityPlayerSP sender) {
        visible = true;
        sender.sendMessage(new TextComponentString(I18n.format("msg.gridshown", (Object[]) null)));
    }
    
    private void cmdHide(EntityPlayerSP sender) {
        visible = false;
        sender.sendMessage(new TextComponentString(I18n.format("msg.gridhidden", (Object[]) null)));
    }
    
    private void cmdLines(EntityPlayerSP sender) {
        visible = true; isBlocks = false;
        sender.sendMessage(new TextComponentString(I18n.format("msg.gridlines", (Object[]) null)));
    }
    
    private void cmdBlocks(EntityPlayerSP sender) {
        visible = true; isBlocks = true;
        sender.sendMessage(new TextComponentString(I18n.format("msg.gridblocks", (Object[]) null)));
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
    }
}
