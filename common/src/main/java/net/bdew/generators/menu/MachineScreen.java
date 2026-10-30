package net.bdew.generators.menu;

import dev.architectury.hooks.fluid.FluidStackHooks;
import net.bdew.generators.Generators;
import net.bdew.generators.ModContent;
import net.bdew.generators.machine.MachineKind;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import java.util.List;
import java.util.Optional;

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
            boolean inRow=row>=0&&row<menu.outputCount()&&mouseY-(panelTop+19+19*row)<16;
            if(inRow&&mouseX>=panelLeft+100&&mouseX<panelLeft+116&&button==0) {
                clickMenuButton(row); return true;
            }
            if(inRow&&mouseX>=panelLeft+82&&mouseX<panelLeft+98) {
                var outputKind=menu.outputKind(row);
                if(menu.machine().kind()==MachineKind.EXCHANGER_CONTROLLER&&outputKind==MachineKind.FLUID_OUTPUT_SELECT&&(button==0||button==1)) clickMenuButton(6+row);
                else if(outputKind!=null&&outputKind.isEnergyOutput()&&button==0) clickMenuButton(12+row);
                return true;
            }
            if(mouseX<panelLeft||mouseX>=panelLeft+124||mouseY<panelTop||mouseY>=panelTop+138) {
                outputConfigOpen=false;
                return true;
            }
            return true;
        }
        if (button == 0 && menu.machine().kind()==MachineKind.CONTROL) {
            if (inside(x,y,98,38)) { clickMenuButton(0); return true; }
            if (inside(x,y,62,38)) { clickMenuButton(1); return true; }
        } else if (button == 0 && menu.machine().kind()==MachineKind.SENSOR) {
            if (inside(x,y,53,38)) { clickMenuButton(0); return true; }
            if (inside(x,y,71,38)) { clickMenuButton(1); return true; }
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
    /** Output colours, the same as the badges drawn on the machine blocks. */
    private static final int[] OUTPUT_COLORS={0xffff0000,0xff00ff00,0xff0000ff,0xffffff00,0xff00ffff,0xffff00ff};
    private static final int[] SLOT_ICONS={101,133,117}; // both, cold, hot

    private void drawOutputConfig(GuiGraphics g,int mouseX,int mouseY) {
        int x=(width-124)/2,y=(height-138)/2;
        g.blit(Generators.id("textures/gui/outputs.png"),x,y,0,0,124,138,256,256);
        g.drawString(font,Component.translatable("advgenerators.gui.output.title"),x+8,y+6,0xff404040,false);
        for(int row=0;row<6;row++) {
            int rowY=y+18+row*19;
            if(row>=menu.outputCount()) {
                // unused output: the crossed-out marker in the colour of that output
                drawSpriteStretched(g,blockSprite(Generators.id("block/connected/disabled")),x+8,rowY+1,16,16,OUTPUT_COLORS[row]);
                continue;
            }
            var outputKind=menu.outputKind(row);
            var outputItem=outputKind==null?null:ModContent.ALL_ITEMS.get(outputKind.id());
            if(outputItem!=null) {
                g.pose().pushPose(); g.pose().translate(x+9,rowY+2,0); g.pose().scale(14/16f,14/16f,1);
                g.renderItem(new ItemStack(outputItem.get()),0,0);
                g.pose().popPose();
            }
            boolean power=outputKind!=null&&(outputKind.isEnergyOutput()||outputKind.isMjOutput());
            boolean fluid=outputKind==MachineKind.FLUID_OUTPUT_SELECT;
            if(power) g.drawString(font,GuiFormat.perTick(menu.outputAverage(row),menu.outputUnit(row)),x+28,rowY+5,0xff404040,false);
            else if(fluid) g.drawString(font,GuiFormat.fluidPerTick(menu.outputAverage(row)),x+28,rowY+5,0xff404040,false);
            if(menu.machine().kind()==MachineKind.EXCHANGER_CONTROLLER&&fluid) {
                // which of the exchanger's fluids this output takes
                drawAtlasButton(g,x+82,rowY+1,mouseX,mouseY,20,SLOT_ICONS[Math.floorMod(menu.outputSelection(row),3)],35);
            } else if(outputKind!=null&&outputKind.isEnergyOutput()) {
                // per-face FE output limit (an addition of this edition)
                drawTextButton(g,x+82,rowY+1,mouseX,mouseY,switch(menu.outputLimitIndex(row)){case 0->"8k";case 1->"32k";case 2->"128k";default->"Max";});
            }
            int mode=menu.outputMode(row);
            int iconU=switch(mode){case 1->37;case 2->21;case 3->69;default->53;};
            drawAtlasButton(g,x+100,rowY+1,mouseX,mouseY,20,iconU,35);
        }
    }
    private void drawTextButton(GuiGraphics g,int x,int y,int mx,int my,String label) {
        g.blit(WIDGETS,x,y,mx>=x&&mx<x+16&&my>=y&&my<y+16?36:20,18,16,16,256,256);
        g.drawCenteredString(font,label,x+8,y+4,0xff202020);
    }
    private void drawButtons(GuiGraphics g,int x,int y,int mouseX,int mouseY) {
        // Button positions below are in screen coordinates, so compare them with
        // the screen-coordinate mouse position as well.
        int mx=mouseX,my=mouseY;
        var kind=menu.machine().kind();
        if(kind.controller()) {
            drawAtlasButton(g,x+153,y+19,mx,my,0,85,35);
            drawAtlasButton(g,x+153,y+61,mx,my,52,69,35);
        } else if(kind==MachineKind.CONTROL) {
            // The slot frames are part of the background image; the icons are drawn plainly on top.
            String[] icons={"disabled","fuel","steam","energy","exchange","heating","mix"};
            int action=Math.max(0,Math.min(icons.length-1,menu.controlAction()));
            g.blit(Generators.id("textures/control/"+icons[action]+".png"),x+98,y+38,0,0,16,16,16,16);
            drawTorch(g,x+62,y+38,menu.controlHigh());
        } else if(kind==MachineKind.SENSOR) {
            g.blit(sensorIcon(),x+53,y+38,0,0,16,16,16,16);
            g.blit(conditionIcon(),x+71,y+38,0,0,16,16,16,16);
            var controller=menu.machine().controller();
            if(controller!=null&&controller.kind()==MachineKind.EXCHANGER_CONTROLLER) {
                int metric=controller.sensorMetricFor(menu.sensorMetric());
                if(metric>=2&&metric<=5&&menu.sensorCondition()==1)
                    g.renderItem(new ItemStack(Items.COBBLESTONE),x+71,y+38);
                else if(metric>=2&&metric<=5&&menu.sensorCondition()==2)
                    g.renderItem(new ItemStack(Items.WATER_BUCKET),x+71,y+38);
            }
            drawTorch(g,x+107,y+38,menu.machine().getBlockState().getValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.POWERED));
        }
    }
    private void drawTorch(GuiGraphics g,int x,int y,boolean on) {
        drawSpriteStretched(g,blockSprite(ResourceLocation.withDefaultNamespace(on?"block/redstone_torch":"block/redstone_torch_off")),x,y,16,16,WHITE);
    }
    private void drawAtlasButton(GuiGraphics g,int x,int y,int mx,int my,int baseU,int iconU,int iconV) {
        g.blit(WIDGETS,x,y,mx>=x&&mx<x+16&&my>=y&&my<y+16?36:baseU==52?52:20,18,16,16,256,256);
        g.blit(WIDGETS,x+1,y+1,iconU,iconV,14,14,256,256);
    }
    private static boolean inside(int x,int y,int bx,int by) { return x>=bx&&x<bx+16&&y>=by&&y<by+16; }
    private ResourceLocation sensorIcon() {
        var controller=menu.machine().controller();
        if(controller==null||menu.sensorMetric()==0)return Generators.id("textures/sensor/disabled.png");
        String[] choices=switch(controller.kind()) {
            case FUEL_TURBINE_CONTROLLER -> new String[]{"power","tank/fuel","turbine/turbine"};
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
        if(metric==6 || metric==9) name=switch(menu.sensorCondition()){case 0->"turbine/stopped";case 1->"turbine/low";case 2->"turbine/med";default->"turbine/high";};
        else if(metric==7) name=switch(menu.sensorCondition()){case 0->"heat/cold";case 1->"heat/low";case 2->"heat/med";default->"heat/high";};
        else if(controller.kind()==MachineKind.EXCHANGER_CONTROLLER && metric>=2 && metric<=5)
            name=switch(menu.sensorCondition()){case 0->"fill/empty";case 1,2->"fill/empty";case 3->"fill/5";case 4->"fill/25";case 5->"fill/50";case 6->"fill/75";case 7->"fill/95";case 8->"fill/not_full";default->"fill/full";};
        else name=switch(menu.sensorCondition()){case 0->"fill/empty";case 1->"fill/not_empty";case 2->"fill/5";case 3->"fill/25";case 4->"fill/50";case 5->"fill/75";case 6->"fill/95";case 7->"fill/not_full";default->"fill/full";};
        return Generators.id("textures/sensor/"+name+".png");
    }
    private static TextureAtlasSprite blockSprite(ResourceLocation id) {
        return Minecraft.getInstance().getTextureAtlas(InventoryMenu.BLOCK_ATLAS).apply(id);
    }
    private static TextureAtlasSprite itemSprite(net.minecraft.world.item.Item item) {
        return Minecraft.getInstance().getItemRenderer().getItemModelShaper().getItemModel(item).getParticleIcon();
    }
    private static final int WHITE=0xffffffff;

    /** Sprite of the resource in a buffer (fluid still texture or item icon), or null when it is empty. */
    private record Icon(TextureAtlasSprite sprite,int color) {}
    private static Icon iconFor(Fluid fluid,net.minecraft.world.item.Item item) {
        if(fluid!=null) return new Icon(FluidStackHooks.getStillTexture(fluid),FluidStackHooks.getColor(fluid));
        if(item!=null) return new Icon(itemSprite(item),WHITE);
        return null;
    }
    private Icon bufferIcon(int index) { return iconFor(menu.bufferFluid(index),menu.bufferItem(index)); }

    private static void tint(GuiGraphics g,int argb) {
        g.setColor(((argb>>16)&0xff)/255f,((argb>>8)&0xff)/255f,(argb&0xff)/255f,1f);
    }
    private static void untint(GuiGraphics g) { g.setColor(1f,1f,1f,1f); }

    /** The sprite stretched over a rectangle. */
    private static void drawSpriteStretched(GuiGraphics g,TextureAtlasSprite sprite,int x,int y,int w,int h,int argb) {
        tint(g,argb); g.blit(x,y,0,w,h,sprite); untint(g);
    }
    /** The sprite repeated in 16x16 tiles from the bottom left, as the original's gauges did. */
    private static void drawSpriteTiled(GuiGraphics g,TextureAtlasSprite sprite,int x,int y,int w,int h,int argb) {
        if(w<=0||h<=0) return;
        g.enableScissor(x,y,x+w,y+h);
        tint(g,argb);
        for(int tx=x;tx<x+w;tx+=16) for(int ty=y+h;ty>y;ty-=16) g.blit(tx,ty-16,0,16,16,sprite);
        untint(g);
        g.disableScissor();
    }
    /** A resource gauge: the buffer's contents tiled up from the bottom, then the scale marks on top. */
    private void drawBufferGauge(GuiGraphics g,int x,int y,int w,int h,int buffer,int overlayU,int overlayV) {
        var icon=bufferIcon(buffer);
        int fill=(int)Math.round(h*Math.max(0,Math.min(1,menu.bufferFill(buffer))));
        if(icon!=null&&fill>0) drawSpriteTiled(g,icon.sprite(),x,y+h-fill,w,fill,icon.color());
        if(overlayU>=0) g.blit(WIDGETS,x,y,overlayU,overlayV,w,h,256,256);
    }
    /** A sprite stretched over a rectangle and revealed from one edge by the fill fraction. */
    private static void drawSpriteFill(GuiGraphics g,TextureAtlasSprite sprite,int x,int y,int w,int h,double fill,boolean fromTop,int argb) {
        int part=(int)Math.round((fromTop?h:h)*Math.max(0,Math.min(1,fill)));
        if(part<=0) return;
        g.enableScissor(x,fromTop?y:y+h-part,x+w,fromTop?y+part:y+h);
        drawSpriteStretched(g,sprite,x,y,w,h,argb);
        g.disableScissor();
    }
    private void drawGauges(GuiGraphics g,int x,int y) {
        switch(menu.machine().kind()) {
            case FUEL_TURBINE_CONTROLLER, STEAM_TURBINE_CONTROLLER -> {
                drawBufferGauge(g,x+9,y+19,9,58,0,10,0);
                int fill=(int)Math.round(58*(menu.capacity()<=0?0:Math.max(0,Math.min(1,(double)menu.energy()/menu.capacity()))));
                if(fill>0) g.blit(WIDGETS,x+61,y+19+58-fill,0,58-fill,9,fill,256,256);
            }
            case SYNGAS_CONTROLLER -> {
                drawBufferGauge(g,x+11,y+19,10,35,0,137,54);
                drawBufferGauge(g,x+57,y+19,10,35,2,137,54);
                drawSpriteFill(g,FluidStackHooks.getStillTexture(ModContent.GASES.get("steam").source.get()),x+30,y+61,37,8,menu.bufferFill(1),true,WHITE);
                drawSpriteFill(g,blockSprite(ResourceLocation.withDefaultNamespace("block/coal_block")),x+30,y+69,37,8,menu.carbonFill(),false,WHITE);
                if(menu.heatingChambers()>0) {
                    int fill=(int)Math.round(14*Math.max(0,Math.min(1,menu.heatFill())));
                    if(fill>0) g.blit(WIDGETS,x+9,y+62+14-fill,69,19+14-fill,14,fill,256,256);
                } else g.blit(WIDGETS,x+9,y+62,69,35,14,14,256,256);
            }
            case EXCHANGER_CONTROLLER -> {
                drawBufferGauge(g,x+9,y+19,9,58,1,10,0);
                drawBufferGauge(g,x+61,y+19,9,58,2,10,0);
                drawBufferGauge(g,x+21,y+19,37,16,0,-1,0);
                drawBufferGauge(g,x+21,y+61,37,16,3,-1,0);
            }
            default -> { }
        }
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
            drawInfo(g,x,y,21,48,106,16,menu.heatingChambers()>0?GuiFormat.delta(menu.heatDelta(),"hu"):Component.literal("-----"));
            drawSpriteInfo(g,x,y,32,new Icon(blockSprite(ResourceLocation.withDefaultNamespace("block/coal_block")),WHITE),GuiFormat.perTick(menu.consumption(),"carbon_short"));
            drawSpriteInfo(g,x,y,43,new Icon(FluidStackHooks.getStillTexture(ModContent.GASES.get("syngas").source.get()),WHITE),GuiFormat.fluidPerTick(menu.process()));
        } else if(machine.kind()==MachineKind.EXCHANGER_CONTROLLER) {
            drawInfo(g,x,y,21,96,90,32,GuiFormat.amount(menu.heat(),"hu"));
            drawInfo(g,x,y,32,48,106,16,GuiFormat.perTick(menu.output(),"hu"));
            drawInfo(g,x,y,43,0,122,16,GuiFormat.perTick(menu.heatLoss(),"hu"));
            drawSpriteInfo(g,x,y,54,orDisabled(iconFor(menu.lastInputFluid(),menu.lastInputItem())),
                menu.exchangerInputIsFluid()?GuiFormat.fluidPerTick(menu.consumption()):GuiFormat.perTick(menu.consumption()));
            drawSpriteInfo(g,x,y,65,orDisabled(iconFor(menu.lastOutputFluid(),menu.lastOutputItem())),
                menu.exchangerOutputIsFluid()?GuiFormat.fluidPerTick(menu.process()):GuiFormat.perTick(menu.process()));
        } else if(machine.kind()==MachineKind.FUEL_TURBINE_CONTROLLER || machine.kind()==MachineKind.STEAM_TURBINE_CONTROLLER) {
            drawInfo(g,x,y,21,0,90,32,Component.literal(Integer.toString(menu.turbines())));
            drawInfo(g,x,y,32,32,106,16,GuiFormat.energyPerTick(menu.output()));
            if (machine.kind()==MachineKind.FUEL_TURBINE_CONTROLLER)
                drawInfo(g,x,y,43,64,90,32,GuiFormat.fluidPerTick(menu.fuelPerTick()));
            else
                drawInfo(g,x,y,43,32,122,32,GuiFormat.amount(menu.rpm(),"rpm"));
            drawInfo(g,x,y,54,48,90,16,GuiFormat.energyPerTick(menu.generation()));
            Icon fuel=machine.kind()==MachineKind.STEAM_TURBINE_CONTROLLER
                ?new Icon(FluidStackHooks.getStillTexture(ModContent.GASES.get("steam").source.get()),WHITE)
                :orDisabled(bufferIcon(0));
            drawSpriteInfo(g,x,y,65,fuel,GuiFormat.fluidPerTick(menu.consumption()));
        }
    }
    /** The marker shown where there is no resource yet: the crossed-out texture, in red. */
    private Icon orDisabled(Icon icon) {
        return icon!=null?icon:new Icon(blockSprite(Generators.id("block/connected/disabled")),0xffff0000);
    }
    private Component sensorMetricTooltip() {
        if (menu.machine().controller() == null) return Component.translatable("advgenerators.sensor.disabled");
        String[] choices=switch(menu.machine().controller().kind()) {
            case FUEL_TURBINE_CONTROLLER -> new String[]{"power","turbine.fuel","turbine.generation"};
            case STEAM_TURBINE_CONTROLLER -> new String[]{"power","turbine.steam","turbine.speed"};
            case SYNGAS_CONTROLLER -> new String[]{"syngas.water","syngas.syngas","syngas.steam","syngas.carbon","syngas.heat"};
            case EXCHANGER_CONTROLLER -> new String[]{"exchanger.hot.input","exchanger.hot.output","exchanger.cold.input","exchanger.cold.output","exchanger.heat"};
            default -> new String[0];
        };
        int selection=menu.sensorMetric();
        return selection<1||selection>choices.length?Component.translatable("advgenerators.sensor.disabled"):
            Component.translatable("advgenerators.sensor."+choices[selection-1]);
    }
    private Component sensorConditionText(net.bdew.generators.machine.MachineBlockEntity controller) {
        if(controller==null)return Component.translatable("advgenerators.sensor.param.disabled");
        int metric=controller.sensorMetricFor(menu.sensorMetric()),condition=menu.sensorCondition();
        var config=net.bdew.generators.config.GeneratorConfig.INSTANCE;
        if(metric==6) return switch(condition) {
            case 0 -> Component.translatable("advgenerators.sensor.param.turbine.speed.stop");
            case 1 -> Component.translatable("advgenerators.sensor.param.turbine.speed.low");
            case 2 -> Component.translatable("advgenerators.sensor.param.turbine.speed.medium",format(config.maxRpm/2));
            default -> Component.translatable("advgenerators.sensor.param.turbine.speed.high",format(config.maxRpm));
        };
        if(metric==9) return switch(condition) {
            case 0 -> Component.translatable("advgenerators.sensor.param.turbine.generation.stop");
            case 1 -> Component.translatable("advgenerators.sensor.param.turbine.generation.low","0 FE/t");
            case 2 -> Component.translatable("advgenerators.sensor.param.turbine.generation.medium",format(controller.maxOutput/2)+" FE/t");
            default -> Component.translatable("advgenerators.sensor.param.turbine.generation.high",format(controller.maxOutput)+" FE/t");
        };
        if(metric==7) {
            boolean syngas=controller.kind()==MachineKind.SYNGAS_CONTROLLER;
            String type=syngas?"syngas":"exchanger";
            double work=syngas?config.syngasWorkHeat:config.exchangerStartHeat;
            double max=syngas?config.syngasMaxHeat:config.exchangerMaxHeat;
            return switch(condition) {
                case 0 -> Component.translatable("advgenerators.sensor.param.heat."+type+".cold");
                case 1 -> Component.translatable("advgenerators.sensor.param.heat."+type+".low",format(work));
                case 2 -> Component.translatable("advgenerators.sensor.param.heat."+type+".medium",format(work));
                default -> Component.translatable("advgenerators.sensor.param.heat."+type+".high",format(max));
            };
        }
        if(controller.kind()==MachineKind.EXCHANGER_CONTROLLER&&metric>=2&&metric<=5) return switch(condition) {
            case 0 -> Component.translatable("advgenerators.sensor.param.fill.empty");
            case 1 -> Component.translatable("advgenerators.sensor.param.resource.solid");
            case 2 -> Component.translatable("advgenerators.sensor.param.resource.fluid");
            case 3 -> Component.translatable("advgenerators.sensor.param.fill.gt5");
            case 4 -> Component.translatable("advgenerators.sensor.param.fill.gt25");
            case 5 -> Component.translatable("advgenerators.sensor.param.fill.gt50");
            case 6 -> Component.translatable("advgenerators.sensor.param.fill.gt75");
            case 7 -> Component.translatable("advgenerators.sensor.param.fill.gt95");
            case 8 -> Component.translatable("advgenerators.sensor.param.fill.not.full");
            default -> Component.translatable("advgenerators.sensor.param.fill.full");
        };
        return switch(condition) {
            case 0 -> Component.translatable("advgenerators.sensor.param.fill.empty");
            case 1 -> Component.translatable("advgenerators.sensor.param.fill.not.empty");
            case 2 -> Component.translatable("advgenerators.sensor.param.fill.gt5");
            case 3 -> Component.translatable("advgenerators.sensor.param.fill.gt25");
            case 4 -> Component.translatable("advgenerators.sensor.param.fill.gt50");
            case 5 -> Component.translatable("advgenerators.sensor.param.fill.gt75");
            case 6 -> Component.translatable("advgenerators.sensor.param.fill.gt95");
            case 7 -> Component.translatable("advgenerators.sensor.param.fill.not.full");
            default -> Component.translatable("advgenerators.sensor.param.fill.full");
        };
    }
    private void drawInfo(GuiGraphics g,int x,int y,int row,int iconU,int iconV,int iconSize,Component value) {
        // Sample the whole icon from the widget sheet and scale it to the original's 8x8 info icon.
        g.blit(WIDGETS,x+76,y+row+1,8,8,(float)iconU,(float)iconV,
            iconSize,iconSize,TEXTURE_SIZE,TEXTURE_SIZE);
        g.drawString(font,value,x+87,y+row+1,0xff404040,false);
    }
    private void drawSpriteInfo(GuiGraphics g,int x,int y,int row,Icon icon,Component value) {
        drawSpriteStretched(g,icon.sprite(),x+76,y+row+1,8,8,icon.color());
        g.drawString(font,value,x+87,y+row+1,0xff404040,false);
    }
    private static String format(double value) {
        return String.format(java.util.Locale.ROOT,"%.1f",value).replaceAll("\\.0$","");
    }
    private static String signed(double value) { return (value>0?"+":"")+format(value); }
    @Override protected void renderLabels(GuiGraphics g,int x,int y) {
        if(outputConfigOpen) return;
        g.drawString(font,title,8,6,0xff404040,false);
        g.drawString(font,Component.translatable("container.inventory"),8,inventoryLabelY,0xff404040,false);
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
            if(row>=0&&row<menu.outputCount()&&mx>=panelLeft+26&&mx<panelLeft+44) {
                String mode=switch(menu.outputMode(row)){case 1->"Signal";case 2->"No sig";case 3->"Off";default->"Always";};
                g.renderTooltip(font,Component.translatable("advgenerators.gui.output.mode",mode),mx,my);
            }
            else if(row>=0&&row<menu.outputCount()&&mx>=panelLeft+44&&mx<panelLeft+71)
                g.renderTooltip(font,Component.literal(menu.outputFullLabel(row)),mx,my);
            else if(row>=0&&row<menu.outputCount()&&mx>=panelLeft+71&&mx<panelLeft+93)
                g.renderTooltip(font,Component.literal(format(menu.outputAverage(row))+" "+menu.outputUnit(row)),mx,my);
            else if(row>=0&&row<menu.outputCount()&&mx>=panelLeft+94&&mx<panelLeft+117) {
                String key=menu.machine().kind()==MachineKind.EXCHANGER_CONTROLLER?"advgenerators.gui.output.selection":"advgenerators.gui.output.limit";
                g.renderTooltip(font,Component.translatable(key),mx,my);
            }
            return;
        }
        renderBackground(g,mx,my,delta); super.render(g,mx,my,delta);
        int x=mx-leftPos,y=my-topPos;
        List<Component> widgetTooltip=widgetTooltip(x,y);
        if(widgetTooltip.isEmpty()) renderTooltip(g,mx,my);
        else g.renderTooltip(font,widgetTooltip,Optional.empty(),mx,my);
    }

    /** Mirrors BdLib BaseScreen: widget tooltips take precedence over item tooltips. */
    private List<Component> widgetTooltip(int x,int y) {
        var kind=menu.machine().kind();
        if(kind.controller()&&inside(x,y,153,19)) return List.of(Component.translatable("advgenerators.gui.output.title"));
        if(kind.controller()&&inside(x,y,153,61)) return List.of(Component.translatable("advgenerators.gui.dump"));
        if(kind==MachineKind.CONTROL&&inside(x,y,62,37)) return List.of(Component.translatable(menu.controlHigh()?"advgenerators.control.mode.high":"advgenerators.control.mode.low"));
        if(kind==MachineKind.CONTROL&&inside(x,y,98,37)) {
            String[] actions={"disabled","useFuel","useSteam","generateEnergy","exchangeHeat","heatWater","mix"};
            int action=Math.max(0,Math.min(actions.length-1,menu.controlAction()));
            return List.of(Component.translatable("advgenerators.control.action."+actions[action]));
        }
        if(kind==MachineKind.SENSOR&&inside(x,y,53,38)) return List.of(sensorMetricTooltip());
        if(kind==MachineKind.SENSOR&&inside(x,y,71,38)) return List.of(sensorConditionText(menu.machine().controller()));
        if(kind==MachineKind.SENSOR&&inside(x,y,107,38)) return List.of(Component.translatable(menu.machine().getBlockState().getValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.POWERED)?"advgenerators.sensor.on":"advgenerators.sensor.off"));
        List<Component> gaugeTip=gaugeTooltip(x,y);
        if(!gaugeTip.isEmpty()) return gaugeTip;
        if(x>=76&&x<84) {
            int row=y>=22&&y<30?21:y>=33&&y<41?32:y>=44&&y<52?43:y>=55&&y<63?54:y>=66&&y<74?65:-1;
            if(kind==MachineKind.SYNGAS_CONTROLLER) return switch(row) {
                case 21 -> List.of(Component.translatable("advgenerators.label.syngas.heat.delta"));
                case 32 -> List.of(Component.translatable("advgenerators.label.syngas.consumed"));
                case 43 -> List.of(Component.translatable("advgenerators.label.syngas.produced"));
                default -> List.of();
            };
            if(kind==MachineKind.EXCHANGER_CONTROLLER) return switch(row) {
                case 21 -> List.of(Component.translatable("advgenerators.label.exchanger.heat"));
                case 32 -> List.of(Component.translatable("advgenerators.label.exchanger.maxtransfer"));
                case 43 -> List.of(Component.translatable("advgenerators.label.exchanger.heatloss"));
                case 54 -> List.of(menu.exchangerInputName());
                case 65 -> List.of(menu.exchangerOutputName());
                default -> List.of();
            };
            if(kind==MachineKind.FUEL_TURBINE_CONTROLLER||kind==MachineKind.STEAM_TURBINE_CONTROLLER) return switch(row) {
                case 21 -> List.of(Component.translatable("advgenerators.label.turbine.turbines"));
                case 32 -> List.of(Component.translatable("advgenerators.label.turbine.maxprod"));
                case 43 -> kind==MachineKind.FUEL_TURBINE_CONTROLLER
                    ? List.of(Component.translatable("advgenerators.label.turbine.fuel"),Component.translatable("advgenerators.label.turbine.efficiency",format(menu.efficiency()*100)+"%"))
                    : List.of(Component.translatable("advgenerators.label.turbine.rpm"));
                case 54 -> List.of(Component.translatable("advgenerators.label.turbine.prod"));
                case 65 -> List.of(Component.translatable(kind==MachineKind.STEAM_TURBINE_CONTROLLER?"advgenerators.label.turbine.steamaverage":"advgenerators.label.turbine.fuelaverage"));
                default -> List.of();
            };
        }
        return List.of();
    }

    private List<Component> gaugeTooltip(int x,int y) {
        var kind=menu.machine().kind();
        if(kind==MachineKind.FUEL_TURBINE_CONTROLLER||kind==MachineKind.STEAM_TURBINE_CONTROLLER) {
            if(x>=9&&x<18&&y>=19&&y<77)return resourceTooltip(0);
            if(x>=61&&x<70&&y>=19&&y<77)
                return List.of(Component.literal(format(menu.energy())+" / "+format(menu.capacity())+" FE"));
        } else if(kind==MachineKind.EXCHANGER_CONTROLLER) {
            if(x>=9&&x<18&&y>=19&&y<77)return resourceTooltip(1);
            if(x>=21&&x<58&&y>=19&&y<35)return resourceTooltip(0);
            if(x>=61&&x<70&&y>=19&&y<77)return resourceTooltip(2);
            if(x>=21&&x<58&&y>=61&&y<77)return resourceTooltip(3);
        } else if(kind==MachineKind.SYNGAS_CONTROLLER) {
            if(x>=11&&x<21&&y>=19&&y<54)return resourceTooltip(0);
            if(x>=57&&x<67&&y>=19&&y<54)return resourceTooltip(2);
            if(x>=30&&x<67&&y>=61&&y<69||x>=30&&x<67&&y>=69&&y<77)
                return List.of(
                    Component.translatable("advgenerators.label.syngas.steam",format(menu.bufferFill(1)*100)+"%"),
                    Component.translatable("advgenerators.label.syngas.carbon",format(menu.carbonFill()*100)+"%"));
            if(x>=9&&x<23&&y>=62&&y<76) {
                if(menu.machine().heatingChambers<=0)return List.of(Component.translatable("advgenerators.label.syngas.heat.disabled"));
                return List.of(Component.translatable("advgenerators.label.syngas.heat",format(menu.heat()),format(net.bdew.generators.config.GeneratorConfig.INSTANCE.syngasMaxHeat)));
            }
        }
        return List.of();
    }

    private List<Component> resourceTooltip(int index) {
        Component name=menu.bufferResourceName(index);
        if(name.getString().isEmpty())return List.of(Component.translatable("advgenerators.sensor.param.fill.empty"));
        String key=menu.bufferIsFluid(index)?"resource.fluid.format":"resource.item.format";
        return List.of(name,Component.translatable(key,format(menu.bufferAmount(index)),format(menu.bufferCapacity(index))));
    }
}
