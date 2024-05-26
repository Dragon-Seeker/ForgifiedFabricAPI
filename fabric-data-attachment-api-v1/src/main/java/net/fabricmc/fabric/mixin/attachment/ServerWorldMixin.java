/*
 * Copyright (c) 2016, 2017, 2018, 2019 FabricMC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package net.fabricmc.fabric.mixin.attachment;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedData.Factory;
import net.fabricmc.fabric.impl.attachment.AttachmentPersistentState;

@Mixin(ServerLevel.class)
abstract class ServerWorldMixin {
	@Shadow
	@Final
	private MinecraftServer server;

	@Inject(at = @At("TAIL"), method = "<init>")
	private void createAttachmentsPersistentState(CallbackInfo ci) {
		// Force persistent state creation
		ServerLevel world = (ServerLevel) (Object) this;
		var type = new SavedData.Factory<>(
				() -> new AttachmentPersistentState(world),
				(nbt, wrapperLookup) -> AttachmentPersistentState.read(world, nbt, server.registryAccess()),
				null // Object builder API 12.1.0 and later makes this a no-op
		);
		world.getDataStorage().computeIfAbsent(type, AttachmentPersistentState.ID);
	}
}