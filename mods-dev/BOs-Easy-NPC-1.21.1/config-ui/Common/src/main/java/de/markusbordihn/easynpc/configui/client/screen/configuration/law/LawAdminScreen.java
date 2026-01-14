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

package de.markusbordihn.easynpc.configui.client.screen.configuration.law;

import de.markusbordihn.easynpc.client.screen.components.Checkbox;
import de.markusbordihn.easynpc.configui.Constants;
import de.markusbordihn.easynpc.data.crime.AIBehavior;
import de.markusbordihn.easynpc.data.crime.CrimeType;
import de.markusbordihn.easynpc.data.crime.GuardTier;
import de.markusbordihn.easynpc.data.crime.LawSystemConfig;
import de.markusbordihn.easynpc.data.crime.MerchantTemplate;
import de.markusbordihn.easynpc.data.crime.RegionRule;
import de.markusbordihn.easynpc.network.NetworkHandlerManager;
import de.markusbordihn.easynpc.network.message.AdminActionMessage;
import de.markusbordihn.easynpc.network.message.LawAdminDataMessage;
import de.markusbordihn.easynpc.network.message.LawAdminRequestMessage;
import de.markusbordihn.easynpc.network.message.LawConfigUpdateMessage;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Modern Admin GUI Screen for the Law Enforcement System.
 * Redesigned with a premium look inspired by modern quest logs and dialogue UIs.
 */
public class LawAdminScreen extends Screen {

  private static final Logger log = LogManager.getLogger(Constants.LOG_NAME);
  private static final String LOG_PREFIX = "[LawAdminScreen]";

  // Aesthetics constants
  private static final int ACCENT_COLOR = 0xFF00DDAA;
  private static final int TEXT_COLOR = 0xFFFFFFFF;
  private static final int BG_COLOR = 0xEE1a1a1a;
  
  // Dimensions
  private static final int SIDEBAR_WIDTH = 80;
  private static final int HEADER_HEIGHT = 40;
  private static final int CONTENT_PADDING = 15;

  private enum Tab {
    OVERVIEW("Overview", "📊"),
    REGIONS("Regions", "🗺️"),
    WANTED("Wanted", "⚖️"),
    GUARDS("Guards", "🛡️"),
    MERCHANTS("Merchants", "💰"),
    PLAYERS("Players", "👥"),
    SYSTEM("System", "⚙️");

    private final String label;
    private final String icon;
    Tab(String label, String icon) { 
      this.label = label;
      this.icon = icon;
    }
    public String getLabel() { return label; }
    public String getIcon() { return icon; }
  }

  private Tab currentTab = Tab.OVERVIEW;
  private LawSystemConfig serverConfig;
  private LawSystemConfig localConfig;
  private List<LawAdminDataMessage.PlayerSnapshot> playerSnapshots = new ArrayList<>();
  
  // Dynamic components
  private final List<Button> sidebarButtons = new ArrayList<>();
  private EditBox searchBox;

  // Selection states
  private int selectedRegionIndex = -1;
  private int selectedGuardTierIndex = -1;
  private int selectedMerchantIndex = -1;

  public LawAdminScreen() {
    super(Component.translatable("gui.easy_npc.law_admin.title"));
    this.localConfig = new LawSystemConfig();
    this.serverConfig = new LawSystemConfig();
  }

  public LawAdminScreen(LawAdminDataMessage message) {
    this();
    applyServerData(message);
  }

  public static void openFromServer(LawAdminDataMessage message) {
    Minecraft minecraft = Minecraft.getInstance();
    if (minecraft.screen instanceof LawAdminScreen screen) {
      screen.applyServerData(message);
      return;
    }
    if (message != null && message.openScreen()) {
      minecraft.setScreen(new LawAdminScreen(message));
    }
  }

  @Override
  protected void init() {
    super.init();
    
    // Initialize sidebar
    createSidebar();
    
    // Initialize header actions
    createHeaderActions();
    
    // Initialize tab content
    refreshTabContent();
  }

