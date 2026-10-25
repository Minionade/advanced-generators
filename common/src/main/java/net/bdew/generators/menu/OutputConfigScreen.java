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
            graphics.drawString(font,modeName(menu.outputMode(side)),left+26,rowY+5,0xff303030,false);
            if(menu.machine().kind()==MachineKind.EXCHANGER_CONTROLLER)
                graphics.drawString(font,selectionName(menu.outputSelection(side)),left+80,rowY+5,0xff202020,false);
            if(isPowerController(menu.machine().kind()))
                graphics.drawString(font,limitName(menu.outputLimitIndex(side)),left+80,rowY+5,0xff202020,false);
            if(mouseX>=left+7&&mouseX<left+24&&mouseY>=rowY&&mouseY<rowY+18)
                graphics.renderTooltip(font,Component.translatable("advgenerators.output."+side),mouseX,mouseY);
        }
        if(mouseX>=left+20&&mouseX<left+110&&mouseY>=top+18&&mouseY<top+132)
            graphics.renderTooltip(font,Component.literal(menu.machine().kind()==MachineKind.EXCHANGER_CONTROLLER ? "Left click: redstone mode | Right click: output fluid" : "Left click: redstone mode | Middle click: FE limit"),mouseX,mouseY);
        super.render(graphics,mouseX,mouseY,partialTick);
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
        if(mouseX>=left+20&&mouseX<left+110&&row>=0&&row<6) {
            int action=button==0?row:button==1&&menu.machine().kind()==MachineKind.EXCHANGER_CONTROLLER?6+row:button==2&&isPowerController(menu.machine().kind())?12+row:-1;
            if(action>=0&&minecraft!=null&&minecraft.gameMode!=null)
                minecraft.gameMode.handleInventoryButtonClick(menu.containerId,action);
            return true;
        }
        return super.mouseClicked(mouseX,mouseY,button);
    }

    @Override public void onClose() {
        if(minecraft!=null)minecraft.setScreen(parent);
    }
}
