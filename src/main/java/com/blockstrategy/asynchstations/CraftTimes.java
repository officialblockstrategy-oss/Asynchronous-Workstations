package com.blockstrategy.asynchstations;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener;
import net.minecraft.item.ArmorItem;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.SwordItem;
import net.minecraft.item.ToolItem;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.resource.ResourceManager;
import net.minecraft.resource.ResourceType;
import net.minecraft.util.Identifier;

import java.io.BufferedReader;
import java.io.IOException;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class CraftTimes {
    private static final Identifier FILE = AsynchronousWorkstations.id("craft_times.json");

    // Only used when craft_times.json is missing or broken, or lacks a key. Edit the JSON file instead.
    private static final Map<String, Double> BUILT_IN_ITEM_CLASS_SECONDS = Map.of(
            "ArmorItem", 30.0, "SwordItem", 20.0, "ToolItem", 15.0, "BlockItem", 2.0, "Item", 3.0);

    // Each map is replaced as a whole on reload, which doesn't happen on the server thread.
    private static volatile Map<Identifier, Double> ingredientSeconds = Map.of();
    private static volatile Map<TagKey<Item>, Double> ingredientTagSeconds = Map.of();
    private static volatile Map<Identifier, Double> outputSeconds = Map.of();
    private static volatile Map<String, Double> fallbackItemClassSeconds = BUILT_IN_ITEM_CLASS_SECONDS;

    public static void initialize() {
        ResourceManagerHelper.get(ResourceType.SERVER_DATA).registerReloadListener(new SimpleSynchronousResourceReloadListener() {
            @Override
            public Identifier getFabricId() {
                return AsynchronousWorkstations.id("craft_times");
            }

            @Override
            public void reload(ResourceManager manager) {
                load(manager);
            }
        });
    }

    // Times are edited in data/asynch-stations/craft_times.json and applied by /reload.
    // Lookup order for one craft:
    // 1. output_seconds for the result item.
    // 2. The sum of ingredient_seconds over the grid ingredients. An item's own entry beats a "#tag" entry.
    // 3. fallback_itemclass_seconds for the result's item class, if that sum is 0. This is what covers modded items.
    public static int getCraftTicks(ItemStack result, List<ItemStack> ingredients) {
        Double override = outputSeconds.get(Registries.ITEM.getId(result.getItem()));
        if (override != null) {
            return toTicks(override);
        }
        double seconds = 0;
        for (ItemStack ingredient : ingredients) {
            seconds += secondsPerItem(ingredient) * ingredient.getCount();
        }
        return toTicks(seconds > 0 ? seconds : fallbackItemClassSeconds.get(itemClassLabel(result.getItem())));
    }

    private static double secondsPerItem(ItemStack ingredient) {
        Double own = ingredientSeconds.get(Registries.ITEM.getId(ingredient.getItem()));
        if (own != null) {
            return own;
        }
        for (Map.Entry<TagKey<Item>, Double> tag : ingredientTagSeconds.entrySet()) {
            if (ingredient.isIn(tag.getKey())) {
                return tag.getValue();
            }
        }
        return 0;
    }

    // The JSON keys are only labels for these checks, so they never depend on class names at runtime.
    private static String itemClassLabel(Item item) {
        if (item instanceof ArmorItem) {
            return "ArmorItem";
        }
        if (item instanceof SwordItem) {
            return "SwordItem";
        }
        if (item instanceof ToolItem) {
            return "ToolItem";
        }
        return item instanceof BlockItem ? "BlockItem" : "Item";
    }

    private static int toTicks(double seconds) {
        return (int) Math.round(seconds * 20);
    }

    private static void load(ResourceManager manager) {
        Map<Identifier, Double> items = new HashMap<>();
        Map<TagKey<Item>, Double> tags = new LinkedHashMap<>();
        Map<Identifier, Double> outputs = new HashMap<>();
        Map<String, Double> itemClasses = new HashMap<>(BUILT_IN_ITEM_CLASS_SECONDS);
        try (BufferedReader reader = manager.openAsReader(FILE)) {
            JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
            readEntries(section(root, "input_ingredient_seconds"), items, tags);
            readEntries(section(root, "strict_output_seconds"), outputs, null);
            for (Map.Entry<String, JsonElement> entry : section(root, "fallback_itemclass_seconds").entrySet()) {
                if (BUILT_IN_ITEM_CLASS_SECONDS.containsKey(entry.getKey())) {
                    itemClasses.put(entry.getKey(), entry.getValue().getAsDouble());
                } else {
                    AsynchronousWorkstations.LOGGER.debug("Ignoring unknown item class key {}", entry.getKey());
                }
            }
        } catch (IOException | RuntimeException e) {
            AsynchronousWorkstations.LOGGER.warn("Could not read {}, using built-in item class defaults: {}", FILE, e.toString());
            items.clear();
            tags.clear();
            outputs.clear();
            itemClasses = new HashMap<>(BUILT_IN_ITEM_CLASS_SECONDS);
        }
        ingredientSeconds = items;
        ingredientTagSeconds = tags;
        outputSeconds = outputs;
        fallbackItemClassSeconds = itemClasses;
    }

    private static JsonObject section(JsonObject root, String name) {
        return root.has(name) ? root.getAsJsonObject(name) : new JsonObject();
    }

    // Pass null for tags where "#tag" keys aren't allowed.
    private static void readEntries(JsonObject section, Map<Identifier, Double> items, Map<TagKey<Item>, Double> tags) {
        for (Map.Entry<String, JsonElement> entry : section.entrySet()) {
            String key = entry.getKey();
            double seconds = entry.getValue().getAsDouble();
            boolean isTag = tags != null && key.startsWith("#");
            Identifier id = Identifier.tryParse(isTag ? key.substring(1) : key);
            if (isTag && id != null) {
                // Tags may not be loaded yet, so a missing tag simply never matches later.
                tags.put(TagKey.of(RegistryKeys.ITEM, id), seconds);
            } else if (id != null && Registries.ITEM.containsId(id)) {
                items.put(id, seconds);
            } else {
                AsynchronousWorkstations.LOGGER.debug("Skipping craft time entry {}: no such item", key);
            }
        }
    }
}