/*
 * Copyright 2024 Markus Bordihn
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this software and
 * associated documentation files (the "Software"), to deal in the Software without restriction,
 * including without limitation the rights to use, copy, modify, merge, publish, distribute,
 * sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all copies or
 * substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT
 * NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND
 * NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM,
 * DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */

package de.markusbordihn.easynpc.entity.easynpc.ai.goal;

import de.markusbordihn.easynpc.data.crime.PlayerLawState;
import de.markusbordihn.easynpc.handler.LawSystemHandler;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.targeting.TargetingConditions;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.player.Player;

/**
 * Custom goal to attack wanted players.
 * This goal will periodically check for closer wanted players and switch targets.
 */
public class AttackWantedPlayerGoal extends NearestAttackableTargetGoal<Player> {

  private final PathfinderMob mob;
  private int checkTick = 0;

  public AttackWantedPlayerGoal(
      PathfinderMob mob, int interval, boolean mustSee, boolean mustReach) {
    super(
        mob,
        Player.class,
        interval,
        mustSee,
        mustReach,
        entity -> {
          if (entity instanceof ServerPlayer serverPlayer) {
            PlayerLawState state =
                LawSystemHandler.getInstance().getPlayerState(serverPlayer.getUUID());
            return state != null && state.isWanted();
          }
          return false;
        });
    this.mob = mob;
  }

  @Override
  public boolean canContinueToUse() {
    LivingEntity target = this.mob.getTarget();
    
    // Stop if target is null or not a wanted player
    if (!(target instanceof ServerPlayer serverPlayer)) {
      return false;
    }
    
    PlayerLawState state = LawSystemHandler.getInstance().getPlayerState(serverPlayer.getUUID());
    if (state == null || !state.isWanted()) {
      return false;
    }

    // Periodically check if there's a closer wanted player to allow switching targets
    checkTick++;
    if (checkTick >= 40) { // Every 2 seconds
      checkTick = 0;
      
      // Define targeting conditions for wanted players
      net.minecraft.world.entity.ai.targeting.TargetingConditions conditions = 
          net.minecraft.world.entity.ai.targeting.TargetingConditions.forCombat()
              .range(this.getFollowDistance())
              .selector(entity -> {
                if (entity instanceof ServerPlayer p) {
                  PlayerLawState nearestState = LawSystemHandler.getInstance().getPlayerState(p.getUUID());
                  return nearestState != null && nearestState.isWanted();
                }
                return false;
              });

      Player nearest = this.mob.level().getNearestPlayer(conditions, this.mob);
      if (nearest != null && nearest != target) {
        double distToCurrent = this.mob.distanceToSqr(target);
        double distToNearest = this.mob.distanceToSqr(nearest);
        
        // Switch if the new target is significantly closer (25% closer)
        if (distToNearest < distToCurrent * 0.75) {
          return false; // Returning false will stop the goal and let the selector pick the new nearest in the next tick
        }
      }
    }

    return super.canContinueToUse();
  }
}
