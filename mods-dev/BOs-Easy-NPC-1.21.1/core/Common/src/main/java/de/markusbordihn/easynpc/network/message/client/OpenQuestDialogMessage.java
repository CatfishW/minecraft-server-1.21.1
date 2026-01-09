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

package de.markusbordihn.easynpc.network.message.client;

import de.markusbordihn.easynpc.Constants;
import de.markusbordihn.easynpc.data.quest.QuestDataEntry;
import de.markusbordihn.easynpc.network.message.NetworkMessageRecord;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record OpenQuestDialogMessage(QuestDataEntry quest) implements NetworkMessageRecord {

  public static final ResourceLocation MESSAGE_ID =
      ResourceLocation.fromNamespaceAndPath(Constants.MOD_ID, "open_quest_dialog");
  public static final CustomPacketPayload.Type<OpenQuestDialogMessage> PAYLOAD_TYPE =
      new Type<>(MESSAGE_ID);
  public static final StreamCodec<RegistryFriendlyByteBuf, OpenQuestDialogMessage> STREAM_CODEC =
      StreamCodec.of((buffer, message) -> message.write(buffer), OpenQuestDialogMessage::create);

  public static OpenQuestDialogMessage create(final FriendlyByteBuf buffer) {
    CompoundTag tag = buffer.readNbt();
    QuestDataEntry quest = new QuestDataEntry(tag);
    return new OpenQuestDialogMessage(quest);
  }

  @Override
  public void write(FriendlyByteBuf buffer) {
    buffer.writeNbt(quest.save(new CompoundTag()));
  }

  @Override
  public Type<OpenQuestDialogMessage> type() {
    return PAYLOAD_TYPE;
  }

  @Override
  public ResourceLocation id() {
    return MESSAGE_ID;
  }

  @Override
  public void handleClient() {
    log.debug("Received open quest dialog request for quest {}", quest.getId());
    OpenQuestDialogHandler.onOpenQuestDialog(quest);
  }
  
  public static class OpenQuestDialogHandler {
    private static java.util.function.Consumer<QuestDataEntry> handler = null;
    
    public static void setHandler(java.util.function.Consumer<QuestDataEntry> h) {
      handler = h;
    }
    
    public static void onOpenQuestDialog(QuestDataEntry quest) {
      if (handler != null) {
        handler.accept(quest);
      }
    }
  }
}