  private void createSidebar() {
    sidebarButtons.clear();
    int y = HEADER_HEIGHT + 10;
    for (Tab tab : Tab.values()) {
      final Tab t = tab;
      ModernButton btn = new ModernButton(10, y, SIDEBAR_WIDTH - 20, 24, 
          Component.literal(t.getIcon()), b -> switchTab(t)); // Using icon only for small sidebar
      btn.setLeftAligned(false);
      if (currentTab == t) btn.setSelection(true);
      sidebarButtons.add(btn);
      addRenderableWidget(btn);
      y += 28;
    }
  }

  private void createHeaderActions() {
    int x = width - 100 - 10;
    int y = (HEADER_HEIGHT - 20) / 2;
    
    addRenderableWidget(new ModernButton(x, y, 100, 20, Component.literal("Apply Changes"), b -> applyChanges()));
    addRenderableWidget(new ModernButton(x - 110, y, 100, 20, Component.literal("Revert"), b -> revertChanges()));
    addRenderableWidget(new ModernButton(x - 220, y, 100, 20, Component.literal("Save Profile"), b -> saveProfile()));
  }

  private void switchTab(Tab tab) {
    if (this.currentTab == tab) return;
    this.currentTab = tab;
    this.clearWidgets();
    init();
  }

  private void refreshTabContent() {
    switch (currentTab) {
      case OVERVIEW -> renderOverviewTab();
      case REGIONS -> renderRegionsTab();
      case WANTED -> renderWantedTab();
      case GUARDS -> renderGuardsTab();
      case MERCHANTS -> renderMerchantsTab();
      case PLAYERS -> renderPlayersTab();
      case SYSTEM -> renderSystemTab();
    }
  }

  // --- Content Renderers ---

  private void renderOverviewTab() {
    int startX = SIDEBAR_WIDTH + CONTENT_PADDING;
    int startY = HEADER_HEIGHT + CONTENT_PADDING;
    
    addRenderableWidget(new Checkbox(startX, startY + 20, Component.literal("Law System Enabled"), 
        localConfig.isSystemEnabled(), true, cb -> localConfig.setSystemEnabled(cb.selected())));
    
    int btnY = startY + 50;
    addRenderableWidget(new ModernButton(startX, btnY, 120, 20, Component.literal("Clear All Wanted"), b -> clearAllWanted()));
    addRenderableWidget(new ModernButton(startX + 130, btnY, 120, 20, Component.literal("Despawn All Guards"), b -> despawnAllGuards()));
    addRenderableWidget(new ModernButton(startX + 260, btnY, 140, 20, Component.literal("Despawn All Merchants"), b -> despawnAllMerchants()));
  }

