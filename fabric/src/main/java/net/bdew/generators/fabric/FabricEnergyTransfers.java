package net.bdew.generators.fabric;

import net.bdew.generators.machine.MachineBlockEntity;
import net.bdew.generators.machine.MachineKind;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import team.reborn.energy.api.EnergyStorage;

/** Team Reborn Energy API adapter, loaded only when the API mod is installed. */
final class FabricEnergyTransfers implements FabricTransfers.EnergyIntegration {
    private static final long FE_PER_EU=4;

    @Override public void register(BlockEntityType<MachineBlockEntity> type) {
        EnergyStorage.SIDED.registerForBlockEntity((machine,side)->(EnergyStorage)storage(machine,side),type);
    }

    @Override public Object storage(MachineBlockEntity machine,Direction side) {
        if(!machine.kind().isEnergyOutput()||side==null)return null;
        if(machine.kind().hasFacing()&&machine.getBlockState().getValue(BlockStateProperties.FACING)!=side)return null;
        var core=machine.controller();
        return core==null||!machine.outputEnabled(side)?null:new EnergyBufferStorage(core,machine,side,machine.kind());
    }

    @Override public boolean canOutputOn(MachineBlockEntity machine,Direction side) {
        var level=machine.getLevel();
        if(level==null||side==null||!machine.kind().isEnergyOutput()||machine.controller()==null)return false;
        if(machine.kind().hasFacing()&&machine.getBlockState().getValue(BlockStateProperties.FACING)!=side)return false;
        var targetPos=machine.getBlockPos().relative(side);
        if(!level.hasChunkAt(targetPos))return false;
        var target=EnergyStorage.SIDED.find(level,targetPos,side.getOpposite());
        return target!=null&&target.supportsInsertion();
    }

    @Override public void push(MachineBlockEntity module) {
        var level=module.getLevel();
        var core=module.controller();if(core==null)return;
        for(Direction side:Direction.values()) {
            if(module.kind().hasFacing()&&module.getBlockState().getValue(BlockStateProperties.FACING)!=side)continue;
            if(!module.outputEnabled(side))continue;
            BlockEntity target=level.getBlockEntity(module.getBlockPos().relative(side));if(target==null)continue;
            var destination=EnergyStorage.SIDED.find(level,target.getBlockPos(),side.getOpposite());if(destination==null)continue;
            if(module.kind().isEuOutput()) {
                long movedEu=Math.min(module.kind().euOutputRate(),Math.max(0,core.outputLimit(module,side))/FE_PER_EU);
                movedEu=Math.min(movedEu,core.energy/FE_PER_EU);
                if(movedEu>0){long accepted=destination.insert(movedEu,null);if(accepted>0){core.extractEnergy(accepted*FE_PER_EU,false);core.recordOutput(module,side,accepted);}}
            } else {
                long moved=core.extractEnergy(Math.min(core.outputLimit(module,side),Integer.MAX_VALUE),true);
                if(moved>0){long accepted=destination.insert(moved,null);if(accepted>0){core.extractEnergy(accepted,false);core.recordOutput(module,side,accepted);}}
            }
        }
    }
}
