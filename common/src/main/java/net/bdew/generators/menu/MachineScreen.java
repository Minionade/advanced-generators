package net.bdew.generators.menu;

import net.bdew.generators.Generators;
import net.bdew.generators.machine.MachineKind;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;

/** Shared renderer for the original machine-specific Advanced Generators screens. */
public final class MachineScreen extends AbstractContainerScreen<MachineMenu> {
    private static final int TEXTURE_SIZE = 256;
    private static final ResourceLocation WIDGETS = Generators.id("textures/gui/widgets.png");
    private boolean outputConfigOpen;
    public MachineScreen(MachineMenu menu, Inventory inventory, Component title) {
        super(menu,inventory,title); imageWidth=176; imageHeight=175; inventoryLabelY=82;
    }
    @Override protected void init() { super.init(); }
    private void clickMenuButton(int id) {
        if (minecraft.gameMode != null) minecraft.gameMode.handleInventoryButtonClick(menu.containerId,id);
    }
    @Override public boolean mouseClicked(double mouseX,double mouseY,int button) {
        int x=(int)mouseX-leftPos,y=(int)mouseY-topPos;
        if(outputConfigOpen) {
            int panelLeft=(width-124)/2, panelTop=(height-138)/2;
            int row=(int)Math.floor((mouseY-(panelTop+19))/19.0);
            if(row>=0&&row<6&&mouseX>=panelLeft+25&&mouseX<panelLeft+77&&button==0) {
                clickMenuButton(row); return true;
            }
            if(row>=0&&row<6&&mouseX>=panelLeft+79&&mouseX<panelLeft+112) {
                if(menu.machine().kind()==MachineKind.EXCHANGER_CONTROLLER&&(button==0||button==1)) clickMenuButton(6+row);
                else if((menu.machine().kind()==MachineKind.FUEL_TURBINE_CONTROLLER||menu.machine().kind()==MachineKind.STEAM_TURBINE_CONTROLLER)&&button==2) clickMenuButton(12+row);
                return true;
            }
            if(mouseX<panelLeft||mouseX>=panelLeft+124||mouseY<panelTop||mouseY>=panelTop+138) {
                outputConfigOpen=false;
                return true;
            }
            return true;
        }
        if (button == 0 && menu.machine().kind()==MachineKind.CONTROL) {
            if (inside(x,y,98,37)) { clickMenuButton(0); return true; }
            if (inside(x,y,62,37)) { clickMenuButton(1); return true; }
        } else if (button == 0 && menu.machine().kind()==MachineKind.SENSOR) {
            if (inside(x,y,53,37)) { clickMenuButton(0); return true; }
            if (inside(x,y,71,37)) { clickMenuButton(1); return true; }
        }
        if(menu.machine().kind().controller()&&x>=153&&x<170) {
            if(y>=19&&y<36) { outputConfigOpen=!outputConfigOpen; return true; }
            if(y>=61&&y<78&&button==0) { clickMenuButton(18); return true; }
        }
        return super.mouseClicked(mouseX,mouseY,button);
    }
    @Override protected void renderBg(GuiGraphics g,float partial,int mouseX,int mouseY) {
        if(outputConfigOpen) { drawOutputConfig(g,mouseX,mouseY); return; }
        int x=(width-imageWidth)/2,y=(height-imageHeight)/2;
        g.blit(background(),x,y,0,0,imageWidth,imageHeight,TEXTURE_SIZE,TEXTURE_SIZE);
        drawGauges(g,x,y);
        drawMachineInfo(g,x,y);
        drawButtons(g,x,y,mouseX,mouseY);
    }
    private void drawOutputConfig(GuiGraphics g,int mouseX,int mouseY) {
        int x=(width-124)/2,y=(height-138)/2;
        g.blit(Generators.id("textures/gui/outputs.png"),x,y,0,0,124,138,256,256);
        g.drawString(font,Component.translatable("advgenerators.gui.output.title"),x+8,y+6,0xff404040,false);
        int[][] colors={{0xffc94a4a},{0xff61b34b},{0xff5687d6},{0xffd7b63e},{0xff45bfc2},{0xffa65cc4}};
        for(int side=0;side<6;side++) {
            int rowY=y+19+side*19,mode=menu.outputMode(side);
            g.fill(x+9,rowY+2,x+21,rowY+14,colors[side][0]);
            int iconU=switch(mode){case 1->37;case 2->21;case 3->69;default->53;};
            drawAtlasButton(g,x+25,rowY,mouseX,mouseY,20,iconU,35);
            String modeText=switch(mode){case 1->"Signal";case 2->"No sig";case 3->"Off";default->"Always";};
            g.drawString(font,modeText,x+44,rowY+5,0xff303030,false);
            if(menu.machine().kind()==MachineKind.EXCHANGER_CONTROLLER) {
                drawAtlasButton(g,x+79,rowY,mouseX,mouseY,20,20,35);
                String selection=switch(menu.outputSelection(side)){case 1->"Cold";case 2->"Hot";default->"Both";};
                g.drawString(font,selection,x+98,rowY+5,0xff202020,false);
            } else if(menu.machine().kind()==MachineKind.FUEL_TURBINE_CONTROLLER||menu.machine().kind()==MachineKind.STEAM_TURBINE_CONTROLLER) {
                drawAtlasButton(g,x+79,rowY,mouseX,mouseY,20,20,35);
                String limit=switch(menu.outputLimitIndex(side)){case 0->"8k";case 1->"32k";case 2->"128k";default->"Max";};
                g.drawString(font,limit,x+98,rowY+5,0xff202020,false);
            }
        }
    }
    private void drawButtons(GuiGraphics g,int x,int y,int mouseX,int mouseY) {
        int mx=mouseX-leftPos,my=mouseY-topPos;
        var kind=menu.machine().kind();
        if(kind.controller()) {
            drawAtlasButton(g,x+153,y+19,mx,my,0,85,35);
            drawAtlasButton(g,x+153,y+61,mx,my,52,69,35);
        } else if(kind==MachineKind.CONTROL) {
            String[] icons={"disabled","fuel","steam","energy","exchange","heating","mix"};
            int action=Math.max(0,Math.min(icons.length-1,menu.controlAction()));
            drawTextureButton(g,x+98,y+37,mx,my,Generators.id("textures/control/"+icons[action]+".png"));
            drawAtlasButton(g,x+62,y+37,mx,my,20,menu.controlHigh()?37:21,35);
        } else if(kind==MachineKind.SENSOR) {
            drawTextureButton(g,x+53,y+37,mx,my,sensorIcon());
            drawTextureButton(g,x+71,y+37,mx,my,conditionIcon());
            drawAtlasButton(g,x+107,y+37,mx,my,20,menu.machine().getBlockState().getValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.POWERED)?37:21,35);
        }
    }
    private void drawAtlasButton(GuiGraphics g,int x,int y,int mx,int my,int baseU,int iconU,int iconV) {
        g.blit(WIDGETS,x,y,mx>=x&&mx<x+16&&my>=y&&my<y+16?36:baseU==52?52:20,18,16,16,256,256);
        g.blit(WIDGETS,x+1,y+1,iconU,iconV,14,14,256,256);
    }
    private void drawTextureButton(GuiGraphics g,int x,int y,int mx,int my,ResourceLocation icon) {
        g.blit(WIDGETS,x,y,mx>=x&&mx<x+16&&my>=y&&my<y+16?36:20,18,16,16,256,256);
        g.blit(icon,x,y,0,0,16,16,16,16);
    }
    private static boolean inside(int x,int y,int bx,int by) { return x>=bx&&x<bx+16&&y>=by&&y<by+16; }
    private ResourceLocation sensorIcon() {
        var controller=menu.machine().controller();
        if(controller==null||menu.sensorMetric()==0)return Generators.id("textures/sensor/disabled.png");
        String[] choices=switch(controller.kind()) {
            case FUEL_TURBINE_CONTROLLER -> new String[]{"power","tank/fuel","power"};
            case STEAM_TURBINE_CONTROLLER -> new String[]{"power","tank/steam","turbine/turbine"};
            case SYNGAS_CONTROLLER -> new String[]{"tank/water","tank/fuel","tank/steam","tank/carbon","heat/heat"};
            case EXCHANGER_CONTROLLER -> new String[]{"tank/hot_in","tank/hot_out","tank/cold_in","tank/cold_out","heat/heat"};
            default -> new String[0];
        };
        int index=menu.sensorMetric()-1;
        return index<0||index>=choices.length?Generators.id("textures/sensor/disabled.png"):Generators.id("textures/sensor/"+choices[index]+".png");
    }
    private ResourceLocation conditionIcon() {
        var controller=menu.machine().controller();
        if(controller==null||menu.sensorMetric()==0)return Generators.id("textures/sensor/null.png");
        int metric=controller.sensorMetricFor(menu.sensorMetric());
        String name;
        if(metric==6) name=switch(menu.sensorCondition()){case 0->"turbine/stopped";case 1->"turbine/low";case 2->"turbine/med";default->"turbine/high";};
        else if(metric==7) name=switch(menu.sensorCondition()){case 0->"heat/cold";case 1->"heat/low";case 2->"heat/med";default->"heat/high";};
        else name=switch(menu.sensorCondition()){case 0->"fill/empty";case 1->"fill/not_empty";case 2->"fill/5";case 3->"fill/25";case 4->"fill/50";case 5->"fill/75";case 6->"fill/95";case 7->"fill/full";default->"fill/not_full";};
        return Generators.id("textures/sensor/"+name+".png");
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
            drawInfo(g,x,y,21,"Heat "+signed(menu.heatDelta())+" HU/t");
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
            if (machine.kind()==MachineKind.FUEL_TURBINE_CONTROLLER)
                drawInfo(g,x,y,43,"Efficiency "+format(menu.efficiency()*100)+"%");
            else
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
    private static String signed(double value) { return (value>0?"+":"")+format(value); }
    @Override protected void renderLabels(GuiGraphics g,int x,int y) {
        if(outputConfigOpen) return;
        g.drawString(font,title,8,5,0xff404040,false);
        if (menu.status() == 0) {
            g.drawString(font,Component.translatable("container.inventory"),8,inventoryLabelY,0xff404040,false);
        } else {
            g.drawString(font,Component.translatable("advgenerators.status."+menu.status()),8,inventoryLabelY,0xffaa2020,false);
        }
    }
    @Override public void onClose() {
        if(outputConfigOpen) outputConfigOpen=false;
        else super.onClose();
    }
    @Override public void render(GuiGraphics g,int mx,int my,float delta) {
        if(outputConfigOpen) {
            renderBackground(g,mx,my,delta);
            drawOutputConfig(g,mx,my);
            int panelLeft=(width-124)/2,panelTop=(height-138)/2;
            int row=(int)Math.floor((my-(panelTop+19))/19.0);
            if(row>=0&&row<6&&mx>=panelLeft+7&&mx<panelLeft+24&&my>=panelTop+19+row*19&&my<panelTop+37+row*19)
                g.renderTooltip(font,Component.translatable("advgenerators.output."+row),mx,my);
            else if(row>=0&&row<6&&mx>=panelLeft+25&&mx<panelLeft+77) {
                String mode=switch(menu.outputMode(row)){case 1->"Signal";case 2->"No sig";case 3->"Off";default->"Always";};
                g.renderTooltip(font,Component.translatable("advgenerators.gui.output.mode",mode),mx,my);
            }
            else if(row>=0&&row<6&&mx>=panelLeft+79&&mx<panelLeft+112) {
                String key=menu.machine().kind()==MachineKind.EXCHANGER_CONTROLLER?"advgenerators.gui.output.selection":"advgenerators.gui.output.limit";
                g.renderTooltip(font,Component.translatable(key),mx,my);
            }
            return;
        }
        renderBackground(g,mx,my,delta); super.render(g,mx,my,delta); renderTooltip(g,mx,my);
        int x=mx-leftPos,y=my-topPos;
        if(menu.machine().kind().controller()&&inside(x,y,153,19)) g.renderTooltip(font,Component.translatable("advgenerators.gui.button.output"),mx,my);
        else if(menu.machine().kind().controller()&&inside(x,y,153,61)) g.renderTooltip(font,Component.translatable("advgenerators.gui.button.dump"),mx,my);
        else if(menu.machine().kind()==MachineKind.CONTROL&&inside(x,y,62,37)) g.renderTooltip(font,Component.translatable(menu.controlHigh()?"advgenerators.control.mode.high":"advgenerators.control.mode.low"),mx,my);
        else if(menu.machine().kind()==MachineKind.CONTROL&&inside(x,y,98,37)) {
            String[] actions={"disabled","useFuel","useSteam","generateEnergy","exchangeHeat","heatWater","mix"};
            int action=Math.max(0,Math.min(actions.length-1,menu.controlAction()));
            g.renderTooltip(font,Component.translatable("advgenerators.control.action."+actions[action]),mx,my);
        } else if(menu.machine().kind()==MachineKind.SENSOR&&inside(x,y,53,37)) {
            g.renderTooltip(font,Component.translatable("advgenerators.sensor.metric."+sensorControllerId()+"."+menu.sensorMetric()),mx,my);
        } else if(menu.machine().kind()==MachineKind.SENSOR&&inside(x,y,71,37)) {
            g.renderTooltip(font,Component.translatable("advgenerators.sensor.condition."+menu.sensorCondition()),mx,my);
        } else if(menu.machine().kind()==MachineKind.SENSOR&&inside(x,y,107,37)) {
            g.renderTooltip(font,Component.translatable(menu.machine().getBlockState().getValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.POWERED)?"advgenerators.sensor.on":"advgenerators.sensor.off"),mx,my);
        }
    }
}