  private void renderRegionsTab() {
    int startX = SIDEBAR_WIDTH + CONTENT_PADDING;
    int startY = HEADER_HEIGHT + CONTENT_PADDING;
    
    List<RegionRule> regions = localConfig.getRegions();
    if (selectedRegionIndex < 0 && !regions.isEmpty()) selectedRegionIndex = 0;
    
    int listX = startX;
    int listY = startY + 20;
    addRenderableWidget(new ModernButton(listX, listY, 100, 20, Component.literal("+ Add Region"), b -> addRegionFromPosition()));
    
    if (!regions.isEmpty()) {
      RegionRule region = regions.get(selectedRegionIndex);
      
      addRenderableWidget(new ModernButton(listX + 110, listY, 20, 20, Component.literal("<"), b -> {
        selectedRegionIndex = (selectedRegionIndex - 1 + regions.size()) % regions.size();
        refreshTabContent();
      }));
      addRenderableWidget(new ModernButton(listX + 135, listY, 20, 20, Component.literal(">"), b -> {
        selectedRegionIndex = (selectedRegionIndex + 1) % regions.size();
        refreshTabContent();
      }));
      
      int editY = listY + 30;
      createEditBox(startX, editY, 200, "Name", region.getName()).setResponder(region::setName);
      
      addRenderableWidget(new Checkbox(startX + 210, editY, Component.literal("Enabled"), 
          region.isEnabled(), true, cb -> region.setEnabled(cb.selected())));
      
      editY += 30;
      createEditBox(startX, editY, 60, "Radius", String.valueOf(region.getRadius()))
          .setResponder(v -> region.setRadius(parseInt(v, region.getRadius(), 1, 10000)));
      createEditBox(startX + 70, editY, 60, "Resp. R", String.valueOf(region.getResponseRadius()))
          .setResponder(v -> region.setResponseRadius(parseInt(v, region.getResponseRadius(), 1, 10000)));
      createEditBox(startX + 140, editY, 60, "Cap", String.valueOf(region.getGuardSpawnCap()))
          .setResponder(v -> region.setGuardSpawnCap(parseInt(v, region.getGuardSpawnCap(), 0, 100)));
      createEditBox(startX + 210, editY, 80, "Cooldown", String.valueOf(region.getCooldownTicks()))
          .setResponder(v -> region.setCooldownTicks(parseInt(v, region.getCooldownTicks(), 0, 1000000)));
      
      editY += 30;
      int cx = startX;
      int cy = editY + 15;
      for (CrimeType type : CrimeType.values()) {
        addRenderableWidget(new Checkbox(cx, cy, Component.literal(type.name()), region.isCrimeEnabled(type), true, cb -> {
          if (cb.selected()) region.getEnabledCrimes().add(type);
          else region.getEnabledCrimes().remove(type);
        }));
        cy += 20;
        if (cy > height - 50) { cy = editY + 15; cx += 100; }
      }
      
      addRenderableWidget(new ModernButton(width - 80, listY, 60, 20, Component.literal("Delete"), b -> {
        localConfig.removeRegion(region);
        selectedRegionIndex = -1;
        refreshTabContent();
      }));
    }
  }

