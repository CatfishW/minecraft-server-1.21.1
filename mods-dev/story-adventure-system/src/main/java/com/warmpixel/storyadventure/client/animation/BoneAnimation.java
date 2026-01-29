package com.warmpixel.storyadventure.client.animation;

import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

import java.util.List;

public class BoneAnimation {
    private final List<Keyframe> rotationKeyframes;

    public BoneAnimation(List<Keyframe> rotationKeyframes) {
        this.rotationKeyframes = rotationKeyframes;
        this.rotationKeyframes.sort((a, b) -> Float.compare(a.tick(), b.tick()));
    }

    public Vec3 getRotationAt(float tick) {
        if (rotationKeyframes.isEmpty()) {
            return Vec3.ZERO;
        }

        if (tick <= rotationKeyframes.get(0).tick()) {
            return rotationKeyframes.get(0).rotation();
        }

        if (tick >= rotationKeyframes.get(rotationKeyframes.size() - 1).tick()) {
            return rotationKeyframes.get(rotationKeyframes.size() - 1).rotation();
        }

        // Find keyframes surrounding current tick
        for (int i = 0; i < rotationKeyframes.size() - 1; i++) {
            Keyframe current = rotationKeyframes.get(i);
            Keyframe next = rotationKeyframes.get(i + 1);

            if (tick >= current.tick() && tick < next.tick()) {
                float t = (tick - current.tick()) / (next.tick() - current.tick());
                t = applyEasing(t, current.easing());
                
                return new Vec3(
                    Mth.lerp(t, current.rotation().x, next.rotation().x),
                    Mth.lerp(t, current.rotation().y, next.rotation().y),
                    Mth.lerp(t, current.rotation().z, next.rotation().z)
                );
            }
        }

        return rotationKeyframes.get(0).rotation();
    }

    private float applyEasing(float t, String easing) {
        switch (easing) {
            case "EASE_IN": return t * t;
            case "EASE_OUT": return t * (2 - t);
            case "EASE_IN_OUT": return t < 0.5f ? 2 * t * t : -1 + (4 - 2 * t) * t;
            default: return t;
        }
    }
}
