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

package net.fabricmc.fabric.impl.client.rendering;

import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;

import java.util.HashMap;
import java.util.function.BiConsumer;

public final class BlockEntityRendererRegistryImpl {
	private static final HashMap<BlockEntityType<?>, BlockEntityRendererProvider<?>> MAP = new HashMap<>();
	private static BiConsumer<BlockEntityType<?>, BlockEntityRendererProvider<?>> handler = MAP::put;

	public static <E extends BlockEntity> void register(BlockEntityType<E> blockEntityType, BlockEntityRendererProvider<? super E> blockEntityRendererFactory) {
		handler.accept(blockEntityType, blockEntityRendererFactory);
	}

	public static void setup(BiConsumer<BlockEntityType<?>, BlockEntityRendererProvider<?>> vanillaHandler) {
		MAP.forEach(vanillaHandler);
		handler = vanillaHandler;
	}

	private BlockEntityRendererRegistryImpl() {
	}
}
