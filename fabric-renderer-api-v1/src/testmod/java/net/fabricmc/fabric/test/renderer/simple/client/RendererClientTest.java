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

package net.fabricmc.fabric.test.renderer.simple.client;

import net.fabricmc.fabric.api.blockrenderlayer.v1.BlockRenderLayerMap;
import net.fabricmc.fabric.api.client.model.ModelLoadingRegistry;
import net.fabricmc.fabric.test.renderer.simple.FrameBlock;
import net.fabricmc.fabric.test.renderer.simple.RendererTest;
import net.minecraft.client.renderer.RenderType;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

import static net.fabricmc.fabric.test.renderer.simple.RendererTest.id;

public final class RendererClientTest {

    public static void onInitializeClient(FMLClientSetupEvent event) {
        ModelLoadingRegistry.INSTANCE.registerResourceProvider(manager -> new FrameModelResourceProvider());
        ModelLoadingRegistry.INSTANCE.registerVariantProvider(manager -> new PillarModelVariantProvider());

        for (RegistryObject<FrameBlock> frameBlock : RendererTest.FRAMES) {
            // We don't specify a material for the frame mesh,
            // so it will use the default material, i.e. the one from BlockRenderLayerMap.
            BlockRenderLayerMap.INSTANCE.putBlock(frameBlock.get(), RenderType.cutoutMipped());

            String itemPath = ForgeRegistries.ITEMS.getKey(frameBlock.get().asItem()).getPath();
            FrameModelResourceProvider.FRAME_MODELS.add(id("item/" + itemPath));
        }

        FrameModelResourceProvider.FRAME_MODELS.add(id("block/frame"));
    }
}
