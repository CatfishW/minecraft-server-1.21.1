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

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import de.markusbordihn.easynpc.client.screen.components.Checkbox;
import de.markusbordihn.easynpc.configui.Constants;
import de.markusbordihn.easynpc.data.crime.AIBehavior;
import de.markusbordihn.easynpc.data.crime.CrimeType;
import de.markusbordihn.easynpc.data.crime.GuardTier;
import de.markusbordihn.easynpc.data.crime.LawSystemConfig;
import de.markusbordihn.easynpc.data.crime.MerchantTemplate;
import de.markusbordihn.easynpc.data.crime.PlayerLawState;
import de.markusbordihn.easynpc.data.crime.RegionMode;
import de.markusbordihn.easynpc.data.crime.RegionRule;
import de.markusbordihn.easynpc.network.NetworkHandlerManager;
import de.markusbordihn.easynpc.network.message.AdminActionMessage;
import de.markusbordihn.easynpc.network.message.LawAdminDataMessage;
import de.markusbordihn.easynpc.network.message.LawAdminRequestMessage;
import de.markusbordihn.easynpc.network.message.LawConfigUpdateMessage;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.TagParser;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.core.BlockPos;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Main Admin GUI Screen for the Law Enforcement System.
 * Provides a tabbed interface for system configuration.
 */
public class LawAdminScreen extends Screen {

  private static final Logger log = LogManager.getLogger(Constants.LOG_NAME);
  private static final String LOG_PREFIX = "[LawAdminScreen]";
  private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
  private static final String CLIPBOARD_KIND_KEY = "kind";
  private static final String CLIPBOARD_SNBT_KEY = "snbt";

  // Dimensions
  private static final int GUI_WIDTH = 400;
  private static final int GUI_HEIGHT = 280;
  private static final int TAB_HEIGHT = 24;
  private static final int TAB_WIDTH = 56;
  private static final int CONTENT_PADDING = 10;
  private static final int BUTTON_HEIGHT = 20;
  private static final int BUTTON_WIDTH = 80;

  // Tabs
  private enum Tab {
    OVERVIEW("Overview"),
    REGIONS("Regions"),
    WANTED("Wanted/Peace"),
    MERCHANTS("Merchants"),
    GUARDS("Guards"),
    PLAYERS("Players"),
    PROFILES("Profiles");

    private final String label;
    Tab(String label) { this.label = label; }
    public String getLabel() { return label; }
  }

  private Tab currentTab = Tab.OVERVIEW;
  private List<Button> tabButtons = new ArrayList<>();
  
  // Common components
  private Button applyButton;
  private Button revertButton;
  private Button saveProfileButton;
  private Button loadProfileButton;
  private EditBox searchBox;
  
  // Tab-specific components
  private Checkbox systemEnabledCheckbox;
  private Checkbox regionEnabledCheckbox;
  private Checkbox guardArcherCheckbox;
  private Checkbox guardCaptainCheckbox;
  private Checkbox guardTrackerCheckbox;
  private Checkbox resetOnDeathCheckbox;
  private Checkbox resetOnJailCheckbox;
  private Checkbox resetOnBribeCheckbox;
  private EditBox profileNameBox;
  private EditBox wantedLevelInput;
  private EditBox peaceValueInput;
  private EditBox repeatMultiplierInput;
  private EditBox repeatWindowInput;
  private EditBox merchantGroupCountInput;
  private final Map<CrimeType, Checkbox> crimeCheckboxes = new EnumMap<>(CrimeType.class);

  private LawSystemConfig serverConfig;
  private LawSystemConfig localConfig;
  private List<LawAdminDataMessage.PlayerSnapshot> playerSnapshots = new ArrayList<>();
  private int merchantCount;
  private int guardCount;
  private int selectedRegionIndex = 0;
  private int selectedMerchantIndex = 0;
  private int selectedGuardIndex = 0;

  // Screen position
  private int leftPos;
  private int topPos;
  private int contentLeft;
  private int contentTop;
  private int contentWidth;
  private int contentHeight;

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
    if (this.localConfig == null) {
      this.localConfig = new LawSystemConfig();
    }
    if (this.serverConfig == null) {
      this.serverConfig = new LawSystemConfig();
    }

    // Calculate positions
    this.leftPos = (this.width - GUI_WIDTH) / 2;
    this.topPos = (this.height - GUI_HEIGHT) / 2;
    this.contentLeft = leftPos + CONTENT_PADDING;
    this.contentTop = topPos + TAB_HEIGHT + CONTENT_PADDING;
    this.contentWidth = GUI_WIDTH - CONTENT_PADDING * 2;
    this.contentHeight = GUI_HEIGHT - TAB_HEIGHT - CONTENT_PADDING * 3 - BUTTON_HEIGHT;

    // Create tab buttons
    createTabButtons();

    // Create bottom action buttons
    createActionButtons();
    createQuickActions();

    // Initialize current tab content
    initTabContent();

