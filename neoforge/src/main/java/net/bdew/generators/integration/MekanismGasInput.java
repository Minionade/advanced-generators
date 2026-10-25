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
            for (RotaryRecipe recipe : recipes().stream().map(net.minecraft.world.item.crafting.RecipeHolder::value)
                    .filter(RotaryRecipe::hasChemicalToFluid).toList()) {
                FluidStack output = recipe.getFluidOutputDefinition().stream().findFirst().orElse(FluidStack.EMPTY);
                if (output.isEmpty()) continue;
                double fluidCapacity = 0;
                ResourceLocation outputId = BuiltInRegistries.FLUID.getKey(output.getFluid());
                for (int slot : core.inputSlots()) if (core.accepts(slot, false, outputId, DataComponentPatch.EMPTY))
                    fluidCapacity += Math.max(0, core.buffers[slot].capacity);
                long inputPerBatch = recipe.getChemicalInput().amount();
                long outputPerBatch = output.getAmount();
                if (outputPerBatch > 0) capacity = Math.max(capacity,
                    (long)Math.floor(fluidCapacity * inputPerBatch / outputPerBatch));
            }
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
            long inputPerBatch = recipe.getChemicalInput().amount();
            long outputPerBatch = output.getAmount();
            if (inputPerBatch <= 0 || outputPerBatch <= 0) return stack;
            long batches = stack.getAmount() / inputPerBatch;
            if (batches <= 0) return stack;
            long requested;
            try { requested = Math.multiplyExact(batches, outputPerBatch); }
            catch (ArithmeticException overflow) { requested = Integer.MAX_VALUE; batches = requested / outputPerBatch; }
            requested = Math.min(requested, Integer.MAX_VALUE);
            ResourceLocation fluidId = BuiltInRegistries.FLUID.getKey(output.getFluid());
            double available = insertFluid(core, fluidId, output, requested, true);
            long acceptedBatches = Math.min(batches, (long)Math.floor(available / outputPerBatch));
            if (acceptedBatches <= 0) return stack;
            long acceptedFluid = acceptedBatches * outputPerBatch;
            if (action.execute()) insertFluid(core, fluidId, output, acceptedFluid, false);
            long consumed = acceptedBatches * inputPerBatch;
            return consumed == 0 ? stack : stack.copyWithAmount(stack.getAmount() - consumed);
        }
        @Override public ChemicalStack extractChemical(int tank, long amount, Action action) { return ChemicalStack.EMPTY; }

        private static double insertFluid(MachineBlockEntity core, ResourceLocation id, FluidStack output,
                                          long amount, boolean simulate) {
            double accepted = 0;
            for (int slot : core.inputSlots()) {
                if (!core.accepts(slot, false, id, DataComponentPatch.EMPTY)) continue;
                accepted += core.insertResource(slot, false, id, DataComponentPatch.EMPTY,
                    amount - accepted, simulate);
                if (accepted >= amount) break;
            }
            return accepted;
        }

        private static boolean accepts(MachineBlockEntity core, net.minecraft.world.level.material.Fluid fluid) {
            ResourceLocation id = BuiltInRegistries.FLUID.getKey(fluid);
            for (int slot : core.inputSlots())
                if (core.accepts(slot, false, id, DataComponentPatch.EMPTY)) return true;
            return false;
        }
    }
}
