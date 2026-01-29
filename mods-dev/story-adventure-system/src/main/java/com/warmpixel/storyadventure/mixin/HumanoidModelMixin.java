package com.warmpixel.storyadventure.mixin;

import com.warmpixel.storyadventure.client.animation.AnimationManager;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = HumanoidModel.class, priority = 2000)
public class HumanoidModelMixin<T extends LivingEntity> {
    @Shadow public ModelPart head;
    @Shadow public ModelPart hat;
    @Shadow public ModelPart body;
    @Shadow public ModelPart rightArm;
    @Shadow public ModelPart leftArm;
    @Shadow public ModelPart rightLeg;
    @Shadow public ModelPart leftLeg;

    @Inject(method = "setupAnim(Lnet/minecraft/world/entity/LivingEntity;FFFFF)V", at = @At("TAIL"))
    public void onSetupAnim(T entity, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch, CallbackInfo ci) {
        AnimationManager manager = AnimationManager.getInstance();
        
        if (manager.applyRotation(entity, "head", head)) {
            hat.copyFrom(head);
        }
        
        manager.applyRotation(entity, "body", body);
        manager.applyRotation(entity, "right_arm", rightArm);
        manager.applyRotation(entity, "left_arm", leftArm);
        manager.applyRotation(entity, "right_leg", rightLeg);
        manager.applyRotation(entity, "left_leg", leftLeg);
    }
}
