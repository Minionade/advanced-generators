package net.bdew.generators.menu;

import net.bdew.generators.ModContent;
import net.bdew.generators.machine.*;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.ItemStack;

/** Server-authoritative menu. All contents and gauges are synchronized by vanilla menu slots/data. */
public final class MachineMenu extends AbstractContainerMenu {
    private static final int SYNC_CHUNKS = 3;
    private static final int MACHINE_VALUES = 95;
    private final MachineBlockEntity machine;
    private final ContainerData data;
    private final int[] clientData = new int[MACHINE_VALUES * SYNC_CHUNKS];
    public MachineMenu(int id, Inventory playerInventory, MachineBlockEntity machine) {
        super(ModContent.MACHINE_MENU.get(), id);
        this.machine = machine;
        this.data = new ContainerData() {
            @Override public int get(int i) {
                int value = machine.getLevel() != null && !machine.getLevel().isClientSide
                    ? serverValue(i / SYNC_CHUNKS) : clientData[i];
                return (value >>> ((i % SYNC_CHUNKS) * 15)) & 0x7fff;
            }
            @Override public void set(int i,int value) { clientData[i] = value & 0x7fff; }
            @Override public int getCount() { return clientData.length; }
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
    private int serverValue(int i) {
        if (i >= 59) return outputData((i - 59) / 6, (i - 59) % 6);
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
                    case 43 -> (int)Math.max(Integer.MIN_VALUE,Math.min(Integer.MAX_VALUE,(machine.kind()==MachineKind.SYNGAS_CONTROLLER?machine.heatDeltaRate:machine.heatLossRate)*100));
                    case 44 -> (int)Math.round(machine.efficiency*100);
                    case 45 -> encodeResource(machine.lastExchangerInputItem, machine.lastExchangerInput);
                    case 46 -> encodeResource(machine.lastExchangerOutputItem, machine.lastExchangerOutput);
                    case 47,48,49,50 -> {
                        var buffer=machine.buffers[i-47];
                        yield buffer.empty()?0:encodeResource(buffer.item,buffer.id);
                    }
                    case 51,52,53,54 -> (int)Math.min(Integer.MAX_VALUE,Math.round(machine.buffers[i-51].amount*100));
                    case 55,56,57,58 -> (int)Math.min(Integer.MAX_VALUE,Math.round(machine.buffers[i-55].capacity*100));
                    default -> 0;
        };
    }
    private int outputData(int row, int field) {
        var entries = machine.outputEntries();
        if (row < 0 || row >= entries.size()) return 0;
        var entry = entries.get(row);
        return switch (field) {
            case 0 -> entry.kind().ordinal() + 1;
            case 1 -> entry.direction().get3DDataValue() + 1;
            case 2 -> entry.config().mode;
            case 3 -> entry.config().selection;
            case 4 -> machine.outputLimitIndex(row);
            case 5 -> (int)Math.min(Integer.MAX_VALUE, Math.max(0, Math.round(entry.config().average * 100)));
            default -> 0;
        };
    }
    private static int encodeResource(boolean item, ResourceLocation id) {
        if (id == null) return 0;
        int registryId = item ? BuiltInRegistries.ITEM.getId(BuiltInRegistries.ITEM.get(id))
            : BuiltInRegistries.FLUID.getId(BuiltInRegistries.FLUID.get(id));
        return (item ? 0 : 1 << 30) | (registryId + 1);
    }
    private int value(int index) {
        return data.get(index*SYNC_CHUNKS) | (data.get(index*SYNC_CHUNKS+1)<<15) | (data.get(index*SYNC_CHUNKS+2)<<30);
    }
    public static MachineMenu client(int id, Inventory inventory, FriendlyByteBuf buf) {
        var pos=buf.readBlockPos();
        if (!(inventory.player.level().getBlockEntity(pos) instanceof MachineBlockEntity machine)) throw new IllegalStateException("Machine menu opened without a machine at "+pos);
        return new MachineMenu(id,inventory,machine);
    }
    public MachineBlockEntity machine() { return machine; }
    public long energy() { return Integer.toUnsignedLong(value(0)) | ((long)value(1)<<31); }
    public long capacity() { return value(2); }
    public double heat() { return value(4)/100.0; }
    public double carbon() { return value(5)/100.0; }
    public double output() { return value(3)/100.0; }
    public double generation() { return value(6)/100.0; }
    public int status() { return value(7); }
    public int turbines() { return value(8); }
    public double consumption() { return value(9)/100.0; }
    public double process() { return value(10)/100.0; }
    public double rpm() { return value(11)/100.0; }
    public int heatingChambers() { return value(12); }
    public int mixingChambers() { return value(13); }
    public int exchangers() { return value(14); }
    public int controlAction() { return value(15); }
    public boolean controlHigh() { return value(16)!=0; }
    public int sensorMetric() { return value(17); }
    public int sensorCondition() { return value(18); }
    private int outputValue(int row,int field) { return value(59+row*6+field); }
    public int outputCount() { int count=0; while(count<6&&outputValue(count,0)!=0) count++; return count; }
    public MachineKind outputKind(int row) { int id=outputValue(row,0)-1; return id<0||id>=MachineKind.values().length?null:MachineKind.values()[id]; }
    public net.minecraft.core.Direction outputDirection(int row) { int id=outputValue(row,1)-1; return id<0||id>=6?null:net.minecraft.core.Direction.from3DDataValue(id); }
    public int outputMode(int row) { return outputValue(row,2); }
    public int outputSelection(int row) { return outputValue(row,3); }
    public int outputLimitIndex(int row) { return outputValue(row,4); }
    public double outputAverage(int row) { return outputValue(row,5)/100.0; }
    public String outputUnit(int row) {
        var kind=outputKind(row);
        if(kind==null)return "/t";
        if(kind.isEuOutput())return "EU/t";
        if(kind.isEnergyOutput())return "FE/t";
        return kind==MachineKind.FLUID_OUTPUT_SELECT?"mB/t":"items/t";
    }
    public String outputLabel(int row) {
        var kind=outputKind(row); var direction=outputDirection(row);
        if(kind==null||direction==null)return "";
        String output=kind.isEuOutput()?"EU":kind.isEnergyOutput()?"FE":kind==MachineKind.FLUID_OUTPUT_SELECT?"FL":kind==MachineKind.ITEM_OUTPUT?"IT":"Out";
        String face=switch(direction){case DOWN->"D";case UP->"U";case NORTH->"N";case SOUTH->"S";case WEST->"W";case EAST->"E";};
        return output+" "+face;
    }
    public String outputFullLabel(int row) {
        var kind=outputKind(row); var direction=outputDirection(row);
        if(kind==null||direction==null)return "";
        var item=ModContent.ALL_ITEMS.get(kind.id());
        String name=item==null?kind.id():new ItemStack(item.get()).getHoverName().getString();
        return name+" "+Component.translatable("direction.minecraft."+direction.getName()).getString();
    }
    public double bufferFill(int index) { return value(37+index)/1000.0; }
    public Component bufferResourceName(int index) {
        int encoded=value(47+index);
        if(encoded==0)return Component.empty();
        boolean fluid=(encoded&(1<<30))!=0;
        int registryId=(encoded&~(1<<30))-1;
        if(fluid) {
            var id=BuiltInRegistries.FLUID.getKey(BuiltInRegistries.FLUID.byId(registryId));
            return Component.translatable("fluid."+id.getNamespace()+"."+id.getPath());
        }
        return new ItemStack(BuiltInRegistries.ITEM.byId(registryId)).getHoverName();
    }
    public boolean bufferIsFluid(int index) { return (value(47+index)&(1<<30))!=0; }
    public double bufferAmount(int index) { return value(51+index)/100.0; }
    public double bufferCapacity(int index) { return value(55+index)/100.0; }
    public double carbonFill() { return value(41)/1000.0; }
    public double heatFill() { return value(42)/1000.0; }
    public double heatLoss() { return value(43)/100.0; }
    public double heatDelta() { return value(43)/100.0; }
    public double efficiency() { return value(44)/100.0; }
    public Component exchangerInputName() { return resourceName(value(45), true); }
    public Component exchangerOutputName() { return resourceName(value(46), false); }
    private static Component resourceName(int encoded, boolean input) {
        if (encoded == 0) return Component.translatable(input ? "advgenerators.label.exchanger.noinput" : "advgenerators.label.exchanger.nooutput");
        boolean fluid = (encoded & (1 << 30)) != 0;
        int registryId = (encoded & ~(1 << 30)) - 1;
        Component name;
        if (fluid) {
            var id = BuiltInRegistries.FLUID.getKey(BuiltInRegistries.FLUID.byId(registryId));
            name = Component.translatable("fluid." + id.getNamespace() + "." + id.getPath());
        } else {
            name = new ItemStack(BuiltInRegistries.ITEM.byId(registryId)).getHoverName();
        }
        return Component.translatable(input ? "advgenerators.label.exchanger.inrate" : "advgenerators.label.exchanger.outrate", name);
    }
    public boolean exchangerInputIsFluid() { return (value(45) & (1 << 30)) != 0; }
    public boolean exchangerOutputIsFluid() { return (value(46) & (1 << 30)) != 0; }
    private static int fill(ResourceBuffer buffer) {
        return fill(buffer.amount,buffer.capacity);
    }
    private static int fill(double amount,double capacity) { return capacity<=0?0:(int)Math.round(1000*Math.max(0,Math.min(1,amount/capacity))); }
    @Override public boolean clickMenuButton(Player player,int button) {
        if (machine.kind().controller()) {
            if (button>=0 && button<machine.outputEntries().size()) machine.setOutputMode(button,(machine.outputEntries().get(button).config().mode+1)%4);
            else if (button>=6 && button<12) machine.cycleOutputSelection(button-6);
            else if (button>=12 && button<18) machine.cycleOutputLimit(button-12);
            else if (button==18) dumpBuffers();
            else return false;
        } else if (machine.kind()==MachineKind.CONTROL) {
            if (button==0) {
                var core = machine.controller();
                int[] actions = core == null ? new int[]{0} : MachineBlockEntity.controlActions(core.kind());
                int current = 0;
                for (int i=0;i<actions.length;i++) if (actions[i] == machine.controlAction) current = i;
                machine.controlAction = actions[(current+1)%actions.length];
            }
            else if (button==1) machine.controlHigh=!machine.controlHigh;
            else return false;
        } else if (machine.kind()==MachineKind.SENSOR) {
            if (button==0) {
                var core = machine.controller();
                int count = core == null ? 0 : MachineBlockEntity.sensorMetricCount(core.kind());
                machine.sensorMetric=(machine.sensorMetric+1)%(count+1);
                machine.sensorCondition=0;
            }
            else if (button==1) {
                var core = machine.controller();
                int count = core == null ? 9 : MachineBlockEntity.sensorConditionCount(core.kind(), machine.sensorMetric);
                machine.sensorCondition=(machine.sensorCondition+1)%count;
            }
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
