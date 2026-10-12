package com.haramisi98.LingoScape;

import com.google.common.annotations.VisibleForTesting;
import com.google.inject.Provides;
import net.runelite.api.Client;
import net.runelite.api.MenuAction;
import net.runelite.api.MenuEntry;
import net.runelite.api.events.*;
import net.runelite.api.widgets.ComponentID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.events.ConfigChanged;

import javax.inject.Inject;
import java.util.Arrays;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicBoolean;
import net.runelite.client.input.KeyManager;
import net.runelite.client.util.HotkeyListener;
import net.runelite.client.callback.ClientThread;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Logger;
import java.util.regex.Pattern;
import java.nio.file.Path;
import java.awt.image.BufferedImage;
import java.awt.Graphics2D;
import javax.swing.SwingUtilities;
import net.runelite.client.RuneLite;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;

@PluginDescriptor(
        name = "LingoScape",
        description = "Translates game text elements to various languages",
        tags = {"translation", "language"},
        enabledByDefault = false
)
public class TranslationPlugin extends Plugin {

    @VisibleForTesting
    volatile TranslationManager translationManager;

    @Inject
    TranslationConfig config;

    @Provides
    TranslationConfig provideConfig(ConfigManager configManager) {
        return configManager.getConfig(TranslationConfig.class);
    }

    @Inject
    Client client;

    @Inject
    ClientToolbar toolbar;

    @Inject
    KeyManager keyManager;

    @Inject
    ClientThread clientThread;

    @Inject
    net.runelite.client.ui.overlay.OverlayManager overlayManager;
    private LearningOverlay learningOverlay;

    boolean isEnglishOverride() { return englishOverride; }

    private final AtomicInteger pendingToggles = new AtomicInteger();
    private final AtomicBoolean previewHeld = new AtomicBoolean();
    private final AtomicBoolean previewReset = new AtomicBoolean();
    private final AtomicBoolean restorationPending = new AtomicBoolean();
    private volatile boolean englishOverride;
    private HotkeyListener englishHotkey;
    private TranslationManager renderedManager;
    private boolean menuRefreshPending;
    private final Map<MenuEntry, DisplayText> menuOptions = new IdentityHashMap<>();
    private final Map<MenuEntry, DisplayText> menuTargets = new IdentityHashMap<>();
    private final Map<Widget, DisplayText> dialogueTexts = new IdentityHashMap<>();

    // Keep the exact source, including markup. A game/other-plugin write replaces our source.
    private static final class DisplayText {
        private String original;
        private String displayed;
        private TranslationManager manager;
        private boolean english;

        boolean unchanged(String current, TranslationManager currentManager, boolean currentEnglish) {
            return manager == currentManager && english == currentEnglish && Objects.equals(current, displayed);
        }

        String source(String current) {
            if (!Objects.equals(current, displayed)) original = current;
            return original;
        }

        String display(String value) { displayed = value; return value; }
    }

    private ScheduledExecutorService worker;
    private final AtomicLong languageGeneration = new AtomicLong();
    volatile Future<?> pendingLanguage;
    private volatile boolean active;
    private volatile boolean collecting;
    private volatile MissingTextCollector collector;
    private volatile KnownTextIndex knownIndex;
    private volatile Pattern playerNamePattern;
    private String playerName;
    private boolean dialoguePending;
    private long nextStatus;
    private CollectionPanel panel;
    private NavigationButton navigation;
    private static final Logger LOG = Logger.getLogger(TranslationPlugin.class.getName());

    Path collectionDirectory() { return RuneLite.RUNELITE_DIR.toPath().resolve("lingoscape"); }

