package com.haramisi98.LingoScape;

import net.runelite.client.RuneLite;
import net.runelite.client.externalplugins.ExternalPluginManager;

public class LingoScapeLauncher {
    public static void main(String[] args) throws Exception {
        ExternalPluginManager.loadBuiltin(TranslationPlugin.class);
        RuneLite.main(args);
    }
}
