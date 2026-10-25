package net.bdew.generators;

import dev.architectury.core.fluid.ArchitecturyFlowingFluid;
import dev.architectury.core.fluid.SimpleArchitecturyFluidAttributes;
import dev.architectury.registry.CreativeTabRegistry;
import dev.architectury.registry.menu.MenuRegistry;
import dev.architectury.registry.registries.DeferredRegister;
import dev.architectury.registry.registries.RegistrySupplier;
import net.bdew.generators.machine.*;
import net.bdew.generators.menu.MachineMenu;
import net.bdew.generators.recipe.MachineRecipe;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.*;
import net.minecraft.world.item.crafting.*;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.*;
import java.util.*;

public final class ModContent {
    private static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(Generators.MOD_ID, Registries.BLOCK);
    private static final DeferredRegister<Item> ITEMS = DeferredRegister.create(Generators.MOD_ID, Registries.ITEM);
    private static final DeferredRegister<Fluid> FLUIDS = DeferredRegister.create(Generators.MOD_ID, Registries.FLUID);
    private static final DeferredRegister<BlockEntityType<?>> ENTITIES = DeferredRegister.create(Generators.MOD_ID, Registries.BLOCK_ENTITY_TYPE);
    private static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister.create(Generators.MOD_ID, Registries.MENU);
    private static final DeferredRegister<RecipeType<?>> TYPES = DeferredRegister.create(Generators.MOD_ID, Registries.RECIPE_TYPE);
    private static final DeferredRegister<RecipeSerializer<?>> SERIALIZERS = DeferredRegister.create(Generators.MOD_ID, Registries.RECIPE_SERIALIZER);
    private static final DeferredRegister<CreativeModeTab> TABS = DeferredRegister.create(Generators.MOD_ID, Registries.CREATIVE_MODE_TAB);
    public static final Map<MachineKind, RegistrySupplier<MachineBlock>> MACHINES = new EnumMap<>(MachineKind.class);
    public static final Map<String, RegistrySupplier<Item>> ALL_ITEMS = new LinkedHashMap<>();
    public static final Map<String, RegistrySupplier<RecipeType<MachineRecipe>>> RECIPE_TYPES = new LinkedHashMap<>();
    public static final Map<String, RegistrySupplier<RecipeSerializer<MachineRecipe>>> RECIPE_SERIALIZERS = new LinkedHashMap<>();
    public static final Map<String, FluidDef> GASES = new LinkedHashMap<>();
    static {
        for (var kind : MachineKind.values()) {
            var block = BLOCKS.register(kind.id(), () -> new MachineBlock(kind)); MACHINES.put(kind, block);
            ALL_ITEMS.put(kind.id(), ITEMS.register(kind.id(), () -> new BlockItem(block.get(), new Item.Properties())));
        }
        for (var name : List.of("iron_frame", "power_io", "iron_tubing", "iron_wiring", "controller", "pressure_valve", "advanced_pressure_valve", "turbine_blade", "turbine_rotor_tier1", "turbine_rotor_tier2", "turbine_rotor_tier3", "turbine_rotor_tier4", "turbine_rotor_tier5"))
            ALL_ITEMS.put(name, ITEMS.register(name, () -> new Item(new Item.Properties())));
        for (var name : List.of("upgrade_kit", "turbine_kit_tier2", "turbine_kit_tier3", "turbine_kit_tier4", "turbine_kit_tier5", "capacitor_kit_tier2", "capacitor_kit_tier3"))
            ALL_ITEMS.put(name, ITEMS.register(name, UpgradeKitItem::new));
        for (var name : List.of("liquid_fuel", "carbon_source", "upgrade_kit", "exchanger_heating", "exchanger_cooling")) {
            RECIPE_TYPES.put(name, TYPES.register(name, () -> new RecipeType<MachineRecipe>() { @Override public String toString() { return Generators.id(name).toString(); } }));
            RECIPE_SERIALIZERS.put(name, SERIALIZERS.register(name, () -> new MachineRecipe.Serializer(name)));
        }
        GASES.put("steam", new FluidDef("steam")); GASES.put("syngas", new FluidDef("syngas"));
    }
    public static final RegistrySupplier<BlockEntityType<MachineBlockEntity>> MACHINE_ENTITY = ENTITIES.register("machine", () ->
        BlockEntityType.Builder.of(MachineBlockEntity::new, MACHINES.values().stream().map(RegistrySupplier::get).toArray(Block[]::new)).build(null));
    public static final RegistrySupplier<MenuType<MachineMenu>> MACHINE_MENU = MENUS.register("machine", () -> MenuRegistry.ofExtended(MachineMenu::client));
    public static final RegistrySupplier<CreativeModeTab> TAB = TABS.register("main", () -> CreativeTabRegistry.create(builder -> builder
        .title(Component.translatable("itemGroup.bdew.generators"))
        .icon(() -> new ItemStack(MACHINES.get(MachineKind.FUEL_TURBINE_CONTROLLER).get()))
        .displayItems((parameters, output) -> ALL_ITEMS.values().forEach(item -> output.accept(item.get())))));

    public static void register() {
        FLUIDS.register(); BLOCKS.register(); ITEMS.register(); ENTITIES.register(); MENUS.register(); TYPES.register(); SERIALIZERS.register(); TABS.register();
    }
    public static final class FluidDef {
        public final RegistrySupplier<Fluid> source, flowing;
        public final RegistrySupplier<LiquidBlock> block;
        public final RegistrySupplier<Item> bucket;
        public FluidDef(String name) {
            var attrs = SimpleArchitecturyFluidAttributes.of(() -> GASES.get(name).flowing.get(), () -> GASES.get(name).source.get())
                .sourceTexture(Generators.id("block/" + name + "/still")).flowingTexture(Generators.id("block/" + name + "/flowing"))
                .density(-10).lighterThanAir(true).color(0xffffffff);
            source = FLUIDS.register(name, () -> new ArchitecturyFlowingFluid.Source(attrs));
            flowing = FLUIDS.register("flowing_" + name, () -> new ArchitecturyFlowingFluid.Flowing(attrs));
            block = BLOCKS.register(name, () -> new LiquidBlock((FlowingFluid) source.get(), BlockBehaviour.Properties.ofFullCopy(Blocks.WATER)));
            bucket = ITEMS.register(name + "_bucket", () -> new BucketItem(source.get(), new Item.Properties().craftRemainder(Items.BUCKET).stacksTo(1)));
            attrs.block(block).bucketItem(bucket);
            ALL_ITEMS.put(name + "_bucket", bucket);
        }
    }
}
