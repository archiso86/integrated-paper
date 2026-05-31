package io.github.archiso86.paper.client;

import dev.isxander.yacl3.api.ConfigCategory;
import dev.isxander.yacl3.api.Option;
import dev.isxander.yacl3.api.OptionDescription;
import dev.isxander.yacl3.api.YetAnotherConfigLib;
import dev.isxander.yacl3.api.controller.IntegerFieldControllerBuilder;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public final class IntegratedPaperConfigScreen {

    private IntegratedPaperConfigScreen() {}

    public static Screen create(Screen parent) {
        IntegratedPaperConfig config = IntegratedPaperConfig.get();
        return YetAnotherConfigLib.createBuilder()
            .title(Component.literal("Integrated Paper"))
            .category(
                ConfigCategory.createBuilder()
                    .name(Component.literal("Server"))
                    .option(
                        Option.<Integer>createBuilder()
                            .name(Component.literal("Server port"))
                            .description(
                                OptionDescription.of(
                                    Component.literal(
                                        "Port used for the local Paper server."
                                    )
                                )
                            )
                            .binding(
                                IntegratedPaperConfig.DEFAULT_SERVER_PORT,
                                () -> config.serverPort,
                                config::setServerPort
                            )
                            .controller(controller ->
                                IntegerFieldControllerBuilder.create(controller)
                                    .min(IntegratedPaperConfig.MIN_SERVER_PORT)
                                    .max(IntegratedPaperConfig.MAX_SERVER_PORT)
                            )
                            .build()
                    )
                    .build()
            )
            .save(IntegratedPaperConfig::save)
            .build()
            .generateScreen(parent);
    }
}