    @Override
    protected synchronized void startUp() {
        active = true;
        if (overlayManager != null) {
            learningOverlay = new LearningOverlay(this, config, client);
            overlayManager.add(learningOverlay);
        }
        englishOverride = false;
        pendingToggles.set(0);
        previewHeld.set(false);
        previewReset.set(false);
        englishHotkey = new HotkeyListener(() -> config.englishOverrideKey()) {
            @Override public void hotkeyPressed() {
                if (!active) return;
                if (config.holdToPreview()) previewHeld.set(true);
                else pendingToggles.incrementAndGet();
            }
            @Override public void hotkeyReleased() { previewHeld.set(false); }
            @Override public void focusLost() {
                super.focusLost();
                previewHeld.set(false);
            }
        };
        if (keyManager != null) keyManager.registerKeyListener(englishHotkey);
        translationManager = new TranslationManager("en");
        worker = Executors.newSingleThreadScheduledExecutor(task -> {
            Thread thread = new Thread(task, "lingoscape-worker"); thread.setDaemon(true); return thread;
        });
        collecting = config.collectMissing();
        if (toolbar != null) {
            SwingUtilities.invokeLater(() -> {
                if (!active) return;
                panel = new CollectionPanel(this::exportMissing);
                BufferedImage icon = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
                Graphics2D graphics = icon.createGraphics();
                graphics.setColor(java.awt.Color.CYAN); graphics.drawString("L", 3, 13); graphics.dispose();
                navigation = NavigationButton.builder().tooltip("LingoScape").icon(icon).panel(panel).build();
                toolbar.addNavigation(navigation);
            });
        }
        if (collecting) initializeCollector();
        worker.scheduleWithFixedDelay(this::flushMissing, 15, 15, TimeUnit.SECONDS);
        changeLanguage(config.language().getCode());
    }

    private synchronized void initializeCollector() {
        if (!active) return;
        worker.execute(() -> {
            if (collector != null) return;
            try {
                KnownTextIndex index = KnownTextIndex.load();
                MissingTextCollector loaded = new MissingTextCollector(collectionDirectory());
                loaded.load();
                knownIndex = index; collector = loaded;
            } catch (Exception ex) { report("Collection unavailable: " + ex.getMessage()); }
        });
    }

    private void report(String text) {
        LOG.warning(text);
        if (panel != null) panel.message(text);
    }
    private void flushMissing() {
        MissingTextCollector current = collector;
        if (current != null) try { current.flush(); } catch (Exception ex) { report("Collection save failed: " + ex.getMessage()); }
    }
    private synchronized void exportMissing() {
        if (!active) return;
        worker.execute(() -> {
            try {
                if (collector == null) { report("Enable collection first to load saved candidates."); return; }
                Path path = collector.export();
                if (panel != null) panel.message("Export saved to: " + path);
            } catch (Exception ex) { report("Export failed: " + ex.getMessage()); }
        });
    }

    @Subscribe
    public void onConfigChanged(ConfigChanged event) {
        if (TranslationConfig.GROUP.equals(event.getGroup())
                && ("holdToPreview".equals(event.getKey()) || "englishOverrideKey".equals(event.getKey()))) {
            if (englishHotkey != null) englishHotkey.focusLost();
            pendingToggles.set(0);
            previewHeld.set(false);
            previewReset.set(true);
        }
        if (TranslationConfig.GROUP.equals(event.getGroup()) && "language".equals(event.getKey())) {
            changeLanguage(config.language().getCode());
        } else if (TranslationConfig.GROUP.equals(event.getGroup()) && "collectMissing".equals(event.getKey())) {
            collecting = config.collectMissing();
            if (collecting) initializeCollector();
        }
    }

