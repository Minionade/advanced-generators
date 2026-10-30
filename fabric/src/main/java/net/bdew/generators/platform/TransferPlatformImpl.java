package net.bdew.generators.fabric.platform;

import net.bdew.generators.fabric.FabricTransfers;
import net.bdew.generators.machine.MachineBlockEntity;
import net.bdew.generators.platform.TransferPlatform;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

public final class TransferPlatformImpl {
    public static TransferPlatform.Provider provider() {
        return new TransferPlatform.Provider() {
            public void push(MachineBlockEntity module){FabricTransfers.push(module);}
            public boolean interact(MachineBlockEntity module,Player player,InteractionHand hand,Direction side){return FabricTransfers.interact(module,player,hand,side);}
            public Object fluidStorage(MachineBlockEntity machine,Direction side){return FabricTransfers.fluids(machine,side);}
            public Object itemStorage(MachineBlockEntity machine,Direction side){return FabricTransfers.items(machine,side);}
            public Object energyStorage(MachineBlockEntity machine,Direction side){return FabricTransfers.energy(machine,side);}
            public boolean canOutputOn(MachineBlockEntity machine,Direction side){return FabricTransfers.canOutputOn(machine,side);}
            public double externalFuelFePerMb(net.minecraft.world.level.material.Fluid fluid){return 0;}
            public void capabilitiesChanged(MachineBlockEntity machine){}
            public MachineBlockEntity createMachineBlockEntity(BlockPos pos,BlockState state){return new MachineBlockEntity(pos,state);}
        };
    }
    private TransferPlatformImpl(){}
}
