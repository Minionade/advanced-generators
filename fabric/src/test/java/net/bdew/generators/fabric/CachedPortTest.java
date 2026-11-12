package net.bdew.generators.fabric;

import net.bdew.generators.machine.MachineBlockEntity;
import net.bdew.generators.machine.MachineKind;
import net.minecraft.core.Direction;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CachedPortTest {
    private static final Direction SIDE = Direction.NORTH;

    @Test void cachedFluidPortsFollowSelectionAndRedstoneWithoutBeingRecreated() {
        var core = mock(MachineBlockEntity.class);
        var module = mock(MachineBlockEntity.class);
        when(core.kind()).thenReturn(MachineKind.EXCHANGER_CONTROLLER);
        when(module.kind()).thenReturn(MachineKind.FLUID_OUTPUT_SELECT);
        when(core.canAccessFrom(module)).thenReturn(true);
        when(module.outputEnabled(SIDE)).thenReturn(true);
        when(core.outputSlots(anyInt())).thenCallRealMethod();
        when(core.canAccessBuffer(module,SIDE,2,true)).thenCallRealMethod();
        when(core.canAccessBuffer(module,SIDE,3,true)).thenCallRealMethod();
        var hot = new FluidBufferStorage(core,module,SIDE,2,false,true);
        var cold = new FluidBufferStorage(core,module,SIDE,3,false,true);

        assertTrue(hot.supportsExtraction());
        assertTrue(cold.supportsExtraction());
        when(core.outputSelection(module,SIDE)).thenReturn(1); // Cold only
        assertFalse(hot.supportsExtraction());
        assertTrue(cold.supportsExtraction());
        when(core.outputSelection(module,SIDE)).thenReturn(2); // Hot only
        assertTrue(hot.supportsExtraction());
        assertFalse(cold.supportsExtraction());
        when(module.outputEnabled(SIDE)).thenReturn(false);
        assertFalse(hot.supportsExtraction());
        assertFalse(cold.supportsExtraction());
        when(module.outputEnabled(SIDE)).thenReturn(true);
        when(core.outputSelection(module,SIDE)).thenReturn(0);
        assertTrue(hot.supportsExtraction());
        assertTrue(cold.supportsExtraction());
    }

    @Test void cachedItemAndEnergyPortsStopWhenTheirControllerChanges() {
        var core = mock(MachineBlockEntity.class);
        var module = mock(MachineBlockEntity.class);
        when(core.canAccessFrom(module)).thenReturn(true);
        when(core.kind()).thenReturn(MachineKind.EXCHANGER_CONTROLLER);
        when(module.kind()).thenReturn(MachineKind.ITEM_OUTPUT);
        when(module.outputEnabled(SIDE)).thenReturn(true);
        when(core.outputSlots(anyInt())).thenCallRealMethod();
        when(core.canAccessBuffer(module,SIDE,3,true)).thenCallRealMethod();
        var item = new ItemBufferStorage(core,module,SIDE,3,false,true);
        var energy = new EnergyBufferStorage(core,module,SIDE,MachineKind.EU_OUTPUT_LV);
        assertTrue(item.supportsExtraction());
        assertTrue(energy.supportsExtraction());
        when(core.canAccessFrom(module)).thenReturn(false);
        assertFalse(item.supportsExtraction());
        assertFalse(energy.supportsExtraction());
    }
}
