package net.bdew.generators.menu;

import net.bdew.generators.ModContent;
import net.bdew.generators.machine.*;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.ItemStack;

/** Server-authoritative menu. All contents and gauges are synchronized by vanilla menu slots/data. */
public final class MachineMenu extends AbstractContainerMenu {
    private final MachineBlockEntity machine;
    private final ContainerData data;
    public MachineMenu(int id, Inventory playerInventory, MachineBlockEntity machine) {
        super(ModContent.MACHINE_MENU.get(), id);
        this.machine = machine;
        this.data = new SimpleContainerData(44) {
            @Override public int get(int i) {
                return switch(i) {
                    case 0 -> (int)(machine.energy & 0x7fffffff);
                    case 1 -> (int)(machine.energy >>> 31);
                    case 2 -> (int)Math.min(Integer.MAX_VALUE,machine.energyCapacity);
                    case 3 -> (int)Math.min(Integer.MAX_VALUE,(machine.kind()==MachineKind.EXCHANGER_CONTROLLER?machine.exchangers*net.bdew.generators.config.GeneratorConfig.INSTANCE.exchangerTransfer:machine.maxOutput)*100);
                    case 4 -> (int)Math.min(Integer.MAX_VALUE,machine.heat*100);
                    case 5 -> (int)Math.min(Integer.MAX_VALUE,machine.carbon*100);
                    case 6 -> (int)Math.min(Integer.MAX_VALUE,machine.generationRate*100);
                    case 7 -> machine.network().error();
                    case 8 -> machine.turbines;
                    case 9 -> (int)Math.min(Integer.MAX_VALUE,machine.consumptionRate*100);
                    case 10 -> (int)Math.min(Integer.MAX_VALUE,machine.processRate*100);
                    case 11 -> (int)Math.min(Integer.MAX_VALUE,machine.rpm*100);
                    case 12 -> machine.heatingChambers;
                    case 13 -> machine.mixingChambers;
                    case 14 -> machine.exchangers;
                    case 15 -> machine.controlAction;
                    case 16 -> machine.controlHigh ? 1 : 0;
                    case 17 -> machine.sensorMetric;
                    case 18 -> machine.sensorCondition;
                    case 19,20,21,22,23,24 -> machine.outputMode[i-19];
                    case 25,26,27,28,29,30 -> machine.outputSelection[i-25];
                    case 31,32,33,34,35,36 -> outputLimitIndex(machine.outputLimit[i-31]);
                    case 37 -> fill(machine.buffers[0]);
                    case 38 -> fill(machine.buffers[1]);
                    case 39 -> fill(machine.buffers[2]);
                    case 40 -> fill(machine.buffers[3]);
                    case 41 -> fill(machine.carbon,Math.max(1,net.bdew.generators.config.GeneratorConfig.INSTANCE.syngasCapacity));
                    case 42 -> fill(machine.heat,machine.kind()==MachineKind.SYNGAS_CONTROLLER?net.bdew.generators.config.GeneratorConfig.INSTANCE.syngasMaxHeat:net.bdew.generators.config.GeneratorConfig.INSTANCE.exchangerMaxHeat);
                    case 43 -> (int)Math.min(Integer.MAX_VALUE,machine.heatLossRate*100);
                    default -> 0;
                };
            }
            @Override public void set(int i,int value) { }
        };
        addDataSlots(data);
        if (machine.kind()==MachineKind.SYNGAS_CONTROLLER) {
            for (int slot=0;slot<4;slot++) {
                int inventorySlot=slot;
                addSlot(new Slot(machine.inventory,inventorySlot,77+18*slot,61) {
                    @Override public boolean mayPlace(ItemStack stack) { return machine.carbonValue(stack)>0; }
                });
            }
        }
        for(int row=0;row<3;row++) for(int col=0;col<9;col++) addSlot(new Slot(playerInventory,col+row*9+9,8+col*18,94+row*18));
        for(int col=0;col<9;col++) addSlot(new Slot(playerInventory,col,8+col*18,152));
    }
    public static MachineMenu client(int id, Inventory inventory, FriendlyByteBuf buf) {
        var pos=buf.readBlockPos();
        if (!(inventory.player.level().getBlockEntity(pos) instanceof MachineBlockEntity machine)) throw new IllegalStateException("Machine menu opened without a machine at "+pos);
        return new MachineMenu(id,inventory,machine);
    }
    public MachineBlockEntity machine() { return machine; }
    public long energy() { return Integer.toUnsignedLong(data.get(0)) | ((long)data.get(1)<<31); }
    public long capacity() { return data.get(2); }
    public double heat() { return data.get(4)/100.0; }
    public double carbon() { return data.get(5)/100.0; }
    public double output() { return data.get(3)/100.0; }
    public double generation() { return data.get(6)/100.0; }
    public int status() { return data.get(7); }
    public int turbines() { return data.get(8); }
    public double consumption() { return data.get(9)/100.0; }
    public double process() { return data.get(10)/100.0; }
    public double rpm() { return data.get(11)/100.0; }
    public int heatingChambers() { return data.get(12); }
    public int mixingChambers() { return data.get(13); }
    public int exchangers() { return data.get(14); }
    public int controlAction() { return data.get(15); }
    public boolean controlHigh() { return data.get(16)!=0; }
    public int sensorMetric() { return data.get(17); }
    public int sensorCondition() { return data.get(18); }
    public int outputMode(int side) { return data.get(19+side); }
    public int outputSelection(int side) { return data.get(25+side); }
    public int outputLimitIndex(int side) { return data.get(31+side); }
    public double bufferFill(int index) { return data.get(37+index)/1000.0; }
    public double carbonFill() { return data.get(41)/1000.0; }
    public double heatFill() { return data.get(42)/1000.0; }
    public double heatLoss() { return data.get(43)/100.0; }
    private static int fill(ResourceBuffer buffer) {
        return fill(buffer.amount,buffer.capacity);
    }
    private static int fill(double amount,double capacity) { return capacity<=0?0:(int)Math.round(1000*Math.max(0,Math.min(1,amount/capacity))); }
    @Override public boolean clickMenuButton(Player player,int button) {
        if (machine.kind().controller()) {
            if (button>=0 && button<6) machine.outputMode[button]=(machine.outputMode[button]+1)%4;
            else if (button>=6 && button<12) machine.outputSelection[button-6]=(machine.outputSelection[button-6]+1)%3;
            else if (button>=12 && button<18) {
                long[] limits={8000,32000,128000,Integer.MAX_VALUE};
                int side=button-12; machine.outputLimit[side]=limits[(outputLimitIndex(side)+1)%limits.length];
            } else if (button==18) dumpBuffers();
            else return false;
        } else if (machine.kind()==MachineKind.CONTROL) {
            if (button==0) machine.controlAction=(machine.controlAction+1)%7;
            else if (button==1) machine.controlHigh=!machine.controlHigh;
            else return false;
        } else if (machine.kind()==MachineKind.SENSOR) {
            if (button==0) machine.sensorMetric=(machine.sensorMetric+1)%10;
            else if (button==1) machine.sensorCondition=(machine.sensorCondition+1)%9;
            else return false;
        } else return false;
        machine.setChanged();
        if (machine.getLevel()!=null) machine.getLevel().sendBlockUpdated(machine.getBlockPos(),machine.getBlockState(),machine.getBlockState(),3);
        return true;
    }
    private static int outputLimitIndex(long limit) {
        if (limit<=8000)return 0;
        if (limit<=32000)return 1;
        if (limit<=128000)return 2;
        return 3;
    }
    private void dumpBuffers() {
        switch(machine.kind()) {
            case FUEL_TURBINE_CONTROLLER,STEAM_TURBINE_CONTROLLER -> machine.buffers[0].clear();
            case SYNGAS_CONTROLLER -> {
                for(int slot=0;slot<3;slot++)machine.buffers[slot].clear();
                machine.inventory.clearContent(); machine.carbon=0; machine.heat=0;
            }
            case EXCHANGER_CONTROLLER -> { for(var buffer:machine.buffers)buffer.clear(); }
            default -> { return; }
        }
    }
    @Override public boolean stillValid(Player player) { return machine.getLevel()!=null && player.distanceToSqr(machine.getBlockPos().getX()+.5,machine.getBlockPos().getY()+.5,machine.getBlockPos().getZ()+.5)<64; }
    @Override public ItemStack quickMoveStack(Player player,int slotIndex) {
        if(slotIndex<0 || slotIndex>=slots.size()) return ItemStack.EMPTY;
        var slot=slots.get(slotIndex); if(!slot.hasItem()) return ItemStack.EMPTY;
        var original=slot.getItem(); var copy=original.copy();
        int machineSlots=machine.kind()==MachineKind.SYNGAS_CONTROLLER?4:0;
        if(slotIndex < machineSlots) {
            if(!moveItemStackTo(original,machineSlots,slots.size(),true)) return ItemStack.EMPTY;
        } else if(machineSlots==0 || !moveItemStackTo(original,0,machineSlots,false)) return ItemStack.EMPTY;
        if(original.isEmpty()) slot.set(ItemStack.EMPTY); else slot.setChanged();
        return copy;
    }
}
