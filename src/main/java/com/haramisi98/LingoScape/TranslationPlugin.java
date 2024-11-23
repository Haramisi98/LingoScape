package com.haramisi98.LingoScape;

import com.haramisi98.LingoScape.TranslationManager.TranslationManager;
import com.haramisi98.LingoScape.TranslationManager.TranslationType;
import net.runelite.api.Client;
import net.runelite.api.MenuAction;
import net.runelite.api.MenuEntry;
import net.runelite.api.events.MenuEntryAdded;
import net.runelite.api.events.MenuOpened;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.eventbus.Subscribe;

import javax.inject.Inject;
import java.util.Arrays;

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
        // Assume the default language is English ("en")
        translationManager = new TranslationManager("dk");
    }

    @Override
    protected void shutDown() throws Exception {
        // Cleanup if necessary, currently no resources to clean up
    }

    // THIS TRANSLATES, NPC MENU OPTIONS BUT NOT NPC NAMES
    // So just menu options basically.
    @Subscribe
    public void onMenuEntryAdded(MenuEntryAdded event)
    {
        try {
            String eng = event.getMenuEntry().getOption();
            String translatedText = translationManager.translate(TranslationType.MENU , eng);

            event.getMenuEntry().setOption(event.getOption().replace(eng, translatedText));

        } catch (Exception e){
        }
    }

    // NPC Names, Item names, NPC DIALOGE! SKILLING MENU, Initial out lay!

    @Subscribe
    public void onMenuOpened(MenuOpened event)
    {
        MenuEntry[] menuEntries = client.getMenuEntries();
        MenuEntry[] newMenuEntries = Arrays.copyOf(menuEntries, menuEntries.length);

        for (int idx = 1; idx < newMenuEntries.length; idx++) {
            MenuEntry entry = newMenuEntries[idx];

            //item
            if (entry.getItemId() > 0) {
                String translatedText = translationManager.translate(TranslationType.ITEM, entry.getTarget());
                entry.setTarget(translatedText);
            }

            //equipped item
            if (entry.getWidget() != null && entry.getWidget().getId() <= 25362457 && entry.getWidget().getId() >= 25362447) {

            }
            //ground items
            if (entry.getType() == MenuAction.EXAMINE_ITEM_GROUND | entry.getType() == MenuAction.GROUND_ITEM_THIRD_OPTION ) {

            }

            //not item
            else if (entry.getItemId() == -1) {
                //player
                if (entry.getPlayer() != null) {
                }
                //npc
                else if (entry.getNpc() != null) {

                }
                //object
                else if (entry.getIdentifier() > 0 & entry.getType() != MenuAction.CC_OP & entry.getType() != MenuAction.RUNELITE & entry.getType() != MenuAction.WALK && entry.getType() != MenuAction.CC_OP_LOW_PRIORITY) {

                }
            }
        }

        // THIS Is the part that overWrites the gameClient!
        client.setMenuEntries(newMenuEntries);
    }


    // Additional methods to change language during runtime
    public void changeLanguage(String languageCode) {
        translationManager.setLanguage(languageCode);
    }
}