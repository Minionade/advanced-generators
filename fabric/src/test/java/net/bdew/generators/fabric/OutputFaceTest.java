package net.bdew.generators.fabric;

import net.bdew.generators.machine.MachineBlockEntity;
import net.bdew.generators.machine.MachineKind;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class OutputFaceTest {
    private static void field(Class<?> owner, Object object, String name, Object value) throws Exception {
        var field = owner.getDeclaredField(name);
        field.setAccessible(true);
        field.set(object,value);
    }
    private static MachineBlockEntity.OutputConfig config(int mode) throws Exception {
        var constructor = MachineBlockEntity.OutputConfig.class.getDeclaredConstructor(int.class,int.class,long.class);
        constructor.setAccessible(true);
        return constructor.newInstance(mode,0,Integer.MAX_VALUE);
    }
    @Test void seventhUnregisteredFaceCannotTransferEvenWhenDefaultsAreAlwaysOn() throws Exception {
        var level = mock(Level.class);
        var core = mock(MachineBlockEntity.class);
        var output = mock(MachineBlockEntity.class);
        var faces = new LinkedHashMap<MachineBlockEntity.OutputFace,MachineBlockEntity.OutputConfig>();
        for (int i=1;i<=6;i++) faces.put(new MachineBlockEntity.OutputFace(new BlockPos(i,0,0),Direction.NORTH),config(0));
        field(MachineBlockEntity.class,core,"outputConfigs",faces);
        field(BlockEntity.class,output,"level",level);
        field(BlockEntity.class,output,"worldPosition",BlockPos.ZERO);
        when(output.kind()).thenReturn(MachineKind.FE_OUTPUT);
        when(output.controller()).thenReturn(core);
        when(output.outputEnabled(Direction.NORTH)).thenCallRealMethod();
        assertFalse(output.outputEnabled(Direction.NORTH));
        faces.remove(faces.keySet().iterator().next());
        var settings = config(0);
        faces.put(new MachineBlockEntity.OutputFace(BlockPos.ZERO,Direction.NORTH),settings);
        assertTrue(output.outputEnabled(Direction.NORTH));
        settings.mode=3; // Disabled after the cable acquired its handler.
        assertFalse(output.outputEnabled(Direction.NORTH));
        settings.mode=1;
        assertFalse(output.outputEnabled(Direction.NORTH));
        when(level.hasNeighborSignal(BlockPos.ZERO)).thenReturn(true);
        assertTrue(output.outputEnabled(Direction.NORTH));
        settings.mode=2;
        assertFalse(output.outputEnabled(Direction.NORTH));
    }
    @Test void cachedConnectionsCannotReachRemovedOrReplacedControllers() throws Exception {
        var core = mock(MachineBlockEntity.class);
        var module = mock(MachineBlockEntity.class);
        field(MachineBlockEntity.class,core,"acceptingModules",true);
        when(core.canAccessFrom(module)).thenCallRealMethod();
        when(module.controller()).thenReturn(core);
        assertTrue(core.canAccessFrom(module));
        when(module.controller()).thenReturn(mock(MachineBlockEntity.class));
        assertFalse(core.canAccessFrom(module));
        when(module.controller()).thenReturn(core);
        when(core.isRemoved()).thenReturn(true);
        assertFalse(core.canAccessFrom(module));
        when(core.isRemoved()).thenReturn(false);
        field(MachineBlockEntity.class,core,"acceptingModules",false);
        assertFalse(core.canAccessFrom(module));
    }
}
