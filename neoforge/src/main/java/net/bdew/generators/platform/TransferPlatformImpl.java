package net.bdew.generators.neoforge.platform;

import net.bdew.generators.ModContent;
import net.bdew.generators.machine.*;
import net.bdew.generators.platform.TransferPlatform;
import net.bdew.generators.platform.BuildCraftMachineBlockEntity;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.FluidUtil;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemHandlerHelper;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.material.Fluid;
import java.util.ArrayList;
import java.util.List;

public final class TransferPlatformImpl {
    private TransferPlatformImpl() {}
    public static TransferPlatform.Provider provider() {
        return new TransferPlatform.Provider() {
            public void push(MachineBlockEntity module) { TransferPlatformImpl.push(module); }
            public boolean interact(MachineBlockEntity module, Player player, InteractionHand hand, Direction side) { return TransferPlatformImpl.interact(module, player, hand, side); }
            public Object fluidStorage(MachineBlockEntity machine, Direction side) { return TransferPlatformImpl.fluids(machine, side); }
            public Object itemStorage(MachineBlockEntity machine, Direction side) { return TransferPlatformImpl.items(machine, side); }
            public Object energyStorage(MachineBlockEntity machine, Direction side) { return TransferPlatformImpl.energy(machine, side); }
            public boolean canOutputOn(MachineBlockEntity machine, Direction side) { return TransferPlatformImpl.canOutputOn(machine, side); }
            public MachineBlockEntity createMachineBlockEntity(BlockPos pos, BlockState state) {
                return ModList.get().isLoaded("buildcraftlib")
                    ? BuildCraftMachineBlockEntity.create(pos, state)
                    : new MachineBlockEntity(pos, state);
            }
        };
    }
    public static void registerCapabilities(net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent event) {
        var type=ModContent.MACHINE_ENTITY.get();
        event.registerBlockEntity(Capabilities.FluidHandler.BLOCK,type,TransferPlatformImpl::fluids);
        event.registerBlockEntity(Capabilities.ItemHandler.BLOCK,type,TransferPlatformImpl::items);
        event.registerBlockEntity(Capabilities.EnergyStorage.BLOCK,type,TransferPlatformImpl::energy);
    }
    public static void push(MachineBlockEntity module) {
        if (module.kind().isMjOutput()) return; // BCCE pulls from the native MJ port.
        var level=module.getLevel();
        var core=module.controller();if(core==null)return;
        for(Direction side:Direction.values()) {
            if(module.kind().hasFacing()&&module.getBlockState().getValue(BlockStateProperties.FACING)!=side)continue;
            if(!module.outputEnabled(side))continue;
            BlockEntity target=level.getBlockEntity(module.getBlockPos().relative(side));if(target==null)continue;
            if(module.kind().isEnergyOutput()) {
                IEnergyStorage dst=level.getCapability(Capabilities.EnergyStorage.BLOCK,target.getBlockPos(),side.getOpposite());if(dst==null||!dst.canReceive())continue;
                int moved=(int)core.extractEnergy(Math.min(core.outputLimit(module,side),Integer.MAX_VALUE),true);
                if(moved>0){int accepted=dst.receiveEnergy(moved,false);if(accepted>0){core.extractEnergy(accepted,false);core.recordOutput(module,side,accepted);}}
            } else if(module.kind()==MachineKind.FLUID_OUTPUT_SELECT) {
                IFluidHandler src=fluids(module,side),dst=level.getCapability(Capabilities.FluidHandler.BLOCK,target.getBlockPos(),side.getOpposite());
                if(src!=null&&dst!=null)for(int i=0;i<src.getTanks();i++){var stack=src.getFluidInTank(i);if(!stack.isEmpty()){var simulated=src.drain(stack, IFluidHandler.FluidAction.SIMULATE);int accepted=dst.fill(simulated,IFluidHandler.FluidAction.EXECUTE);if(accepted>0){src.drain(accepted,IFluidHandler.FluidAction.EXECUTE);core.recordOutput(module,side,accepted);}}}
            } else if(module.kind()==MachineKind.ITEM_OUTPUT) {
                IItemHandler src=items(module,side),dst=level.getCapability(Capabilities.ItemHandler.BLOCK,target.getBlockPos(),side.getOpposite());
                if(src!=null&&dst!=null)for(int i=0;i<src.getSlots();i++){var stack=src.extractItem(i,64,true);if(!stack.isEmpty()){var remainder=ItemHandlerHelper.insertItemStacked(dst,stack,false);if(remainder.getCount()<stack.getCount()){int moved=stack.getCount()-remainder.getCount();src.extractItem(i,moved,false);core.recordOutput(module,side,moved);}}}
            }
        }
    }
    public static boolean interact(MachineBlockEntity module,Player player,InteractionHand hand,Direction side) {
        var core=module.kind().controller()?module:module.controller();if(core==null)return false;
        IFluidHandler target=fluids(core,side);return target!=null&&FluidUtil.interactWithFluidHandler(player,hand,target);
    }
    public static IFluidHandler fluids(MachineBlockEntity machine,Direction side) {
        if (machine.kind().isMjOutput()) return BuildCraftMachineBlockEntity.canOutputOn(machine, side);
        if(machine.kind()==MachineKind.FLUID_OUTPUT_SELECT) {
            var core=machine.controller();if(core==null||!machine.outputEnabled(side))return null;
            return new NativeFluidHandler(core,core.outputSlots(core.outputSelection(machine,side)),false,true);
        }
        var core=machine.kind().controller()?machine:machine.kind()==MachineKind.FLUID_INPUT?machine.controller():null;
        return core==null?null:new NativeFluidHandler(core,core.inputSlots(),true,false);
    }
    public static IItemHandler items(MachineBlockEntity machine,Direction side) {
        if(machine.kind()==MachineKind.ITEM_OUTPUT) {
            var core=machine.controller();if(core==null||!machine.outputEnabled(side))return null;
            return new NativeItemHandler(core,core.outputSlots(core.outputSelection(machine,side)),false,true);
        }
        var core=machine.kind().controller()?machine:machine.kind()==MachineKind.ITEM_INPUT?machine.controller():null;
        if(core==null)return null;
        if(core.kind()==MachineKind.SYNGAS_CONTROLLER)return new NativeCarbonInventory(core);
        return new NativeItemHandler(core,core.inputSlots(),true,false);
    }
    public static IEnergyStorage energy(MachineBlockEntity machine,Direction side) {
        if(!machine.kind().isEnergyOutput()||side==null)return null;
        if(machine.kind().hasFacing()&&machine.getBlockState().getValue(BlockStateProperties.FACING)!=side)return null;
        var core=machine.controller();if(core==null)return null;
        if(!machine.outputEnabled(side))return null;
        return new IEnergyStorage(){
            public int receiveEnergy(int amount,boolean simulate){return 0;}
            public int extractEnergy(int amount,boolean simulate){if(amount<=0||!machine.outputEnabled(side))return 0;int moved=(int)Math.min(Math.min(amount,core.outputLimit(machine,side)),core.energy);if(!simulate&&moved>0){core.extractEnergy(moved,false);core.recordOutput(machine,side,moved);}return moved;}
            public int getEnergyStored(){return(int)Math.min(Integer.MAX_VALUE,core.energy);}
            public int getMaxEnergyStored(){return(int)Math.min(Integer.MAX_VALUE,core.energyCapacity);}
            public boolean canExtract(){return machine.outputEnabled(side)&&core.outputLimit(machine,side)>0;}public boolean canReceive(){return false;}
        };
    }
    public static boolean canOutputOn(MachineBlockEntity machine,Direction side) {
        var level=machine.getLevel();
        if(level==null||side==null||!machine.kind().output()||machine.controller()==null)return false;
        if(machine.kind().hasFacing()&&machine.getBlockState().getValue(BlockStateProperties.FACING)!=side)return false;
        var targetPos=machine.getBlockPos().relative(side);
        if(!level.hasChunkAt(targetPos))return false;
        if(machine.kind().isEnergyOutput()) {
            IEnergyStorage target=level.getCapability(Capabilities.EnergyStorage.BLOCK,targetPos,side.getOpposite());
            return target!=null&&target.canReceive();
        }
        if(machine.kind()==MachineKind.FLUID_OUTPUT_SELECT) {
            IFluidHandler target=level.getCapability(Capabilities.FluidHandler.BLOCK,targetPos,side.getOpposite());
            return target!=null;
        }
        if(machine.kind()==MachineKind.ITEM_OUTPUT) {
            IItemHandler target=level.getCapability(Capabilities.ItemHandler.BLOCK,targetPos,side.getOpposite());
            return target!=null;
        }
        return false;
    }
    private static final class NativeFluidHandler implements IFluidHandler {
        private final MachineBlockEntity core;private final int[] buffers;private final boolean fill,drain;
        NativeFluidHandler(MachineBlockEntity core,int[] buffers,boolean fill,boolean drain){this.core=core;this.buffers=buffers;this.fill=fill;this.drain=drain;}
        public int getTanks(){return buffers.length;}
        public FluidStack getFluidInTank(int i){var b=core.buffers[buffers[i]];return b.empty()||b.item?FluidStack.EMPTY:new FluidStack(b.fluid(),(int)Math.min(Integer.MAX_VALUE,Math.floor(b.amount)));}
        public int getTankCapacity(int i){return(int)Math.min(Integer.MAX_VALUE,Math.floor(core.buffers[buffers[i]].capacity));}
        public boolean isFluidValid(int i,FluidStack stack){return fill&&core.accepts(buffers[i],false,BuiltInRegistries.FLUID.getKey(stack.getFluid()),net.minecraft.core.component.DataComponentPatch.EMPTY);}
        public int fill(FluidStack resource,FluidAction action){if(!fill||resource.isEmpty())return 0;int accepted=0;for(int slot:buffers){double n=core.insertResource(slot,false,BuiltInRegistries.FLUID.getKey(resource.getFluid()),net.minecraft.core.component.DataComponentPatch.EMPTY,resource.getAmount()-accepted,action.simulate());accepted+=Math.floor(n);if(accepted>=resource.getAmount())break;}return accepted;}
        public FluidStack drain(FluidStack resource,FluidAction action){if(!drain||resource.isEmpty())return FluidStack.EMPTY;int moved=0;for(int slot:buffers){var b=core.buffers[slot];if(b.empty()||b.item||b.fluid()!=resource.getFluid())continue;moved+=(int)b.extract(resource.getAmount()-moved,action.simulate());if(moved>=resource.getAmount())break;}if(!action.simulate()&&moved>0)core.setChanged();return moved==0?FluidStack.EMPTY:new FluidStack(resource.getFluid(),moved);}
        public FluidStack drain(int max,FluidAction action){if(!drain||max<=0)return FluidStack.EMPTY;for(int slot:buffers){var b=core.buffers[slot];if(!b.empty()&&!b.item)return drain(new FluidStack(b.fluid(),Math.min(max,(int)b.amount)),action);}return FluidStack.EMPTY;}
    }
    private static final class NativeItemHandler implements IItemHandler {
        private final MachineBlockEntity core;private final int[] buffers;private final boolean insert,extract;
        NativeItemHandler(MachineBlockEntity core,int[] buffers,boolean insert,boolean extract){this.core=core;this.buffers=buffers;this.insert=insert;this.extract=extract;}
        public int getSlots(){return buffers.length;}
        public ItemStack getStackInSlot(int i){return core.buffers[buffers[i]].stack();}
        public ItemStack insertItem(int i,ItemStack stack,boolean simulate){if(!insert||stack.isEmpty()||!core.accepts(buffers[i],true,BuiltInRegistries.ITEM.getKey(stack.getItem()),stack.getComponentsPatch()))return stack;double n=core.insertResource(buffers[i],true,BuiltInRegistries.ITEM.getKey(stack.getItem()),stack.getComponentsPatch(),stack.getCount(),simulate);int accepted=(int)Math.floor(n);if(accepted==stack.getCount())return ItemStack.EMPTY;var result=stack.copy();result.shrink(accepted);return result;}
        public ItemStack extractItem(int i,int amount,boolean simulate){if(!extract||amount<=0)return ItemStack.EMPTY;var b=core.buffers[buffers[i]];if(!b.item||b.empty())return ItemStack.EMPTY;int n=(int)Math.floor(Math.min(amount,b.amount));var result=b.stack();result.setCount(n);if(!simulate&&n>0){b.extract(n,false);core.setChanged();}return result;}
        public int getSlotLimit(int i){return 64;}
        public boolean isItemValid(int i,ItemStack stack){return insert&&core.accepts(buffers[i],true,BuiltInRegistries.ITEM.getKey(stack.getItem()),stack.getComponentsPatch());}
    }
    private static final class NativeCarbonInventory implements IItemHandler {
        private final MachineBlockEntity core;NativeCarbonInventory(MachineBlockEntity core){this.core=core;}
        public int getSlots(){return core.inventory.getContainerSize();}
        public ItemStack getStackInSlot(int i){return core.inventory.getItem(i);}
        public ItemStack insertItem(int i,ItemStack stack,boolean simulate){
            if(stack.isEmpty()||core.carbonValue(stack)<=0||!core.inventory.canPlaceItem(i,stack))return stack;
            ItemStack existing=core.inventory.getItem(i);
            if(!existing.isEmpty()&&!ItemStack.isSameItemSameComponents(existing,stack))return stack;
            int current=existing.isEmpty()?0:existing.getCount();
            int limit=Math.min(core.inventory.getMaxStackSize(),stack.getMaxStackSize());
            int accepted=Math.min(stack.getCount(),limit-current);
            if(accepted<=0)return stack;
            if(!simulate){
                ItemStack updated=stack.copy();
                updated.setCount(current+accepted);
                core.inventory.setItem(i,updated);
                core.setChanged();
            }
            ItemStack remainder=stack.copy();
            remainder.shrink(accepted);
            return remainder;
        }
        public ItemStack extractItem(int i,int amount,boolean simulate){return ItemStack.EMPTY;}
        public int getSlotLimit(int i){return 64;}
        public boolean isItemValid(int i,ItemStack stack){return core.carbonValue(stack)>0;}
    }
}
