package net.bdew.generators.machine;

import net.bdew.generators.legacy.LegacyBlockEntityData;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.TagParser;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class ModuleInventoryTest {
    @Test void realScalaSaveRetainsItsUntypedMembershipAcrossConversionAndResaving() throws Exception {
        CompoundTag old;
        try (var in = getClass().getResourceAsStream("/legacy/real/fuel_turbine_controller.snbt")) {
            assertNotNull(in);
            old = TagParser.parseTag(new String(in.readAllBytes(), StandardCharsets.UTF_8));
        }
        var inventory = new ModuleInventory();
        inventory.load(LegacyBlockEntityData.upgrade(old, MachineKind.FUEL_TURBINE_CONTROLLER));
        var expected = Set.of(new BlockPos(1,-60,1), new BlockPos(0,-60,1),
            new BlockPos(1,-60,0), new BlockPos(1,-59,0), new BlockPos(0,-59,0));
        assertEquals(expected, inventory.positions());
        assertFalse(inventory.knowsAll(expected));
        var saved = new CompoundTag();
        inventory.save(saved);
        var reloaded = new ModuleInventory();
        reloaded.load(saved);
        assertEquals(expected, reloaded.positions());
        assertFalse(reloaded.knowsAll(expected));
    }
}
