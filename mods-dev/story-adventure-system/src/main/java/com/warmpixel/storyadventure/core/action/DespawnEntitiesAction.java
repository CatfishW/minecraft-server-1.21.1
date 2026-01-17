package com.warmpixel.storyadventure.core.action;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.warmpixel.storyadventure.StoryAdventureMod;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Action that despawns entities by matching tags.
 */
public class DespawnEntitiesAction implements NodeAction {

    private final List<String> tags;
    private UUID instanceId;

    public DespawnEntitiesAction(List<String> tags) {
        this.tags = new ArrayList<>(tags);
    }

    public void setInstanceId(UUID instanceId) {
        this.instanceId = instanceId;
    }

    @Override
    public String getType() {
        return "DESPAWN_ENTITIES";
    }

    @Override
    public void execute(List<ServerPlayer> players) {
        if (players.isEmpty()) return;

        var server = players.get(0).getServer();
        if (server == null) return;

        List<String> resolvedTags = new ArrayList<>();
        for (String tag : tags) {
            if (tag == null || tag.isEmpty()) continue;
            if (instanceId != null) {
                resolvedTags.add(tag.replace("{instance_id}", instanceId.toString()));
            } else {
                resolvedTags.add(tag);
            }
        }

        if (resolvedTags.isEmpty()) {
            StoryAdventureMod.LOGGER.warn("[DespawnEntitiesAction] No tags specified for despawn.");
            return;
        }

        int removedCount = 0;
        for (ServerLevel level : server.getAllLevels()) {
            List<Entity> entitiesToRemove = new ArrayList<>();
            for (Entity entity : level.getAllEntities()) {
                if (entity instanceof ServerPlayer) {
                    continue;
                }
                boolean matchesAll = true;
                for (String tag : resolvedTags) {
                    if (!entity.getTags().contains(tag)) {
                        matchesAll = false;
                        break;
                    }
                }
                if (matchesAll) {
                    entitiesToRemove.add(entity);
                }
            }

            for (Entity entity : entitiesToRemove) {
                try {
                    entity.ejectPassengers();
                    entity.discard();
                    removedCount++;
                } catch (Exception e) {
                    StoryAdventureMod.LOGGER.warn("[DespawnEntitiesAction] Failed to discard entity {}: {}", entity, e.getMessage());
                }
            }
        }

        StoryAdventureMod.LOGGER.info("[DespawnEntitiesAction] Removed {} entities with tags {}", removedCount, resolvedTags);
    }

    @Override
    public JsonObject toJson() {
        JsonObject json = new JsonObject();
        json.addProperty("type", "DESPAWN_ENTITIES");
        JsonArray tagArray = new JsonArray();
        for (String tag : tags) {
            tagArray.add(tag);
        }
        json.add("tags", tagArray);
        return json;
    }

    @Override
    public String getSummary() {
        return "Despawn entities with tags: " + String.join(", ", tags);
    }

    public static DespawnEntitiesAction fromJson(JsonObject json) {
        List<String> tags = new ArrayList<>();
        if (json.has("tags") && json.get("tags").isJsonArray()) {
            for (var elem : json.getAsJsonArray("tags")) {
                if (elem.isJsonPrimitive()) {
                    tags.add(elem.getAsString());
                }
            }
        }
        return new DespawnEntitiesAction(tags);
    }
}