    @Override
    protected synchronized void shutDown() {
        active = false; collecting = false; dialoguePending = false;
        if (overlayManager != null && learningOverlay != null) overlayManager.remove(learningOverlay);
        learningOverlay = null;
        if (keyManager != null && englishHotkey != null) keyManager.unregisterKeyListener(englishHotkey);
        englishHotkey = null;
        pendingToggles.set(0);
        englishOverride = false;
        restorationPending.set(true);
        if (clientThread != null) clientThread.invokeLater(this::restorePendingText);
        languageGeneration.incrementAndGet();
        if (worker != null) {
            ScheduledExecutorService closing = worker;
            MissingTextCollector closingCollector = collector;
            closing.execute(() -> {
                if (closingCollector != null) try { closingCollector.flush(); }
                catch (Exception ex) { LOG.warning("Final collection save failed: " + ex.getMessage()); }
            });
            closing.shutdown();
            worker = null;
        }
        collector = null; knownIndex = null;
        translationManager = new TranslationManager("en");
        if (toolbar != null) SwingUtilities.invokeLater(() -> {
            if (navigation != null) toolbar.removeNavigation(navigation);
            navigation = null; panel = null;
        });
    }

    // Client-thread-only: finish old lifecycle cleanup before processing new events.
    private void restorePendingText() {
        if (!restorationPending.getAndSet(false)) return;
        if (client != null) {
            MenuEntry[] entries = client.getMenuEntries();
            if (entries != null) for (MenuEntry entry : entries) {
                DisplayText option = menuOptions.get(entry);
                DisplayText target = menuTargets.get(entry);
                if (option != null && Objects.equals(entry.getOption(), option.displayed)) entry.setOption(option.original);
                if (target != null && Objects.equals(entry.getTarget(), target.displayed)) entry.setTarget(target.original);
            }
            for (Widget widget : liveDialogueWidgets()) {
                DisplayText text = dialogueTexts.get(widget);
                if (text != null && Objects.equals(widget.getText(), text.displayed)) widget.setText(text.original);
            }
        }
        menuOptions.clear(); menuTargets.clear(); dialogueTexts.clear();
        renderedManager = null;
    }

    // Translates NPC menu options, but not NPC names
    @Subscribe
    public void onMenuEntryAdded(MenuEntryAdded event) {
        try {
            MenuEntry menuEntry = event.getMenuEntry();
            // RuneLite can reuse entry objects when rebuilding a menu.
            menuOptions.remove(menuEntry);
            menuTargets.remove(menuEntry);
            restorePendingText();
            if (!active) return;
            translateMenuOption(menuEntry);
        } catch (Exception ignored) {
            // Silently handle any exceptions
        }
    }

    private void translateMenuOption(MenuEntry entry) {
        DisplayText text = menuOptions.computeIfAbsent(entry, key -> new DisplayText());
        String original = text.source(entry.getOption());
        entry.setOption(text.display(englishOverride ? original
                : translationManager.translateTextType(TranslationType.MENU, original)));
    }

    // Handles translations for NPC names, item names, and other menu entries
    @Subscribe
    public void onMenuOpened(MenuOpened event) {
        restorePendingText();
        if (!active) return;
        MenuEntry[] menuEntries = event.getMenuEntries();
        MenuEntry[] updatedEntries = Arrays.copyOf(menuEntries, menuEntries.length);

        for (int idx = 0; idx < updatedEntries.length; idx++) {
            MenuEntry entry = updatedEntries[idx];
            translateMenuOption(entry);
            processMenuEntry(entry);
        }

        // This is the point that overrides the UI element!
        event.setMenuEntries(updatedEntries);
    }

    void processMenuEntry(MenuEntry entry) {
        if (entry.getPlayer() != null) return;
        if (isItemEntry(entry)) {
            translateEntryTarget(entry, TranslationType.ITEM);
            return;
        }

        if (isEquippedItemEntry(entry)) {
            translateEntryTarget(entry, TranslationType.ITEM);
            return;
        }

        if (isGroundItemEntry(entry)) {
            translateEntryTarget(entry, TranslationType.ITEM);
            return;
        }

        if (entry.getPlayer() != null) {
            // No action needed for player entries yet
            return;
        }

        if (entry.getNpc() != null) {
            translateEntryTarget(entry, TranslationType.NPC);
            return;
        }

        if (isObjectEntry(entry)) {
            translateEntryTarget(entry, TranslationType.OBJECT);
        }
    }


