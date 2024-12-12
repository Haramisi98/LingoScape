package com.haramisi98.LingoScape;

import com.haramisi98.LingoScape.TranslationManager.TranslationManager;
import com.haramisi98.LingoScape.TranslationManager.TranslationType;
import net.runelite.api.Actor;
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
    private TranslationManager translationManager;

    @Inject
    private Client client;

    @Override
    protected void startUp() throws Exception {
        // TODO Assume the default language is English ("en")
        translationManager = new TranslationManager("dk");
    }

    @Override
    protected void shutDown() throws Exception {
        // TODO Cleanup if necessary, currently no resources to clean up
    }

    // THIS TRANSLATES, NPC MENU OPTIONS BUT NOT NPC NAMES
    // So just menu options basically.
    @Subscribe
    public void onMenuEntryAdded(MenuEntryAdded event)
    {
        try {
            String eng = event.getMenuEntry().getOption();
            String translatedText = translationManager.translateTextType(TranslationType.MENU , eng);

            event.getMenuEntry().setOption(event.getOption().replace(eng, translatedText));

        } catch (Exception e){
        }
    }

    // NPC Names, Item names
    @Subscribe
    public void onMenuOpened(MenuOpened event)
    {
        MenuEntry[] menuEntries = event.getMenuEntries();
        MenuEntry[] newMenuEntries = Arrays.copyOf(menuEntries, menuEntries.length);

        for (int idx = 1; idx < newMenuEntries.length; idx++) {
            MenuEntry entry = newMenuEntries[idx];

            // Item
            // Items are expected to have <col> tag with them.
            if (entry.getItemId() > 0) {
                String translatedText = translationManager.getTranslationFromUnfilteredText(TranslationType.ITEM, entry.getTarget());
                entry.setTarget(translatedText);
                continue;
            }

            // equipped item
            // TODO Widget has some translateable text! right click options.
            if (entry.getWidget() != null && entry.getWidget().getId() <= 25362457 && entry.getWidget().getId() >= 25362447) {
                String translatedText = translationManager.getTranslationFromUnfilteredText(TranslationType.ITEM, entry.getTarget());
                entry.setTarget(translatedText);
                continue;
            }

            // ground items
            if (entry.getType() == MenuAction.EXAMINE_ITEM_GROUND | entry.getType() == MenuAction.GROUND_ITEM_THIRD_OPTION | entry.getType() == MenuAction.GROUND_ITEM_FOURTH_OPTION | entry.getType() == MenuAction.GROUND_ITEM_FIFTH_OPTION) {
                String translatedText = translationManager.getTranslationFromUnfilteredText(TranslationType.ITEM, entry.getTarget());
                entry.setTarget(translatedText);
                continue;
            }

            // player, I guess nothing here yet!
            if (entry.getPlayer() != null) {
                continue;
            }

            // npc names
            // TODO Not all npc names are this color, General solution is needed for the Col tag.
            if (entry.getNpc() != null) {
                String prefix = "<col=ffff00>";
                String translatedText = translationManager.getTranslationFromUnfilteredText(TranslationType.NPC, entry.getTarget().toUpperCase());
                if (translatedText.contains(entry.getTarget().toUpperCase()))
                {
                    continue;
                }
                entry.setTarget(prefix + translatedText);
                continue;
            }

            // object
            // Although I cant name any instance where there is a different color, it would be wise to use a generalized solution here.
            if (entry.getIdentifier() > 0 & entry.getType() != MenuAction.CC_OP & entry.getType() != MenuAction.RUNELITE & entry.getType() != MenuAction.WALK && entry.getType() != MenuAction.CC_OP_LOW_PRIORITY) {
                String prefix = "<col=00ffff>";
                String translatedText = translationManager.getTranslationFromUnfilteredText(TranslationType.OBJECT, entry.getTarget().toUpperCase());
                if (!translatedText.startsWith(prefix)) {
                    translatedText = prefix + translatedText;
                }
                if (translatedText == entry.getTarget().toUpperCase())
                {
                    continue;
                }
                entry.setTarget(translatedText);
                continue;
            }
        }

        // THIS Is the part that sets the translated text!
        event.setMenuEntries(newMenuEntries);
    }

    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();

    // This is needed since Events are called before the actual render happens.
    // This means that without the wait, we won`t be able to request the rendered text since they are not there.
    private void translateAfterRender()
    {
        scheduler.schedule(this::checkWidgetDialogsAndOptions, 5, TimeUnit.MILLISECONDS);
    }

    private void checkWidgetDialogsAndOptions()
    {
        checkWidgetDialogs();
        checkWidgetOptionDialogs();
    }


    @Subscribe
    public void onWidgetLoaded(WidgetLoaded event)
    {
        translateAfterRender();
    }


    // For translating Option elements
    private void checkWidgetOptionDialogs()
    {
        Widget playerOptionsWidget = client.getWidget(ComponentID.DIALOG_OPTION_OPTIONS);
        Widget[] optionWidgets = playerOptionsWidget.getChildren();
        if (optionWidgets != null) {
            for (Widget i : optionWidgets) {
                String optionText = i.getText() != null ? i.getText().replace("<br>", " ") : null;
                if (optionText != null) {
                    i.setText(translationManager.translateTextType(TranslationType.DIALOGUE, optionText));
                }
            }
        }
    }

    // For translating dialogs, both player, and NPC REFACTOR into separate methods!
    private void checkWidgetDialogs()
    {
        Widget npcTextWidget = client.getWidget(ComponentID.DIALOG_NPC_TEXT);
        String npcDialogText = (npcTextWidget != null) ? npcTextWidget.getText() : null;
        Widget playerTextWidget = client.getWidget(ComponentID.DIALOG_PLAYER_TEXT);
        String playerDialogText = (playerTextWidget != null) ? playerTextWidget.getText() : null;
        String npcdialogue = npcDialogText != null ? npcDialogText.replace("<br>", " ") : null;
        String playerdialogue = playerDialogText != null ? playerDialogText.replace("<br>", " ") : null;

        if (npcdialogue!= null) {
            npcTextWidget.setText(translationManager.translateTextType(TranslationType.DIALOGUE, npcdialogue));
        }
        if (playerdialogue!= null) {
            playerTextWidget.setText(translationManager.translateTextType(TranslationType.DIALOGUE, playerdialogue));
        }
    }

    // Additional methods to change language during runtime
    public void changeLanguage(String languageCode) {
        translationManager.setLanguage(languageCode);
    }
}