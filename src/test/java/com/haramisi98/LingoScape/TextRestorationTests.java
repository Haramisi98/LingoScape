package com.haramisi98.LingoScape;

import net.runelite.api.Client;
import net.runelite.api.MenuEntry;
import net.runelite.api.events.ClientTick;
import net.runelite.api.events.MenuEntryAdded;
import net.runelite.api.widgets.ComponentID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.callback.ClientThread;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

class TextRestorationTests {
    private TranslationPlugin plugin;
    private Widget widget;
    private MenuEntry entry;
    private final AtomicReference<String> dialogue = new AtomicReference<>("Hello<br>there");
    private final AtomicReference<String> option = new AtomicReference<>("Use");
    private final List<Runnable> cleanup = new ArrayList<>();

    @BeforeEach
    void setup() throws Exception {
        plugin = new TranslationPlugin();
        plugin.config = new TranslationConfig() {};
        plugin.client = mock(Client.class);
        plugin.clientThread = mock(ClientThread.class);
        doAnswer(call -> { cleanup.add(call.getArgument(0)); return null; })
                .when(plugin.clientThread).invokeLater(any(Runnable.class));
        widget = mock(Widget.class);
        when(widget.getText()).thenAnswer(call -> dialogue.get());
        doAnswer(call -> { dialogue.set(call.getArgument(0)); return widget; }).when(widget).setText(anyString());
        entry = mock(MenuEntry.class);
        when(entry.getOption()).thenAnswer(call -> option.get());
        doAnswer(call -> { option.set(call.getArgument(0)); return entry; }).when(entry).setOption(anyString());
        when(plugin.client.getMenuEntries()).thenReturn(new MenuEntry[] {entry});
        when(plugin.client.getWidget(ComponentID.DIALOG_NPC_TEXT)).thenReturn(widget);
        start();
        tick();
        assertEquals("Hej der", dialogue.get());
        assertEquals("Brug", option.get());
    }

    private void start() throws Exception {
        plugin.startUp();
        plugin.pendingLanguage.get(10, TimeUnit.SECONDS);
        plugin.translationManager.translations.get(TranslationType.DIALOGUE).put("Hello there", "Hej der");
    }

    private void tick() { plugin.onClientTick(new ClientTick()); }

    @AfterEach
    void stop() { plugin.shutDown(); }

    @Test
    void openMenuKeepsItsMeasuredTextUntilClosed() throws Exception {
        when(plugin.client.isMenuOpen()).thenReturn(true);
        plugin.changeLanguage("en");
        plugin.pendingLanguage.get(10, TimeUnit.SECONDS);
        tick();
        assertEquals("Brug", option.get(), "An open menu must retain the text used to calculate its width");
        assertEquals("Hello<br>there", dialogue.get());
        when(plugin.client.isMenuOpen()).thenReturn(false);
        tick();
        assertEquals("Use", option.get());
    }

    @Test
    void shutdownDoesNotTouchDetachedElements() {
        when(plugin.client.getMenuEntries()).thenReturn(new MenuEntry[0]);
        when(plugin.client.getWidget(ComponentID.DIALOG_NPC_TEXT)).thenReturn(null);
        clearInvocations(widget, entry);
        plugin.shutDown();
        cleanup.forEach(Runnable::run);
        verify(widget, never()).setText(anyString());
        verify(entry, never()).setOption(anyString());
    }

    @Test
    void shutdownPreservesOtherPluginsEdits() {
        dialogue.set("Other plugin's dialogue");
        option.set("Other plugin's action");
        clearInvocations(widget, entry);
        plugin.shutDown();
        cleanup.forEach(Runnable::run);
        assertEquals("Other plugin's dialogue", dialogue.get());
        assertEquals("Other plugin's action", option.get());
        verify(widget, never()).setText(anyString());
        verify(entry, never()).setOption(anyString());
    }

    @Test
    void delayedCleanupCannotUndoNewLifecycleTranslation() throws Exception {
        plugin.shutDown();
        start();
        tick();
        cleanup.forEach(Runnable::run);
        assertEquals("Hej der", dialogue.get());
        assertEquals("Brug", option.get());
        plugin.changeLanguage("en");
        plugin.pendingLanguage.get(10, TimeUnit.SECONDS);
        tick();
        assertEquals("Hello<br>there", dialogue.get());
        assertEquals("Use", option.get());
    }

    @Test
    void reusedEntryRestoresNewActionNotOldAction() throws Exception {
        option.set("Drop");
        MenuEntryAdded event = mock(MenuEntryAdded.class);
        when(event.getMenuEntry()).thenReturn(entry);
        plugin.onMenuEntryAdded(event);
        assertEquals("Smid", option.get());
        plugin.changeLanguage("en");
        plugin.pendingLanguage.get(10, TimeUnit.SECONDS);
        tick();
        assertEquals("Drop", option.get());
    }

    @Test
    void unchangedDialogueIsNotRewrittenButNewContentIsTranslated() {
        clearInvocations(widget);
        tick();
        tick();
        verify(widget, never()).setText(anyString());
        plugin.translationManager.translations.get(TranslationType.DIALOGUE).put("New line", "New translation");
        dialogue.set("New<br>line");
        tick();
        assertEquals("New translation", dialogue.get());
        plugin.shutDown();
        cleanup.forEach(Runnable::run);
        assertEquals("New<br>line", dialogue.get());
    }
}