  private void renderGuardsTab() {
    int startX = SIDEBAR_WIDTH + CONTENT_PADDING;
    int startY = HEADER_HEIGHT + CONTENT_PADDING;
    
    List<GuardTier> tiers = localConfig.getGuardTiers();
    if (selectedGuardTierIndex < 0 && !tiers.isEmpty()) selectedGuardTierIndex = 0;
    
    int listY = startY + 20;
    addRenderableWidget(new ModernButton(startX, listY, 100, 20, Component.literal("+ Add Tier"), b -> addGuardTier()));
    
    if (!tiers.isEmpty()) {
      GuardTier tier = tiers.get(selectedGuardTierIndex);
      
      addRenderableWidget(new ModernButton(startX + 110, listY, 20, 20, Component.literal("<"), b -> {
        selectedGuardTierIndex = (selectedGuardTierIndex - 1 + tiers.size()) % tiers.size();
        refreshTabContent();
      }));
      addRenderableWidget(new ModernButton(startX + 135, listY, 20, 20, Component.literal(">"), b -> {
        selectedGuardTierIndex = (selectedGuardTierIndex + 1) % tiers.size();
        refreshTabContent();
      }));
      
      int editY = listY + 30;
      createEditBox(startX, editY, 50, "Tier", String.valueOf(tier.getTier()))
          .setResponder(v -> tier.setTier(parseInt(v, tier.getTier(), 1, 100)));
      createEditBox(startX + 60, editY, 80, "Min Wanted", String.valueOf(tier.getMinWantedLevel()))
          .setResponder(v -> tier.setMinWantedLevel(parseInt(v, tier.getMinWantedLevel(), 1, 100)));
      createEditBox(startX + 150, editY, 60, "Squad", String.valueOf(tier.getSquadSize()))
          .setResponder(v -> tier.setSquadSize(parseInt(v, tier.getSquadSize(), 1, 50)));
      
      editY += 30;
      createEditBox(startX, editY, 60, "Health", String.valueOf(tier.getHealth()))
          .setResponder(v -> tier.setHealth(parseInt(v, tier.getHealth(), 1, 1000)));
      createEditBox(startX + 70, editY, 60, "Speed", String.valueOf(tier.getSpeed()))
          .setResponder(v -> tier.setSpeed(parseFloat(v, tier.getSpeed(), 0.1f, 2.0f)));
      createEditBox(startX + 140, editY, 60, "Damage", String.valueOf(tier.getAttackDamage()))
          .setResponder(v -> tier.setAttackDamage(parseFloat(v, tier.getAttackDamage(), 0f, 100f)));
      
      editY += 30;
      createEditBox(startX, editY, 80, "Spawn R", String.valueOf(tier.getSpawnRadius()))
          .setResponder(v -> tier.setSpawnRadius(parseInt(v, tier.getSpawnRadius(), 1, 200)));
      createEditBox(startX + 90, editY, 80, "Desp. D", String.valueOf(tier.getDespawnDistance()))
          .setResponder(v -> tier.setDespawnDistance(parseInt(v, tier.getDespawnDistance(), 10, 1000)));
      createEditBox(startX + 180, editY, 80, "Desp. T", String.valueOf(tier.getDespawnTime()))
          .setResponder(v -> tier.setDespawnTime(parseInt(v, tier.getDespawnTime(), 0, 1000000)));
      
      editY += 30;
      createEditBox(startX, editY, 150, "Template", tier.getTemplateName()).setResponder(tier::setTemplateName);
      
      editY += 30;
      addRenderableWidget(new Checkbox(startX, editY, Component.literal("Archer"), tier.isArcher(), true, cb -> tier.setArcher(cb.selected())));
      addRenderableWidget(new Checkbox(startX + 80, editY, Component.literal("Captain"), tier.isCaptain(), true, cb -> tier.setCaptain(cb.selected())));
      addRenderableWidget(new Checkbox(startX + 170, editY, Component.literal("Tracker"), tier.isTracker(), true, cb -> tier.setTracker(cb.selected())));
      
      addRenderableWidget(new ModernButton(width - 80, listY, 60, 20, Component.literal("Delete"), b -> {
        localConfig.getGuardTiers().remove(tier);
        selectedGuardTierIndex = -1;
        refreshTabContent();
      }));
    }
  }

