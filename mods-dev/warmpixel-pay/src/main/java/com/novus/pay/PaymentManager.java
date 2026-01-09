package com.novus.pay;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.HoverEvent;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.concurrent.CompletableFuture;

public class PaymentManager {
    private static final HttpClient CLIENT = HttpClient.newHttpClient();
    private static final Gson GSON = new Gson();

    public static void init() {
        // Init logic if needed
    }

    public static void createOrder(ServerPlayer player, double cnyAmount, int expectedCoins) {
        String url = PaymentConfig.DATA.internalApiUrl + "/internal/create_order";
        
        JsonObject requestBody = new JsonObject();
        requestBody.addProperty("player_name", player.getName().getString());
        requestBody.addProperty("amount_cny", cnyAmount);
        requestBody.addProperty("game_coins", expectedCoins);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Content-Type", "application/json")
                .header("x-api-key", PaymentConfig.DATA.apiKey)
                .POST(HttpRequest.BodyPublishers.ofString(GSON.toJson(requestBody)))
                .build();

        CompletableFuture.runAsync(() -> {
            try {
                HttpResponse<String> response = CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() == 200) {
                    String responseBody = response.body();
                    NovusPayMod.LOGGER.info("Payment Service Response: " + responseBody);
                    JsonObject json = GSON.fromJson(responseBody, JsonObject.class);
                    String qrUrl = json.get("checkout_url").getAsString();
                    String orderId = json.get("order_id").getAsString();
                    
                    player.getServer().execute(() -> {
                        sendPaymentLink(player, qrUrl, orderId, expectedCoins);
                    });
                    
                    // Start polling for status (simplified for prototype)
                    startPolling(player, orderId, expectedCoins);
                } else {
                    player.sendSystemMessage(Component.literal("Error creating order: " + response.statusCode()).withStyle(ChatFormatting.RED));
                }
            } catch (Exception e) {
                NovusPayMod.LOGGER.error("Failed to create order", e);
                player.sendSystemMessage(Component.literal("Internal payment error.").withStyle(ChatFormatting.RED));
            }
        });
    }

    private static void sendPaymentLink(ServerPlayer player, String qrUrl, String orderId, int coins) {
        player.sendSystemMessage(Component.nullToEmpty(""));
        player.sendSystemMessage(Component.literal("=======================================").withStyle(ChatFormatting.GOLD));
        player.sendSystemMessage(Component.literal(" Payment Order Created").withStyle(ChatFormatting.YELLOW, ChatFormatting.BOLD));
        player.sendSystemMessage(Component.literal(" Coins: " + coins).withStyle(ChatFormatting.WHITE));
        
        Component link = Component.literal(" [CLICK HERE TO PAY (Open Web)] ")
                .withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD, ChatFormatting.UNDERLINE)
                .withStyle(style -> style
                        .withClickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL, qrUrl))
                        .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal("Opens in Browser: " + qrUrl))));
                        
        player.sendSystemMessage(link);
        player.sendSystemMessage(Component.literal("=======================================").withStyle(ChatFormatting.GOLD));
    }

    private static void startPolling(ServerPlayer player, String orderId, int coins) {
        // Simple polling mechanism (runs in background)
        // In production, use WebSockets or more robust scheduling
        new Thread(() -> {
            int attempts = 0;
            while (attempts < 60) { // 2 minutes timeout
                try {
                    Thread.sleep(2000);
                    attempts++;
                    
                    String url = PaymentConfig.DATA.internalApiUrl + "/internal/check_status/" + orderId;
                    HttpRequest request = HttpRequest.newBuilder()
                            .uri(URI.create(url))
                            .header("x-api-key", PaymentConfig.DATA.apiKey)
                            .GET()
                            .build();
                            
                    HttpResponse<String> response = CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
                    if (response.statusCode() == 200) {
                        JsonObject json = GSON.fromJson(response.body(), JsonObject.class);
                        String status = json.get("status").getAsString();
                        
                        if ("PAID".equals(status)) {
                            // Payment confirmed!
                            player.getServer().execute(() -> completeOrder(player, orderId, coins));
                            break;
                        }
                    }
                } catch (Exception e) {
                    break;
                }
            }
        }).start();
    }
    
    private static void completeOrder(ServerPlayer player, String orderId, int coins) {
         // Give items
         player.getServer().getCommands().performPrefixedCommand(player.createCommandSourceStack().withSuppressedOutput().withPermission(4), 
                 "give " + player.getName().getString() + " novus_items:gold_novus_coin " + coins);
                 
         player.sendSystemMessage(Component.literal("Payment Successful! Received " + coins + " coins.").withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD));
         
         // Notify backend to mark as COMPLETED
         updateBackendStatus(orderId);
    }
    
    private static void updateBackendStatus(String orderId) {
         String url = PaymentConfig.DATA.internalApiUrl + "/internal/complete_order/" + orderId;
         HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Content-Type", "application/json")
                .header("x-api-key", PaymentConfig.DATA.apiKey)
                .POST(HttpRequest.BodyPublishers.noBody())
                .build();
         CLIENT.sendAsync(request, HttpResponse.BodyHandlers.discarding());
    }
}
