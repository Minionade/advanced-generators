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
        this.data = new SimpleContainerData(8) {
            @Override public int get(int i) {
                return switch(i) {
                    case 0 -> (int)(machine.energy & 0x7fffffff);
                    case 1 -> (int)(machine.energy >>> 31);
                    case 2 -> (int)Math.min(Integer.MAX_VALUE,machine.energyCapacity);
                    case 3 -> (int)Math.min(Integer.MAX_VALUE,machine.maxOutput*100);
                    case 4 -> (int)Math.min(Integer.MAX_VALUE,machine.heat*100);
                    case 5 -> (int)Math.min(Integer.MAX_VALUE,machine.carbon*100);
                    case 6 -> (int)Math.min(Integer.MAX_VALUE,machine.generationRate*100);
                    case 7 -> machine.network().error();
                    default -> 0;
                };
            }
            @Override public void set(int i,int value) { }
        };
        addDataSlots(data);
        int[] slots = machine.inputSlots();
        for (int i=0;i<slots.length;i++) {
            final int bufferSlot=slots[i];
            addSlot(new Slot(machine.inventory, bufferSlot, 8+18*i, 54) {
                @Override public boolean mayPlace(ItemStack stack) { return machine.kind()==MachineKind.SYNGAS_CONTROLLER && machine.carbonValue(stack)>0; }
            });
        }
        for(int row=0;row<3;row++) for(int col=0;col<9;col++) addSlot(new Slot(playerInventory,col+row*9+9,8+col*18,100+row*18));
        for(int col=0;col<9;col++) addSlot(new Slot(playerInventory,col,8+col*18,158));
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
    @Override public boolean stillValid(Player player) { return machine.getLevel()!=null && player.distanceToSqr(machine.getBlockPos().getX()+.5,machine.getBlockPos().getY()+.5,machine.getBlockPos().getZ()+.5)<64; }
    @Override public ItemStack quickMoveStack(Player player,int slotIndex) {
        if(slotIndex<0 || slotIndex>=slots.size()) return ItemStack.EMPTY;
        var slot=slots.get(slotIndex); if(!slot.hasItem()) return ItemStack.EMPTY;
        var original=slot.getItem(); var copy=original.copy();
        if(slotIndex < machine.inputSlots().length) {
            if(!moveItemStackTo(original,machine.inputSlots().length,slots.size(),true)) return ItemStack.EMPTY;
        } else if(!moveItemStackTo(original,0,machine.inputSlots().length,false)) return ItemStack.EMPTY;
        if(original.isEmpty()) slot.set(ItemStack.EMPTY); else slot.setChanged();
        return copy;
    }
}
