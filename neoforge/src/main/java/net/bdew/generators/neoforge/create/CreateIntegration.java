package net.bdew.generators.neoforge.create;

import dev.architectury.registry.registries.RegistrySupplier;
import net.bdew.generators.ModContent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;

import java.util.List;

/** Create support: the Kinetic Converter. Only loaded when Create is installed. */
public final class CreateIntegration {
    public static RegistrySupplier<KineticConverterBlock> BLOCK;
    public static RegistrySupplier<BlockEntityType<KineticConverterBlockEntity>> BLOCK_ENTITY;

    private CreateIntegration() {}

    /** Call before the mod's registries are finalised. */
    public static void registerContent() {
        BLOCK = ModContent.addBlock("kinetic_converter", KineticConverterBlock::new);
        ModContent.addItem("kinetic_converter", () -> new BlockItem(BLOCK.get(), new Item.Properties()) {
            @Override public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, net.minecraft.world.item.TooltipFlag flag) {
                tooltip.add(Component.translatable("advgenerators.tooltip.kinetic_converter"));
                super.appendHoverText(stack, context, tooltip, flag);
            }
        });
        BLOCK_ENTITY = ModContent.addBlockEntityType("kinetic_converter", () ->
            BlockEntityType.Builder.<KineticConverterBlockEntity>of((pos, state) -> new KineticConverterBlockEntity(BLOCK_ENTITY.get(), pos, state), BLOCK.get()).build(null));
    }

    public static void registerCapabilities(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(Capabilities.EnergyStorage.BLOCK, BLOCK_ENTITY.get(), (entity, side) -> entity.energyHandler());
    }
}
