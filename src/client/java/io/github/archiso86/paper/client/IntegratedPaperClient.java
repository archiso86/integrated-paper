package io.github.archiso86.paper.client;

import net.fabricmc.api.ClientModInitializer;

public class IntegratedPaperClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		IntegratedPaperConfig.load();
		PaperServerManager.requireServerJar();
	}
}
