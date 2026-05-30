package io.github.archiso86.paper.client.mixin;

import io.github.archiso86.paper.client.PaperServerManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.worldselection.WorldOpenFlows;
import net.minecraft.world.level.storage.LevelStorageSource;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(WorldOpenFlows.class)
public class WorldOpenFlowsMixin {
	@Shadow
	@Final
	private Minecraft minecraft;

	@Shadow
	@Final
	private LevelStorageSource levelSource;

	@Inject(method = "openWorld", at = @At("HEAD"), cancellable = true)
	private void integratedpaper$openPaperWorld(String levelId, Runnable onCancel, CallbackInfo callbackInfo) {
		callbackInfo.cancel();
		PaperServerManager.openWorld(this.minecraft, this.levelSource, levelId);
	}
}
