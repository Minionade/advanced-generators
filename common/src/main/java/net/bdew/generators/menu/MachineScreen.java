package net.bdew.generators.menu;

import net.bdew.generators.Generators;
import net.bdew.generators.machine.MachineKind;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;

/** Shared renderer for the original machine-specific Advanced Generators screens. */
public final class MachineScreen extends AbstractContainerScreen<MachineMenu> {
    private static final int TEXTURE_SIZE = 256;
    public MachineScreen(MachineMenu menu, Inventory inventory, Component title) {
        super(menu,inventory,title); imageWidth=176; imageHeight=175; inventoryLabelY=82;
    }
    @Override protected void init() {
        super.init();
        if (menu.machine().kind()==MachineKind.CONTROL) {
            addRenderableWidget(Button.builder(Component.literal("↻"),b -> clickMenuButton(0))
                .bounds(leftPos+98,topPos+37,18,18).build());
            addRenderableWidget(Button.builder(Component.literal("↻"),b -> clickMenuButton(1))
                .bounds(leftPos+62,topPos+37,18,18).build());
        } else if (menu.machine().kind()==MachineKind.SENSOR) {
            addRenderableWidget(Button.builder(Component.literal("↻"),b -> clickMenuButton(0))
                .bounds(leftPos+53,topPos+37,18,18).build());
            addRenderableWidget(Button.builder(Component.literal("↻"),b -> clickMenuButton(1))
                .bounds(leftPos+71,topPos+37,18,18).build());
        }
    }
    private void clickMenuButton(int id) {
        if (minecraft.gameMode != null) minecraft.gameMode.handleInventoryButtonClick(menu.containerId,id);
    }
    @Override public boolean mouseClicked(double mouseX,double mouseY,int button) {
        int x=(int)mouseX-leftPos,y=(int)mouseY-topPos;
        if(menu.machine().kind().controller()&&x>=153&&x<170) {
            if(y>=19&&y<36) { minecraft.setScreen(new OutputConfigScreen(menu,this)); return true; }
            if(y>=61&&y<78&&button==0) { clickMenuButton(18); return true; }
        }
        return super.mouseClicked(mouseX,mouseY,button);
    }
    @Override protected void renderBg(GuiGraphics g,float partial,int mouseX,int mouseY) {
        int x=(width-imageWidth)/2,y=(height-imageHeight)/2;
        g.blit(background(),x,y,0,0,imageWidth,imageHeight,TEXTURE_SIZE,TEXTURE_SIZE);
        drawGauges(g,x,y);
        drawMachineInfo(g,x,y);
    }
    private void drawGauges(GuiGraphics g,int x,int y) {
        switch(menu.machine().kind()) {
            case FUEL_TURBINE_CONTROLLER -> drawVertical(g,x+9,y+19,9,58,menu.bufferFill(0),0xffe2a43b);
            case STEAM_TURBINE_CONTROLLER -> drawVertical(g,x+9,y+19,9,58,menu.bufferFill(0),0xff58b6df);
            case SYNGAS_CONTROLLER -> {
                drawVertical(g,x+11,y+19,10,35,menu.bufferFill(0),0xff519ee0);
                drawVertical(g,x+57,y+19,10,35,menu.bufferFill(2),0xff57c59a);
                drawHorizontal(g,x+30,y+61,37,8,menu.bufferFill(1),0xff55b8e8);
                drawHorizontal(g,x+30,y+69,37,8,menu.carbonFill(),0xffa9a9a9);
                int heat=(int)(12*menu.heatFill()); if(heat>0)g.fill(x+10,y+75-heat,x+12,y+75,0x99ff5722);
            }
            case EXCHANGER_CONTROLLER -> {
                drawVertical(g,x+9,y+19,9,58,menu.bufferFill(1),0xff60a9de);
                drawHorizontal(g,x+21,y+19,37,16,menu.bufferFill(0),0xffe4a044);
                drawVertical(g,x+61,y+19,9,58,menu.bufferFill(2),0xff64b3df);
                drawHorizontal(g,x+21,y+61,37,16,menu.bufferFill(3),0xffe4a044);
            }
            default -> { }
        }
        if(menu.machine().kind()==MachineKind.FUEL_TURBINE_CONTROLLER||menu.machine().kind()==MachineKind.STEAM_TURBINE_CONTROLLER)
            drawVertical(g,x+61,y+19,9,58,menu.capacity()<=0?0:(double)menu.energy()/menu.capacity(),0xff65b95a);
    }
    private static void drawVertical(GuiGraphics g,int x,int y,int w,int h,double amount,int color) {
        int fill=(int)Math.round((h-2)*Math.max(0,Math.min(1,amount)));
        if(fill>0)g.fill(x+1,y+h-1-fill,x+w-1,y+h-1,color);
    }
    private static void drawHorizontal(GuiGraphics g,int x,int y,int w,int h,double amount,int color) {
        int fill=(int)Math.round((w-2)*Math.max(0,Math.min(1,amount)));
        if(fill>0)g.fill(x+1,y+1,x+1+fill,y+h-1,color);
    }
    private ResourceLocation background() {
        String name = switch(menu.machine().kind()) {
            case SYNGAS_CONTROLLER -> "syngas";
            case EXCHANGER_CONTROLLER -> "exchanger";
            case FUEL_TURBINE_CONTROLLER, STEAM_TURBINE_CONTROLLER -> "turbine";
            case CONTROL -> "control";
            case SENSOR -> "sensor";
            default -> "turbine";
        };
        return Generators.id("textures/gui/"+name+".png");
    }
    private void drawMachineInfo(GuiGraphics g,int x,int y) {
        var machine=menu.machine();
        if(machine.kind()==MachineKind.SYNGAS_CONTROLLER) {
            drawInfo(g,x,y,21,format(menu.heat())+" HU");
            drawInfo(g,x,y,32,"Carbon "+format(menu.consumption())+"/t");
            drawInfo(g,x,y,43,"Gas "+format(menu.process())+"/t");
        } else if(machine.kind()==MachineKind.EXCHANGER_CONTROLLER) {
            drawInfo(g,x,y,21,format(menu.heat())+" HU");
            drawInfo(g,x,y,32,"Max "+format(menu.output())+" HU/t");
            drawInfo(g,x,y,43,"Loss "+format(menu.heatLoss())+" HU/t");
            drawInfo(g,x,y,54,"Hot in "+format(menu.consumption())+" HU/t");
            drawInfo(g,x,y,65,"Cold out "+format(menu.process())+" HU/t");
        } else if(machine.kind()==MachineKind.FUEL_TURBINE_CONTROLLER || machine.kind()==MachineKind.STEAM_TURBINE_CONTROLLER) {
            drawInfo(g,x,y,21,menu.turbines()+" turbines");
            drawInfo(g,x,y,32,"Peak "+format(menu.output())+" FE/t");
            drawInfo(g,x,y,43,format(menu.rpm())+" RPM");
            drawInfo(g,x,y,54,"Out "+format(menu.generation())+" FE/t");
            drawInfo(g,x,y,65,(machine.kind()==MachineKind.STEAM_TURBINE_CONTROLLER?"Steam ":"Fuel ")+format(menu.consumption())+"/t");
        } else if(machine.kind()==MachineKind.CONTROL) {
            String[] actions={"disabled","useFuel","useSteam","generateEnergy","exchangeHeat","heatWater","mix"};
            g.drawString(font,Component.translatable(menu.controlHigh()?"advgenerators.control.mode.high":"advgenerators.control.mode.low"),x+8,y+61,0xff404040,false);
            g.drawString(font,Component.translatable("advgenerators.control.action."+actions[menu.controlAction()]),x+8,y+72,0xff404040,false);
        } else if(machine.kind()==MachineKind.SENSOR) {
            g.drawString(font,Component.translatable("advgenerators.sensor.metric."+sensorControllerId()+"."+menu.sensorMetric()),x+8,y+61,0xff404040,false);
            g.drawString(font,Component.translatable("advgenerators.sensor.condition."+menu.sensorCondition()),x+8,y+72,0xff404040,false);
        }
    }
    private String sensorControllerId() {
        var controller = menu.machine().controller();
        return controller == null ? "none" : controller.kind().id();
    }
    private void drawInfo(GuiGraphics g,int x,int y,int row,String value) {
        g.drawString(font,Component.literal(value),x+93,y+row,0xff404040,false);
    }
    private static String format(double value) {
        return String.format(java.util.Locale.ROOT,"%.1f",value).replaceAll("\\.0$","");
    }
    @Override protected void renderLabels(GuiGraphics g,int x,int y) {
        g.drawString(font,title,8,5,0xff404040,false);
        g.drawString(font,Component.translatable("container.inventory"),8,inventoryLabelY,0xff404040,false);
    }
    @Override public void render(GuiGraphics g,int mx,int my,float delta) { renderBackground(g,mx,my,delta); super.render(g,mx,my,delta); renderTooltip(g,mx,my); }
}
