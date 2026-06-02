package io.github.archiso86.paper.client.mixin;

import io.github.archiso86.paper.client.PaperServerManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.server.WorldStem;
import net.minecraft.server.packs.repository.PackRepository;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.level.storage.LevelStorageSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Optional;

@Mixin(Minecraft.class)
public class MinecraftMixin {
	@Inject(method = "doWorldLoad", at = @At("HEAD"), cancellable = true)
	private void integratedpaper$openPaperWorld(LevelStorageSource.LevelStorageAccess access, PackRepository packRepository, WorldStem worldStem, Optional<GameRules> gameRules, boolean newWorld, CallbackInfo callbackInfo) {
		callbackInfo.cancel();
		worldStem.close();
		PaperServerManager.openWorld((Minecraft) (Object) this, access);
	}

	@Inject(method = "disconnectFromWorld", at = @At("HEAD"), cancellable = true)
	private void integratedpaper$returnToSingleplayerScreen(Component reason, CallbackInfo callbackInfo) {
		if (!PaperServerManager.hasCurrentServer()) {
			return;
		}

		callbackInfo.cancel();
		Minecraft minecraft = (Minecraft) (Object) this;
		if (minecraft.level != null) {
			minecraft.level.disconnect(reason);
		}
		PaperServerManager.stopCurrentServerAsync(minecraft, PaperServerManager.singleplayerScreen());
		minecraft.disconnectWithProgressScreen();
	}

	@Inject(method = "disconnect(Lnet/minecraft/client/gui/screens/Screen;ZZ)V", at = @At("HEAD"))
	private void integratedpaper$stopPaperOnDisconnect(Screen screen, boolean keepResourcePacks, boolean closeIntegratedServer, CallbackInfo callbackInfo) {
		if (closeIntegratedServer) {
			PaperServerManager.stopCurrentServerAsync(null, null);
		}
	}

	@Inject(method = "destroy", at = @At("HEAD"))
	private void integratedpaper$stopPaperOnDestroy(CallbackInfo callbackInfo) {
		PaperServerManager.stopCurrentServerForExit();
	}

	@Inject(method = "stop", at = @At("HEAD"))
	private void integratedpaper$stopPaperOnStop(CallbackInfo callbackInfo) {
		PaperServerManager.stopCurrentServerForExit();
	}
}
