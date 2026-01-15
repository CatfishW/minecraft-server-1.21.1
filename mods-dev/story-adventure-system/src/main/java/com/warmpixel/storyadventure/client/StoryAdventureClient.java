package com.warmpixel.storyadventure.client;

import com.warmpixel.storyadventure.client.command.ClientUICommands;
import com.warmpixel.storyadventure.client.render.EnemyIndicatorRenderer;
import com.warmpixel.storyadventure.client.render.TriggerBoxGizmoRenderer;
import com.warmpixel.storyadventure.client.render.WaypointIndicatorRenderer;
import com.warmpixel.storyadventure.client.ui.hud.EdgeIndicatorRenderer;
import com.warmpixel.storyadventure.client.ui.hud.StrangerHudRenderer;
import com.warmpixel.storyadventure.network.ClientNetworkHandler;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;

/**
 * Client-side entrypoint for Story Adventure System.
 * Handles UI rendering, HUD overlays, and client-side networking.
 */
@Environment(EnvType.CLIENT)
public class StoryAdventureClient implements ClientModInitializer {
    
    @Override
    public void onInitializeClient() {
        // Register client-side packet receivers for server-to-client UI commands
        ClientNetworkHandler.registerClientReceivers();
        
        // Register HUD overlays
        StrangerHudRenderer.register();
        EdgeIndicatorRenderer.register();
        WaypointIndicatorRenderer.register();
        com.warmpixel.storyadventure.client.render.WorldDestinationRenderer.register();
        
        // Register world-space renderers
        TriggerBoxGizmoRenderer.register();
        EnemyIndicatorRenderer.register();
        
        // Register cinematic overlay for cutscenes
        com.warmpixel.storyadventure.client.render.CinematicOverlayRenderer.register();
        
        // Register client-side UI commands (these work when typing directly in chat)
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
            ClientUICommands.register(dispatcher);
        });
    }
}
