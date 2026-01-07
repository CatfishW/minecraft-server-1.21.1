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

package de.markusbordihn.easynpc.server.commands;

import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.ArgumentBuilder;
import de.markusbordihn.easynpc.commands.Command;
import de.markusbordihn.easynpc.commands.arguments.EasyNPCArgument;
import de.markusbordihn.easynpc.entity.easynpc.EasyNPC;
import de.markusbordihn.easynpc.entity.easynpc.data.DialogDataCapable;
import de.markusbordihn.easynpc.llm.LLMChatHandler;
import de.markusbordihn.easynpc.llm.LLMConfig;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.server.level.ServerPlayer;

/**
 * Commands for managing LLM chat integration with NPCs.
 */
public class LLMCommand extends Command {

  private LLMCommand() {}

  public static ArgumentBuilder<CommandSourceStack, ?> register() {
    return Commands.literal("llm")
        .requires(commandSourceStack -> commandSourceStack.hasPermission(Commands.LEVEL_GAMEMASTERS))
        .then(
            Commands.literal("status")
                .executes(context -> showStatus(context.getSource())))
        .then(
            Commands.literal("enable")
                .then(
                    Commands.argument(NPC_TARGET_ARG, EasyNPCArgument.npc())
                        .executes(
                            context ->
                                setLLMEnabled(
                                    context.getSource(),
                                    EasyNPCArgument.getEntityWithAccess(context, NPC_TARGET_ARG),
                                    true))))
        .then(
            Commands.literal("disable")
                .then(
                    Commands.argument(NPC_TARGET_ARG, EasyNPCArgument.npc())
                        .executes(
                            context ->
                                setLLMEnabled(
                                    context.getSource(),
                                    EasyNPCArgument.getEntityWithAccess(context, NPC_TARGET_ARG),
                                    false))))
        .then(
            Commands.literal("prompt")
                .then(
                    Commands.argument(NPC_TARGET_ARG, EasyNPCArgument.npc())
                        .then(
                            Commands.argument("prompt", StringArgumentType.greedyString())
                                .executes(
                                    context ->
                                        setSystemPrompt(
                                            context.getSource(),
                                            EasyNPCArgument.getEntityWithAccess(context, NPC_TARGET_ARG),
                                            StringArgumentType.getString(context, "prompt"))))))
        .then(
            Commands.literal("chat")
                .then(
                    Commands.argument(NPC_TARGET_ARG, EasyNPCArgument.npc())
                        .then(
                            Commands.argument("message", StringArgumentType.greedyString())
                                .executes(
                                    context ->
                                        sendChat(
                                            context.getSource(),
                                            EasyNPCArgument.getEntityWithAccess(context, NPC_TARGET_ARG),
                                            StringArgumentType.getString(context, "message"))))));
  }

  public static int showStatus(CommandSourceStack context) {
    sendSuccessMessage(
        context,
        "LLM Configuration Status:",
        ChatFormatting.AQUA);
    sendSuccessMessage(
        context,
        "  Enabled: " + LLMConfig.isEnabled(),
        LLMConfig.isEnabled() ? ChatFormatting.GREEN : ChatFormatting.RED);
    sendSuccessMessage(
        context,
        "  API Endpoint: " + LLMConfig.getApiEndpoint(),
        ChatFormatting.GRAY);
    sendSuccessMessage(
        context,
        "  Model: " + LLMConfig.getEffectiveModel(),
        ChatFormatting.GRAY);
    sendSuccessMessage(
        context,
        "  Max History: " + LLMConfig.getMaxConversationHistory(),
        ChatFormatting.GRAY);
    sendSuccessMessage(
        context,
        "  Timeout: " + LLMConfig.getRequestTimeoutMs() + "ms",
        ChatFormatting.GRAY);
    return 1;
  }

  public static int setLLMEnabled(CommandSourceStack context, EasyNPC<?> easyNPC, boolean enabled) {
    if (!(easyNPC instanceof DialogDataCapable<?> dialogData)) {
      return sendFailureMessage(context, "NPC does not support dialog data!");
    }

    dialogData.setLLMChatEnabled(enabled);
    return sendSuccessMessage(
        context,
        "► " + (enabled ? "Enabled" : "Disabled") + " LLM chat for " + easyNPC.getEntity().getName().getString(),
        enabled ? ChatFormatting.GREEN : ChatFormatting.YELLOW);
  }

  public static int setSystemPrompt(CommandSourceStack context, EasyNPC<?> easyNPC, String prompt) {
    if (!(easyNPC instanceof DialogDataCapable<?> dialogData)) {
      return sendFailureMessage(context, "NPC does not support dialog data!");
    }

    dialogData.setLLMSystemPrompt(prompt);
    return sendSuccessMessage(
        context,
        "► Set system prompt for " + easyNPC.getEntity().getName().getString() + ": \"" + 
            (prompt.length() > 50 ? prompt.substring(0, 50) + "..." : prompt) + "\"",
        ChatFormatting.GREEN);
  }

  public static int sendChat(CommandSourceStack context, EasyNPC<?> easyNPC, String message) {
    if (!(context.getEntity() instanceof ServerPlayer serverPlayer)) {
      return sendFailureMessage(context, "This command must be run by a player!");
    }

    if (!(easyNPC instanceof DialogDataCapable<?> dialogData)) {
      return sendFailureMessage(context, "NPC does not support dialog data!");
    }

    if (!dialogData.isLLMChatEnabled()) {
      return sendFailureMessage(context, "LLM chat is not enabled for this NPC!");
    }

    if (!LLMConfig.isEnabled()) {
      return sendFailureMessage(context, "LLM integration is globally disabled!");
    }

    LLMChatHandler.handleChatMessage(serverPlayer, easyNPC, message);
    return sendSuccessMessage(
        context,
        "► Sent message to " + easyNPC.getEntity().getName().getString() + ": \"" + message + "\"",
        ChatFormatting.GREEN);
  }
}
