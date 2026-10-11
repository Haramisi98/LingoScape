package com.haramisi98.LingoScape;

import net.runelite.api.MenuEntry;
import net.runelite.api.events.MenuEntryAdded;
import net.runelite.client.events.ConfigChanged;
import net.runelite.api.widgets.Widget;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.assertEquals;

class TranslationPluginTests {

    private TranslationPlugin plugin;

    @Mock
    private TranslationManager translationManager;

    @Mock
    private MenuEntry menuEntry;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        plugin = new TranslationPlugin();
        plugin.config = new TranslationConfig() {};
        plugin.translationManager = translationManager;
    }

    private void startPlugin() { plugin.startUp(); awaitLanguage(); }
    private void awaitLanguage() {
        try { if (plugin.pendingLanguage != null) plugin.pendingLanguage.get(10, java.util.concurrent.TimeUnit.SECONDS); }
        catch (Exception ex) { throw new AssertionError(ex); }
    }
    @AfterEach
    void stopPlugin() { plugin.shutDown(); }

    // Tests for processMenuEntry logic
    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "translationMissing"})
    void unavailableDialoguePreservesOriginalLineBreaks(String value) {
        plugin.translationManager = new TranslationManager("dk");
        plugin.translationManager.translations.get(TranslationType.DIALOGUE).put("Hello there", value);
        assertEquals("Hello<br>there", plugin.translateDialogueText("Hello<br>there"));
        assertEquals("Unknown<br>dialogue", plugin.translateDialogueText("Unknown<br>dialogue"));
    }

    @Test
    void validDialogueUsesNormalizedLookup() {
        plugin.translationManager = new TranslationManager("dk");
        plugin.translationManager.translations.get(TranslationType.DIALOGUE).put("Hello there", "Hej der");
        assertEquals("Hej der", plugin.translateDialogueText("Hello<br>there"));
    }

    @Test
    void testProcessMenuEntry_TranslatesItemEntry() {
        when(menuEntry.getItemId()).thenReturn(1);
        when(menuEntry.getTarget()).thenReturn("Sword");
        when(translationManager.getTranslationFromUnfilteredText(TranslationType.ITEM, "Sword"))
                .thenReturn("Épée");

        plugin.processMenuEntry(menuEntry);

        verify(menuEntry).setTarget("Épée");
    }

    @Test
    void testProcessMenuEntry_TranslatesObjectName() {
        when(menuEntry.getIdentifier()).thenReturn(123);
        when(menuEntry.getTarget()).thenReturn("Tree");
        when(translationManager.getTranslationFromUnfilteredText(TranslationType.OBJECT, "Tree"))
                .thenReturn("Arbre");

        plugin.processMenuEntry(menuEntry);

        verify(menuEntry).setTarget("Arbre");
    }

    @Test
    void testChangeLanguage() {
        startPlugin();
        plugin.changeLanguage("dk");
        awaitLanguage();
        assertEquals("Brug", plugin.translationManager.translateTextType(TranslationType.MENU, "Use"));
        plugin.changeLanguage("en");
        awaitLanguage();
        assertEquals("Use", plugin.translationManager.translateTextType(TranslationType.MENU, "Use"));
    }

    @ParameterizedTest
    @CsvSource({"Use,Brug", "Drop,Smid", "Examine,Undersøg", "Open,Åbn",
            "Walk here,Gå herhen", "Take,Tag", "Talk-to,Tal med", "Eat,Spis",
            "Cancel,Annuller", "Unknown action,Unknown action", "Light,Light"})
    void menuEventsUseBundledDanishTranslations(String english, String danish) {
        startPlugin();
        MenuEntryAdded event = mock(MenuEntryAdded.class);
        when(event.getMenuEntry()).thenReturn(menuEntry);
        when(menuEntry.getOption()).thenReturn(english);

        plugin.onMenuEntryAdded(event);

        verify(menuEntry).setOption(danish);
        verify(menuEntry, never()).setTarget(anyString());
    }

    @Test
    void startupUsesSavedEnglishSetting() {
        plugin.config = mock(TranslationConfig.class);
        when(plugin.config.language()).thenReturn(TranslationConfig.Language.ENGLISH);
        startPlugin();
        assertEquals("Use", plugin.translationManager.translateTextType(TranslationType.MENU, "Use"));
    }

    @Test
    void languageSettingChangesApplyWithoutRestart() {
        startPlugin();
        assertEquals("Brug", plugin.translationManager.translateTextType(TranslationType.MENU, "Use"));
        plugin.config = mock(TranslationConfig.class);
        when(plugin.config.language()).thenReturn(TranslationConfig.Language.ENGLISH);
        ConfigChanged event = mock(ConfigChanged.class);
        when(event.getGroup()).thenReturn(TranslationConfig.GROUP);
        when(event.getKey()).thenReturn("language");
        plugin.onConfigChanged(event);
        awaitLanguage();
        assertEquals("Use", plugin.translationManager.translateTextType(TranslationType.MENU, "Use"));

        when(plugin.config.language()).thenReturn(TranslationConfig.Language.DANISH);
        plugin.onConfigChanged(event);
        awaitLanguage();
        assertEquals("Brug", plugin.translationManager.translateTextType(TranslationType.MENU, "Use"));
    }

    @Test
    void unrelatedSettingsDoNotReloadLanguage() {
        ConfigChanged event = mock(ConfigChanged.class);
        when(event.getGroup()).thenReturn("anotherPlugin");
        when(event.getKey()).thenReturn("language");
        plugin.onConfigChanged(event);
        awaitLanguage();
        org.junit.jupiter.api.Assertions.assertSame(translationManager, plugin.translationManager);

        when(event.getGroup()).thenReturn(TranslationConfig.GROUP);
        when(event.getKey()).thenReturn("anotherSetting");
        plugin.onConfigChanged(event);
        awaitLanguage();
        org.junit.jupiter.api.Assertions.assertSame(translationManager, plugin.translationManager);
    }

    @Test
    void testIsItemEntry_ReturnsTrue() {
        when(menuEntry.getItemId()).thenReturn(1);
        boolean result = plugin.isItemEntry(menuEntry);
        assert (result);
    }

    @Test
    void testIsEquippedItemEntry_ReturnsTrue() {
        Widget mockWidget = mock(Widget.class);
        when(menuEntry.getWidget()).thenReturn(mockWidget);
        when(mockWidget.getId()).thenReturn(25362450);

        boolean result = plugin.isEquippedItemEntry(menuEntry);
        assert (result);
    }

    @Test
    void testIsObjectEntry_ReturnsTrue() {
        when(menuEntry.getIdentifier()).thenReturn(42);

        boolean result = plugin.isObjectEntry(menuEntry);
        assert (result);
    }
}
