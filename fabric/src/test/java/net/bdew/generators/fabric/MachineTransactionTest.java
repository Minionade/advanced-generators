package net.bdew.generators.fabric;

import net.bdew.generators.machine.MachineBlockEntity;
import net.bdew.generators.machine.MachineKind;
import net.bdew.generators.machine.ResourceBuffer;
import net.fabricmc.fabric.api.transfer.v1.transaction.Transaction;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class MachineTransactionTest {
    // Avoid constructing registered blocks or starting a world: only resource ownership is under test.
    private MachineBlockEntity controller() throws Exception {
        var machine = mock(MachineBlockEntity.class);
        var buffers = new ResourceBuffer[]{new ResourceBuffer(16000)};
        MachineBlockEntity.class.getField("buffers").set(machine, buffers);
        buffers[0].insert(true, ResourceLocation.parse("minecraft:obsidian"), DataComponentPatch.EMPTY, 2.75, false);
        return machine;
    }

    @Test void nestedRollbackPreservesOuterChangesAndFractions() throws Exception {
        var machine = controller();
        machine.energy = 100;
        var state = MachineTransaction.of(machine);
        assertSame(state, MachineTransaction.of(machine));
        try (var outer = Transaction.openOuter()) {
            state.beforeChange(outer);
            machine.buffers[0].extract(1, false);
            machine.energy = 80;
            try (var inner = outer.openNested()) {
                state.beforeChange(inner);
                machine.buffers[0].extract(1, false);
                machine.energy = 60;
            }
            assertEquals(1.75, machine.buffers[0].amount);
            assertEquals(80, machine.energy);
            outer.commit();
        }
        assertEquals(1.75, machine.buffers[0].amount);
        assertEquals(80, machine.energy);
        verify(machine).setChanged();
    }

    @Test void committedNestedTransferStillRollsBackWithOuterTransaction() throws Exception {
        var machine = controller();
        var state = MachineTransaction.of(machine);
        try (var outer = Transaction.openOuter()) {
            try (var inner = outer.openNested()) {
                state.beforeChange(inner);
                machine.buffers[0].extract(2, false);
                inner.commit();
            }
        }
        assertEquals(2.75, machine.buffers[0].amount);
        verify(machine, never()).setChanged();
    }

    @Test void cachedEnergyPortsShareLiveBalanceAndRollback() throws Exception {
        var machine = controller();
        var module = mock(MachineBlockEntity.class);
        when(module.controller()).thenReturn(machine);
        when(module.outputEnabled(Direction.NORTH)).thenReturn(true);
        when(machine.outputLimit(module, Direction.NORTH)).thenReturn(1000L);
        doCallRealMethod().when(machine).extractEnergy(anyLong(), anyBoolean());
        var first = new EnergyBufferStorage(machine, module, Direction.NORTH, MachineKind.EU_OUTPUT_LV);
        var second = new EnergyBufferStorage(machine, module, Direction.NORTH, MachineKind.EU_OUTPUT_LV);
        machine.energy = 100; // Energy produced after the adapters were cached.
        try (var outer = Transaction.openOuter()) {
            assertEquals(20, first.extract(20, outer));
            assertEquals(5, second.getAmount());
            try (var inner = outer.openNested()) {
                assertEquals(5, second.extract(20, inner));
                assertEquals(0, first.getAmount());
            }
            assertEquals(5, first.getAmount());
        }
        assertEquals(100, machine.energy);
    }
}
