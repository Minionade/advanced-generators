package net.bdew.generators.integration;

import mekanism.api.Action;
import mekanism.api.chemical.ChemicalStack;
import mekanism.api.chemical.IChemicalHandler;
import mekanism.api.recipes.MekanismRecipeTypes;
import mekanism.api.recipes.RotaryRecipe;
import mekanism.common.capabilities.Capabilities;
import net.bdew.generators.ModContent;
import net.bdew.generators.machine.MachineBlockEntity;
import net.bdew.generators.machine.MachineKind;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.fluids.FluidStack;

/** Converts Mekanism chemicals through Rotary Condensentrator recipes into machine fluids. */
public final class MekanismGasInput {
    private MekanismGasInput() {}

    public static void register(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(Capabilities.CHEMICAL.block(), ModContent.MACHINE_ENTITY.get(),
            (machine, side) -> machine.kind() == MachineKind.GAS_INPUT ? new ChemicalInput(machine) : null);
    }

    private static final class ChemicalInput implements IChemicalHandler {
        private final MachineBlockEntity module;
        private ChemicalInput(MachineBlockEntity module) { this.module = module; }
        private MachineBlockEntity core() { return module.controller(); }
        private java.util.List<net.minecraft.world.item.crafting.RecipeHolder<RotaryRecipe>> recipes() {
            if (module.getLevel() == null) return java.util.List.of();
            return module.getLevel().getRecipeManager().getAllRecipesFor(MekanismRecipeTypes.TYPE_ROTARY.value());
        }
        private RotaryRecipe conversion(ChemicalStack input) {
            if (input.isEmpty()) return null;
            return recipes().stream().map(net.minecraft.world.item.crafting.RecipeHolder::value)
                .filter(recipe -> recipe.hasChemicalToFluid() && recipe.test(input)).findFirst().orElse(null);
        }
        @Override public int getChemicalTanks() { return core() == null ? 0 : 1; }
        @Override public ChemicalStack getChemicalInTank(int tank) { return ChemicalStack.EMPTY; }
        @Override public void setChemicalInTank(int tank, ChemicalStack stack) {
            if (!stack.isEmpty() && !insertChemical(tank, stack, Action.EXECUTE).isEmpty())
                throw new IllegalStateException("Mekanism chemical did not fit in Advanced Generators input");
        }
        @Override public long getChemicalTankCapacity(int tank) {
            MachineBlockEntity core = core();
            if (core == null) return 0;
            long capacity = 0;
            for (int slot : core.inputSlots()) capacity += (long)Math.max(0, core.buffers[slot].capacity);
            return capacity;
        }
        @Override public boolean isValid(int tank, ChemicalStack stack) {
            RotaryRecipe recipe = conversion(stack);
            MachineBlockEntity core = core();
            if (recipe == null || core == null) return false;
            FluidStack output = recipe.getFluidOutput(stack);
            return !output.isEmpty() && accepts(core, output.getFluid());
        }
        @Override public ChemicalStack insertChemical(int tank, ChemicalStack stack, Action action) {
            if (stack.isEmpty()) return ChemicalStack.EMPTY;
            RotaryRecipe recipe = conversion(stack);
            MachineBlockEntity core = core();
            if (recipe == null || core == null) return stack;
            FluidStack output = recipe.getFluidOutput(stack);
            if (output.isEmpty()) return stack;
            int amount = (int)Math.min(Integer.MAX_VALUE, stack.getAmount());
            ResourceLocation fluidId = BuiltInRegistries.FLUID.getKey(output.getFluid());
            boolean simulate = action == Action.SIMULATE;
            double accepted = 0;
            for (int slot : core.inputSlots()) {
                if (!core.accepts(slot, false, fluidId, DataComponentPatch.EMPTY)) continue;
                accepted += core.insertResource(slot, false, fluidId, DataComponentPatch.EMPTY,
                    amount - accepted, simulate);
                if (accepted >= amount) break;
            }
            long consumed = Math.min(stack.getAmount(), (long)Math.floor(accepted));
            return consumed == 0 ? stack : stack.copyWithAmount(stack.getAmount() - consumed);
        }
        @Override public ChemicalStack extractChemical(int tank, long amount, Action action) { return ChemicalStack.EMPTY; }

        private static boolean accepts(MachineBlockEntity core, net.minecraft.world.level.material.Fluid fluid) {
            ResourceLocation id = BuiltInRegistries.FLUID.getKey(fluid);
            for (int slot : core.inputSlots())
                if (core.accepts(slot, false, id, DataComponentPatch.EMPTY)) return true;
            return false;
        }
    }
}
