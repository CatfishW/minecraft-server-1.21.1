package com.warmpixel.storyadventure.client.animation;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import de.markusbordihn.easynpc.data.animation.AnimationData;

public class AnimationDefinition {
    private final Map<String, BoneAnimation> bones = new HashMap<>();
    private final float lengthTicks;
    private final boolean loop;

    public AnimationDefinition(JsonObject json) {
        this.lengthTicks = json.has("length_ticks") ? json.get("length_ticks").getAsFloat() : 20f;
        this.loop = json.has("loop") && json.get("loop").getAsBoolean();

        if (json.has("bones")) {
            JsonObject bonesObj = json.getAsJsonObject("bones");
            for (Map.Entry<String, JsonElement> entry : bonesObj.entrySet()) {
                String boneName = entry.getKey();
                JsonObject boneData = entry.getValue().getAsJsonObject();
                
                List<Keyframe> rotations = new ArrayList<>();
                if (boneData.has("rotation")) {
                    parseKeyframes(boneData.getAsJsonArray("rotation"), rotations);
                }
                
                bones.put(boneName, new BoneAnimation(rotations));
            }
        }
    }

    public AnimationDefinition(AnimationData.Animation easyAnimation) {
        this.lengthTicks = easyAnimation.getAnimationLength() != null ? easyAnimation.getAnimationLength() * 20f : 20f;
        this.loop = "true".equalsIgnoreCase(easyAnimation.getLoop()) || "loop".equalsIgnoreCase(easyAnimation.getLoop());

        if (easyAnimation.getBones() != null) {
            for (Map.Entry<String, AnimationData.Bone> entry : easyAnimation.getBones().entrySet()) {
                String boneName = entry.getKey();
                AnimationData.Bone bone = entry.getValue();

                List<Keyframe> rotations = new ArrayList<>();

                // Handle keyframes
                if (bone.getKeyframeRotation() != null && !bone.getKeyframeRotation().isEmpty()) {
                    for (Map.Entry<String, List<Float>> kf : bone.getKeyframeRotation().entrySet()) {
                        try {
                            float time = Float.parseFloat(kf.getKey());
                            List<Float> rot = kf.getValue();
                            if (rot != null && rot.size() >= 3) {
                                float tick = time * 20f; // Convert seconds to ticks
                                Vec3 rotVec = new Vec3(
                                    Math.toRadians(rot.get(0)),
                                    Math.toRadians(rot.get(1)),
                                    Math.toRadians(rot.get(2))
                                );
                                rotations.add(new Keyframe(tick, rotVec, "LINEAR"));
                            }
                        } catch (NumberFormatException e) {
                            // ignore
                        }
                    }
                }
                // Handle static rotation as fallback or base
                else if (bone.getRotation() != null && bone.getRotation().size() >= 3) {
                    List<Float> rot = bone.getRotation();
                    Vec3 rotVec = new Vec3(
                        Math.toRadians(rot.get(0)),
                        Math.toRadians(rot.get(1)),
                        Math.toRadians(rot.get(2))
                    );
                    rotations.add(new Keyframe(0f, rotVec, "LINEAR"));
                    rotations.add(new Keyframe(this.lengthTicks, rotVec, "LINEAR"));
                }

                if (!rotations.isEmpty()) {
                    bones.put(boneName, new BoneAnimation(rotations));
                }
            }
        }
    }

    private void parseKeyframes(JsonArray array, List<Keyframe> keyframes) {
        for (JsonElement elem : array) {
            if (elem.isJsonObject()) {
                JsonObject obj = elem.getAsJsonObject();
                float tick = obj.get("tick").getAsFloat();
                JsonArray rot = obj.getAsJsonArray("value");
                String easing = obj.has("easing") ? obj.get("easing").getAsString() : "LINEAR";
                
                keyframes.add(new Keyframe(
                    tick,
                    new Vec3(
                        Math.toRadians(rot.get(0).getAsDouble()),
                        Math.toRadians(rot.get(1).getAsDouble()),
                        Math.toRadians(rot.get(2).getAsDouble())
                    ),
                    easing
                ));
            }
        }
    }

    public BoneAnimation getBone(String name) {
        return bones.get(name);
    }
    
    public float getLengthTicks() {
        return lengthTicks;
    }
    
    public boolean isLoop() {
        return loop;
    }
}