  private void renderWantedTab() {
    int startX = SIDEBAR_WIDTH + CONTENT_PADDING;
    int startY = HEADER_HEIGHT + CONTENT_PADDING;
    
    int editY = startY + 20;
    createEditBox(startX, editY, 80, "Max Level", String.valueOf(localConfig.getMaxWantedLevel()))
        .setResponder(v -> localConfig.setMaxWantedLevel(parseInt(v, localConfig.getMaxWantedLevel(), 1, 100)));
    
    editY += 30;
    createEditBox(startX, editY, 80, "Peace Min", String.valueOf(localConfig.getPeaceValueMin()))
        .setResponder(v -> localConfig.setPeaceValueMin(parseInt(v, localConfig.getPeaceValueMin(), 0, 100)));
    createEditBox(startX + 90, editY, 80, "Peace Max", String.valueOf(localConfig.getPeaceValueMax()))
        .setResponder(v -> localConfig.setPeaceValueMax(parseInt(v, localConfig.getPeaceValueMax(), 0, 100)));
    createEditBox(startX + 180, editY, 80, "Regen Rate", String.valueOf(localConfig.getPeaceRegenRate()))
        .setResponder(v -> localConfig.setPeaceRegenRate(parseInt(v, localConfig.getPeaceRegenRate(), 1, 1000000)));
    
    editY += 30;
    createEditBox(startX, editY, 80, "Decay Rate", String.valueOf(localConfig.getWantedDecayRate()))
        .setResponder(v -> localConfig.setWantedDecayRate(parseInt(v, localConfig.getWantedDecayRate(), 1, 1000000)));
    createEditBox(startX + 90, editY, 80, "Decay Delay", String.valueOf(localConfig.getWantedDecayDelayTicks()))
        .setResponder(v -> localConfig.setWantedDecayDelayTicks(parseInt(v, localConfig.getWantedDecayDelayTicks(), 0, 1000000)));
    
    editY += 30;
    createEditBox(startX, editY, 80, "Multiplier", String.valueOf(localConfig.getCrimeRule().getRepeatOffenseMultiplier()))
        .setResponder(v -> localConfig.getCrimeRule().setRepeatOffenseMultiplier(parseFloat(v, localConfig.getCrimeRule().getRepeatOffenseMultiplier(), 1.0f, 10.0f)));
    createEditBox(startX + 90, editY, 80, "Window (T)", String.valueOf(localConfig.getCrimeRule().getRepeatWindowTicks()))
        .setResponder(v -> localConfig.getCrimeRule().setRepeatWindowTicks(parseInt(v, localConfig.getCrimeRule().getRepeatWindowTicks(), 0, 1000000)));

    editY += 45;
    addRenderableWidget(new Checkbox(startX, editY, Component.literal("Reset on Death"), localConfig.isResetOnDeath(), true, cb -> localConfig.setResetOnDeath(cb.selected())));
    addRenderableWidget(new Checkbox(startX + 110, editY, Component.literal("Reset on Jail"), localConfig.isResetOnJail(), true, cb -> localConfig.setResetOnJail(cb.selected())));
    addRenderableWidget(new Checkbox(startX + 220, editY, Component.literal("Reset on Bribe"), localConfig.isResetOnBribe(), true, cb -> localConfig.setResetOnBribe(cb.selected())));
    
    editY += 30;
    int cx = startX;
    int cy = editY + 15;
    for (CrimeType type : CrimeType.values()) {
      createEditBox(cx + 70, cy - 2, 40, "W", String.valueOf(localConfig.getCrimeRule().getWantedPenalty(type)))
          .setResponder(v -> localConfig.getCrimeRule().setWantedPenalty(type, parseInt(v, localConfig.getCrimeRule().getWantedPenalty(type), 0, 100)));
      createEditBox(cx + 115, cy - 2, 40, "P", String.valueOf(localConfig.getCrimeRule().getPeacePenalty(type)))
          .setResponder(v -> localConfig.getCrimeRule().setPeacePenalty(type, parseInt(v, localConfig.getCrimeRule().getPeacePenalty(type), 0, 100)));
      cy += 20;
      if (cy > height - 40) { cy = editY + 15; cx += 165; }
    }
  }

