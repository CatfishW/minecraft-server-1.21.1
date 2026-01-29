package com.warmpixel.storyadventure.client.integration;

import com.warmpixel.storyadventure.StoryAdventureMod;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import xaero.common.minimap.waypoints.Waypoint;
import xaero.common.minimap.waypoints.WaypointSet;
import xaero.common.minimap.waypoints.WaypointWorld;
import xaero.common.minimap.waypoints.WaypointsManager;
import xaero.hud.minimap.waypoint.WaypointPurpose;

/**
 * Client-side integration with Xaero's Waypoints.
 * Safely manages waypoint sets to hide user waypoints during story instances.
 */
@Environment(EnvType.CLIENT)
public class XaeroWaypointIntegration {
    
    private static String originalSetName = null;
    private static final String STORY_SET_NAME = "Story_Adventure";

    /**
     * Called when a story instance starts.
     * Backs up the current waypoint set and switches to a clean "Story" set.
     */
    public static void onInstanceStart() {
        try {
            WaypointsManager waypointsManager = getWaypointsManager();
            if (waypointsManager == null) return;

            WaypointWorld world = waypointsManager.getCurrentWorld();
            if (world == null) return;

            // Backup current set
            originalSetName = world.getCurrent();
            StoryAdventureMod.LOGGER.info("Backing up Xaero waypoint set: {}", originalSetName);

            // Create or switch to story set
            if (!world.getSets().containsKey(STORY_SET_NAME)) {
                world.addSet(STORY_SET_NAME);
            }
            world.setCurrent(STORY_SET_NAME);
            
            // Clear any existing waypoints in the story set (just in case)
            WaypointSet storySet = world.getSets().get(STORY_SET_NAME);
            if (storySet != null) {
                storySet.getList().clear();
            }
            
            waypointsManager.updateWaypoints();
            StoryAdventureMod.LOGGER.info("Switched to Xaero waypoint set: {}", STORY_SET_NAME);
        } catch (Throwable t) {
            StoryAdventureMod.LOGGER.error("Failed to handle Xaero waypoint instance start", t);
        }
    }

    /**
     * Adds a story waypoint.
     */
    public static void addWaypoint(String id, String name, double x, double y, double z, int color) {
        try {
            WaypointsManager waypointsManager = getWaypointsManager();
            if (waypointsManager == null) return;

            WaypointWorld world = waypointsManager.getCurrentWorld();
            if (world == null) return;

            WaypointSet storySet = world.getSets().get(STORY_SET_NAME);
            if (storySet == null) {
                world.addSet(STORY_SET_NAME);
                storySet = world.getSets().get(STORY_SET_NAME);
            }

            // Remove existing waypoint with same id/name if present
            storySet.getList().removeIf(wp -> wp.getName().equals(name));

            // Create new waypoint
            // Waypoint(int x, int y, int z, String name, String initials, int color)
            // Using a simple initial (first letter of name or 'S')
            String initials = name.isEmpty() ? "S" : name.substring(0, 1).toUpperCase();
            Waypoint wp = new Waypoint((int)x, (int)y, (int)z, name, initials, color);
            wp.setPurpose(WaypointPurpose.DESTINATION);
            
            storySet.getList().add(wp);
            waypointsManager.updateWaypoints();
            StoryAdventureMod.LOGGER.info("Added Xaero story waypoint: {} at {},{},{}", name, x, y, z);
        } catch (Throwable t) {
            StoryAdventureMod.LOGGER.error("Failed to add Xaero waypoint", t);
        }
    }

    /**
     * Removes a story waypoint.
     */
    public static void removeWaypoint(String id, String name) {
        try {
            WaypointsManager waypointsManager = getWaypointsManager();
            if (waypointsManager == null) return;

            WaypointWorld world = waypointsManager.getCurrentWorld();
            if (world == null) return;

            WaypointSet storySet = world.getSets().get(STORY_SET_NAME);
            if (storySet != null) {
                storySet.getList().removeIf(wp -> wp.getName().equals(name));
                waypointsManager.updateWaypoints();
            }
        } catch (Throwable t) {
            StoryAdventureMod.LOGGER.error("Failed to remove Xaero waypoint", t);
        }
    }

    /**
     * Called when a story instance ends.
     * Restores the original waypoint set.
     */
    public static void onInstanceEnd() {
        try {
            WaypointsManager waypointsManager = getWaypointsManager();
            if (waypointsManager == null) return;

            WaypointWorld world = waypointsManager.getCurrentWorld();
            if (world == null) return;

            // Clear story set waypoints
            WaypointSet storySet = world.getSets().get(STORY_SET_NAME);
            if (storySet != null) {
                storySet.getList().clear();
            }

            String targetSet = null;

            // 1. Try to restore original set
            if (originalSetName != null && world.getSets().containsKey(originalSetName)) {
                targetSet = originalSetName;
            } 
            
            // 2. Fallback: Try "gui.xaero_default" explicitly
            if (targetSet == null) {
                if (world.getSets().containsKey("gui.xaero_default")) {
                    targetSet = "gui.xaero_default";
                }
            }
            
            // 3. Fallback: Find ANY set that isn't the story set
            if (targetSet == null) {
                for (String setName : world.getSets().keySet()) {
                    if (!setName.equals(STORY_SET_NAME)) {
                        targetSet = setName;
                        break;
                    }
                }
            }

            // 4. Last Resort: Create the default set if nothing else exists
            if (targetSet == null) {
                targetSet = "gui.xaero_default";
                if (!world.getSets().containsKey(targetSet)) {
                    world.addSet(targetSet);
                }
            }

            // Always ensure we switch to a valid set BEFORE removing anything
            world.setCurrent(targetSet);
            StoryAdventureMod.LOGGER.info("Restored Xaero waypoint set to: {}", targetSet);

            // Clean up the story set only if we're not currently using it
            if (!targetSet.equals(STORY_SET_NAME) && world.getSets().containsKey(STORY_SET_NAME)) {
                 world.getSets().remove(STORY_SET_NAME);
                 StoryAdventureMod.LOGGER.info("Removed Xaero story waypoint set");
            }
            
            originalSetName = null;
            waypointsManager.updateWaypoints();
        } catch (Throwable t) {
            StoryAdventureMod.LOGGER.error("Failed to restore Xaero waypoints", t);
        }
    }

    private static WaypointsManager getWaypointsManager() {
        try {
            // Access via XaeroMinimapSession directly - this is the correct API for Xaero 25.x
            xaero.common.XaeroMinimapSession session = xaero.common.XaeroMinimapSession.getCurrentSession();
            if (session == null) {
                StoryAdventureMod.LOGGER.debug("[XaeroWaypoint] No active XaeroMinimapSession");
                return null;
            }
            
            // Get the WaypointsManager from the session
            WaypointsManager waypointsManager = session.getWaypointsManager();
            if (waypointsManager == null) {
                StoryAdventureMod.LOGGER.debug("[XaeroWaypoint] WaypointsManager is null in session");
                return null;
            }
            
            StoryAdventureMod.LOGGER.debug("[XaeroWaypoint] Successfully obtained WaypointsManager");
            return waypointsManager;
        } catch (Throwable t) {
            StoryAdventureMod.LOGGER.debug("[XaeroWaypoint] Integration not available: {}", t.getMessage());
            return null;
        }
    }

}
