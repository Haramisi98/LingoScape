package com.haramisi98.LingoScape;

import net.runelite.api.MenuEntry;
import net.runelite.api.widgets.Widget;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import static org.mockito.Mockito.*;

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
        plugin.translationManager = translationManager;
    }

    // Tests for processMenuEntry logic
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
        plugin.changeLanguage("fr");
        verify(translationManager).setLanguage("fr");
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
