package cn.sh1rocu.tacz.mixin.common;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.tacz.guns.init.ModRecipe;
import net.fabricmc.fabric.impl.recipe.ingredient.CustomIngredientImpl;
import net.minecraft.world.item.crafting.RecipeManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(RecipeManager.class)
public abstract class RecipeManagerMixin {
    @ModifyExpressionValue(method = "apply(Ljava/util/Map;Lnet/minecraft/server/packs/resources/ResourceManager;Lnet/minecraft/util/profiling/ProfilerFiller;)V", at = @At(remap = false, value = "INVOKE", target = "Ljava/util/Map$Entry;getValue()Ljava/lang/Object;"))
    private <V> V tacz$injectApply(V original) {
        JsonElement element = (JsonElement) original;
        if (element.isJsonObject()) {
            JsonObject obj = element.getAsJsonObject();
            if (obj.has("materials") && obj.has("type") && obj.get("type").getAsString().equals(ModRecipe.GUN_SMITH_TABLE_CRAFTING.toString())) {
                JsonArray materials = obj.getAsJsonArray("materials");
                materials.forEach(material -> {
                    if (material.isJsonObject()) {
                        JsonElement item = material.getAsJsonObject().get("item");
                        if (item.isJsonObject() && item.getAsJsonObject().has("type")) {
                            JsonObject itemObj = item.getAsJsonObject();
                            itemObj.addProperty(CustomIngredientImpl.TYPE_KEY, itemObj.get("type").getAsString());
                        }
                    }
                });
            }
        }
        return original;
    }
}