    boolean isItemEntry(MenuEntry entry) {
        return entry.getItemId() > 0;
    }

    boolean isEquippedItemEntry(MenuEntry entry) {
        Widget widget = entry.getWidget();
        return widget != null && widget.getId() >= 25362447 && widget.getId() <= 25362457;
    }

    private boolean isGroundItemEntry(MenuEntry entry) {
        return entry.getType() == MenuAction.EXAMINE_ITEM_GROUND ||
                entry.getType() == MenuAction.GROUND_ITEM_THIRD_OPTION ||
                entry.getType() == MenuAction.GROUND_ITEM_FOURTH_OPTION ||
                entry.getType() == MenuAction.GROUND_ITEM_FIFTH_OPTION;
    }

    boolean isObjectEntry(MenuEntry entry) {
        return entry.getIdentifier() > 0 &&
                entry.getType() != MenuAction.CC_OP &&
                entry.getType() != MenuAction.RUNELITE &&
                entry.getType() != MenuAction.WALK &&
                entry.getType() != MenuAction.CC_OP_LOW_PRIORITY;
    }

    void translateEntryTarget(MenuEntry entry, TranslationType type) {
        DisplayText text = menuTargets.computeIfAbsent(entry, key -> new DisplayText());
        String original = text.source(entry.getTarget());
        entry.setTarget(text.display(englishOverride ? original
                : translationManager.getTranslationFromUnfilteredText(type, original)));
    }

    @Subscribe
    public void onWidgetLoaded(WidgetLoaded event) {
        translateAfterRender();
    }

    private void translateAfterRender() { dialoguePending = true; }

    @Subscribe
    public void onClientTick(ClientTick event) {
        restorePendingText();
        if (!active) return;
        boolean previousOverride = englishOverride;
        boolean reset = previewReset.getAndSet(false);
        boolean toggled = (pendingToggles.getAndSet(0) & 1) != 0;
        if (reset) englishOverride = false;
        else if (config.holdToPreview()) englishOverride = previewHeld.get();
        else if (toggled) englishOverride = !englishOverride;
        boolean refresh = previousOverride != englishOverride || renderedManager != translationManager;
        menuRefreshPending |= refresh;
        renderedManager = translationManager;
        MenuEntry[] entries = client.getMenuEntries();
        if (entries != null) {
            java.util.Set<MenuEntry> live = java.util.Collections.newSetFromMap(new IdentityHashMap<>());
            live.addAll(Arrays.asList(entries));
            menuOptions.keySet().retainAll(live);
            menuTargets.keySet().retainAll(live);
            // RuneLite measures menus on opening, not when setMenuEntries is called.
            // Do not replace text inside an already measured, visible menu.
            if (menuRefreshPending && !client.isMenuOpen()) {
                for (MenuEntry entry : entries) {
                    translateMenuOption(entry);
                    if (client.isMenuOpen() || menuTargets.containsKey(entry)) processMenuEntry(entry);
                }
                client.setMenuEntries(entries);
                menuRefreshPending = false;
            }
        }
        String name = client.getLocalPlayer() == null ? null : client.getLocalPlayer().getName();
        if (!java.util.Objects.equals(name, playerName)) {
            playerName = name;
            playerNamePattern = name == null || name.isBlank() ? null : Pattern.compile(Pattern.quote(name), Pattern.CASE_INSENSITIVE);
        }
        if (dialoguePending || refresh || !dialogueTexts.isEmpty()) {
            dialoguePending = false;
            checkWidgetDialogsAndOptions();
        }
        if (panel != null && System.currentTimeMillis() >= nextStatus) {
            nextStatus = System.currentTimeMillis() + 2000;
            String state = !collecting ? "Collection off" : englishOverride || "en".equals(translationManager.getLanguage())
                    ? "Collection paused (English)" : collector == null ? "Collection unavailable/loading" : collector.status();
            panel.status(state);
        }
    }

