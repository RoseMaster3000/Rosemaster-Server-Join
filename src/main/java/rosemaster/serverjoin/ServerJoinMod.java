package rosemaster.serverjoin;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ServerJoinMod implements ModInitializer {
    public static final String MOD_ID = "rosemaster_server_join";
    private static final String FIRST_JOIN_KEY = "rosemaster_first_join";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);


    @Override
    public void onInitialize() {
        // Register the player join event
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            ServerPlayerEntity player = handler.player;
            if (isFirstJoin(player)) {
                markPlayerAsJoined(player);
                handleFirstTimeJoin(player);
            }
        });

        LOGGER.info("ServerJoinMod has been initialized!");
    }


    // Return T/F (if player has joined in the past)
    private boolean isFirstJoin(ServerPlayerEntity player) {
        NbtCompound persistentData = getPersistentData(player);
        return !persistentData.contains(FIRST_JOIN_KEY);
    }


    // Mark player has joined => True
    private void markPlayerAsJoined(ServerPlayerEntity player) {
        NbtCompound persistentData = getPersistentData(player);
        persistentData.putBoolean(FIRST_JOIN_KEY, true);
        player.writeCustomDataToNbt(persistentData);
    }


    // First Join Event
    private void handleFirstTimeJoin(ServerPlayerEntity player) {
        // Send a special first-time welcome message
        player.sendMessage(Text.literal("Welcome to the server for the first time, " + player.getName().getString() + "!"));
        

        player.getInventory().insertStack(new net.minecraft.item.ItemStack(net.minecraft.item.Items.DIAMOND, 3));
        
        // Optional: Log first-time join
        LOGGER.info("First-time player {} has joined the server", player.getName().getString());
    }


    // Retrieve the player's persistent NBT data (for marking player joined)
    private NbtCompound getPersistentData(ServerPlayerEntity player) {
        NbtCompound persistentData = new NbtCompound();
        player.readCustomDataFromNbt(persistentData);
        return persistentData;
    }
}