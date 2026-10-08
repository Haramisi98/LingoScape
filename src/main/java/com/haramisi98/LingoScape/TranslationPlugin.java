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
        languageGeneration.incrementAndGet();
        if (worker != null) {
            ScheduledExecutorService closing = worker;
            MissingTextCollector closingCollector = collector;
            closing.execute(() -> {
                if (closingCollector != null) try { closingCollector.flush(); }
                catch (Exception ex) { LOG.warning("Final collection save failed: " + ex.getMessage()); }
            });
            closing.shutdown();
        }
        collector = null; knownIndex = null;
        translationManager = new TranslationManager("en");
        if (toolbar != null) SwingUtilities.invokeLater(() -> {
            if (navigation != null) toolbar.removeNavigation(navigation);
            navigation = null; panel = null;
        });
    }

    // Translates NPC menu options, but not NPC names
    @Subscribe
    public void onMenuEntryAdded(MenuEntryAdded event) {
        try {
            MenuEntry menuEntry = event.getMenuEntry();
            String originalOption = menuEntry.getOption();
            String translatedOption = translationManager.translateTextType(TranslationType.MENU, originalOption);
            menuEntry.setOption(translatedOption);
        } catch (Exception ignored) {
            // Silently handle any exceptions
        }
    }

    // Handles translations for NPC names, item names, and other menu entries
    @Subscribe
    public void onMenuOpened(MenuOpened event) {
        MenuEntry[] menuEntries = event.getMenuEntries();
        MenuEntry[] updatedEntries = Arrays.copyOf(menuEntries, menuEntries.length);

        for (int idx = 1; idx < updatedEntries.length; idx++) {
            MenuEntry entry = updatedEntries[idx];
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
        String translatedText = translationManager.getTranslationFromUnfilteredText(type, entry.getTarget());
        entry.setTarget(translatedText);
    }

    @Subscribe
    public void onWidgetLoaded(WidgetLoaded event) {
        translateAfterRender();
    }

    private void translateAfterRender() { dialoguePending = true; }

    @Subscribe
    public void onClientTick(ClientTick event) {
        if (!active) return;
        String name = client.getLocalPlayer() == null ? null : client.getLocalPlayer().getName();
        if (!java.util.Objects.equals(name, playerName)) {
            playerName = name;
            playerNamePattern = name == null || name.isBlank() ? null : Pattern.compile(Pattern.quote(name), Pattern.CASE_INSENSITIVE);
        }
        if (dialoguePending) {
            dialoguePending = false;
            checkWidgetDialogsAndOptions();
        }
        if (panel != null && System.currentTimeMillis() >= nextStatus) {
            nextStatus = System.currentTimeMillis() + 2000;
            String state = !collecting ? "Collection off" : "en".equals(translationManager.getLanguage())
                    ? "Collection paused (English)" : collector == null ? "Collection unavailable/loading" : collector.status();
            panel.status(state);
        }
    }

    private void checkWidgetDialogsAndOptions() {
        translateWidgetDialogs();
        translateOptionDialogs();
    }

    private void translateOptionDialogs() {
        Widget optionsWidget = client.getWidget(ComponentID.DIALOG_OPTION_OPTIONS);

        if (optionsWidget != null && optionsWidget.getChildren() != null) {
            for (Widget child : optionsWidget.getChildren()) {
                if (child != null && child.getText() != null) {
                    child.setText(translateDialogueText(child.getText()));
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
            dialogWidget.setText(translateDialogueText(dialogWidget.getText()));
        }
    }

    String translateDialogueText(String originalText) {
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