    private java.util.Set<Widget> liveDialogueWidgets() {
        java.util.Set<Widget> live = java.util.Collections.newSetFromMap(new IdentityHashMap<>());
        Widget npc = client.getWidget(ComponentID.DIALOG_NPC_TEXT);
        Widget player = client.getWidget(ComponentID.DIALOG_PLAYER_TEXT);
        if (npc != null) live.add(npc);
        if (player != null) live.add(player);
        Widget options = client.getWidget(ComponentID.DIALOG_OPTION_OPTIONS);
        if (options != null && options.getChildren() != null) live.addAll(Arrays.asList(options.getChildren()));
        live.remove(null);
        return live;
    }

    private void checkWidgetDialogsAndOptions() {
        java.util.Set<Widget> live = liveDialogueWidgets();
        dialogueTexts.keySet().retainAll(live);
        translateWidgetDialogs();
        translateOptionDialogs();
    }

    private void translateOptionDialogs() {
        Widget optionsWidget = client.getWidget(ComponentID.DIALOG_OPTION_OPTIONS);

        if (optionsWidget != null && optionsWidget.getChildren() != null) {
            for (Widget child : optionsWidget.getChildren()) {
                if (child != null && child.getText() != null) {
                    translateWidget(child);
                }
            }
        }
    }

    private void translateWidgetDialogs() {
        translateDialogText(ComponentID.DIALOG_NPC_TEXT);
        translateDialogText(ComponentID.DIALOG_PLAYER_TEXT);
    }

    private void translateDialogText(int componentId) {
        Widget dialogWidget = client.getWidget(componentId);

        if (dialogWidget != null && dialogWidget.getText() != null) {
            translateWidget(dialogWidget);
        }
    }

    private void translateWidget(Widget widget) {
        DisplayText text = dialogueTexts.computeIfAbsent(widget, key -> new DisplayText());
        TranslationManager manager = translationManager;
        if (text.unchanged(widget.getText(), manager, englishOverride)) return;
        String original = text.source(widget.getText());
        widget.setText(text.display(translateDialogueText(original)));
        text.manager = manager;
        text.english = englishOverride;
    }

    String translateDialogueText(String originalText) {
        if (englishOverride) return originalText;
        String lookupText = originalText.replace("<br>", " ");
        String translated = translationManager.translateTextType(TranslationType.DIALOGUE, lookupText);
        return lookupText.equals(translated) ? originalText : translated;
    }

    // Method to change the language dynamically during runtime
    public synchronized void changeLanguage(String languageCode) {
        if (!active) return;
        long generation = languageGeneration.incrementAndGet();
        // English fallback while loading, so menus never use the previous language accidentally.
        translationManager = new TranslationManager("en");
        pendingLanguage = worker.submit(() -> {
            try {
                TranslationManager loaded = new TranslationManager(languageCode);
                loaded.onMissing((type, text) -> {
                    MissingTextCollector current = collector;
                    KnownTextIndex index = knownIndex;
                    if (!active || !collecting || current == null || index == null || generation != languageGeneration.get()) return;
                    // Dialogue can contain the player's substituted name; never save it unredacted.
                    Pattern name = playerNamePattern;
                    if (type == TranslationType.DIALOGUE && name == null) return;
                    String safe = name == null ? text : name.matcher(text).replaceAll("[player]");
                    String reason = loaded.resemblesTranslatedKey(type, text) ? "POSSIBLE_LOOKUP_MISMATCH"
                            : index.contains(type, safe) ? "KNOWN_UNTRANSLATED" : "UNKNOWN_TEXT";
                    current.record(languageCode, type, safe, reason);
                });
                synchronized (TranslationPlugin.this) {
                    if (active && generation == languageGeneration.get()) translationManager = loaded;
                }
            } catch (Exception ex) { report("Language load failed; using English: " + ex.getMessage()); }
        });
    }
}
