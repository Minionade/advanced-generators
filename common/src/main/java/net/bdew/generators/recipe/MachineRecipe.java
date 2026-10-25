package net.bdew.generators.recipe;

import com.google.gson.*;
import com.mojang.serialization.*;
import net.bdew.generators.ModContent;
import net.bdew.generators.machine.ResourceBuffer;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.*;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import java.util.stream.Stream;

/** Data-pack recipes, retaining the original IDs and JSON fields. */
public final class MachineRecipe implements Recipe<RecipeInput> {
    public final String kind;
    public final JsonObject json;
    public final Ingredient ingredient;
    public MachineRecipe(String kind, JsonObject json) {
        this.kind = kind; this.json = json;
        ingredient = kind.equals("carbon_source") ? parseIngredient(json.get("items")) : Ingredient.EMPTY;
        switch (kind) {
            case "carbon_source" -> positive("carbonValue");
            case "liquid_fuel" -> { positive("fePerMb"); validateFluid(json.get("fuel")); }
            case "exchanger_heating", "exchanger_cooling" -> {
                positive("heat");
                var input = json.getAsJsonObject("input"); var output = json.getAsJsonObject("output");
                positive(input, "amount"); positive(output, "amount");
                if (input.has("fluids")) validateFluid(input.get("fluids"));
                else parseIngredient(input.has("items") ? input.get("items") : input.get("item"));
                ResourceLocation.parse(output.get(output.has("item") ? "item" : "fluid").getAsString());
            }
            case "upgrade_kit" -> { for (var key : new String[]{"from", "to", "item"}) ResourceLocation.parse(json.get(key).getAsString()); }
            default -> throw new IllegalArgumentException("Unknown recipe type " + kind);
        }
    }
    private void positive(String key) { positive(json, key); }
    private static Ingredient parseIngredient(JsonElement element) {
        return Ingredient.CODEC_NONEMPTY.parse(JsonOps.INSTANCE, normalizeIngredient(element)).getOrThrow();
    }
    private static JsonElement normalizeIngredient(JsonElement element) {
        if (element == null || !element.isJsonObject()) {
            if (element != null && element.isJsonArray()) {
                var result = new JsonArray();
                for (var child : element.getAsJsonArray()) result.add(normalizeIngredient(child));
                return result;
            }
            return element;
        }
        var object = element.getAsJsonObject();
        if (object.has("items") && object.get("items").isJsonPrimitive() && object.get("items").getAsJsonPrimitive().isString()) {
            var normalized = object.deepCopy();
            normalized.add("item", normalized.remove("items"));
            return normalized;
        }
        return object;
    }
    private static void positive(JsonObject obj, String key) {
        double value = obj.get(key).getAsDouble();
        if (!Double.isFinite(value) || value <= 0) throw new IllegalArgumentException(key + " must be positive and finite");
    }
    private static void validateFluid(JsonElement element) {
        if (element.isJsonArray()) { for (var x : element.getAsJsonArray()) validateFluid(x); return; }
        var o = element.getAsJsonObject(); ResourceLocation.parse(o.get(o.has("tag") ? "tag" : "fluid").getAsString());
    }
    public double value(String name) { return json.get(name).getAsDouble(); }
    public ResourceLocation id(String name) { return ResourceLocation.parse(json.get(name).getAsString()); }
    public boolean matchesFluid(Fluid fluid) { return matchesFluid(json.get("fuel"), fluid); }
    public static boolean matchesFluid(JsonElement spec, Fluid fluid) {
        if (spec == null) return false;
        if (spec.isJsonArray()) { for (var part : spec.getAsJsonArray()) if (matchesFluid(part, fluid)) return true; return false; }
        var obj = spec.getAsJsonObject();
        if (obj.has("tag")) return fluid.is(TagKey.create(Registries.FLUID, ResourceLocation.parse(obj.get("tag").getAsString())));
        return BuiltInRegistries.FLUID.getKey(fluid).equals(ResourceLocation.parse(obj.get("fluid").getAsString()));
    }
    public boolean matchesInput(boolean item, ResourceLocation id, DataComponentPatch components) {
        var input = json.getAsJsonObject("input");
        if (item && (input.has("items") || input.has("item"))) {
            var stack = new ItemStack(BuiltInRegistries.ITEM.get(id)); stack.applyComponents(components);
            return parseIngredient(input.has("items") ? input.get("items") : input.get("item")).test(stack);
        }
        return !item && input.has("fluids") && matchesFluid(input.get("fluids"), BuiltInRegistries.FLUID.get(id));
    }
    public double inPerHeat() { return json.getAsJsonObject("input").get("amount").getAsDouble() / value("heat"); }
    public double outPerHeat() { return json.getAsJsonObject("output").get("amount").getAsDouble() / value("heat"); }
    public boolean outputItem() { return json.getAsJsonObject("output").has("item"); }
    public ResourceLocation outputId() { return ResourceLocation.parse(json.getAsJsonObject("output").get(outputItem() ? "item" : "fluid").getAsString()); }
    @Override public boolean matches(RecipeInput input, Level level) { return false; }
    @Override public ItemStack assemble(RecipeInput input, HolderLookup.Provider registries) { return ItemStack.EMPTY; }
    @Override public boolean canCraftInDimensions(int width, int height) { return false; }
    @Override public ItemStack getResultItem(HolderLookup.Provider registries) { return ItemStack.EMPTY; }
    @Override public boolean isSpecial() { return true; }
    @Override public RecipeSerializer<?> getSerializer() { return ModContent.RECIPE_SERIALIZERS.get(kind).get(); }
    @Override public RecipeType<?> getType() { return ModContent.RECIPE_TYPES.get(kind).get(); }

    public static final class Serializer implements RecipeSerializer<MachineRecipe> {
        private final MapCodec<MachineRecipe> codec;
        private final StreamCodec<RegistryFriendlyByteBuf, MachineRecipe> stream;
        public Serializer(String kind) {
            codec = new MapCodec<>() {
                @Override public <T> DataResult<MachineRecipe> decode(DynamicOps<T> ops, MapLike<T> input) {
                    try {
                        var json = new JsonObject();
                        input.entries().forEach(e -> json.add(ops.getStringValue(e.getFirst()).getOrThrow(), ops.convertTo(JsonOps.INSTANCE, e.getSecond())));
                        return DataResult.success(new MachineRecipe(kind, json));
                    } catch (RuntimeException e) { return DataResult.error(() -> "Invalid " + kind + " recipe: " + e.getMessage()); }
                }
                @Override public <T> RecordBuilder<T> encode(MachineRecipe value, DynamicOps<T> ops, RecordBuilder<T> prefix) {
                    value.json.entrySet().forEach(e -> prefix.add(e.getKey(), JsonOps.INSTANCE.convertTo(ops, e.getValue()))); return prefix;
                }
                @Override public <T> Stream<T> keys(DynamicOps<T> ops) {
                    return Stream.of("items", "carbonValue", "fuel", "fePerMb", "input", "output", "heat", "from", "to", "item").map(ops::createString);
                }
            };
            stream = new StreamCodec<>() {
                public MachineRecipe decode(RegistryFriendlyByteBuf buf) { return new MachineRecipe(kind, JsonParser.parseString(buf.readUtf(262144)).getAsJsonObject()); }
                public void encode(RegistryFriendlyByteBuf buf, MachineRecipe recipe) { buf.writeUtf(recipe.json.toString(), 262144); }
            };
        }
        @Override public MapCodec<MachineRecipe> codec() { return codec; }
        @Override public StreamCodec<RegistryFriendlyByteBuf, MachineRecipe> streamCodec() { return stream; }
    }
}
