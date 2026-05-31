package io.github.archiso86.paper.client;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.gui.screens.Screen;

public final class IntegratedPaperModMenu implements ModMenuApi {

    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        if (!FabricLoader.getInstance().isModLoaded("yet_another_config_lib_v3")) {
            return parent -> parent;
        }

        return IntegratedPaperYaclConfig::create;
    }

    private static final class IntegratedPaperYaclConfig {

        private static Screen create(Screen parent) {
            return IntegratedPaperConfigScreen.create(parent);
        }
    }
}
