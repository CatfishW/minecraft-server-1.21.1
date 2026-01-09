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

package de.markusbordihn.easynpc.client.renderer;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import de.markusbordihn.easynpc.item.ModItems;
import de.markusbordihn.easynpc.item.configuration.SpawnRectWandItem;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

/**
 * Client-side renderer for spawn rect gizmos.
 * Renders wireframe boxes when player holds the Spawn Rect Wand.
 */
public class SpawnRectRenderer {

  private SpawnRectRenderer() {}

  /**
   * Register the render event.
   */
  public static void register() {
    WorldRenderEvents.AFTER_TRANSLUCENT.register(SpawnRectRenderer::onWorldRender);
  }

  /**
   * Called after translucent rendering.
   */
  private static void onWorldRender(WorldRenderContext context) {
    Minecraft minecraft = Minecraft.getInstance();
    Player player = minecraft.player;
    if (player == null) {
      return;
    }

    // Check if player is holding the spawn rect wand
    ItemStack mainHand = player.getMainHandItem();
    ItemStack offHand = player.getOffhandItem();
    
    ItemStack wandStack = null;
    if (mainHand.getItem() == ModItems.SPAWN_RECT_WAND) {
      wandStack = mainHand;
    } else if (offHand.getItem() == ModItems.SPAWN_RECT_WAND) {
      wandStack = offHand;
    }
    
    if (wandStack == null) {
      return;
    }

    // Get selection from wand
    BlockPos pos1 = SpawnRectWandItem.getPos1(wandStack);
    BlockPos pos2 = SpawnRectWandItem.getPos2(wandStack);
    
    if (pos1 == null && pos2 == null) {
      return;
    }

    // Render the selection box
    PoseStack poseStack = context.matrixStack();
    Camera camera = context.camera();
    Vec3 cameraPos = camera.getPosition();

    poseStack.pushPose();
    poseStack.translate(-cameraPos.x, -cameraPos.y, -cameraPos.z);

    RenderSystem.enableBlend();
    RenderSystem.defaultBlendFunc();
    RenderSystem.disableDepthTest();
    RenderSystem.setShader(GameRenderer::getPositionColorShader);

    if (pos1 != null && pos2 != null) {
      // Draw box between pos1 and pos2
      drawWireframeBox(poseStack, 
          Math.min(pos1.getX(), pos2.getX()),
          Math.min(pos1.getY(), pos2.getY()),
          Math.min(pos1.getZ(), pos2.getZ()),
          Math.max(pos1.getX(), pos2.getX()) + 1,
          Math.max(pos1.getY(), pos2.getY()) + 1,
          Math.max(pos1.getZ(), pos2.getZ()) + 1,
          0.0f, 1.0f, 0.0f, 0.8f); // Green
    } else if (pos1 != null) {
      // Draw single block at pos1
      drawWireframeBox(poseStack, 
          pos1.getX(), pos1.getY(), pos1.getZ(),
          pos1.getX() + 1, pos1.getY() + 1, pos1.getZ() + 1,
          1.0f, 1.0f, 0.0f, 0.8f); // Yellow
    } else if (pos2 != null) {
      // Draw single block at pos2
      drawWireframeBox(poseStack, 
          pos2.getX(), pos2.getY(), pos2.getZ(),
          pos2.getX() + 1, pos2.getY() + 1, pos2.getZ() + 1,
          0.0f, 1.0f, 1.0f, 0.8f); // Cyan
    }

    RenderSystem.enableDepthTest();
    RenderSystem.disableBlend();

    poseStack.popPose();
  }

  /**
   * Draw a wireframe box.
   */
  private static void drawWireframeBox(PoseStack poseStack, 
      double x1, double y1, double z1, 
      double x2, double y2, double z2,
      float r, float g, float b, float a) {
    
    Matrix4f matrix = poseStack.last().pose();
    
    Tesselator tesselator = Tesselator.getInstance();
    BufferBuilder buffer = tesselator.begin(VertexFormat.Mode.DEBUG_LINES, DefaultVertexFormat.POSITION_COLOR);

    // Bottom face
    buffer.addVertex(matrix, (float) x1, (float) y1, (float) z1).setColor(r, g, b, a);
    buffer.addVertex(matrix, (float) x2, (float) y1, (float) z1).setColor(r, g, b, a);
    
    buffer.addVertex(matrix, (float) x2, (float) y1, (float) z1).setColor(r, g, b, a);
    buffer.addVertex(matrix, (float) x2, (float) y1, (float) z2).setColor(r, g, b, a);
    
    buffer.addVertex(matrix, (float) x2, (float) y1, (float) z2).setColor(r, g, b, a);
    buffer.addVertex(matrix, (float) x1, (float) y1, (float) z2).setColor(r, g, b, a);
    
    buffer.addVertex(matrix, (float) x1, (float) y1, (float) z2).setColor(r, g, b, a);
    buffer.addVertex(matrix, (float) x1, (float) y1, (float) z1).setColor(r, g, b, a);

    // Top face
    buffer.addVertex(matrix, (float) x1, (float) y2, (float) z1).setColor(r, g, b, a);
    buffer.addVertex(matrix, (float) x2, (float) y2, (float) z1).setColor(r, g, b, a);
    
    buffer.addVertex(matrix, (float) x2, (float) y2, (float) z1).setColor(r, g, b, a);
    buffer.addVertex(matrix, (float) x2, (float) y2, (float) z2).setColor(r, g, b, a);
    
    buffer.addVertex(matrix, (float) x2, (float) y2, (float) z2).setColor(r, g, b, a);
    buffer.addVertex(matrix, (float) x1, (float) y2, (float) z2).setColor(r, g, b, a);
    
    buffer.addVertex(matrix, (float) x1, (float) y2, (float) z2).setColor(r, g, b, a);
    buffer.addVertex(matrix, (float) x1, (float) y2, (float) z1).setColor(r, g, b, a);

    // Vertical edges
    buffer.addVertex(matrix, (float) x1, (float) y1, (float) z1).setColor(r, g, b, a);
    buffer.addVertex(matrix, (float) x1, (float) y2, (float) z1).setColor(r, g, b, a);
    
    buffer.addVertex(matrix, (float) x2, (float) y1, (float) z1).setColor(r, g, b, a);
    buffer.addVertex(matrix, (float) x2, (float) y2, (float) z1).setColor(r, g, b, a);
    
    buffer.addVertex(matrix, (float) x2, (float) y1, (float) z2).setColor(r, g, b, a);
    buffer.addVertex(matrix, (float) x2, (float) y2, (float) z2).setColor(r, g, b, a);
    
    buffer.addVertex(matrix, (float) x1, (float) y1, (float) z2).setColor(r, g, b, a);
    buffer.addVertex(matrix, (float) x1, (float) y2, (float) z2).setColor(r, g, b, a);

    BufferUploader.drawWithShader(buffer.buildOrThrow());
  }
}
