package net.bdew.generators.jei;

import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.helpers.IJeiHelpers;
import mezz.jei.api.helpers.IPlatformFluidHelper;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.category.IRecipeCategory;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.ingredients.IIngredientType;
import net.bdew.generators.Generators;
import net.bdew.generators.ModContent;
import net.bdew.generators.machine.MachineKind;
import net.bdew.generators.config.GeneratorConfig;
import net.bdew.generators.recipe.MachineRecipe;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.material.Fluid;
import com.google.gson.JsonElement;
import com.mojang.serialization.JsonOps;
import java.util.ArrayList;
import java.util.List;

/** Shared JEI renderer for the six Advanced Generators recipe displays. */
public final class GeneratorRecipeCategory implements IRecipeCategory<MachineRecipe> {
    private static IJeiHelpers helpers;
    private final String id;
    private final String title;
    private final MachineKind icon;
    private final RecipeType<MachineRecipe> type;

    public static void setHelpers(IJeiHelpers value) { helpers = value; }
    public GeneratorRecipeCategory(String id, String title, MachineKind icon) {
        this.id = id;
        this.title = title;
        this.icon = icon;
        this.type = RecipeType.create(Generators.MOD_ID, id, MachineRecipe.class);
    }
    @Override public RecipeType<MachineRecipe> getRecipeType() { return type; }
    @Override public Component getTitle() { return Component.translatable(title); }
    @Override public IDrawable getBackground() { return helpers.getGuiHelper().createBlankDrawable(144, 54); }
    @Override public IDrawable getIcon() {
        return helpers.getGuiHelper().createDrawableItemStack(new ItemStack(ModContent.MACHINES.get(icon).get()));
    }

    @Override public void setRecipe(IRecipeLayoutBuilder builder, MachineRecipe recipe, IFocusGroup focuses) {
        switch (recipe.kind) {
            case "carbon_source" -> {
                builder.addSlot(RecipeIngredientRole.INPUT, 12, 18).addIngredients(recipe.ingredient);
                fluidSlot(builder, RecipeIngredientRole.OUTPUT, 110, 18,
                    BuiltInRegistries.FLUID.get(Generators.id("syngas")),
                    Math.max(1, (long)(recipe.value("carbonValue") / GeneratorConfig.INSTANCE.carbonPerSyngas)));
            }
            case "liquid_fuel" -> {
                fluidSlots(builder, RecipeIngredientRole.INPUT, 12, 18, recipe.json.get("fuel"), 1000);
            }
            case "upgrade_kit" -> {
                itemSlot(builder, RecipeIngredientRole.INPUT, 12, 18, recipe.id("from"));
                itemSlot(builder, RecipeIngredientRole.INPUT, 58, 18, recipe.id("item"));
                itemSlot(builder, RecipeIngredientRole.OUTPUT, 110, 18, recipe.id("to"));
            }
            case "exchanger_heating", "exchanger_cooling" -> {
                var input = recipe.json.getAsJsonObject("input");
                var output = recipe.json.getAsJsonObject("output");
                if (input.has("items")) {
                    Ingredient ingredient = Ingredient.CODEC_NONEMPTY.parse(JsonOps.INSTANCE, input.get("items")).getOrThrow();
                    builder.addSlot(RecipeIngredientRole.INPUT, 12, 18).addIngredients(ingredient);
                } else fluidSlots(builder, RecipeIngredientRole.INPUT, 12, 18, input.get("fluids"), input.get("amount").getAsLong());
                long outAmount = Math.max(1, Math.round(input.get("amount").getAsDouble() / recipe.value("heat") * output.get("amount").getAsDouble()));
                if (output.has("item")) itemSlot(builder, RecipeIngredientRole.OUTPUT, 110, 18, ResourceLocation.parse(output.get("item").getAsString()));
                else fluidSlot(builder, RecipeIngredientRole.OUTPUT, 110, 18, BuiltInRegistries.FLUID.get(ResourceLocation.parse(output.get("fluid").getAsString())), outAmount);
            }
            default -> { }
        }
    }

    @Override public void draw(MachineRecipe recipe, IRecipeSlotsView slots, GuiGraphics graphics, double mouseX, double mouseY) {
        String details = switch (recipe.kind) {
            case "liquid_fuel" -> String.format(java.util.Locale.ROOT, "%.2f FE/mB", recipe.value("fePerMb"));
            case "exchanger_heating", "exchanger_cooling" -> String.format(java.util.Locale.ROOT, "%.2f HU", recipe.value("heat"));
            case "carbon_source" -> String.format(java.util.Locale.ROOT, "%.2f carbon", recipe.value("carbonValue"));
            default -> "";
        };
        if (!details.isEmpty()) graphics.drawString(Minecraft.getInstance().font, details, 54, 40, 0x404040, false);
    }

    private static void itemSlot(IRecipeLayoutBuilder builder, RecipeIngredientRole role, int x, int y, ResourceLocation id) {
        builder.addSlot(role, x, y).addItemStack(new ItemStack(BuiltInRegistries.ITEM.get(id)));
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static void fluidSlot(IRecipeLayoutBuilder builder, RecipeIngredientRole role, int x, int y, Fluid fluid, long amount) {
        if (fluid == null || fluid == net.minecraft.world.level.material.Fluids.EMPTY) return;
        IPlatformFluidHelper platform = helpers.getPlatformFluidHelper();
        IIngredientType fluidType = platform.getFluidIngredientType();
        Object ingredient = platform.create(Holder.direct(fluid), Math.max(1, amount), DataComponentPatch.EMPTY);
        builder.addSlot(role, x, y).addIngredient(fluidType, ingredient);
    }

    private static void fluidSlots(IRecipeLayoutBuilder builder, RecipeIngredientRole role, int x, int y, JsonElement spec, long amount) {
        List<Fluid> matching = new ArrayList<>();
        for (Fluid fluid : BuiltInRegistries.FLUID) if (MachineRecipe.matchesFluid(spec, fluid)) matching.add(fluid);
        if (matching.isEmpty()) return;
        for (Fluid fluid : matching) fluidSlot(builder, role, x, y, fluid, amount);
    }
}
