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
import de.markusbordihn.easynpc.llm.LLMDialogResponseManager;
import de.markusbordihn.easynpc.network.message.NetworkMessageRecord;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Network message for sending LLM chat responses from server to client.
 */
public record LLMChatResponseMessage(UUID npcUuid, String npcName, String response)
    implements NetworkMessageRecord {

  public static final ResourceLocation MESSAGE_ID =
      ResourceLocation.fromNamespaceAndPath(Constants.MOD_ID, "llm_chat_response");
  public static final CustomPacketPayload.Type<LLMChatResponseMessage> PAYLOAD_TYPE =
      new Type<>(MESSAGE_ID);
  public static final StreamCodec<RegistryFriendlyByteBuf, LLMChatResponseMessage> STREAM_CODEC =
      StreamCodec.of((buffer, message) -> message.write(buffer), LLMChatResponseMessage::create);

  private static final int MAX_NAME_LENGTH = 64;
  private static final int MAX_RESPONSE_LENGTH = 2048;

  public static LLMChatResponseMessage create(final FriendlyByteBuf buffer) {
    return new LLMChatResponseMessage(
        buffer.readUUID(),
        buffer.readUtf(MAX_NAME_LENGTH),
        buffer.readUtf(MAX_RESPONSE_LENGTH));
  }

  @Override
  public void write(final FriendlyByteBuf buffer) {
    buffer.writeUUID(this.npcUuid);
    buffer.writeUtf(this.npcName, MAX_NAME_LENGTH);
    buffer.writeUtf(this.response, MAX_RESPONSE_LENGTH);
  }

  @Override
  public ResourceLocation id() {
    return MESSAGE_ID;
  }

  @Override
  public Type<? extends CustomPacketPayload> type() {
    return PAYLOAD_TYPE;
  }

  @Override
  public void handleClient() {
    if (this.response == null || this.response.isEmpty()) {
      log.debug("Empty response received from NPC {}", this.npcUuid);
      return;
    }

    // Store the response in the static map for the dialog screen to pick up
    LLMDialogResponseManager.setResponse(this.npcUuid, this.response);
    
    log.debug("Received LLM response for NPC {}: {}", this.npcName, 
        this.response.length() > 50 ? this.response.substring(0, 50) + "..." : this.response);
  }
}
