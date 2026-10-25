package net.bdew.generators.menu;

import net.bdew.generators.Generators;
import net.bdew.generators.machine.MachineKind;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

/** Six-face output editor; left click changes redstone mode, right click changes output selection. */
public final class OutputConfigScreen extends Screen {
    private static final ResourceLocation BACKGROUND=Generators.id("textures/gui/outputs.png");
    private static final ResourceLocation WIDGETS=Generators.id("textures/gui/widgets.png");
    private static final int[][] COLORS={{0xffc94a4a},{0xff61b34b},{0xff5687d6},{0xffd7b63e},{0xff45bfc2},{0xffa65cc4}};
    private final MachineMenu menu;
    private final MachineScreen parent;
    private int left,top;

    public OutputConfigScreen(MachineMenu menu,MachineScreen parent) {
        super(Component.translatable("advgenerators.gui.output.title"));
        this.menu=menu;
        this.parent=parent;
    }

    @Override protected void init() {
        left=(width-124)/2;
        top=(height-138)/2;
    }

    @Override public void render(GuiGraphics graphics,int mouseX,int mouseY,float partialTick) {
        renderBackground(graphics,mouseX,mouseY,partialTick);
        graphics.blit(BACKGROUND,left,top,0,0,124,138,256,256);
        graphics.drawString(font,title,left+8,top+6,0xff404040,false);
        for(int side=0;side<6;side++) {
            int rowY=top+19+side*19;
            graphics.fill(left+9,rowY+2,left+21,rowY+14,COLORS[side][0]);
            int[] icon=modeIcon(menu.outputMode(side));
            drawButton(graphics,left+25,rowY,mouseX,mouseY,icon[0],icon[1]);
            graphics.drawString(font,modeName(menu.outputMode(side)),left+44,rowY+5,0xff303030,false);
            if(menu.machine().kind()==MachineKind.EXCHANGER_CONTROLLER) {
                drawButton(graphics,left+79,rowY,mouseX,mouseY,20,35);
                graphics.drawString(font,selectionName(menu.outputSelection(side)),left+98,rowY+5,0xff202020,false);
            } else if(isPowerController(menu.machine().kind())) {
                drawButton(graphics,left+79,rowY,mouseX,mouseY,20,35);
                graphics.drawString(font,limitName(menu.outputLimitIndex(side)),left+98,rowY+5,0xff202020,false);
            }
            if(mouseX>=left+7&&mouseX<left+24&&mouseY>=rowY&&mouseY<rowY+18)
                graphics.renderTooltip(font,Component.translatable("advgenerators.output."+side),mouseX,mouseY);
        }
        int hoverRow=(mouseY-(top+18))/19;
        if(hoverRow>=0&&hoverRow<6&&mouseX>=left+25&&mouseX<left+77)
            graphics.renderTooltip(font,Component.translatable("advgenerators.gui.output.mode",modeName(menu.outputMode(hoverRow))),mouseX,mouseY);
        else if(hoverRow>=0&&hoverRow<6&&mouseX>=left+79&&mouseX<left+112) {
            String key=menu.machine().kind()==MachineKind.EXCHANGER_CONTROLLER?"advgenerators.gui.output.selection":"advgenerators.gui.output.limit";
            graphics.renderTooltip(font,Component.translatable(key),mouseX,mouseY);
        }
        super.render(graphics,mouseX,mouseY,partialTick);
    }

    private static void drawButton(GuiGraphics g,int x,int y,int mx,int my,int iconU,int iconV) {
        g.blit(WIDGETS,x,y,mx>=x&&mx<x+16&&my>=y&&my<y+16?36:20,18,16,16,256,256);
        g.blit(WIDGETS,x+1,y+1,iconU,iconV,14,14,256,256);
    }
    private static int[] modeIcon(int mode) {
        return switch(mode) { case 1 -> new int[]{37,35}; case 2 -> new int[]{21,35}; case 3 -> new int[]{69,35}; default -> new int[]{53,35}; };
    }

    private static String modeName(int mode) {
        return switch(mode) { case 1 -> "Signal"; case 2 -> "No sig"; case 3 -> "Off"; default -> "Always"; };
    }
    private static String selectionName(int selection) {
        return switch(selection) { case 1 -> "Cold"; case 2 -> "Hot"; default -> "Both"; };
    }
    private static String limitName(int limit) {
        return switch(limit) { case 0 -> "8k"; case 1 -> "32k"; case 2 -> "128k"; default -> "Max"; };
    }
    private static boolean isPowerController(MachineKind kind) {
        return kind==MachineKind.FUEL_TURBINE_CONTROLLER||kind==MachineKind.STEAM_TURBINE_CONTROLLER;
    }

    @Override public boolean mouseClicked(double mouseX,double mouseY,int button) {
        int row=(int)((mouseY-(top+18))/19);
        if(row>=0&&row<6&&mouseX>=left+25&&mouseX<left+77&&button==0) {
            int action=row;
            if(action>=0&&minecraft!=null&&minecraft.gameMode!=null)
                minecraft.gameMode.handleInventoryButtonClick(menu.containerId,action);
            return true;
        }
        if(row>=0&&row<6&&mouseX>=left+79&&mouseX<left+112) {
            int action=menu.machine().kind()==MachineKind.EXCHANGER_CONTROLLER&&(button==0||button==1)?6+row:
                    isPowerController(menu.machine().kind())&&button==2?12+row:-1;
            if(action>=0&&minecraft!=null&&minecraft.gameMode!=null) minecraft.gameMode.handleInventoryButtonClick(menu.containerId,action);
            return action>=0;
        }
        return super.mouseClicked(mouseX,mouseY,button);
    }

    @Override public void onClose() {
        if(minecraft!=null)minecraft.setScreen(parent);
    }
}
