package net.bdew.generators.menu;

import net.bdew.generators.machine.MachineBlockEntity;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/** Compact vanilla-style generator readout, with the original input slots and live machine gauges. */
public final class MachineScreen extends AbstractContainerScreen<MachineMenu> {
    public MachineScreen(MachineMenu menu, Inventory inventory, Component title) {
        super(menu,inventory,title); imageWidth=176; imageHeight=184; inventoryLabelY=90;
    }
    @Override protected void renderBg(GuiGraphics g,float partial,int mouseX,int mouseY) {
        int x=(width-imageWidth)/2,y=(height-imageHeight)/2;
        g.fill(x,y,x+imageWidth,y+imageHeight,0xff282828);
        g.fill(x+2,y+2,x+imageWidth-2,y+imageHeight-2,0xffc6c6c6);
        g.fill(x+5,y+5,x+imageWidth-5,y+47,0xff373737);
        long cap=menu.capacity(); long stored=menu.energy();
        drawGauge(g,x+7,y+8,162,8,cap<=0?0:(double)stored/cap,0xff55aa44);
        var m=menu.machine();
        g.drawString(font,Component.translatable("advgenerators.gui.energy",stored,cap),x+8,y+19,0xffeeeeee,false);
        g.drawString(font,Component.translatable("advgenerators.gui.output",menu.generation(),menu.output()),x+8,y+32,0xffeeeeee,false);
        if(m.kind().controller() && (m.kind().name().contains("SYNGAS") || m.kind().name().contains("EXCHANGER"))) {
            g.drawString(font,Component.translatable("advgenerators.gui.heat",menu.heat()),x+8,y+68,0xff303030,false);
            if(m.kind().name().contains("SYNGAS")) g.drawString(font,Component.translatable("advgenerators.gui.carbon",menu.carbon()),x+8,y+80,0xff303030,false);
        }
        g.fill(x+3,y+96,x+imageWidth-3,y+imageHeight-3,0xff8b8b8b);
    }
    private void drawGauge(GuiGraphics g,int x,int y,int w,int h,double value,int color) {
        g.fill(x,y,x+w,y+h,0xff101010); int fill=(int)(Math.max(0,Math.min(1,value))*(w-2)); if(fill>0)g.fill(x+1,y+1,x+1+fill,y+h-1,color);
    }
    @Override protected void renderLabels(GuiGraphics g,int x,int y) {
        g.drawString(font,title,8,5,0xff404040,false);
        g.drawString(font,Component.translatable("container.inventory"),8,inventoryLabelY,0xff404040,false);
        if(menu.status()!=0) g.drawString(font,Component.translatable("advgenerators.status."+menu.status()),8,45,0xffff5555,false);
    }
    @Override public void render(GuiGraphics g,int mx,int my,float delta) { renderBackground(g,mx,my,delta); super.render(g,mx,my,delta); renderTooltip(g,mx,my); }
}
