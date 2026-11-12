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

    @Test void wholeItemInsertionDoesNotDuplicateTheFractionThatWillNotFit() {
        var buffer = new ResourceBuffer(16000);
        buffer.insert(true, OBSIDIAN, DataComponentPatch.EMPTY, 62.75, false);
        assertEquals(1, buffer.insertWhole(true, OBSIDIAN, DataComponentPatch.EMPTY, 2, true));
        assertEquals(62.75, buffer.amount);
        assertEquals(1, buffer.insertWhole(true, OBSIDIAN, DataComponentPatch.EMPTY, 2, false));
        assertEquals(63.75, buffer.amount);
        assertEquals(0, buffer.insertWhole(true, OBSIDIAN, DataComponentPatch.EMPTY, 1, false));
        assertEquals(63.75, buffer.amount);
    }

    @Test void wholeFluidInsertionOnlyAddsTheReportedAmount() {
        var buffer = new ResourceBuffer(16000);
        buffer.insert(false, LAVA, DataComponentPatch.EMPTY, 15998.75, false);
        assertEquals(1, buffer.insertWhole(false, LAVA, DataComponentPatch.EMPTY, 1000, false));
        assertEquals(15999.75, buffer.amount);
    }

    @Test void wholeFluidExtractionPreservesFractionalOutput() {
        var buffer = new ResourceBuffer(16000);
        buffer.insert(false, LAVA, DataComponentPatch.EMPTY, 1.75, false);
        assertEquals(1, buffer.extractWhole(1000, true));
        assertEquals(1.75, buffer.amount);
        assertEquals(1, buffer.extractWhole(1000, false));
        assertEquals(.75, buffer.amount);
        assertEquals(0, buffer.extractWhole(1000, false));
        assertEquals(.75, buffer.amount);
    }
}
