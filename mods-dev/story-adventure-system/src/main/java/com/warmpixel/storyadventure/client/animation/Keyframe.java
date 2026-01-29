package com.warmpixel.storyadventure.client.animation;

import net.minecraft.world.phys.Vec3;

public record Keyframe(float tick, Vec3 rotation, String easing) {
    public Keyframe(float tick, Vec3 rotation) {
        this(tick, rotation, "LINEAR");
    }
}
