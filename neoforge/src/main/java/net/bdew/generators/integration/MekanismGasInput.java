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
        @Override public int getChemicalTanks() {
            MachineBlockEntity core = core();
            return core == null ? 0 : core.inputSlots().length;
        }
        @Override public ChemicalStack getChemicalInTank(int tank) {
            MachineBlockEntity core = core();
            int slot = slot(core, tank);
            if (slot < 0 || core.buffers[slot].empty() || core.buffers[slot].item) return ChemicalStack.EMPTY;
            return chemicalFromFluid(new FluidStack(core.buffers[slot].fluid(),
                (int)Math.min(Integer.MAX_VALUE, core.buffers[slot].amount)));
        }
        @Override public void setChemicalInTank(int tank, ChemicalStack stack) {
            if (!stack.isEmpty() && !insertChemical(tank, stack, Action.EXECUTE).isEmpty())
                throw new IllegalStateException("Mekanism chemical did not fit in Advanced Generators input");
        }
        @Override public long getChemicalTankCapacity(int tank) {
            MachineBlockEntity core = core();
            if (core == null) return 0;
            int slot = slot(core, tank);
            return slot < 0 ? 0 : (long)Math.min(Long.MAX_VALUE, core.buffers[slot].capacity);
        }
        @Override public boolean isValid(int tank, ChemicalStack stack) {
            RotaryRecipe recipe = conversion(stack);
            MachineBlockEntity core = core();
            if (recipe == null || core == null) return false;
            FluidStack output = recipe.getFluidOutput(stack);
            return !output.isEmpty() && accepts(core, tank, output.getFluid());
        }
        @Override public ChemicalStack insertChemical(int tank, ChemicalStack stack, Action action) {
            if (stack.isEmpty()) return ChemicalStack.EMPTY;
            RotaryRecipe recipe = conversion(stack);
            MachineBlockEntity core = core();
            if (recipe == null || core == null) return stack;
            FluidStack output = recipe.getFluidOutput(stack);
            if (output.isEmpty()) return stack;
            int requested = (int)Math.min(Integer.MAX_VALUE, stack.getAmount());
            output.setAmount(requested);
            ResourceLocation fluidId = BuiltInRegistries.FLUID.getKey(output.getFluid());
            int slot = slot(core, tank);
            if (slot < 0 || !core.accepts(slot, false, fluidId, DataComponentPatch.EMPTY)) return stack;
            double accepted = core.insertResource(slot, false, fluidId, DataComponentPatch.EMPTY, requested, !action.execute());
            int consumed = Math.min(requested, (int)Math.floor(accepted));
            return consumed == 0 ? stack : stack.copyWithAmount(stack.getAmount() - consumed);
        }
        @Override public ChemicalStack extractChemical(int tank, long amount, Action action) {
            MachineBlockEntity core = core();
            int slot = slot(core, tank);
            if (slot < 0) return ChemicalStack.EMPTY;
            var buffer = core.buffers[slot];
            if (buffer.empty() || buffer.item) return ChemicalStack.EMPTY;
            int requested = (int)Math.min(Integer.MAX_VALUE, Math.min(amount, Math.floor(buffer.amount)));
            if (requested <= 0) return ChemicalStack.EMPTY;
            var fluid = new FluidStack(buffer.fluid(), requested);
            ChemicalStack converted = chemicalFromFluid(fluid);
            if (converted.isEmpty()) return ChemicalStack.EMPTY;
            double extracted = buffer.extract(requested, !action.execute());
            if (extracted <= 0) return ChemicalStack.EMPTY;
            converted.setAmount((long)Math.floor(extracted));
            return converted;
        }

        private ChemicalStack chemicalFromFluid(FluidStack fluid) {
            if (fluid.isEmpty()) return ChemicalStack.EMPTY;
            for (var holder : recipes()) {
                RotaryRecipe recipe = holder.value();
                if (recipe.hasFluidToChemical() && recipe.test(fluid)) {
                    ChemicalStack result = recipe.getChemicalOutput(fluid);
                    result.setAmount(fluid.getAmount());
                    return result;
                }
            }
            return ChemicalStack.EMPTY;
        }
        private static int slot(MachineBlockEntity core, int tank) {
            if (core == null) return -1;
            int[] slots = core.inputSlots();
            return tank < 0 || tank >= slots.length ? -1 : slots[tank];
        }
        private static boolean accepts(MachineBlockEntity core, int tank, net.minecraft.world.level.material.Fluid fluid) {
            ResourceLocation id = BuiltInRegistries.FLUID.getKey(fluid);
            int slot = slot(core, tank);
            return slot >= 0 && core.accepts(slot, false, id, DataComponentPatch.EMPTY);
        }
    }
}
