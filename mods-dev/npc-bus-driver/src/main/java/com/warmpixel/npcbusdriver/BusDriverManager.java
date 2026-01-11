package com.warmpixel.npcbusdriver;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.minecraft.util.Mth;

import java.util.*;
import java.lang.reflect.Method;
import java.lang.reflect.Field;

public class BusDriverManager {
    private static final Map<UUID, BusDriverTask> activeTasks = new HashMap<>();

    public static void startDriving(Entity vehicle, List<BlockPos> path) {
        activeTasks.put(vehicle.getUUID(), new BusDriverTask(vehicle, path));
    }

    public static void tick() {
        Iterator<Map.Entry<UUID, BusDriverTask>> it = activeTasks.entrySet().iterator();
        while (it.hasNext()) {
            BusDriverTask task = it.next().getValue();
            if (task.vehicle.isRemoved()) {
                it.remove();
                continue;
            }
            task.update();
            if (task.finished) {
                task.stop();
                it.remove();
            }
        }
    }

    static class BusDriverTask {
        Entity vehicle;
        List<BlockPos> path;
        int currentIndex = 0;
        boolean finished = false;
        
        // Reflection cache
        private Method setInputsMethod;
        private boolean reflectionFailed = false;

        public BusDriverTask(Entity vehicle, List<BlockPos> path) {
            this.vehicle = vehicle;
            this.path = path;
            
            // Find closest point to start
            double minDist = Double.MAX_VALUE;
            int closest = 0;
            for(int i=0; i<path.size(); i++) {
                double d = vehicle.distanceToSqr(Vec3.atCenterOf(path.get(i)));
                if(d < minDist) {
                    minDist = d;
                    closest = i;
                }
            }
            this.currentIndex = closest;
        }

        public void update() {
            if (currentIndex >= path.size()) {
                finished = true;
                return;
            }

            Vec3 target = Vec3.atCenterOf(path.get(currentIndex));
            double dist = vehicle.position().distanceTo(target);

            if (dist < 3.0) {
                currentIndex++;
                if (currentIndex >= path.size()) {
                    finished = true; // Loop? Or stop. Assuming stop for now.
                    // To loop: currentIndex = 0;
                }
                return;
            }
            
            // Steering logic
            Vec3 toTarget = target.subtract(vehicle.position()).normalize();
            double targetYaw = Math.toDegrees(Math.atan2(-toTarget.x, toTarget.z));
            double currentYaw = vehicle.getYRot(); // Automobility vehicles might use different rotation field, checking standard Entity first
            
            // Normalize angles
            double diff = Mth.wrapDegrees(targetYaw - currentYaw);
            
            boolean left = false;
            boolean right = false;
            boolean fwd = true;
            boolean back = false;
            boolean space = false; // Drift/Brake
            
            if (dist < 5.0) {
                // Slow down strictly near target if it's the LAST point
                if (currentIndex == path.size() - 1) fwd = false; // Coast
            }

            if (diff > 10) {
                left = true;
                if (diff > 45) space = true; // Brake/Drift for sharp turn
            } else if (diff < -10) {
                right = true;
                if (diff < -45) space = true;
            }
            
            setInputs(fwd, back, left, right, space);
        }
        
        public void stop() {
            setInputs(false, false, false, false, true);
        }
        
        public void setInputs(boolean fwd, boolean back, boolean left, boolean right, boolean space) {
            if (reflectionFailed) return;
            try {
                if (setInputsMethod == null) {
                    // Try to find public void setInputs(boolean, boolean, boolean, boolean, boolean)
                    try {
                        setInputsMethod = vehicle.getClass().getMethod("setInputs", boolean.class, boolean.class, boolean.class, boolean.class, boolean.class);
                    } catch (NoSuchMethodException e) {
                        // Automobility uses a record or struct for Input, maybe?
                        // "io.github.foundationgames.automobility.entity.AutomobileEntity"
                        // It has "provideInput" or similar in 1.21 versions maybe?
                        // Let's try searching methods
                        for (Method m : vehicle.getClass().getMethods()) {
                             if (m.getName().equals("setInputs") && m.getParameterCount() == 5) {
                                 setInputsMethod = m;
                                 break;
                             }
                        }
                    }
                }
                
                if (setInputsMethod != null) {
                    setInputsMethod.invoke(vehicle, fwd, back, left, right, space);
                } else {
                    reflectionFailed = true;
                    System.out.println("Could not find setInputs method on " + vehicle.getClass().getName());
                }
            } catch (Exception e) {
                e.printStackTrace();
                reflectionFailed = true;
            }
        }
    }
}
