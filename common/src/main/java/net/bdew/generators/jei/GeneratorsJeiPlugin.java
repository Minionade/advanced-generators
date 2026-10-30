package net.bdew.generators.jei;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.registration.IRecipeCategoryRegistration;
import mezz.jei.api.registration.IRecipeCatalystRegistration;
import mezz.jei.api.registration.IRecipeRegistration;
import net.bdew.generators.Generators;
import net.bdew.generators.ModContent;
import net.bdew.generators.config.GeneratorConfig;
import net.bdew.generators.machine.MachineKind;
import net.bdew.generators.recipe.MachineRecipe;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.server.packs.resources.Resource;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@JeiPlugin
public final class GeneratorsJeiPlugin implements IModPlugin {
    private static final GeneratorRecipeCategory FUEL = new GeneratorRecipeCategory("turbine_fuels", "advgenerators.recipe.turbine_fuels", MachineKind.FUEL_TURBINE_CONTROLLER);
    private static final GeneratorRecipeCategory STEAM = new GeneratorRecipeCategory("turbine_steam", "advgenerators.recipe.turbine_steam", MachineKind.STEAM_TURBINE_CONTROLLER);
    private static final GeneratorRecipeCategory SYNGAS = new GeneratorRecipeCategory("syngas_conversion", "advgenerators.recipe.syngas_conversion", MachineKind.SYNGAS_CONTROLLER);
    private static final GeneratorRecipeCategory UPGRADE = new GeneratorRecipeCategory("upgrade", "advgenerators.recipe.upgrade", MachineKind.EXCHANGER_CONTROLLER);
    private static final GeneratorRecipeCategory HEATING = new GeneratorRecipeCategory("exchanger_heating", "advgenerators.recipe.exchanger.heating", MachineKind.EXCHANGER_CONTROLLER);
    private static final GeneratorRecipeCategory COOLING = new GeneratorRecipeCategory("exchanger_cooling", "advgenerators.recipe.exchanger.cooling", MachineKind.EXCHANGER_CONTROLLER);
    private static final List<GeneratorRecipeCategory> CATEGORIES = List.of(FUEL, STEAM, SYNGAS, UPGRADE, HEATING, COOLING);

    @Override public ResourceLocation getPluginUid() { return Generators.id("jei"); }
    @Override public void registerCategories(IRecipeCategoryRegistration registration) {
        GeneratorRecipeCategory.setHelpers(registration.getJeiHelpers());
        registration.addRecipeCategories(CATEGORIES.toArray(GeneratorRecipeCategory[]::new));
    }
    @Override public void registerRecipes(IRecipeRegistration registration) {
        Map<String, List<MachineRecipe>> recipes = loadRecipes();
        List<MachineRecipe> fuels = recipes.get("liquid_fuel");
        fuels.addAll(externalFuels(fuels));
        registration.addRecipes(FUEL.getRecipeType(), fuels);
        registration.addRecipes(SYNGAS.getRecipeType(), recipes.get("carbon_source"));
        registration.addRecipes(UPGRADE.getRecipeType(), recipes.get("upgrade_kit"));
        registration.addRecipes(HEATING.getRecipeType(), recipes.get("exchanger_heating"));
        registration.addRecipes(COOLING.getRecipeType(), recipes.get("exchanger_cooling"));
        JsonObject steamRecipe = new JsonObject();
        JsonObject steam = new JsonObject();
        steam.addProperty("fluid", Generators.id("steam").toString());
        steamRecipe.add("fuel", steam);
        steamRecipe.addProperty("fePerMb", GeneratorConfig.INSTANCE.steamEnergyDensity);
        registration.addRecipes(STEAM.getRecipeType(), List.of(new MachineRecipe("liquid_fuel", steamRecipe)));
    }
    @Override public void registerRecipeCatalysts(IRecipeCatalystRegistration registration) {
        registration.addRecipeCatalyst(item(MachineKind.FUEL_TURBINE_CONTROLLER), FUEL.getRecipeType());
        registration.addRecipeCatalyst(item(MachineKind.STEAM_TURBINE_CONTROLLER), STEAM.getRecipeType());
        registration.addRecipeCatalyst(item(MachineKind.SYNGAS_CONTROLLER), SYNGAS.getRecipeType());
        registration.addRecipeCatalyst(new ItemStack(ModContent.ALL_ITEMS.get("upgrade_kit").get()), UPGRADE.getRecipeType());
        registration.addRecipeCatalyst(item(MachineKind.EXCHANGER_CONTROLLER), HEATING.getRecipeType());
        registration.addRecipeCatalyst(item(MachineKind.EXCHANGER_CONTROLLER), COOLING.getRecipeType());
    }

    /** Fuels other mods define (BuildCraft, Modern Industrialization) have no recipe file; list them as well. */
    private static List<MachineRecipe> externalFuels(List<MachineRecipe> known) {
        var result = new ArrayList<MachineRecipe>();
        for (var fluid : net.minecraft.core.registries.BuiltInRegistries.FLUID) {
            if (fluid == net.minecraft.world.level.material.Fluids.EMPTY || !fluid.isSource(fluid.defaultFluidState())) continue;
            if (known.stream().anyMatch(r -> r.matchesFluid(fluid))) continue;
            double fePerMb = net.bdew.generators.platform.TransferPlatform.externalFuelFePerMb(fluid);
            if (fePerMb <= 0) continue;
            JsonObject json = new JsonObject();
            JsonObject fuel = new JsonObject();
            fuel.addProperty("fluid", net.minecraft.core.registries.BuiltInRegistries.FLUID.getKey(fluid).toString());
            json.add("fuel", fuel);
            json.addProperty("fePerMb", fePerMb);
            result.add(new MachineRecipe("liquid_fuel", json));
        }
        return result;
    }
    private static ItemStack item(MachineKind kind) { return new ItemStack(ModContent.MACHINES.get(kind).get()); }
    private static Map<String, List<MachineRecipe>> loadRecipes() {
        var result = new LinkedHashMap<String, List<MachineRecipe>>();
        for (String kind : List.of("liquid_fuel", "carbon_source", "upgrade_kit", "exchanger_heating", "exchanger_cooling")) result.put(kind, new ArrayList<>());
        Level level = Minecraft.getInstance().level;
        if (level != null) {
            for (String kind : result.keySet()) {
                var type = ModContent.RECIPE_TYPES.get(kind);
                if (type != null) level.getRecipeManager().getAllRecipesFor(type.get()).stream().map(holder -> holder.value()).forEach(result.get(kind)::add);
            }
            return result;
        }
        Minecraft.getInstance().getResourceManager().listResources("recipe", id -> id.getNamespace().equals(Generators.MOD_ID) && id.getPath().endsWith(".json"))
            .forEach((id, resource) -> readRecipe(id, resource, result));
        return result;
    }
    private static void readRecipe(ResourceLocation id, Resource resource, Map<String, List<MachineRecipe>> recipes) {
        try (var reader = resource.openAsReader()) {
            JsonObject json = JsonParser.parseReader(reader).getAsJsonObject();
            if (!json.has("type")) return;
            String kind = ResourceLocation.parse(json.get("type").getAsString()).getPath();
            List<MachineRecipe> target = recipes.get(kind);
            if (target != null) target.add(new MachineRecipe(kind, json));
        } catch (RuntimeException | IOException ignored) {
            // Invalid datapack recipes are already reported by Minecraft's recipe manager.
        }
    }
}
