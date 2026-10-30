package net.bdew.generators.machine;

import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ResourceBufferTest {
    private static final ResourceLocation OBSIDIAN = ResourceLocation.parse("minecraft:obsidian");
    private static final ResourceLocation LAVA = ResourceLocation.parse("minecraft:lava");

    @Test void itemsFillAnExchangerBufferAtOneTwoFiftiethOfTheFluidCapacity() {
        var buffer = new ResourceBuffer(16000);
        assertEquals(64, buffer.insert(true, OBSIDIAN, DataComponentPatch.EMPTY, 1000, false));
        assertEquals(0, buffer.insert(true, OBSIDIAN, DataComponentPatch.EMPTY, 1, false));
        assertEquals(64, buffer.effectiveCapacity());
    }

    @Test void fluidsKeepTheFullCapacity() {
        var buffer = new ResourceBuffer(16000);
        assertEquals(16000, buffer.insert(false, LAVA, DataComponentPatch.EMPTY, 20000, false));
        assertEquals(16000, buffer.effectiveCapacity());
    }

    @Test void fractionalItemAmountsStillAccumulate() {
        var buffer = new ResourceBuffer(16000);
        for (int i = 0; i < 1000; i++) buffer.insert(true, OBSIDIAN, DataComponentPatch.EMPTY, .001, false);
        assertEquals(1, buffer.amount, 1e-9);
    }
}
