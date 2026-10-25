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
                tooltip.add(Component.translatable("advgenerators.tooltip.turbine.produce", energyRate(config.turbineOutput[tier])).withStyle(ChatFormatting.GRAY));
                tooltip.add(Component.translatable("advgenerators.tooltip.turbine.inertia", percent(config.turbineInertia[tier])).withStyle(ChatFormatting.GRAY));
            }
            case FUEL_TANK -> tooltip.add(Component.translatable("advgenerators.tooltip.capacity", number(config.fuelTankCapacity) + " mB").withStyle(ChatFormatting.GRAY));
            case POWER_CAPACITOR_TIER1, POWER_CAPACITOR_TIER2, POWER_CAPACITOR_TIER3 ->
                    tooltip.add(Component.translatable("advgenerators.tooltip.capacity", number(config.capacitorCapacity[kind.tier - 1]) + " FE").withStyle(ChatFormatting.GRAY));
            case EFFICIENCY_UPGRADE_TIER1 -> tooltip.add(Component.translatable("advgenerators.tooltip.efficiency", percent(config.fuelEfficiency[1])).withStyle(ChatFormatting.GRAY));
            case EFFICIENCY_UPGRADE_TIER2 -> {
                tooltip.add(Component.translatable("advgenerators.tooltip.efficiency", percent(config.fuelEfficiency[2])).withStyle(ChatFormatting.GRAY));
                tooltip.add(Component.translatable("advgenerators.tooltip.efficiency.req").withStyle(ChatFormatting.GRAY));
            }
            case FE_OUTPUT -> tooltip.add(Component.translatable("advgenerators.tooltip.fe_output").withStyle(ChatFormatting.GRAY));
            case EU_OUTPUT_LV, EU_OUTPUT_MV, EU_OUTPUT_HV, EU_OUTPUT_EV, EU_OUTPUT_IV ->
                    tooltip.add(Component.translatable("advgenerators.tooltip.eu", kind.tier, number(new long[]{32, 128, 512, 2048, 8192}[kind.tier - 1])).withStyle(ChatFormatting.GRAY));
            default -> { }
        }
        if (kind.controller()) {
            tooltip.add(Component.translatable("advgenerators.tooltip.controller").withStyle(ChatFormatting.DARK_GRAY));
        } else {
            tooltip.add(Component.translatable("advgenerators.tooltip.module").withStyle(ChatFormatting.DARK_GRAY));
        }
        super.appendHoverText(stack, context, tooltip, flag);
    }

    private static String energyRate(double value) { return number((long) value) + " FE/t"; }
    private static String percent(double value) { return String.format(Locale.ROOT, "%.0f%%", value * 100); }
    private static String number(long value) { return NumberFormat.getIntegerInstance(Locale.ROOT).format(value); }
}
