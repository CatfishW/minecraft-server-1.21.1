/*
 * Copyright 2023 Markus Bordihn
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

import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.datafixers.util.Pair;
import de.markusbordihn.easynpc.commands.Command;
import de.markusbordihn.easynpc.commands.arguments.DialogArgument;
import de.markusbordihn.easynpc.commands.arguments.EasyNPCArgument;
import de.markusbordihn.easynpc.data.saveddata.ActionExecutionTracker;
import de.markusbordihn.easynpc.entity.easynpc.EasyNPC;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.server.level.ServerPlayer;

public class ActionCommand extends Command {

  private ActionCommand() {}

  public static ArgumentBuilder<CommandSourceStack, ?> register() {
    return Commands.literal("action")
        .requires(commandSourceStack -> commandSourceStack.hasPermission(Commands.LEVEL_GAMEMASTERS))
        .then(
            Commands.literal("reset_dialog_limit")
                .then(
                    Commands.argument(TARGET_ARG, EasyNPCArgument.npc())
                        .then(
                            Commands.argument(DIALOG_ARG, DialogArgument.uuidOrLabel())
                                .then(
                                    Commands.argument(PLAYER_ARG, EntityArgument.player())
                                        .executes(
                                            context ->
                                                resetDialogLimit(
                                                    context.getSource(),
                                                    EasyNPCArgument.getEntityWithAccess(
                                                        context, TARGET_ARG),
                                                    DialogArgument.getUuidOrLabel(
                                                        context, DIALOG_ARG),
                                                    EntityArgument.getPlayer(context, PLAYER_ARG)))))));
  }

  public static int resetDialogLimit(
      CommandSourceStack context,
      EasyNPC<?> easyNPC,
      Pair<UUID, String> dialogPair,
      ServerPlayer serverPlayer) {
    if (dialogPair.getFirst() != null) {
      return resetDialogLimit(context, easyNPC, dialogPair.getFirst(), serverPlayer);
    } else if (dialogPair.getSecond() != null) {
      return resetDialogLimit(context, easyNPC, dialogPair.getSecond(), serverPlayer);
    }
    return sendFailureMessage(context, "Invalid dialog UUID or label!");
  }

  public static int resetDialogLimit(
      CommandSourceStack context,
      EasyNPC<?> easyNPC,
      String dialogLabel,
      ServerPlayer serverPlayer) {
    if (easyNPC.getEasyNPCDialogData() == null) {
       return sendFailureMessageNoDialogData(context, easyNPC);
    }
    UUID dialogId = easyNPC.getEasyNPCDialogData().getDialogId(dialogLabel);
    if (dialogId == null) {
      return sendFailureMessage(
          context,
          "Found no Dialog with label "
              + dialogLabel
              + " for EasyNPC with UUID "
              + easyNPC.getEntityUUID()
              + "!");
    }
    return resetDialogLimit(context, easyNPC, dialogId, serverPlayer);
  }

  public static int resetDialogLimit(
      CommandSourceStack context,
      EasyNPC<?> easyNPC,
      UUID dialogUUID,
      ServerPlayer serverPlayer) {

    ActionExecutionTracker tracker = ActionExecutionTracker.get(serverPlayer.serverLevel());
    tracker.resetExecution(serverPlayer.getUUID(), dialogUUID);

    return sendSuccessMessage(
        context,
        "Reset execution limit for player "
            + serverPlayer.getName().getString()
            + " for dialog "
            + dialogUUID,
        ChatFormatting.GREEN);
  }
}
