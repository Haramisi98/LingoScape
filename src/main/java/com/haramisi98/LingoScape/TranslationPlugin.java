package com.haramisi98.LingoScape;

import com.google.common.annotations.VisibleForTesting;
import net.runelite.api.Client;
import net.runelite.api.MenuAction;
import net.runelite.api.MenuEntry;
import net.runelite.api.events.*;
import net.runelite.api.widgets.ComponentID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.eventbus.Subscribe;

import javax.inject.Inject;
import java.util.Arrays;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@PluginDescriptor(
        name = "LingoScape",
        description = "Translates game text elements to various languages",
        tags = {"translation", "language"},
        enabledByDefault = false
)
public class TranslationPlugin extends Plugin {

    @VisibleForTesting
    TranslationManager translationManager;

    @Inject
    private Client client;

    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();

    @Override
    protected void startUp() {
        translationManager = new TranslationManager("dk"); // Default language is Danish
    }

    @Override
    protected void shutDown() {
        // No resources to clean up currently
    }

    // Translates NPC menu options, but not NPC names
    @Subscribe
    public void onMenuEntryAdded(MenuEntryAdded event) {
        try {
            MenuEntry menuEntry = event.getMenuEntry();
            String originalOption = menuEntry.getOption();
            String translatedOption = translationManager.translateTextType(TranslationType.MENU, originalOption);
            menuEntry.setOption(originalOption.replace(originalOption, translatedOption));
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

    private void translateAfterRender() {
        scheduler.schedule(this::checkWidgetDialogsAndOptions, 5, TimeUnit.MILLISECONDS);
    }

    private void checkWidgetDialogsAndOptions() {
        translateWidgetDialogs();
        translateOptionDialogs();
    }

    private void translateOptionDialogs() {
        Widget optionsWidget = client.getWidget(ComponentID.DIALOG_OPTION_OPTIONS);

        if (optionsWidget != null) {
            for (Widget child : optionsWidget.getChildren()) {
                if (child != null && child.getText() != null) {
                    String text = child.getText().replace("<br>", " ");
                    child.setText(translationManager.translateTextType(TranslationType.DIALOGUE, text));
                }
            }
        }
    }

    private void translateWidgetDialogs() {
        translateDialogText(ComponentID.DIALOG_NPC_TEXT, TranslationType.DIALOGUE);
        translateDialogText(ComponentID.DIALOG_PLAYER_TEXT, TranslationType.DIALOGUE);
    }

    private void translateDialogText(int componentId, TranslationType type) {
        Widget dialogWidget = client.getWidget(componentId);

        if (dialogWidget != null && dialogWidget.getText() != null) {
            String text = dialogWidget.getText().replace("<br>", " ");
            dialogWidget.setText(translationManager.translateTextType(type, text));
        }
    }

    // Method to change the language dynamically during runtime
    public void changeLanguage(String languageCode) {
        translationManager.setLanguage(languageCode);
    }
}