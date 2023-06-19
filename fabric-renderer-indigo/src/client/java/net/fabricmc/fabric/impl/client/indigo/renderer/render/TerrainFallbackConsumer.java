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

package net.fabricmc.fabric.impl.client.indigo.renderer.render;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.fabricmc.fabric.api.renderer.v1.material.RenderMaterial;
import net.fabricmc.fabric.api.renderer.v1.mesh.QuadEmitter;
import net.fabricmc.fabric.api.renderer.v1.model.ModelHelper;
import net.fabricmc.fabric.api.renderer.v1.render.RenderContext;
import net.fabricmc.fabric.api.renderer.v1.render.RenderContext.QuadTransform;
import net.fabricmc.fabric.api.util.TriState;
import net.fabricmc.fabric.impl.client.indigo.renderer.IndigoRenderer;
import net.fabricmc.fabric.impl.client.indigo.renderer.aocalc.AoCalculator;
import net.fabricmc.fabric.impl.client.indigo.renderer.mesh.EncodingFormat;
import net.fabricmc.fabric.impl.client.indigo.renderer.mesh.MutableQuadViewImpl;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Consumer for vanilla baked models. Generally intended to give visual results matching a vanilla render,
 * however there could be subtle (and desirable) lighting variations so is good to be able to render
 * everything consistently.
 *
 * <p>Also, the API allows multi-part models that hold multiple vanilla models to render them without
 * combining quad lists, but the vanilla logic only handles one model per block. To route all of
 * them through vanilla logic would require additional hooks.
 *
 *  <p>Works by copying the quad data to an "editor" quad held in the instance,
 *  where all transformations are applied before buffering. Transformations should be
 *  the same as they would be in a vanilla render - the editor is serving mainly
 *  as a way to access vertex data without magical numbers. It also allows a consistent interface
 *  for downstream tesselation routines.
 *
 *  <p>Another difference from vanilla render is that all transformation happens before the
 *  vertex data is sent to the byte buffer.  Generally POJO array access will be faster than
 *  manipulating the data via NIO.
 */
public abstract class TerrainFallbackConsumer extends AbstractQuadRenderer implements RenderContext.BakedModelConsumer {
	private static final RenderMaterial MATERIAL_FLAT = IndigoRenderer.INSTANCE.materialFinder().ambientOcclusion(TriState.FALSE).find();
	private static final RenderMaterial MATERIAL_SHADED = IndigoRenderer.INSTANCE.materialFinder().find();

	TerrainFallbackConsumer(BlockRenderInfo blockInfo, Function<RenderType, VertexConsumer> bufferFunc, AoCalculator aoCalc, QuadTransform transform) {
		super(blockInfo, bufferFunc, aoCalc, transform);
	}

	private final MutableQuadViewImpl editorQuad = new MutableQuadViewImpl() {
		{
			data = new int[EncodingFormat.TOTAL_STRIDE];
			material(MATERIAL_SHADED);
		}

		@Override
		public QuadEmitter emit() {
			// should not be called
			throw new UnsupportedOperationException("Fallback consumer does not support .emit()");
		}
	};

	@Override
	public void accept(BakedModel bakedModel) {
		accept(bakedModel, blockInfo.blockState);
	}

	@Override
	public void accept(BakedModel model, @Nullable BlockState blockState) {
		final Supplier<RandomSource> random = blockInfo.randomSupplier;
		final RenderMaterial defaultMaterial = model.useAmbientOcclusion() ? MATERIAL_SHADED : MATERIAL_FLAT;

		for (int i = 0; i <= ModelHelper.NULL_FACE_ID; i++) {
			final Direction cullFace = ModelHelper.faceFromIndex(i);
			final List<BakedQuad> quads = model.getQuads(blockState, cullFace, random.get());
			final int count = quads.size();

			if (count != 0) {
				for (int j = 0; j < count; j++) {
					final BakedQuad q = quads.get(j);
					renderQuad(q, cullFace, defaultMaterial);
				}
			}
		}
	}

	private void renderQuad(BakedQuad quad, Direction cullFace, RenderMaterial defaultMaterial) {
		final MutableQuadViewImpl editorQuad = this.editorQuad;
		editorQuad.fromVanilla(quad, defaultMaterial, cullFace);

		renderQuad(editorQuad, true);
	}
}
