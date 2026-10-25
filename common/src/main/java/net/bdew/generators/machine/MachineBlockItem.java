package net.bdew.generators.machine;

import net.bdew.generators.config.GeneratorConfig;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

import java.text.NumberFormat;
import java.util.List;
import java.util.Locale;

/** Restores the dynamic legacy descriptions for generator modules. */
public final class MachineBlockItem extends BlockItem {
    private final MachineKind kind;

    public MachineBlockItem(MachineBlock block, MachineKind kind) {
        super(block, new Item.Properties());
        this.kind = kind;
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        var config = GeneratorConfig.INSTANCE;
        switch (kind) {
            case TURBINE_TIER1, TURBINE_TIER2, TURBINE_TIER3, TURBINE_TIER4, TURBINE_TIER5 -> {
                int tier = kind.tier - 1;
                tooltip.add(Component.translatable("advgenerators.tooltip.turbine.produce", Component.literal(energyRate(config.turbineOutput[tier])).withStyle(ChatFormatting.YELLOW)));
                tooltip.add(Component.translatable("advgenerators.tooltip.turbine.inertia", Component.literal(percent(config.turbineInertia[tier])).withStyle(ChatFormatting.YELLOW)));
            }
            case FUEL_TANK -> tooltip.add(Component.translatable("advgenerators.tooltip.capacity", number(config.fuelTankCapacity) + " mB"));
            case POWER_CAPACITOR_TIER1, POWER_CAPACITOR_TIER2, POWER_CAPACITOR_TIER3 ->
                    tooltip.add(Component.translatable("advgenerators.tooltip.capacity", Component.literal(number(config.capacitorCapacity[kind.tier - 1]) + " FE").withStyle(ChatFormatting.YELLOW)));
            case EFFICIENCY_UPGRADE_TIER1 -> tooltip.add(Component.translatable("advgenerators.tooltip.efficiency", Component.literal(percent(config.fuelEfficiency[1])).withStyle(ChatFormatting.YELLOW)));
            case EFFICIENCY_UPGRADE_TIER2 -> {
                tooltip.add(Component.translatable("advgenerators.tooltip.efficiency", Component.literal(percent(config.fuelEfficiency[2])).withStyle(ChatFormatting.YELLOW)));
                tooltip.add(Component.translatable("advgenerators.tooltip.efficiency.req"));
            }
            case FE_OUTPUT -> tooltip.add(Component.translatable("advgenerators.tooltip.fe_output"));
            case MJ_OUTPUT -> tooltip.add(Component.translatable("advgenerators.tooltip.mj_output"));
            case EU_OUTPUT_LV, EU_OUTPUT_MV, EU_OUTPUT_HV, EU_OUTPUT_EV, EU_OUTPUT_IV ->
                    tooltip.add(Component.translatable("advgenerators.tooltip.eu", Component.literal(Integer.toString(kind.tier)).withStyle(ChatFormatting.YELLOW), Component.literal(number(new long[]{32, 128, 512, 2048, 8192}[kind.tier - 1])).withStyle(ChatFormatting.YELLOW)));
            default -> { }
        }
        appendMultiblockTooltip(tooltip);
        super.appendHoverText(stack, context, tooltip, flag);
    }

    /** BdLib ControllerBlockItem/ModuleBlockItem compatibility section. */
    private void appendMultiblockTooltip(List<Component> tooltip) {
        var config = GeneratorConfig.INSTANCE;
        if (kind.controller()) {
            tooltip.add(Component.translatable("bdlib.multiblock.tip.controller"));
            for (var entry : config.moduleLimits.getOrDefault(kind.id(), java.util.Map.of()).entrySet()) {
                int max = entry.getValue();
                if (max <= 0) continue;
                var moduleName = Component.translatable("advgenerators.module." + moduleName(entry.getKey()) + ".name").withStyle(ChatFormatting.YELLOW);
                int required = requiredModules(kind).getOrDefault(entry.getKey(), 0);
                if (required > 0) tooltip.add(Component.translatable("bdlib.multiblock.tip.module.range", moduleName,
                    Component.literal(Integer.toString(required)).withStyle(ChatFormatting.YELLOW),
                    Component.literal(Integer.toString(max)).withStyle(ChatFormatting.YELLOW)));
                else tooltip.add(Component.translatable("bdlib.multiblock.tip.module.max", moduleName,
                    Component.literal(Integer.toString(max)).withStyle(ChatFormatting.YELLOW)));
            }
        } else {
            tooltip.add(Component.translatable("bdlib.multiblock.tip.module"));
            for (var controller : MachineKind.values()) {
                if (!controller.controller()) continue;
                int max = config.limit(controller, kind.category);
                if (max <= 0) continue;
                var machineName = Component.translatable("block.advgenerators." + controller.id()).withStyle(ChatFormatting.YELLOW);
                int required = requiredModules(controller).getOrDefault(kind.category, 0);
                if (required > 0) tooltip.add(Component.translatable("bdlib.multiblock.tip.module.range", machineName,
                    Component.literal(Integer.toString(required)).withStyle(ChatFormatting.YELLOW),
                    Component.literal(Integer.toString(max)).withStyle(ChatFormatting.YELLOW)));
                else tooltip.add(Component.translatable("bdlib.multiblock.tip.module.max", machineName,
                    Component.literal(Integer.toString(max)).withStyle(ChatFormatting.YELLOW)));
            }
        }
    }

    private static java.util.Map<String,Integer> requiredModules(MachineKind controller) {
        return switch (controller) {
            case FUEL_TURBINE_CONTROLLER, STEAM_TURBINE_CONTROLLER -> java.util.Map.of("turbine",1);
            case SYNGAS_CONTROLLER -> java.util.Map.of("mixingChamber",1);
            case EXCHANGER_CONTROLLER -> java.util.Map.of("heatExchanger",1);
            default -> java.util.Map.of();
        };
    }

    private static String moduleName(String category) {
        if (category.equals("fluidOutput")) return "FluidOutputSelect";
        if (category.equals("gasInput")) return "FluidInput";
        return Character.toUpperCase(category.charAt(0)) + category.substring(1);
    }

    private static String energyRate(double value) { return number((long) value) + " FE/t"; }
    private static String percent(double value) { return String.format(Locale.ROOT, "%.0f%%", value * 100); }
    private static String number(long value) { return NumberFormat.getIntegerInstance(Locale.ROOT).format(value); }
}
