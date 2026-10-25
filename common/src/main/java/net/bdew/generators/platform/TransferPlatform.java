package net.bdew.generators.platform;

import net.bdew.generators.machine.MachineBlockEntity;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

/** Loader-neutral entry points for machine transfer integrations. */
public final class TransferPlatform {
    public interface Provider {
        void push(MachineBlockEntity machine);
        boolean interact(MachineBlockEntity machine, Player player, InteractionHand hand, Direction side);
        Object fluidStorage(MachineBlockEntity machine, Direction side);
        Object itemStorage(MachineBlockEntity machine, Direction side);
        Object energyStorage(MachineBlockEntity machine, Direction side);
        boolean canOutputOn(MachineBlockEntity machine, Direction side);
        MachineBlockEntity createMachineBlockEntity(BlockPos pos, BlockState state);
    }

    private static volatile Provider provider;

    private TransferPlatform() {}

    public static void install(Provider value) {
        if (provider != null) throw new IllegalStateException("Transfer provider already installed");
        provider = value;
    }

    private static Provider provider() {
        if (provider == null) throw new IllegalStateException("Transfer provider has not been installed");
        return provider;
    }

    public static void push(MachineBlockEntity machine) { provider().push(machine); }
    public static boolean interact(MachineBlockEntity machine, Player player, InteractionHand hand, Direction side) { return provider().interact(machine, player, hand, side); }
    public static Object fluidStorage(MachineBlockEntity machine, Direction side) { return provider().fluidStorage(machine, side); }
    public static Object itemStorage(MachineBlockEntity machine, Direction side) { return provider().itemStorage(machine, side); }
    public static Object energyStorage(MachineBlockEntity machine, Direction side) { return provider().energyStorage(machine, side); }
    public static boolean canOutputOn(MachineBlockEntity machine, Direction side) { return provider().canOutputOn(machine, side); }
    public static MachineBlockEntity createMachineBlockEntity(BlockPos pos, BlockState state) { return provider().createMachineBlockEntity(pos, state); }
}
