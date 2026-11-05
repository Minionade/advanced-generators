package net.bdew.generators.config;

import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;
import java.util.OptionalDouble;
import java.util.function.Predicate;

/**
 * The {@code fuelValues} setting: {@code "fluid_id=fe_per_mb"} or {@code "#fluid_tag=fe_per_mb"}. The first entry that
 * matches a fluid wins, and entries take precedence over fuel recipes.
 */
public final class FuelOverrides {
    private FuelOverrides() {}

    public record Entry(ResourceLocation id, boolean tag, double fePerMb) {}

    /** Entries that cannot be read are skipped; {@code errors} receives a message for each. */
    public static List<Entry> parse(List<String> raw, List<String> errors) {
        var result = new ArrayList<Entry>();
        if (raw == null) return result;
        for (String line : raw) {
            try {
                int split = line.lastIndexOf('=');
                if (split < 0) throw new IllegalArgumentException("expected fluid_id=fe_per_mb");
                String key = line.substring(0, split).trim();
                boolean tag = key.startsWith("#");
                ResourceLocation id = ResourceLocation.parse(tag ? key.substring(1) : key);
                double value = Double.parseDouble(line.substring(split + 1).trim());
                if (!Double.isFinite(value) || value <= 0) throw new IllegalArgumentException("value must be positive");
                result.add(new Entry(id, tag, value));
            } catch (RuntimeException e) {
                errors.add("\"" + line + "\": " + e.getMessage());
            }
        }
        return result;
    }

    public static OptionalDouble find(List<Entry> entries, ResourceLocation fluid, Predicate<ResourceLocation> inTag) {
        for (Entry entry : entries)
            if (entry.tag() ? inTag.test(entry.id()) : entry.id().equals(fluid)) return OptionalDouble.of(entry.fePerMb());
        return OptionalDouble.empty();
    }
}