  private void renderMerchantsTab() {
    int startX = SIDEBAR_WIDTH + CONTENT_PADDING;
    int startY = HEADER_HEIGHT + CONTENT_PADDING;
    
    List<MerchantTemplate> templates = localConfig.getMerchantTemplates();
    if (selectedMerchantIndex < 0 && !templates.isEmpty()) selectedMerchantIndex = 0;
    
    int listY = startY + 20;
    addRenderableWidget(new ModernButton(startX, listY, 110, 20, Component.literal("+ Add Template"), b -> addMerchantTemplate()));
    
    if (!templates.isEmpty()) {
      MerchantTemplate template = templates.get(selectedMerchantIndex);
      
      addRenderableWidget(new ModernButton(startX + 120, listY, 20, 20, Component.literal("<"), b -> {
        selectedMerchantIndex = (selectedMerchantIndex - 1 + templates.size()) % templates.size();
        refreshTabContent();
      }));
      addRenderableWidget(new ModernButton(startX + 145, listY, 20, 20, Component.literal(">"), b -> {
        selectedMerchantIndex = (selectedMerchantIndex + 1) % templates.size();
        refreshTabContent();
      }));
      
      int editY = listY + 30;
      createEditBox(startX, editY, 150, "Name", template.getName()).setResponder(template::setName);
      createEditBox(startX + 160, editY, 150, "NPC Template", template.getNpcTemplateName()).setResponder(template::setNpcTemplateName);
      
      editY += 30;
      createEditBox(startX, editY, 60, "Min Grp", String.valueOf(template.getMinGroupSize())).setResponder(v -> template.setMinGroupSize(parseInt(v, template.getMinGroupSize(), 1, 20)));
      createEditBox(startX + 70, editY, 60, "Max Grp", String.valueOf(template.getMaxGroupSize())).setResponder(v -> template.setMaxGroupSize(parseInt(v, template.getMaxGroupSize(), 1, 20)));
      createEditBox(startX + 140, editY, 80, "Interval", String.valueOf(template.getSpawnIntervalTicks())).setResponder(v -> template.setSpawnIntervalTicks(parseInt(v, template.getSpawnIntervalTicks(), 600, 1000000)));
      createEditBox(startX + 230, editY, 80, "Cooldown", String.valueOf(template.getRespawnCooldown())).setResponder(v -> template.setRespawnCooldown(parseInt(v, template.getRespawnCooldown(), 0, 1000000)));
      
      editY += 30;
      createEditBox(startX, editY, 60, "Drop Min", String.valueOf(template.getCurrencyDropMin())).setResponder(v -> template.setCurrencyDropMin(parseInt(v, template.getCurrencyDropMin(), 0, 10000)));
      createEditBox(startX + 70, editY, 60, "Drop Max", String.valueOf(template.getCurrencyDropMax())).setResponder(v -> template.setCurrencyDropMax(parseInt(v, template.getCurrencyDropMax(), 0, 10000)));
      
      addRenderableWidget(new ModernButton(startX + 140, editY, 120, 20, Component.literal("Behavior: " + template.getBehavior()), b -> {
        AIBehavior next = AIBehavior.values()[(template.getBehavior().ordinal() + 1) % AIBehavior.values().length];
        template.setBehavior(next);
        b.setMessage(Component.literal("Behavior: " + next));
      }));
      
      addRenderableWidget(new ModernButton(width - 80, listY, 60, 20, Component.literal("Delete"), b -> {
        localConfig.getMerchantTemplates().remove(template);
        selectedMerchantIndex = -1;
        refreshTabContent();
      }));
    }
  }

  private void renderPlayersTab() {
    int startX = SIDEBAR_WIDTH + CONTENT_PADDING;
    int startY = HEADER_HEIGHT + CONTENT_PADDING;
    
    searchBox = createEditBox(startX, startY + 20, 200, "Search Player...", "");
    
    int py = startY + 50;
    for (LawAdminDataMessage.PlayerSnapshot player : playerSnapshots) {
      if (!searchBox.getValue().isEmpty() && !player.name().toLowerCase().contains(searchBox.getValue().toLowerCase())) continue;
      
      py += 22;
      if (py > height - 40) break;
    }
  }

  private void renderSystemTab() {
    int startX = SIDEBAR_WIDTH + CONTENT_PADDING;
    int startY = HEADER_HEIGHT + CONTENT_PADDING;
    
    createEditBox(startX, startY + 20, 200, "Profile Name", localConfig.getProfileName()).setResponder(localConfig::setProfileName);
    
    int editY = startY + 75;
    addRenderableWidget(new ModernButton(startX, editY, 80, 20, Component.literal("Hardcore"), b -> { localConfig.applyPreset("hardcore"); refreshTabContent(); }));
    addRenderableWidget(new ModernButton(startX + 90, editY, 80, 20, Component.literal("Casual"), b -> { localConfig.applyPreset("casual"); refreshTabContent(); }));
    addRenderableWidget(new ModernButton(startX + 180, editY, 80, 20, Component.literal("RP"), b -> { localConfig.applyPreset("rp"); refreshTabContent(); }));
    
    editY += 30;
    addRenderableWidget(new ModernButton(startX, editY, 150, 20, Component.literal("Reload Config from File"), b -> reloadConfig()));
  }

