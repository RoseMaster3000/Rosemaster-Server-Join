package rosemaster.serverjoin;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.loader.api.FabricLoader; // Import FabricLoader
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.registry.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream; // Import InputStream
import java.io.OutputStream; // Import OutputStream
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths; // Keep Paths for command file for consistency
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties; // Import Properties

public class ServerJoinMod implements ModInitializer {
    public static final String MOD_ID = "rosemaster_server_join";
    private static final String LAST_JOIN_DATE_KEY = "last_join_date";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    // --- Configuration Constants ---
    private static final String SETTINGS_FILE_NAME = "server-join-reward.properties";
    private static final String DAILY_ITEM_COUNT_KEY = "dailyItemCount";
    private static final int DEFAULT_DAILY_ITEM_COUNT = 1; // Default value

    // --- Configuration Variables ---
    private static int configuredDailyItemCount = DEFAULT_DAILY_ITEM_COUNT; // Holds the loaded value
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE;


    @Override
    public void onInitialize() {
        // Load configuration settings first
        loadConfig();

        // Register player join event
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            ServerPlayerEntity player = handler.player;
            if (isFirstJoinOfDay(player)) {
                markPlayerAsJoinedToday(player);
                handleDailyFirstJoin(player, server);
            }
        });
        LOGGER.info("ServerJoinMod (Daily Join API - Configurable Count) has been initialized!");
    }

    // --- Configuration Loading ---

    private static Path getConfigDir() {
        // Use FabricLoader to get the standard config directory
        return FabricLoader.getInstance().getConfigDir();
    }

    private static void loadConfig() {
        Path settingsPath = getConfigDir().resolve(SETTINGS_FILE_NAME);

        if (Files.exists(settingsPath)) {
            Properties properties = new Properties();
            try (InputStream input = Files.newInputStream(settingsPath)) {
                properties.load(input);

                String countStr = properties.getProperty(DAILY_ITEM_COUNT_KEY);
                if (countStr != null) {
                    try {
                        configuredDailyItemCount = Integer.parseInt(countStr.trim());
                        LOGGER.info("Loaded {} = {} from {}", DAILY_ITEM_COUNT_KEY, configuredDailyItemCount, SETTINGS_FILE_NAME);
                    } catch (NumberFormatException e) {
                        LOGGER.error("Invalid value for {} in {}: '{}'. Using default value {}.",
                                DAILY_ITEM_COUNT_KEY, SETTINGS_FILE_NAME, countStr, DEFAULT_DAILY_ITEM_COUNT);
                        configuredDailyItemCount = DEFAULT_DAILY_ITEM_COUNT;
                    }
                } else {
                    // Key missing, use default and maybe warn
                    LOGGER.warn("Key {} not found in {}. Using default value {}.", DAILY_ITEM_COUNT_KEY, SETTINGS_FILE_NAME, DEFAULT_DAILY_ITEM_COUNT);
                    configuredDailyItemCount = DEFAULT_DAILY_ITEM_COUNT;
                    // Optionally, add the missing key back to the file here if desired
                }

            } catch (IOException e) {
                LOGGER.error("Could not read configuration file: {}. Using default settings.", settingsPath, e);
                configuredDailyItemCount = DEFAULT_DAILY_ITEM_COUNT;
            }
        } else {
            LOGGER.warn("Configuration file {} not found. Creating default config and using default settings.", settingsPath);
            configuredDailyItemCount = DEFAULT_DAILY_ITEM_COUNT;
            createDefaultConfig(settingsPath);
        }
    }

    private static void createDefaultConfig(Path settingsPath) {
        Properties properties = new Properties();
        // Set the default value in the properties object
        properties.setProperty(DAILY_ITEM_COUNT_KEY, String.valueOf(DEFAULT_DAILY_ITEM_COUNT));

        try (OutputStream output = Files.newOutputStream(settingsPath)) {
            // Write the properties to the file with comments
            properties.store(output, " ServerJoinMod Settings \n " +
                    DAILY_ITEM_COUNT_KEY + ": How many of the daily item (lifesteal:crystal_core) to give?");
            LOGGER.info("Created default configuration file: {}", settingsPath);
        } catch (IOException e) {
            LOGGER.error("Could not create default configuration file: {}", settingsPath, e);
        }
    }


    // --- NBT Handling ---

    private NbtCompound getModNbtData(ServerPlayerEntity player) {
        NbtCompound persistentData = new NbtCompound();
        player.writeCustomDataToNbt(persistentData);
        NbtCompound modData;
        if (persistentData.contains(MOD_ID, NbtElement.COMPOUND_TYPE)) {
            modData = persistentData.getCompound(MOD_ID);
        } else {
            modData = new NbtCompound();
        }
        return modData;
    }

    private void saveModNbtData(ServerPlayerEntity player, NbtCompound modData) {
        NbtCompound persistentData = new NbtCompound();
        player.writeCustomDataToNbt(persistentData);
        persistentData.put(MOD_ID, modData);
        player.readCustomDataFromNbt(persistentData);
    }

    // --- Join Logic ---

    private boolean isFirstJoinOfDay(ServerPlayerEntity player) {
        NbtCompound modData = getModNbtData(player);
        String todayDateStr = LocalDate.now().format(DATE_FORMATTER);
        if (!modData.contains(LAST_JOIN_DATE_KEY)) {
            return true;
        }
        String lastJoinDateStr = modData.getString(LAST_JOIN_DATE_KEY);
        return !lastJoinDateStr.equals(todayDateStr);
    }

    private void markPlayerAsJoinedToday(ServerPlayerEntity player) {
        NbtCompound modData = getModNbtData(player);
        String todayDateStr = LocalDate.now().format(DATE_FORMATTER);
        modData.putString(LAST_JOIN_DATE_KEY, todayDateStr);
        saveModNbtData(player, modData);
        LOGGER.info("Marked player {} as joined today ({}) using read/write.", player.getName().getString(), todayDateStr);
    }

    private void handleDailyFirstJoin(ServerPlayerEntity player, MinecraftServer server) {
        String playerName = player.getName().getString();

        // --- Give Specific Daily Item using API (Now uses configured count) ---
        try {
            Identifier itemId = Identifier.of("lifesteal", "crystal_core"); // Use Identifier.of
            int dailyItemCount = configuredDailyItemCount;
            Item dailyItem = Registries.ITEM.get(itemId);

            if (dailyItem == Items.AIR) {
                LOGGER.error("Failed to give daily item: Item with ID '{}' not found in registry!", itemId);
                player.sendMessage(Text.literal("Sorry, the daily item ('" + itemId + "') could not be found.").formatted(Formatting.RED), false);
            } else if (dailyItemCount > 0) { // Only give if count is positive
                ItemStack itemStackToGive = new ItemStack(dailyItem, dailyItemCount);
                LOGGER.info("Attempting to give {}x {} ({}) to player {} via API", dailyItemCount, itemId, dailyItem.getName().getString(), playerName);
                player.giveItemStack(itemStackToGive);
                player.sendMessage(Text.literal("Welcome back! You received your daily ")
                        .append(Text.literal(String.valueOf(dailyItemCount)).formatted(Formatting.GOLD))
                        .append("x ")
                        .append(Text.translatable(dailyItem.getTranslationKey()).formatted(Formatting.LIGHT_PURPLE))
                        .append("!"), false);
            } else {
                LOGGER.info("Daily item count is configured to {} for {}, not giving item.", dailyItemCount, itemId);
                // Optionally inform the player they received nothing today if count is <= 0
                // player.sendMessage(Text.literal("No daily item configured for today.").formatted(Formatting.GRAY), false);
            }
        } catch (Exception e) {
            LOGGER.error("Unexpected error giving daily item '{}' to player {} via API", "lifesteal:crystal_core", playerName, e);
            player.sendMessage(Text.literal("Sorry, an unexpected error occurred while giving your daily item.").formatted(Formatting.RED), false);
        }

        LOGGER.info("Finished processing daily first join for player {}", playerName);
    }
}