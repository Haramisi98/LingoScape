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

    @Test
    void holdPreviewEndsOnReleaseFocusLossAndModeChange() {
        plugin.config = new TranslationConfig() {
            @Override public boolean holdToPreview() { return true; }
        };
        plugin.client = mock(net.runelite.api.Client.class);
        plugin.keyManager = mock(net.runelite.client.input.KeyManager.class);
        startPlugin();
        org.mockito.ArgumentCaptor<net.runelite.client.input.KeyListener> capture =
                org.mockito.ArgumentCaptor.forClass(net.runelite.client.input.KeyListener.class);
        verify(plugin.keyManager).registerKeyListener(capture.capture());
        net.runelite.client.input.KeyListener listener = capture.getValue();
        java.awt.Canvas canvas = new java.awt.Canvas();
        java.awt.event.KeyEvent press = spy(new java.awt.event.KeyEvent(canvas,
                java.awt.event.KeyEvent.KEY_PRESSED, 1, 0, java.awt.event.KeyEvent.VK_TAB, '\t'));
        java.awt.event.KeyEvent release = spy(new java.awt.event.KeyEvent(canvas,
                java.awt.event.KeyEvent.KEY_RELEASED, 2, 0, java.awt.event.KeyEvent.VK_TAB, '\t'));
        // Native events have an extended code; Java-constructed events leave it undefined.
        when(press.getExtendedKeyCode()).thenReturn(java.awt.event.KeyEvent.VK_TAB);
        when(release.getExtendedKeyCode()).thenReturn(java.awt.event.KeyEvent.VK_TAB);
        listener.keyPressed(press);
        plugin.onClientTick(new net.runelite.api.events.ClientTick());
        assertEquals(true, plugin.isEnglishOverride());
        listener.keyReleased(release);
        plugin.onClientTick(new net.runelite.api.events.ClientTick());
        assertEquals(false, plugin.isEnglishOverride());
        listener.keyPressed(press);
        plugin.onClientTick(new net.runelite.api.events.ClientTick());
        listener.focusLost();
        plugin.onClientTick(new net.runelite.api.events.ClientTick());
        assertEquals(false, plugin.isEnglishOverride());
        listener.keyPressed(press);
        plugin.onClientTick(new net.runelite.api.events.ClientTick());
        plugin.config = new TranslationConfig() {};
        ConfigChanged changed = mock(ConfigChanged.class);
        when(changed.getGroup()).thenReturn(TranslationConfig.GROUP);
        when(changed.getKey()).thenReturn("holdToPreview");
        plugin.onConfigChanged(changed);
        plugin.onClientTick(new net.runelite.api.events.ClientTick());
        assertEquals(false, plugin.isEnglishOverride());
    }

    @Test
    void hotkeyRestoresVisibleMenuAndDialogueWithoutChangingLanguage() {
        plugin.client = mock(net.runelite.api.Client.class);
        plugin.keyManager = mock(net.runelite.client.input.KeyManager.class);
        plugin.clientThread = mock(net.runelite.client.callback.ClientThread.class);
        doAnswer(call -> { ((Runnable) call.getArgument(0)).run(); return null; })
                .when(plugin.clientThread).invokeLater(any(Runnable.class));
        startPlugin();
        org.mockito.ArgumentCaptor<net.runelite.client.input.KeyListener> listener =
                org.mockito.ArgumentCaptor.forClass(net.runelite.client.input.KeyListener.class);
        verify(plugin.keyManager).registerKeyListener(listener.capture());
        net.runelite.client.util.HotkeyListener hotkey = (net.runelite.client.util.HotkeyListener) listener.getValue();
        java.util.concurrent.atomic.AtomicReference<String> option = new java.util.concurrent.atomic.AtomicReference<>("Use");
        when(menuEntry.getOption()).thenAnswer(call -> option.get());
        doAnswer(call -> { option.set(call.getArgument(0)); return menuEntry; }).when(menuEntry).setOption(anyString());
        when(plugin.client.getMenuEntries()).thenReturn(new MenuEntry[] {menuEntry});
        when(plugin.client.isMenuOpen()).thenReturn(false);
        when(menuEntry.getItemId()).thenReturn(1);
        java.util.concurrent.atomic.AtomicReference<String> target = new java.util.concurrent.atomic.AtomicReference<>("<col=ffff00>Sword</col>");
        when(menuEntry.getTarget()).thenAnswer(call -> target.get());
        doAnswer(call -> { target.set(call.getArgument(0)); return menuEntry; }).when(menuEntry).setTarget(anyString());
        plugin.translationManager.translations.get(TranslationType.ITEM).put("Sword", "Test sword");
        Widget widget = mock(Widget.class);
        java.util.concurrent.atomic.AtomicReference<String> dialogue = new java.util.concurrent.atomic.AtomicReference<>("Hello<br>there");
        when(widget.getText()).thenAnswer(call -> dialogue.get());
        doAnswer(call -> { dialogue.set(call.getArgument(0)); return widget; }).when(widget).setText(anyString());
        when(plugin.client.getWidget(net.runelite.api.widgets.ComponentID.DIALOG_NPC_TEXT)).thenReturn(widget);
        plugin.translationManager.translations.get(TranslationType.DIALOGUE).put("Hello there", "Hej der");
        TranslationManager selected = plugin.translationManager;
        plugin.processMenuEntry(menuEntry);
        plugin.onClientTick(new net.runelite.api.events.ClientTick());
        assertEquals("Brug", option.get());
        assertEquals("<col=ffff00>Test sword</col>", target.get());
        assertEquals("Hej der", dialogue.get());
        hotkey.hotkeyPressed();
        plugin.onClientTick(new net.runelite.api.events.ClientTick());
        assertEquals("Use", option.get());
        assertEquals("<col=ffff00>Sword</col>", target.get());
        assertEquals("Hello<br>there", dialogue.get());
        org.junit.jupiter.api.Assertions.assertSame(selected, plugin.translationManager);
        assertEquals(TranslationConfig.Language.DANISH, plugin.config.language());
        hotkey.hotkeyPressed();
        plugin.onClientTick(new net.runelite.api.events.ClientTick());
        assertEquals("Brug", option.get());
        assertEquals("Hej der", dialogue.get());
        dialogue.set("New<br>line");
        plugin.onClientTick(new net.runelite.api.events.ClientTick());
        hotkey.hotkeyPressed();
        plugin.onClientTick(new net.runelite.api.events.ClientTick());
        assertEquals("New<br>line", dialogue.get());
        hotkey.hotkeyPressed();
        plugin.onClientTick(new net.runelite.api.events.ClientTick());
        plugin.shutDown();
        assertEquals("Use", option.get());
        assertEquals("<col=ffff00>Sword</col>", target.get());
        verify(plugin.keyManager).unregisterKeyListener(hotkey);
    }

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