  // --- UI Helpers ---

  private EditBox createEditBox(int x, int y, int width, String hint, String value) {
    EditBox box = new EditBox(this.font, x, y, width, 18, Component.literal(hint));
    box.setValue(value);
    box.setHint(Component.literal(hint));
    addRenderableWidget(box);
    return box;
  }

  @Override
  public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTicks) {
    guiGraphics.fill(0, 0, width, height, 0xAA000000);
    guiGraphics.fill(SIDEBAR_WIDTH, 0, width, height, BG_COLOR);
    guiGraphics.fill(SIDEBAR_WIDTH, 0, width, HEADER_HEIGHT, 0xEE111111);
    guiGraphics.fill(SIDEBAR_WIDTH, HEADER_HEIGHT, width, HEADER_HEIGHT + 2, ACCENT_COLOR);
    guiGraphics.fill(0, 0, SIDEBAR_WIDTH, height, 0xEE0a0a0a);
    guiGraphics.fill(SIDEBAR_WIDTH - 2, 0, SIDEBAR_WIDTH, height, ACCENT_COLOR);

    guiGraphics.drawString(font, title, SIDEBAR_WIDTH + CONTENT_PADDING, (HEADER_HEIGHT - 8) / 2, ACCENT_COLOR, false);

    // Render tab labels and descriptive headers
    int startX = SIDEBAR_WIDTH + CONTENT_PADDING;
    int startY = HEADER_HEIGHT + CONTENT_PADDING;
    switch (currentTab) {
      case OVERVIEW -> guiGraphics.drawString(font, "System Overview", startX, startY, ACCENT_COLOR, false);
      case REGIONS -> guiGraphics.drawString(font, "Region Management", startX, startY, ACCENT_COLOR, false);
      case GUARDS -> guiGraphics.drawString(font, "Guard Tiers & Stats", startX, startY, ACCENT_COLOR, false);
      case WANTED -> guiGraphics.drawString(font, "Wanted System Settings", startX, startY, ACCENT_COLOR, false);
      case MERCHANTS -> guiGraphics.drawString(font, "Merchant Configuration", startX, startY, ACCENT_COLOR, false);
      case PLAYERS -> guiGraphics.drawString(font, "Active Player Management", startX, startY, ACCENT_COLOR, false);
      case SYSTEM -> guiGraphics.drawString(font, "System & Profile Settings", startX, startY, ACCENT_COLOR, false);
    }

    if (currentTab == Tab.PLAYERS) {
       int py = startY + 50;
       for (LawAdminDataMessage.PlayerSnapshot player : playerSnapshots) {
         if (!searchBox.getValue().isEmpty() && !player.name().toLowerCase().contains(searchBox.getValue().toLowerCase())) continue;
         guiGraphics.drawString(font, player.name(), startX, py + 5, 0xFFFFFFFF, false);
         int wanted = player.state().getWantedLevel();
         guiGraphics.drawString(font, "Wanted: " + wanted, startX + 120, py + 5, wanted > 0 ? 0xFFFF5555 : 0xFF55FF55, false);
         py += 22;
         if (py > height - 40) break;
       }
    }

    super.render(guiGraphics, mouseX, mouseY, partialTicks);
  }

  // --- Logic & Network ---

  private void applyServerData(LawAdminDataMessage message) {
    if (message == null) return;
    this.serverConfig = message.config();
    this.localConfig = new LawSystemConfig(serverConfig.createTag());
    this.playerSnapshots = message.players();
    refreshTabContent();
  }

  private void applyChanges() {
    NetworkHandlerManager.sendMessageToServer(new LawConfigUpdateMessage(localConfig, false));
  }

  private void revertChanges() {
    this.localConfig = new LawSystemConfig(serverConfig.createTag());
    refreshTabContent();
  }

  private void saveProfile() {
    NetworkHandlerManager.sendMessageToServer(new LawConfigUpdateMessage(localConfig, true));
  }

  private void clearAllWanted() {
    NetworkHandlerManager.sendMessageToServer(AdminActionMessage.clearAllWanted());
  }

  private void despawnAllGuards() {
    NetworkHandlerManager.sendMessageToServer(AdminActionMessage.despawnAllGuards());
  }

  private void despawnAllMerchants() {
    NetworkHandlerManager.sendMessageToServer(AdminActionMessage.despawnAllMerchants());
  }

  private void reloadConfig() {
    NetworkHandlerManager.sendMessageToServer(LawAdminRequestMessage.refresh());
  }

  private void addRegionFromPosition() {
    RegionRule region = new RegionRule("New Region", minecraft.player.blockPosition(), 50);
    localConfig.addRegion(region);
    selectedRegionIndex = localConfig.getRegions().size() - 1;
    refreshTabContent();
  }

  private void addGuardTier() {
    int nextTier = localConfig.getGuardTiers().size() + 1;
    GuardTier tier = new GuardTier(nextTier, nextTier);
    localConfig.getGuardTiers().add(tier);
    selectedGuardTierIndex = localConfig.getGuardTiers().size() - 1;
    refreshTabContent();
  }

  private void addMerchantTemplate() {
    MerchantTemplate template = new MerchantTemplate();
    template.setName("New Merchant");
    localConfig.addMerchantTemplate(template);
    selectedMerchantIndex = localConfig.getMerchantTemplates().size() - 1;
    refreshTabContent();
  }

  private int parseInt(String value, int defaultValue, int min, int max) {
    try {
      return Mth.clamp(Integer.parseInt(value), min, max);
    } catch (NumberFormatException e) {
      return defaultValue;
    }
  }

  private float parseFloat(String value, float defaultValue, float min, float max) {
    try {
      return Mth.clamp(Float.parseFloat(value), min, max);
    } catch (NumberFormatException e) {
      return defaultValue;
    }
  }

  private class ModernButton extends Button {
    private boolean selected = false;
    private boolean leftAligned = false;

    public ModernButton(int x, int y, int width, int height, Component label, OnPress onPress) {
      super(x, y, width, height, label, onPress, DEFAULT_NARRATION);
    }

    public void setSelection(boolean selected) { this.selected = selected; }
    public void setLeftAligned(boolean leftAligned) { this.leftAligned = leftAligned; }

    @Override
    public void renderWidget(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTicks) {
      boolean hovered = isHoveredOrFocused();
      int bgColor = selected ? ACCENT_COLOR : (hovered ? 0xFF333333 : 0x44000000);
      int textColor = selected ? 0xFF111111 : (hovered ? ACCENT_COLOR : 0xFFFFFFFF);
      
      guiGraphics.fill(getX(), getY(), getX() + width, getY() + height, bgColor);
      guiGraphics.renderOutline(getX(), getY(), width, height, hovered ? ACCENT_COLOR : 0xFF444444);
      
      if (hovered || selected) {
        guiGraphics.fill(getX(), getY(), getX() + 2, getY() + height, ACCENT_COLOR);
        if (hovered) {
          guiGraphics.fill(getX() + 2, getY(), getX() + 3, getY() + height, 0x6600DDAA);
        }
      }
      
      if (leftAligned) {
        guiGraphics.drawString(font, getMessage(), getX() + 6, getY() + (height - 8) / 2, textColor, false);
      } else {
        guiGraphics.drawCenteredString(font, getMessage(), getX() + width / 2, getY() + (height - 8) / 2, textColor);
      }
    }
  }
}