    log.info("{} Initialized", LOG_PREFIX);
  }

  private void createTabButtons() {
    tabButtons.clear();
    int tabX = leftPos;
    
    for (Tab tab : Tab.values()) {
      final Tab currentTabRef = tab;
      Button tabButton = Button.builder(
          Component.literal(tab.getLabel()),
          button -> switchTab(currentTabRef))
          .pos(tabX, topPos)
          .size(TAB_WIDTH, TAB_HEIGHT)
          .build();
      tabButtons.add(tabButton);
      addRenderableWidget(tabButton);
      tabX += TAB_WIDTH + 2;
    }
  }

  private void createActionButtons() {
    int buttonY = topPos + GUI_HEIGHT - BUTTON_HEIGHT - CONTENT_PADDING;
    int buttonX = leftPos + CONTENT_PADDING;
    int spacing = BUTTON_WIDTH + 10;

    applyButton = Button.builder(
        Component.literal("Apply"),
        button -> applyChanges())
        .pos(buttonX, buttonY)
        .size(BUTTON_WIDTH, BUTTON_HEIGHT)
        .build();
    addRenderableWidget(applyButton);

    revertButton = Button.builder(
        Component.literal("Revert"),
        button -> revertChanges())
        .pos(buttonX + spacing, buttonY)
        .size(BUTTON_WIDTH, BUTTON_HEIGHT)
        .build();
    addRenderableWidget(revertButton);

    saveProfileButton = Button.builder(
        Component.literal("Save Profile"),
        button -> saveProfile())
        .pos(buttonX + spacing * 2, buttonY)
        .size(BUTTON_WIDTH, BUTTON_HEIGHT)
        .build();
    addRenderableWidget(saveProfileButton);

    loadProfileButton = Button.builder(
        Component.literal("Load Profile"),
        button -> loadProfile())
        .pos(buttonX + spacing * 3, buttonY)
        .size(BUTTON_WIDTH, BUTTON_HEIGHT)
        .build();
    addRenderableWidget(loadProfileButton);
  }

  private void createQuickActions() {
    int buttonY = topPos + GUI_HEIGHT - BUTTON_HEIGHT * 2 - CONTENT_PADDING - 4;
    int buttonX = leftPos + CONTENT_PADDING;
    int spacing = 90;

    Button spawnMerchantBtn = Button.builder(
        Component.literal("Spawn Merchants"),
        button -> spawnMerchantGroup())
        .pos(buttonX, buttonY)
        .size(100, BUTTON_HEIGHT)
        .build();
    addRenderableWidget(spawnMerchantBtn);

    Button spawnGuardBtn = Button.builder(
        Component.literal("Spawn Guards"),
        button -> spawnGuardPatrol())
        .pos(buttonX + spacing + 20, buttonY)
        .size(100, BUTTON_HEIGHT)
        .build();
    addRenderableWidget(spawnGuardBtn);

    Button clearWantedBtn = Button.builder(
        Component.literal("Clear Wanted"),
        button -> clearAllWanted())
        .pos(buttonX + (spacing + 20) * 2, buttonY)
        .size(90, BUTTON_HEIGHT)
        .build();
    addRenderableWidget(clearWantedBtn);

    Button reloadBtn = Button.builder(
        Component.literal("Reload"),
        button -> reloadConfig())
        .pos(buttonX + (spacing + 20) * 3, buttonY)
        .size(70, BUTTON_HEIGHT)
        .build();
    addRenderableWidget(reloadBtn);
  }

  private void switchTab(Tab newTab) {
    if (currentTab == newTab) return;
    currentTab = newTab;
    this.clearWidgets();
    createTabButtons();
    createActionButtons();
    createQuickActions();
    initTabContent();
    
    log.debug("{} Switched to tab: {}", LOG_PREFIX, newTab);
  }

  private void clearTabContent() {
    this.clearWidgets();
  }

  private void initTabContent() {
    switch (currentTab) {
      case OVERVIEW -> initOverviewTab();
      case REGIONS -> initRegionsTab();
      case WANTED -> initWantedTab();
      case MERCHANTS -> initMerchantsTab();
      case GUARDS -> initGuardsTab();
      case PLAYERS -> initPlayersTab();
      case PROFILES -> initProfilesTab();
    }
  }

  private void initOverviewTab() {
    int y = contentTop;
    int spacing = 25;

    // System enabled checkbox
    systemEnabledCheckbox =
        new Checkbox(
            contentLeft, y, Component.literal("System Enabled"), localConfig.isSystemEnabled(), true);
    addRenderableWidget(systemEnabledCheckbox);
    y += spacing;

    // Quick action buttons
    y += spacing;
    Button clearWantedBtn = Button.builder(
        Component.literal("Clear All Wanted"),
        button -> clearAllWanted())
        .pos(contentLeft, y)
        .size(120, BUTTON_HEIGHT)
        .build();
    addRenderableWidget(clearWantedBtn);

    Button despawnGuardsBtn = Button.builder(
        Component.literal("Despawn Guards"),
        button -> despawnAllGuards())
        .pos(contentLeft + 130, y)
        .size(120, BUTTON_HEIGHT)
        .build();
    addRenderableWidget(despawnGuardsBtn);

    y += spacing;
    Button despawnMerchantsBtn = Button.builder(
        Component.literal("Despawn Merchants"),
        button -> despawnAllMerchants())
        .pos(contentLeft, y)
        .size(120, BUTTON_HEIGHT)
        .build();
    addRenderableWidget(despawnMerchantsBtn);

    Button testSimulationBtn = Button.builder(
        Component.literal("Test 10s"),
        button -> runTestSimulation(10))
        .pos(contentLeft + 130, y)
        .size(90, BUTTON_HEIGHT)
        .build();
    addRenderableWidget(testSimulationBtn);

    Button testSimulation60Btn = Button.builder(
        Component.literal("Test 60s"),
        button -> runTestSimulation(60))
        .pos(contentLeft + 230, y)
        .size(90, BUTTON_HEIGHT)
        .build();
    addRenderableWidget(testSimulation60Btn);
  }

  private void initRegionsTab() {
    int y = contentTop;

    // Add region button
    Button addRegionBtn = Button.builder(
        Component.literal("Add Region from Position"),
        button -> addRegionFromPosition())
        .pos(contentLeft, y)
        .size(160, BUTTON_HEIGHT)
        .build();
    addRenderableWidget(addRegionBtn);

    Button prevBtn = Button.builder(Component.literal("<"), button -> changeRegion(-1))
        .pos(contentLeft + 170, y)
        .size(20, BUTTON_HEIGHT)
        .build();
    addRenderableWidget(prevBtn);

    Button nextBtn = Button.builder(Component.literal(">"), button -> changeRegion(1))
        .pos(contentLeft + 195, y)
        .size(20, BUTTON_HEIGHT)
        .build();
    addRenderableWidget(nextBtn);

    Button exportBtn = Button.builder(Component.literal("Export"), button -> exportRegionToClipboard())
        .pos(contentLeft + 225, y)
        .size(60, BUTTON_HEIGHT)
        .build();
    addRenderableWidget(exportBtn);

    Button importBtn = Button.builder(Component.literal("Import"), button -> importRegionFromClipboard())
        .pos(contentLeft + 290, y)
        .size(60, BUTTON_HEIGHT)
        .build();
    addRenderableWidget(importBtn);

    y += BUTTON_HEIGHT + 6;

    RegionRule region = getSelectedRegion();
    if (region == null) {
      return;
    }

    EditBox nameBox = new EditBox(this.font, contentLeft, y, 160, 18, Component.literal("Region Name"));
    nameBox.setValue(region.getName());
    nameBox.setResponder(region::setName);
    addRenderableWidget(nameBox);

    Button modeBtn = Button.builder(
        Component.literal("Mode: " + region.getMode().name()),
        button -> {
          RegionMode[] modes = RegionMode.values();
          int nextIndex = (region.getMode().ordinal() + 1) % modes.length;
          region.setMode(modes[nextIndex]);
          button.setMessage(Component.literal("Mode: " + region.getMode().name()));
        })
        .pos(contentLeft + 170, y)
        .size(120, BUTTON_HEIGHT)
        .build();
    addRenderableWidget(modeBtn);

    y += 24;

    regionEnabledCheckbox =
        new Checkbox(contentLeft, y, Component.literal("Enabled"), region.isEnabled(), true);
    addRenderableWidget(regionEnabledCheckbox);

    y += 24;

    EditBox radiusBox = new EditBox(this.font, contentLeft, y, 60, 18, Component.literal("Radius"));
    radiusBox.setValue(Integer.toString(region.getRadius()));
    radiusBox.setResponder(value -> region.setRadius(parseInt(value, region.getRadius(), 1, 100000)));
    addRenderableWidget(radiusBox);

    EditBox responseBox = new EditBox(this.font, contentLeft + 70, y, 80, 18, Component.literal("Response"));
    responseBox.setValue(Integer.toString(region.getResponseRadius()));
    responseBox.setResponder(value -> region.setResponseRadius(parseInt(value, region.getResponseRadius(), 1, 100000)));
    addRenderableWidget(responseBox);

    EditBox capBox = new EditBox(this.font, contentLeft + 160, y, 60, 18, Component.literal("Cap"));
    capBox.setValue(Integer.toString(region.getGuardSpawnCap()));
    capBox.setResponder(value -> region.setGuardSpawnCap(parseInt(value, region.getGuardSpawnCap(), 0, 10000)));
    addRenderableWidget(capBox);

    EditBox cooldownBox = new EditBox(this.font, contentLeft + 230, y, 80, 18, Component.literal("Cooldown"));
    cooldownBox.setValue(Integer.toString(region.getCooldownTicks()));
    cooldownBox.setResponder(value -> region.setCooldownTicks(parseInt(value, region.getCooldownTicks(), 0, 240000)));
    addRenderableWidget(cooldownBox);

    y += 26;

    crimeCheckboxes.clear();
    for (CrimeType type : CrimeType.values()) {
      Checkbox checkbox =
          new Checkbox(contentLeft, y, Component.literal(type.name()), region.isCrimeEnabled(type), true);
      crimeCheckboxes.put(type, checkbox);
      addRenderableWidget(checkbox);
      y += 18;
      if (y > contentTop + contentHeight - 24) {
        break;
      }
    }
  }

  private void initWantedTab() {
    int y = contentTop;

    EditBox maxWantedBox = new EditBox(this.font, contentLeft, y, 60, 18, Component.literal("Max"));
    maxWantedBox.setValue(Integer.toString(localConfig.getMaxWantedLevel()));
    maxWantedBox.setResponder(value -> localConfig.setMaxWantedLevel(parseInt(value, localConfig.getMaxWantedLevel(), 1, 20)));
    addRenderableWidget(maxWantedBox);

    EditBox peaceMinBox = new EditBox(this.font, contentLeft + 70, y, 60, 18, Component.literal("Peace Min"));
    peaceMinBox.setValue(Integer.toString(localConfig.getPeaceValueMin()));
    peaceMinBox.setResponder(value -> localConfig.setPeaceValueMin(parseInt(value, localConfig.getPeaceValueMin(), 0, 100)));
    addRenderableWidget(peaceMinBox);

    EditBox peaceMaxBox = new EditBox(this.font, contentLeft + 140, y, 60, 18, Component.literal("Peace Max"));
    peaceMaxBox.setValue(Integer.toString(localConfig.getPeaceValueMax()));
    peaceMaxBox.setResponder(value -> localConfig.setPeaceValueMax(parseInt(value, localConfig.getPeaceValueMax(), 0, 100)));
    addRenderableWidget(peaceMaxBox);

    EditBox peaceRegenBox = new EditBox(this.font, contentLeft + 210, y, 80, 18, Component.literal("Peace Regen"));
    peaceRegenBox.setValue(Integer.toString(localConfig.getPeaceRegenRate()));
    peaceRegenBox.setResponder(value -> localConfig.setPeaceRegenRate(parseInt(value, localConfig.getPeaceRegenRate(), 1, 120000)));
    addRenderableWidget(peaceRegenBox);

    y += 24;

    EditBox decayRateBox = new EditBox(this.font, contentLeft, y, 80, 18, Component.literal("Decay Rate"));
    decayRateBox.setValue(Integer.toString(localConfig.getWantedDecayRate()));
    decayRateBox.setResponder(value -> localConfig.setWantedDecayRate(parseInt(value, localConfig.getWantedDecayRate(), 1, 240000)));
    addRenderableWidget(decayRateBox);

    EditBox decayDelayBox = new EditBox(this.font, contentLeft + 90, y, 80, 18, Component.literal("Decay Delay"));
    decayDelayBox.setValue(Integer.toString(localConfig.getWantedDecayDelayTicks()));
    decayDelayBox.setResponder(value -> localConfig.setWantedDecayDelayTicks(parseInt(value, localConfig.getWantedDecayDelayTicks(), 0, 240000)));
    addRenderableWidget(decayDelayBox);

    y += 24;

    repeatMultiplierInput = new EditBox(this.font, contentLeft, y, 70, 18, Component.literal("Repeat x"));
    repeatMultiplierInput.setValue(Float.toString(localConfig.getCrimeRule().getRepeatOffenseMultiplier()));
    repeatMultiplierInput.setResponder(
        value -> localConfig.getCrimeRule().setRepeatOffenseMultiplier(
            parseFloat(value, localConfig.getCrimeRule().getRepeatOffenseMultiplier(), 1.0f, 10.0f)));
    addRenderableWidget(repeatMultiplierInput);

    repeatWindowInput = new EditBox(this.font, contentLeft + 80, y, 90, 18, Component.literal("Repeat Window"));
    repeatWindowInput.setValue(Integer.toString(localConfig.getCrimeRule().getRepeatWindowTicks()));
    repeatWindowInput.setResponder(value -> localConfig.getCrimeRule().setRepeatWindowTicks(
        parseInt(value, localConfig.getCrimeRule().getRepeatWindowTicks(), 0, 240000)));
    addRenderableWidget(repeatWindowInput);

    resetOnDeathCheckbox =
        new Checkbox(
            contentLeft + 180,
            y,
            Component.literal("Reset on Death"),
            localConfig.isResetOnDeath(),
            true);
    addRenderableWidget(resetOnDeathCheckbox);

    y += 22;

    resetOnJailCheckbox =
        new Checkbox(contentLeft, y, Component.literal("Reset on Jail"), localConfig.isResetOnJail(), true);
    addRenderableWidget(resetOnJailCheckbox);

    resetOnBribeCheckbox =
        new Checkbox(
            contentLeft + 140,
            y,
            Component.literal("Reset on Bribe"),
            localConfig.isResetOnBribe(),
            true);
    addRenderableWidget(resetOnBribeCheckbox);

    y += 26;

    for (CrimeType type : CrimeType.values()) {
      EditBox wantedPenaltyBox = new EditBox(
          this.font, contentLeft, y, 60, 18, Component.literal(type.name() + " Wanted"));
      wantedPenaltyBox.setValue(Integer.toString(localConfig.getCrimeRule().getWantedPenalty(type)));
      wantedPenaltyBox.setResponder(value ->
          localConfig.getCrimeRule().setWantedPenalty(type, parseInt(value, localConfig.getCrimeRule().getWantedPenalty(type), 0, 100)));
      addRenderableWidget(wantedPenaltyBox);

      EditBox peacePenaltyBox = new EditBox(
          this.font, contentLeft + 70, y, 60, 18, Component.literal(type.name() + " Peace"));
      peacePenaltyBox.setValue(Integer.toString(localConfig.getCrimeRule().getPeacePenalty(type)));
      peacePenaltyBox.setResponder(value ->
          localConfig.getCrimeRule().setPeacePenalty(type, parseInt(value, localConfig.getCrimeRule().getPeacePenalty(type), 0, 100)));
      addRenderableWidget(peacePenaltyBox);

      y += 20;
      if (y > contentTop + contentHeight - 24) {
        break;
      }
    }
  }

  private void initMerchantsTab() {
    int y = contentTop;

    Button addTemplateBtn = Button.builder(
        Component.literal("Add Template"),
        button -> addMerchantTemplate())
        .pos(contentLeft, y)
        .size(110, BUTTON_HEIGHT)
        .build();
    addRenderableWidget(addTemplateBtn);

    Button prevBtn = Button.builder(Component.literal("<"), button -> changeMerchant(-1))
        .pos(contentLeft + 120, y)
        .size(20, BUTTON_HEIGHT)
        .build();
    addRenderableWidget(prevBtn);

    Button nextBtn = Button.builder(Component.literal(">"), button -> changeMerchant(1))
        .pos(contentLeft + 145, y)
        .size(20, BUTTON_HEIGHT)
        .build();
    addRenderableWidget(nextBtn);

    Button exportBtn = Button.builder(Component.literal("Export"), button -> exportMerchantTemplateToClipboard())
        .pos(contentLeft + 175, y)
        .size(60, BUTTON_HEIGHT)
        .build();
    addRenderableWidget(exportBtn);

    Button importBtn = Button.builder(Component.literal("Import"), button -> importMerchantTemplateFromClipboard())
        .pos(contentLeft + 240, y)
        .size(60, BUTTON_HEIGHT)
        .build();
    addRenderableWidget(importBtn);

    y += BUTTON_HEIGHT + 6;

    MerchantTemplate template = getSelectedMerchant();
    if (template == null) {
      return;
    }

    EditBox nameBox = new EditBox(this.font, contentLeft, y, 160, 18, Component.literal("Name"));
    nameBox.setValue(template.getName());
    nameBox.setResponder(template::setName);
    addRenderableWidget(nameBox);

    EditBox templateBox = new EditBox(this.font, contentLeft + 170, y, 120, 18, Component.literal("NPC Template"));
    templateBox.setValue(template.getNpcTemplateName());
    templateBox.setResponder(template::setNpcTemplateName);
    addRenderableWidget(templateBox);

    y += 24;

    EditBox minGroupBox = new EditBox(this.font, contentLeft, y, 50, 18, Component.literal("Min"));
    minGroupBox.setValue(Integer.toString(template.getMinGroupSize()));
    minGroupBox.setResponder(value -> template.setMinGroupSize(parseInt(value, template.getMinGroupSize(), 1, 50)));
    addRenderableWidget(minGroupBox);

    EditBox maxGroupBox = new EditBox(this.font, contentLeft + 60, y, 50, 18, Component.literal("Max"));
    maxGroupBox.setValue(Integer.toString(template.getMaxGroupSize()));
    maxGroupBox.setResponder(value -> template.setMaxGroupSize(parseInt(value, template.getMaxGroupSize(), 1, 50)));
    addRenderableWidget(maxGroupBox);

    EditBox intervalBox = new EditBox(this.font, contentLeft + 120, y, 80, 18, Component.literal("Interval"));
    intervalBox.setValue(Integer.toString(template.getSpawnIntervalTicks()));
    intervalBox.setResponder(value -> template.setSpawnIntervalTicks(parseInt(value, template.getSpawnIntervalTicks(), 0, 240000)));
    addRenderableWidget(intervalBox);

    EditBox cooldownBox = new EditBox(this.font, contentLeft + 210, y, 80, 18, Component.literal("Cooldown"));
    cooldownBox.setValue(Integer.toString(template.getRespawnCooldown()));
    cooldownBox.setResponder(value -> template.setRespawnCooldown(parseInt(value, template.getRespawnCooldown(), 0, 240000)));
    addRenderableWidget(cooldownBox);

    y += 24;

    Button behaviorBtn = Button.builder(
        Component.literal("Behavior: " + template.getBehavior().name()),
        button -> {
          AIBehavior[] behaviors = AIBehavior.values();
          int next = (template.getBehavior().ordinal() + 1) % behaviors.length;
          template.setBehavior(behaviors[next]);
          button.setMessage(Component.literal("Behavior: " + template.getBehavior().name()));
        })
        .pos(contentLeft, y)
        .size(140, BUTTON_HEIGHT)
        .build();
    addRenderableWidget(behaviorBtn);

    EditBox dropMinBox = new EditBox(this.font, contentLeft + 150, y, 60, 18, Component.literal("Drop Min"));
    dropMinBox.setValue(Integer.toString(template.getCurrencyDropMin()));
    dropMinBox.setResponder(value -> template.setCurrencyDropMin(parseInt(value, template.getCurrencyDropMin(), 0, 1000)));
    addRenderableWidget(dropMinBox);

    EditBox dropMaxBox = new EditBox(this.font, contentLeft + 220, y, 60, 18, Component.literal("Drop Max"));
    dropMaxBox.setValue(Integer.toString(template.getCurrencyDropMax()));
    dropMaxBox.setResponder(value -> template.setCurrencyDropMax(parseInt(value, template.getCurrencyDropMax(), 0, 1000)));
    addRenderableWidget(dropMaxBox);

    y += 26;

    merchantGroupCountInput = new EditBox(this.font, contentLeft, y, 40, 18, Component.literal("Count"));
    merchantGroupCountInput.setValue("1");
    addRenderableWidget(merchantGroupCountInput);

    Button spawnBtn = Button.builder(
        Component.literal("Spawn Groups"),
        button -> spawnMerchantGroup(parseInt(merchantGroupCountInput.getValue(), 1, 1, 20)))
        .pos(contentLeft + 50, y)
        .size(120, BUTTON_HEIGHT)
        .build();
    addRenderableWidget(spawnBtn);
  }

  private void initGuardsTab() {
    int y = contentTop;

    Button addTierBtn = Button.builder(
        Component.literal("Add Tier"),
        button -> addGuardTier())
        .pos(contentLeft, y)
        .size(90, BUTTON_HEIGHT)
        .build();
    addRenderableWidget(addTierBtn);

    Button prevBtn = Button.builder(Component.literal("<"), button -> changeGuardTier(-1))
        .pos(contentLeft + 100, y)
        .size(20, BUTTON_HEIGHT)
        .build();
    addRenderableWidget(prevBtn);

    Button nextBtn = Button.builder(Component.literal(">"), button -> changeGuardTier(1))
        .pos(contentLeft + 125, y)
        .size(20, BUTTON_HEIGHT)
        .build();
    addRenderableWidget(nextBtn);

    y += BUTTON_HEIGHT + 6;

    GuardTier tier = getSelectedGuardTier();
    if (tier == null) {
      return;
    }

    EditBox tierBox = new EditBox(this.font, contentLeft, y, 50, 18, Component.literal("Tier"));
    tierBox.setValue(Integer.toString(tier.getTier()));
    tierBox.setResponder(value -> tier.setTier(parseInt(value, tier.getTier(), 1, 20)));
    addRenderableWidget(tierBox);

    EditBox minWantedBox = new EditBox(this.font, contentLeft + 60, y, 80, 18, Component.literal("Min Wanted"));
    minWantedBox.setValue(Integer.toString(tier.getMinWantedLevel()));
    minWantedBox.setResponder(value -> tier.setMinWantedLevel(parseInt(value, tier.getMinWantedLevel(), 1, 20)));
    addRenderableWidget(minWantedBox);

    EditBox squadBox = new EditBox(this.font, contentLeft + 150, y, 60, 18, Component.literal("Squad"));
    squadBox.setValue(Integer.toString(tier.getSquadSize()));
    squadBox.setResponder(value -> tier.setSquadSize(parseInt(value, tier.getSquadSize(), 1, 20)));
    addRenderableWidget(squadBox);

    y += 24;

    EditBox radiusBox = new EditBox(this.font, contentLeft, y, 60, 18, Component.literal("Spawn R"));
    radiusBox.setValue(Integer.toString(tier.getSpawnRadius()));
    radiusBox.setResponder(value -> tier.setSpawnRadius(parseInt(value, tier.getSpawnRadius(), 1, 200)));
    addRenderableWidget(radiusBox);

    EditBox despawnDistBox = new EditBox(this.font, contentLeft + 70, y, 70, 18, Component.literal("Despawn D"));
    despawnDistBox.setValue(Integer.toString(tier.getDespawnDistance()));
    despawnDistBox.setResponder(value -> tier.setDespawnDistance(parseInt(value, tier.getDespawnDistance(), 1, 1000)));
    addRenderableWidget(despawnDistBox);

    EditBox despawnTimeBox = new EditBox(this.font, contentLeft + 150, y, 70, 18, Component.literal("Despawn T"));
    despawnTimeBox.setValue(Integer.toString(tier.getDespawnTime()));
    despawnTimeBox.setResponder(value -> tier.setDespawnTime(parseInt(value, tier.getDespawnTime(), 0, 240000)));
    addRenderableWidget(despawnTimeBox);

    EditBox templateBox = new EditBox(this.font, contentLeft + 230, y, 100, 18, Component.literal("Template"));
    templateBox.setValue(tier.getTemplateName());
    templateBox.setResponder(tier::setTemplateName);
    addRenderableWidget(templateBox);

    y += 24;

    guardArcherCheckbox =
        new Checkbox(contentLeft, y, Component.literal("Archer"), tier.isArcher(), true);
    addRenderableWidget(guardArcherCheckbox);

    guardCaptainCheckbox =
        new Checkbox(contentLeft + 80, y, Component.literal("Captain"), tier.isCaptain(), true);
    addRenderableWidget(guardCaptainCheckbox);

    guardTrackerCheckbox =
        new Checkbox(contentLeft + 170, y, Component.literal("Tracker"), tier.isTracker(), true);
    addRenderableWidget(guardTrackerCheckbox);

    y += 26;

    Button spawnPatrolBtn = Button.builder(
        Component.literal("Spawn Patrol"),
        button -> spawnGuardPatrol())
        .pos(contentLeft, y)
        .size(100, BUTTON_HEIGHT)
        .build();
    addRenderableWidget(spawnPatrolBtn);
  }

  private void initPlayersTab() {
    int y = contentTop;

    // Player search box
    searchBox = new EditBox(this.font, contentLeft, y, 200, 20, 
        Component.literal("Search player..."));
    searchBox.setHint(Component.literal("Enter player name..."));
    addRenderableWidget(searchBox);

    y += 26;

    wantedLevelInput = new EditBox(this.font, contentLeft, y, 60, 18, Component.literal("Wanted"));
    wantedLevelInput.setValue("0");
    addRenderableWidget(wantedLevelInput);

    Button setWantedBtn = Button.builder(
        Component.literal("Set Wanted"),
        button -> applyPlayerWanted())
        .pos(contentLeft + 70, y)
        .size(90, BUTTON_HEIGHT)
        .build();
    addRenderableWidget(setWantedBtn);

    y += 24;

    peaceValueInput = new EditBox(this.font, contentLeft, y, 60, 18, Component.literal("Peace"));
    peaceValueInput.setValue("100");
    addRenderableWidget(peaceValueInput);

    Button setPeaceBtn = Button.builder(
        Component.literal("Set Peace"),
        button -> applyPlayerPeace())
        .pos(contentLeft + 70, y)
        .size(90, BUTTON_HEIGHT)
        .build();
    addRenderableWidget(setPeaceBtn);

    y += 24;

    Button forgiveBtn = Button.builder(
        Component.literal("Forgive Crimes"),
        button -> clearPlayerCrimes())
        .pos(contentLeft, y)
        .size(120, BUTTON_HEIGHT)
        .build();
    addRenderableWidget(forgiveBtn);

    Button immunityBtn = Button.builder(
        Component.literal("Toggle Immunity"),
        button -> togglePlayerImmunity())
        .pos(contentLeft + 130, y)
        .size(120, BUTTON_HEIGHT)
        .build();
    addRenderableWidget(immunityBtn);

    y += 24;

    Button pursuitBtn = Button.builder(
        Component.literal("Spawn Pursuit"),
        button -> spawnPursuitForPlayer())
        .pos(contentLeft, y)
        .size(120, BUTTON_HEIGHT)
        .build();
    addRenderableWidget(pursuitBtn);
  }

  private void initProfilesTab() {
    int y = contentTop;
    int spacing = 25;

    profileNameBox = new EditBox(this.font, contentLeft, y, 180, 18, Component.literal("Profile Name"));
    profileNameBox.setValue(localConfig.getProfileName());
    profileNameBox.setResponder(localConfig::setProfileName);
    addRenderableWidget(profileNameBox);

    y += spacing;

    // Preset buttons
    Button rpPresetBtn = Button.builder(
        Component.literal("RP Preset"),
        button -> applyPreset("rp"))
        .pos(contentLeft, y)
        .size(80, BUTTON_HEIGHT)
        .build();
    addRenderableWidget(rpPresetBtn);

    Button hardcorePresetBtn = Button.builder(
        Component.literal("Hardcore"),
        button -> applyPreset("hardcore"))
        .pos(contentLeft + 90, y)
        .size(80, BUTTON_HEIGHT)
        .build();
    addRenderableWidget(hardcorePresetBtn);

    Button casualPresetBtn = Button.builder(
        Component.literal("Casual"),
        button -> applyPreset("casual"))
        .pos(contentLeft + 180, y)
        .size(80, BUTTON_HEIGHT)
        .build();
    addRenderableWidget(casualPresetBtn);

    y += spacing * 2;
    Button resetDefaultsBtn = Button.builder(
        Component.literal("Reset to Defaults"),
        button -> resetToDefaults())
        .pos(contentLeft, y)
        .size(120, BUTTON_HEIGHT)
        .build();
    addRenderableWidget(resetDefaultsBtn);

    Button exportBtn = Button.builder(
        Component.literal("Export JSON"),
        button -> exportConfigToClipboard())
        .pos(contentLeft + 130, y)
        .size(100, BUTTON_HEIGHT)
        .build();
    addRenderableWidget(exportBtn);

    Button importBtn = Button.builder(
        Component.literal("Import JSON"),
        button -> importConfigFromClipboard())
        .pos(contentLeft + 240, y)
        .size(100, BUTTON_HEIGHT)
        .build();
    addRenderableWidget(importBtn);
  }

  @Override
  public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
    // Render background
    renderBackground(guiGraphics, mouseX, mouseY, partialTick);
    
    // Render panel background
    guiGraphics.fill(leftPos, topPos, leftPos + GUI_WIDTH, topPos + GUI_HEIGHT, 0xCC000000);
    guiGraphics.renderOutline(leftPos, topPos, GUI_WIDTH, GUI_HEIGHT, 0xFF666666);

    // Render content area background
    guiGraphics.fill(contentLeft - 2, contentTop - 2, 
        contentLeft + contentWidth + 2, contentTop + contentHeight + 2, 0x44000000);

    // Render tab indicator
    highlightActiveTab(guiGraphics);

    // Render title
    guiGraphics.drawCenteredString(this.font, this.title, 
        leftPos + GUI_WIDTH / 2, topPos + TAB_HEIGHT + 2, 0xFFFFFF);

    // Render tab-specific content
    renderTabContent(guiGraphics, mouseX, mouseY);

    // Render widgets
    super.render(guiGraphics, mouseX, mouseY, partialTick);
  }

  private void highlightActiveTab(GuiGraphics guiGraphics) {
    int tabIndex = currentTab.ordinal();
    int tabX = leftPos + tabIndex * (TAB_WIDTH + 2);
    guiGraphics.fill(tabX, topPos + TAB_HEIGHT - 2, 
        tabX + TAB_WIDTH, topPos + TAB_HEIGHT, 0xFF00FF00);
  }

  private void renderTabContent(GuiGraphics guiGraphics, int mouseX, int mouseY) {
    int y = contentTop + 30;

    switch (currentTab) {
      case OVERVIEW -> {
        // Show live stats
        guiGraphics.drawString(this.font, "Active Merchants: " + merchantCount, contentLeft, y, 0xAAAAAA);
        guiGraphics.drawString(this.font, "Active Guards: " + guardCount, contentLeft, y + 15, 0xAAAAAA);
        guiGraphics.drawString(this.font, "Wanted Players: " + getWantedPlayers().size(), contentLeft, y + 30, 0xAAAAAA);

        int listY = y + 50;
        for (LawAdminDataMessage.PlayerSnapshot snapshot : getWantedPlayers()) {
          String entry =
              snapshot.name() + " (" + snapshot.state().getWantedLevel() + "★, " +
                  snapshot.state().getPeaceValue() + "p)";
          guiGraphics.drawString(this.font, entry, contentLeft, listY, 0xFFCC88);
          listY += 12;
          if (listY > contentTop + contentHeight - 20) {
            break;
          }
        }
      }
      case REGIONS -> {
        guiGraphics.drawString(this.font, "Configure crime detection regions", contentLeft, y, 0xAAAAAA);
      }
      case WANTED -> {
        guiGraphics.drawString(this.font, "Configure wanted levels and peace values", contentLeft, y, 0xAAAAAA);
        GuardTier previewTier = localConfig.getGuardTierForWantedLevel(3);
        if (previewTier != null) {
          guiGraphics.drawString(
              this.font,
              "Preview: Wanted 3 -> Tier " + previewTier.getTier() + " squad " + previewTier.getSquadSize(),
              contentLeft,
              y + 15,
              0x88DD88);
        }
      }
      case MERCHANTS -> {
        guiGraphics.drawString(this.font, "Configure merchant NPC templates", contentLeft, y, 0xAAAAAA);
      }
      case GUARDS -> {
        guiGraphics.drawString(this.font, "Configure guard tiers and spawning", contentLeft, y, 0xAAAAAA);
      }
      case PLAYERS -> {
        guiGraphics.drawString(this.font, "View and modify player states", contentLeft, y, 0xAAAAAA);
        Optional<LawAdminDataMessage.PlayerSnapshot> snapshot = findPlayerSnapshot();
        if (snapshot.isPresent()) {
          PlayerLawState state = snapshot.get().state();
          guiGraphics.drawString(
              this.font,
              "Wanted: " + state.getWantedLevel() + "  Peace: " + state.getPeaceValue(),
              contentLeft,
              y + 15,
              0xFFDD88);
          guiGraphics.drawString(
              this.font,
              "Crimes: " + state.getCrimeHistory().size(),
              contentLeft,
              y + 30,
              0xFF9999);
          int logY = y + 46;
          for (int i = 0; i < Math.min(5, state.getCrimeHistory().size()); i++) {
            var record = state.getCrimeHistory().get(i);
            guiGraphics.drawString(
                this.font,
                record.getCrimeType().name() + " @" + record.getTimestamp(),
                contentLeft,
                logY,
                0xAAAAAA);
            logY += 12;
          }
        }
      }
      case PROFILES -> {
        guiGraphics.drawString(this.font, "Manage configuration profiles", contentLeft, y, 0xAAAAAA);
      }
    }
  }

  // Action handlers
  private void applyChanges() {
    log.info("{} Applying changes", LOG_PREFIX);
    NetworkHandlerManager.sendMessageToServer(new LawConfigUpdateMessage(localConfig, false));
  }

  private void revertChanges() {
    log.info("{} Reverting changes", LOG_PREFIX);
    NetworkHandlerManager.sendMessageToServer(LawAdminRequestMessage.refresh());
  }

  private void saveProfile() {
    log.info("{} Saving profile", LOG_PREFIX);
    NetworkHandlerManager.sendMessageToServer(new LawConfigUpdateMessage(localConfig, true));
  }

  private void loadProfile() {
    log.info("{} Loading profile", LOG_PREFIX);
    NetworkHandlerManager.sendMessageToServer(AdminActionMessage.reloadConfig());
    NetworkHandlerManager.sendMessageToServer(LawAdminRequestMessage.refresh());
  }

  private void clearAllWanted() {
    log.info("{} Clear all wanted", LOG_PREFIX);
    confirmAction(
        Component.literal("Clear all wanted?"),
        Component.literal("This will reset wanted levels for all players."),
        () -> NetworkHandlerManager.sendMessageToServer(AdminActionMessage.clearAllWanted()));
  }

  private void despawnAllGuards() {
    log.info("{} Despawn all guards", LOG_PREFIX);
    confirmAction(
        Component.literal("Despawn all guards?"),
        Component.literal("All spawned guards will be removed."),
        () -> NetworkHandlerManager.sendMessageToServer(AdminActionMessage.despawnAllGuards()));
  }

  private void despawnAllMerchants() {
    log.info("{} Despawn all merchants", LOG_PREFIX);
    confirmAction(
        Component.literal("Despawn all merchants?"),
        Component.literal("All spawned merchants will be removed."),
        () -> NetworkHandlerManager.sendMessageToServer(AdminActionMessage.despawnAllMerchants()));
  }

  private void runTestSimulation(int seconds) {
    log.info("{} Running test simulation", LOG_PREFIX);
    NetworkHandlerManager.sendMessageToServer(AdminActionMessage.runTestSimulation(seconds));
  }

  private void addRegionFromPosition() {
    log.info("{} Add region from current position", LOG_PREFIX);
    if (Minecraft.getInstance().player == null) {
      return;
    }
    BlockPos pos = Minecraft.getInstance().player.blockPosition();
    RegionRule rule = new RegionRule("Region " + (localConfig.getRegions().size() + 1), pos, 50);
    localConfig.addRegion(rule);
    selectedRegionIndex = localConfig.getRegions().size() - 1;
    switchTab(Tab.REGIONS);
  }

  private void spawnMerchantGroup() {
    spawnMerchantGroup(1);
  }

  private void spawnMerchantGroup(int groupCount) {
    log.info("{} Spawn merchant group", LOG_PREFIX);
    NetworkHandlerManager.sendMessageToServer(AdminActionMessage.spawnMerchantGroup(groupCount));
  }

  private void spawnGuardPatrol() {
    log.info("{} Spawn guard patrol", LOG_PREFIX);
    GuardTier tier = getSelectedGuardTier();
    int tierLevel = tier != null ? tier.getTier() : 1;
    NetworkHandlerManager.sendMessageToServer(AdminActionMessage.spawnPatrol(tierLevel));
  }

  private void applyPreset(String presetName) {
    log.info("{} Applying preset: {}", LOG_PREFIX, presetName);
    localConfig.applyPreset(presetName);
    switchTab(currentTab);
  }

  private void resetToDefaults() {
    log.info("{} Reset to defaults", LOG_PREFIX);
    confirmAction(
        Component.literal("Reset to defaults?"),
        Component.literal("This will discard unsaved changes."),
        () -> {
          localConfig = new LawSystemConfig();
          switchTab(currentTab);
        });
  }

  private void reloadConfig() {
    NetworkHandlerManager.sendMessageToServer(AdminActionMessage.reloadConfig());
    NetworkHandlerManager.sendMessageToServer(LawAdminRequestMessage.refresh());
  }

  private void applyPlayerWanted() {
    Optional<LawAdminDataMessage.PlayerSnapshot> snapshot = findPlayerSnapshot();
    if (snapshot.isEmpty()) {
      return;
    }
    int wanted = parseInt(wantedLevelInput.getValue(), 0, 0, 20);
    NetworkHandlerManager.sendMessageToServer(
        AdminActionMessage.setWantedLevel(snapshot.get().name(), wanted));
  }

  private void applyPlayerPeace() {
    Optional<LawAdminDataMessage.PlayerSnapshot> snapshot = findPlayerSnapshot();
    if (snapshot.isEmpty()) {
      return;
    }
    int peace = parseInt(peaceValueInput.getValue(), 100, 0, 100);
    NetworkHandlerManager.sendMessageToServer(
        AdminActionMessage.setPeaceValue(snapshot.get().name(), peace));
  }

  private void clearPlayerCrimes() {
    Optional<LawAdminDataMessage.PlayerSnapshot> snapshot = findPlayerSnapshot();
    snapshot.ifPresent(value ->
        NetworkHandlerManager.sendMessageToServer(AdminActionMessage.clearCrimes(value.name())));
  }

  private void togglePlayerImmunity() {
    Optional<LawAdminDataMessage.PlayerSnapshot> snapshot = findPlayerSnapshot();
    snapshot.ifPresent(value ->
        NetworkHandlerManager.sendMessageToServer(AdminActionMessage.toggleImmunity(value.name())));
  }

  private void spawnPursuitForPlayer() {
    Optional<LawAdminDataMessage.PlayerSnapshot> snapshot = findPlayerSnapshot();
    if (snapshot.isEmpty()) {
      return;
    }
    GuardTier tier = getSelectedGuardTier();
    int tierLevel = tier != null ? tier.getTier() : 1;
    NetworkHandlerManager.sendMessageToServer(
        AdminActionMessage.spawnPursuitSquad(snapshot.get().name(), tierLevel));
  }

  private void changeRegion(int delta) {
    if (localConfig.getRegions().isEmpty()) {
      return;
    }
    int size = localConfig.getRegions().size();
    selectedRegionIndex = (selectedRegionIndex + delta + size) % size;
    switchTab(Tab.REGIONS);
  }

  private RegionRule getSelectedRegion() {
    if (localConfig.getRegions().isEmpty()) {
      return null;
    }
    int index = Math.max(0, Math.min(selectedRegionIndex, localConfig.getRegions().size() - 1));
    return localConfig.getRegions().get(index);
  }

  private void changeMerchant(int delta) {
    if (localConfig.getMerchantTemplates().isEmpty()) {
      return;
    }
    int size = localConfig.getMerchantTemplates().size();
    selectedMerchantIndex = (selectedMerchantIndex + delta + size) % size;
    switchTab(Tab.MERCHANTS);
  }

  private MerchantTemplate getSelectedMerchant() {
    if (localConfig.getMerchantTemplates().isEmpty()) {
      return null;
    }
    int index = Math.max(0, Math.min(selectedMerchantIndex, localConfig.getMerchantTemplates().size() - 1));
    return localConfig.getMerchantTemplates().get(index);
  }

  private void addMerchantTemplate() {
    localConfig.addMerchantTemplate(new MerchantTemplate());
    selectedMerchantIndex = localConfig.getMerchantTemplates().size() - 1;
    switchTab(Tab.MERCHANTS);
  }

  private void changeGuardTier(int delta) {
    if (localConfig.getGuardTiers().isEmpty()) {
      return;
    }
    int size = localConfig.getGuardTiers().size();
    selectedGuardIndex = (selectedGuardIndex + delta + size) % size;
    switchTab(Tab.GUARDS);
  }

  private GuardTier getSelectedGuardTier() {
    if (localConfig.getGuardTiers().isEmpty()) {
      return null;
    }
    int index = Math.max(0, Math.min(selectedGuardIndex, localConfig.getGuardTiers().size() - 1));
    return localConfig.getGuardTiers().get(index);
  }

  private void addGuardTier() {
    localConfig.getGuardTiers().add(new GuardTier(localConfig.getGuardTiers().size() + 1, 1));
    selectedGuardIndex = localConfig.getGuardTiers().size() - 1;
    switchTab(Tab.GUARDS);
  }

  private List<LawAdminDataMessage.PlayerSnapshot> getWantedPlayers() {
    return playerSnapshots.stream()
        .filter(snapshot -> snapshot.state().isWanted())
        .sorted(Comparator.comparingInt((LawAdminDataMessage.PlayerSnapshot s) -> s.state().getWantedLevel()).reversed())
        .toList();
  }

  private Optional<LawAdminDataMessage.PlayerSnapshot> findPlayerSnapshot() {
    if (searchBox == null) {
      return Optional.empty();
    }
    String query = searchBox.getValue().trim().toLowerCase(Locale.ROOT);
    if (query.isEmpty()) {
      return Optional.empty();
    }
    return playerSnapshots.stream()
        .filter(snapshot -> snapshot.name().toLowerCase(Locale.ROOT).contains(query))
        .findFirst();
  }

  private int parseInt(String value, int fallback, int min, int max) {
    if (value == null || value.isBlank()) {
      return fallback;
    }
    try {
      int parsed = Integer.parseInt(value.trim());
      if (parsed < min) {
        return min;
      }
      if (parsed > max) {
        return max;
      }
      return parsed;
    } catch (NumberFormatException e) {
      return fallback;
    }
  }

  private float parseFloat(String value, float fallback, float min, float max) {
    if (value == null || value.isBlank()) {
      return fallback;
    }
    try {
      float parsed = Float.parseFloat(value.trim());
      if (parsed < min) {
        return min;
      }
      if (parsed > max) {
        return max;
      }
      return parsed;
    } catch (NumberFormatException e) {
      return fallback;
    }
  }

  private void confirmAction(Component title, Component warning, Runnable onConfirm) {
    if (this.minecraft == null) {
      return;
    }
    this.minecraft.setScreen(
        new ConfirmScreen(
            confirmed -> {
              if (confirmed) {
                onConfirm.run();
              }
              this.minecraft.setScreen(this);
            },
            title,
            warning,
            Component.literal("Confirm"),
            CommonComponents.GUI_CANCEL));
  }

  private void exportRegionToClipboard() {
    if (this.minecraft == null) {
      return;
    }
    RegionRule region = getSelectedRegion();
    if (region == null) {
      return;
    }
    String json = buildClipboardJson("region", region.createTag());
    this.minecraft.keyboardHandler.setClipboard(json);
  }

  private void importRegionFromClipboard() {
    if (this.minecraft == null) {
      return;
    }
    CompoundTag tag = readClipboardTag("region");
    if (tag == null) {
      return;
    }
    RegionRule region = new RegionRule(tag);
    localConfig.addRegion(region);
    selectedRegionIndex = localConfig.getRegions().size() - 1;
    switchTab(Tab.REGIONS);
  }

  private void exportMerchantTemplateToClipboard() {
    if (this.minecraft == null) {
      return;
    }
    MerchantTemplate template = getSelectedMerchant();
    if (template == null) {
      return;
    }
    String json = buildClipboardJson("merchant", template.createTag());
    this.minecraft.keyboardHandler.setClipboard(json);
  }

  private void importMerchantTemplateFromClipboard() {
    if (this.minecraft == null) {
      return;
    }
    CompoundTag tag = readClipboardTag("merchant");
    if (tag == null) {
      return;
    }
    MerchantTemplate template = new MerchantTemplate(tag);
    localConfig.addMerchantTemplate(template);
    selectedMerchantIndex = localConfig.getMerchantTemplates().size() - 1;
    switchTab(Tab.MERCHANTS);
  }

  private void exportConfigToClipboard() {
    if (this.minecraft == null) {
      return;
    }
    String json = buildClipboardJson("config", localConfig.createTag());
    this.minecraft.keyboardHandler.setClipboard(json);
  }

  private void importConfigFromClipboard() {
    if (this.minecraft == null) {
      return;
    }
    CompoundTag tag = readClipboardTag("config");
    if (tag == null) {
      return;
    }
    localConfig = new LawSystemConfig(tag);
    switchTab(currentTab);
  }

  private String buildClipboardJson(String kind, CompoundTag tag) {
    JsonObject root = new JsonObject();
    root.addProperty(CLIPBOARD_KIND_KEY, kind);
    root.addProperty(CLIPBOARD_SNBT_KEY, tag.toString());
    return GSON.toJson(root);
  }

  private CompoundTag readClipboardTag(String expectedKind) {
    if (this.minecraft == null) {
      return null;
    }
    try {
      String clipboard = this.minecraft.keyboardHandler.getClipboard();
      if (clipboard == null || clipboard.isBlank()) {
        return null;
      }
      String trimmed = clipboard.trim();
      if (trimmed.startsWith("{")) {
        JsonElement element = JsonParser.parseString(trimmed);
        if (!element.isJsonObject()) {
          return null;
        }
        JsonObject root = element.getAsJsonObject();
        if (root.has(CLIPBOARD_KIND_KEY) && expectedKind != null) {
          String kind = root.get(CLIPBOARD_KIND_KEY).getAsString();
          if (!expectedKind.equalsIgnoreCase(kind)) {
            return null;
          }
        }
        if (root.has(CLIPBOARD_SNBT_KEY)) {
          String snbt = root.get(CLIPBOARD_SNBT_KEY).getAsString();
          return TagParser.parseTag(snbt);
        }
      } else {
        return TagParser.parseTag(trimmed);
      }
    } catch (Exception e) {
      log.warn("{} Failed to read clipboard tag: {}", LOG_PREFIX, e.getMessage());
    }
    return null;
  }

  private void applyServerData(LawAdminDataMessage message) {
    if (message == null) {
      return;
    }
    this.serverConfig = new LawSystemConfig(message.config().createTag());
    this.localConfig = new LawSystemConfig(message.config().createTag());
    this.playerSnapshots = new ArrayList<>(message.players());
    this.merchantCount = message.merchantCount();
    this.guardCount = message.guardCount();
    if (this.minecraft != null && this.minecraft.screen == this) {
      this.clearWidgets();
      createTabButtons();
      createActionButtons();
      createQuickActions();
      initTabContent();
    }
  }

  @Override
  public void tick() {
    super.tick();
    if (systemEnabledCheckbox != null) {
      localConfig.setSystemEnabled(systemEnabledCheckbox.selected());
    }
    RegionRule region = getSelectedRegion();
    if (currentTab == Tab.REGIONS && region != null) {
      if (regionEnabledCheckbox != null) {
        region.setEnabled(regionEnabledCheckbox.selected());
      }
      if (!crimeCheckboxes.isEmpty()) {
        EnumSet<CrimeType> newSet = EnumSet.noneOf(CrimeType.class);
        for (Map.Entry<CrimeType, Checkbox> entry : crimeCheckboxes.entrySet()) {
          if (entry.getValue().selected()) {
            newSet.add(entry.getKey());
          }
        }
        region.setEnabledCrimes(newSet);
      }
    }
    GuardTier tier = getSelectedGuardTier();
    if (currentTab == Tab.GUARDS && tier != null) {
      if (guardArcherCheckbox != null) {
        tier.setArcher(guardArcherCheckbox.selected());
      }
      if (guardCaptainCheckbox != null) {
        tier.setCaptain(guardCaptainCheckbox.selected());
      }
      if (guardTrackerCheckbox != null) {
        tier.setTracker(guardTrackerCheckbox.selected());
      }
    }
    if (currentTab == Tab.WANTED) {
      if (resetOnDeathCheckbox != null) {
        localConfig.setResetOnDeath(resetOnDeathCheckbox.selected());
      }
      if (resetOnJailCheckbox != null) {
        localConfig.setResetOnJail(resetOnJailCheckbox.selected());
      }
      if (resetOnBribeCheckbox != null) {
        localConfig.setResetOnBribe(resetOnBribeCheckbox.selected());
      }
    }
  }

  @Override
  public boolean isPauseScreen() {
    return false;
  }
}
