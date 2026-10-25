package net.bdew.generators.fabric;

import net.bdew.generators.ModContent;
import net.bdew.generators.machine.*;
import net.fabricmc.fabric.api.transfer.v1.context.ContainerItemContext;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidStorage;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidVariant;
import net.fabricmc.fabric.api.transfer.v1.item.ItemStorage;
import net.fabricmc.fabric.api.transfer.v1.item.ItemVariant;
import net.fabricmc.fabric.api.transfer.v1.item.InventoryStorage;
import net.fabricmc.fabric.api.transfer.v1.storage.Storage;
import net.fabricmc.fabric.api.transfer.v1.storage.StorageUtil;
import net.fabricmc.fabric.api.transfer.v1.storage.base.CombinedStorage;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import java.util.ArrayList;
import java.util.List;

public final class FabricTransfers {
    public interface EnergyIntegration {
        void register(net.minecraft.world.level.block.entity.BlockEntityType<MachineBlockEntity> type);
        Object storage(MachineBlockEntity machine,Direction side);
        boolean canOutputOn(MachineBlockEntity machine,Direction side);
        void push(MachineBlockEntity module);
    }
    private static EnergyIntegration energyIntegration;
    private FabricTransfers() {}
    public static void register() {
        var type=ModContent.MACHINE_ENTITY.get();
        FluidStorage.SIDED.registerForBlockEntity((be,side)->fluids(be,side),type);
        ItemStorage.SIDED.registerForBlockEntity((be,side)->items(be,side),type);
    }
    public static void installEnergyIntegration(EnergyIntegration integration) {
        energyIntegration=integration;
        integration.register(ModContent.MACHINE_ENTITY.get());
    }
    public static Storage<FluidVariant> fluids(MachineBlockEntity machine,Direction side) {
        if(machine.kind()==MachineKind.FLUID_OUTPUT_SELECT || machine.kind()==MachineKind.ITEM_OUTPUT || machine.kind()==MachineKind.FE_OUTPUT) {
            if(machine.kind()!=MachineKind.FLUID_OUTPUT_SELECT)return null;
            var core=machine.controller(); if(core==null||!machine.outputEnabled(side))return null;
            var parts=new ArrayList<FluidBufferStorage>();
            for(int slot:core.outputSlots(core.outputSelection(machine,side)))parts.add(new FluidBufferStorage(core,slot,false,true));
            return new CombinedStorage<FluidVariant,FluidBufferStorage>(parts);
        }
        var core=machine.kind().controller()?machine:machine.kind()==MachineKind.FLUID_INPUT?machine.controller():null;
        if(core==null)return null;
        var parts=new ArrayList<FluidBufferStorage>();
        for(int slot:core.inputSlots())parts.add(new FluidBufferStorage(core,slot,true,false));
        return new CombinedStorage<FluidVariant,FluidBufferStorage>(parts);
    }
    public static Storage<ItemVariant> items(MachineBlockEntity machine,Direction side) {
        if(machine.kind()==MachineKind.ITEM_OUTPUT) {
            var core=machine.controller();if(core==null||!machine.outputEnabled(side))return null;
            var parts=new ArrayList<ItemBufferStorage>();
            for(int slot:core.outputSlots(core.outputSelection(machine,side)))parts.add(new ItemBufferStorage(core,slot,false,true));
            return new CombinedStorage<ItemVariant,ItemBufferStorage>(parts);
        }
        var core=machine.kind().controller()?machine:machine.kind()==MachineKind.ITEM_INPUT?machine.controller():null;
        if(core==null)return null;
        if(core.kind()==MachineKind.SYNGAS_CONTROLLER)return InventoryStorage.of(core.inventory,side);
        var parts=new ArrayList<ItemBufferStorage>();
        for(int slot:core.inputSlots())parts.add(new ItemBufferStorage(core,slot,true,false));
        return new CombinedStorage<ItemVariant,ItemBufferStorage>(parts);
    }
    public static Object energy(MachineBlockEntity machine,Direction side) {
        return energyIntegration==null?null:energyIntegration.storage(machine,side);
    }
    public static boolean canOutputOn(MachineBlockEntity machine,Direction side) {
        var level=machine.getLevel();
        if(level==null||side==null||!machine.kind().output()||machine.controller()==null)return false;
        if(machine.kind().hasFacing()&&machine.getBlockState().getValue(BlockStateProperties.FACING)!=side)return false;
        if(machine.kind().isEnergyOutput())return energyIntegration!=null&&energyIntegration.canOutputOn(machine,side);
        var targetPos=machine.getBlockPos().relative(side);
        if(!level.hasChunkAt(targetPos))return false;
        if(machine.kind()==MachineKind.FLUID_OUTPUT_SELECT) {
            var target=FluidStorage.SIDED.find(level,targetPos,side.getOpposite());
            return target!=null&&target.supportsInsertion();
        }
        if(machine.kind()==MachineKind.ITEM_OUTPUT) {
            var target=ItemStorage.SIDED.find(level,targetPos,side.getOpposite());
            return target!=null&&target.supportsInsertion();
        }
        return false;
    }
    public static void push(MachineBlockEntity module) {
        if(module.kind().isEnergyOutput()) {
            if(energyIntegration!=null)energyIntegration.push(module);
            return;
        }
        var level=module.getLevel();
        var core=module.controller();if(core==null)return;
        for(Direction side:Direction.values()) {
            if(module.kind().hasFacing()&&module.getBlockState().getValue(BlockStateProperties.FACING)!=side)continue;
            if(!module.outputEnabled(side))continue;
            BlockEntity target=level.getBlockEntity(module.getBlockPos().relative(side));if(target==null)continue;
            if(module.kind()==MachineKind.FLUID_OUTPUT_SELECT) {
                var source=fluids(module,side);var destination=FluidStorage.SIDED.find(level,target.getBlockPos(),side.getOpposite());
                if(source!=null&&destination!=null){long moved=StorageUtil.move(source,destination,v->true,81000,null);core.recordOutput(module,side,moved);}
            } else if(module.kind()==MachineKind.ITEM_OUTPUT) {
                var source=items(module,side);var destination=ItemStorage.SIDED.find(level,target.getBlockPos(),side.getOpposite());
                if(source!=null&&destination!=null){long moved=StorageUtil.move(source,destination,v->true,64,null);core.recordOutput(module,side,moved);}
            }
        }
    }
    public static boolean interact(MachineBlockEntity module,Player player,InteractionHand hand,Direction side) {
        var core=module.kind().controller()?module:module.controller();if(core==null)return false;
        var context=ContainerItemContext.ofPlayerHand(player,hand);
        Storage<FluidVariant> heldFluid=context.find(FluidStorage.ITEM);
        if(heldFluid!=null) {
            var target=fluids(core,side);
            if(target!=null&&StorageUtil.move(heldFluid,target,v->true,81000,null)>0)return true;
        }
        Storage<ItemVariant> heldItems=ItemStorage.ITEM.find(player.getItemInHand(hand),context);
        var target=items(core,side);
        return heldItems!=null&&target!=null&&StorageUtil.move(heldItems,target,v->true,64,null)>0;
    }
}
