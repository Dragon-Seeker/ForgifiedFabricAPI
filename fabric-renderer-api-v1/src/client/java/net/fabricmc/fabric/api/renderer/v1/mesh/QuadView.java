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

package net.fabricmc.fabric.api.renderer.v1.mesh;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import net.fabricmc.fabric.api.renderer.v1.material.RenderMaterial;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.Direction;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector2f;
import org.joml.Vector3f;

/**
 * Interface for reading quad data encoded by {@link MeshBuilder}.
 * Enables models to do analysis, re-texturing or translation without knowing the
 * renderer's vertex formats and without retaining redundant information.
 *
 * <p>Only the renderer should implement or extend this interface.
 */
public interface QuadView {
	/** Count of integers in a conventional (un-modded) block or item vertex. */
	int VANILLA_VERTEX_STRIDE = DefaultVertexFormat.BLOCK.getVertexSize();

	/** Count of integers in a conventional (un-modded) block or item quad. */
	int VANILLA_QUAD_STRIDE = VANILLA_VERTEX_STRIDE * 4;

	/**
	 * Retrieve geometric position, x coordinate.
	 */
	float x(int vertexIndex);

	/**
	 * Retrieve geometric position, y coordinate.
	 */
	float y(int vertexIndex);

	/**
	 * Retrieve geometric position, z coordinate.
	 */
	float z(int vertexIndex);

	/**
	 * Convenience: access x, y, z by index 0-2.
	 */
	float posByIndex(int vertexIndex, int coordinateIndex);

	/**
	 * Pass a non-null target to avoid allocation - will be returned with values.
	 * Otherwise returns a new instance.
	 */
	Vector3f copyPos(int vertexIndex, @Nullable Vector3f target);

	/**
	 * Retrieve vertex color.
	 *
	 * @apiNote The default implementation will be removed in the next breaking release.
	 */
	default int color(int vertexIndex) {
		return spriteColor(vertexIndex, 0);
	}

	/**
	 * Retrieve horizontal texture coordinates.
	 *
	 * @apiNote The default implementation will be removed in the next breaking release.
	 */
	default float u(int vertexIndex) {
		return spriteU(vertexIndex, 0);
	}

	/**
	 * Retrieve vertical texture coordinates.
	 *
	 * @apiNote The default implementation will be removed in the next breaking release.
	 */
	default float v(int vertexIndex) {
		return spriteV(vertexIndex, 0);
	}

	/**
	 * Pass a non-null target to avoid allocation - will be returned with values.
	 * Otherwise returns a new instance.
	 *
	 * @apiNote The default implementation will be removed in the next breaking release.
	 */
	default Vector2f copyUv(int vertexIndex, @Nullable Vector2f target) {
		if (target == null) {
			target = new Vector2f();
		}

		target.set(u(vertexIndex), v(vertexIndex));
		return target;
	}

	/**
	 * Minimum block brightness. Zero if not set.
	 */
	int lightmap(int vertexIndex);

	/**
	 * If false, no vertex normal was provided.
	 * Lighting should use face normal in that case.
	 */
	boolean hasNormal(int vertexIndex);

	/**
	 * Will return {@link Float#NaN} if normal not present.
	 */
	float normalX(int vertexIndex);

	/**
	 * Will return {@link Float#NaN} if normal not present.
	 */
	float normalY(int vertexIndex);

	/**
	 * Will return {@link Float#NaN} if normal not present.
	 */
	float normalZ(int vertexIndex);

	/**
	 * Pass a non-null target to avoid allocation - will be returned with values.
	 * Otherwise returns a new instance. Returns null if normal not present.
	 */
	@Nullable
	Vector3f copyNormal(int vertexIndex, @Nullable Vector3f target);

	/**
	 * If non-null, quad should not be rendered in-world if the
	 * opposite face of a neighbor block occludes it.
	 *
	 * @see MutableQuadView#cullFace(Direction)
	 */
	@Nullable
	Direction cullFace();

	/**
	 * Equivalent to {@link BakedQuad#getDirection()}. This is the face used for vanilla lighting
	 * calculations and will be the block face to which the quad is most closely aligned. Always
	 * the same as cull face for quads that are on a block face, but never null.
	 */
	@NotNull
	Direction lightFace();

	/**
	 * See {@link MutableQuadView#nominalFace(Direction)}.
	 */
	@Nullable
	Direction nominalFace();

	/**
	 * Normal of the quad as implied by geometry. Will be invalid
	 * if quad vertices are not co-planar. Typically computed lazily
	 * on demand and not encoded.
	 *
	 * <p>Not typically needed by models. Exposed to enable standard lighting
	 * utility functions for use by renderers.
	 */
	Vector3f faceNormal();

	/**
	 * Retrieves the material serialized with the quad.
	 */
	RenderMaterial material();

	/**
	 * Retrieves the quad color index serialized with the quad.
	 */
	int colorIndex();

	/**
	 * Retrieves the integer tag encoded with this quad via {@link MutableQuadView#tag(int)}.
	 * Will return zero if no tag was set. For use by models.
	 */
	int tag();

	/**
	 * Reads baked vertex data and outputs standard {@link BakedQuad#getVertices() baked quad vertex data}
	 * in the given array and location.
	 *
	 * @apiNote The default implementation will be removed in the next breaking release.
	 *
	 * @param target Target array for the baked quad data.
	 *
	 * @param targetIndex Starting position in target array - array must have
	 * at least 28 elements available at this index.
	 */
	default void toVanilla(int[] target, int targetIndex) {
		toVanilla(0, target, targetIndex, false);
	}

	/**
	 * Generates a new BakedQuad instance with texture
	 * coordinates and colors from the given sprite.
	 *
	 * @param sprite {@link MutableQuadView} does not serialize sprites
	 * so the sprite must be provided by the caller.
	 *
	 * @return A new baked quad instance with the closest-available appearance
	 * supported by vanilla features. Will retain emissive light maps, for example,
	 * but the standard Minecraft renderer will not use them.
	 */
	default BakedQuad toBakedQuad(TextureAtlasSprite sprite) {
		int[] vertexData = new int[VANILLA_QUAD_STRIDE];
		toVanilla(vertexData, 0);

		// Mimic material properties to the largest possible extent
		int outputColorIndex = material().disableColorIndex() ? -1 : colorIndex();
		boolean outputShade = !material().disableDiffuse();
		return new BakedQuad(vertexData, outputColorIndex, lightFace(), sprite, outputShade);
	}

	/**
	 * @deprecated Use {@link #color(int)} instead.
	 */
	@Deprecated
	default int spriteColor(int vertexIndex, int spriteIndex) {
		return color(vertexIndex);
	}

	/**
	 * @deprecated Use {@link #u(int)} instead.
	 */
	@Deprecated
	default float spriteU(int vertexIndex, int spriteIndex) {
		return u(vertexIndex);
	}

	/**
	 * @deprecated Use {@link #v(int)} instead.
	 */
	@Deprecated
	default float spriteV(int vertexIndex, int spriteIndex) {
		return v(vertexIndex);
	}

	/**
	 * @deprecated Use {@link MutableQuadView#copyFrom(QuadView)} instead.
	 * <b>Unlike {@link MutableQuadView#copyFrom(QuadView) copyFrom}, this method will not copy the material.</b>
	 */
	@Deprecated
	default void copyTo(MutableQuadView target) {
		RenderMaterial material = target.material();
		target.copyFrom(this);
		target.material(material);
	}

	/**
	 * @deprecated Use {@link #toVanilla(int[], int)} instead.
	 */
	@Deprecated
	default void toVanilla(int spriteIndex, int[] target, int targetIndex, boolean isItem) {
		toVanilla(target, targetIndex);
	}

	/**
	 * @deprecated Use {@link #toBakedQuad(TextureAtlasSprite)} instead.
	 */
	@Deprecated
	default BakedQuad toBakedQuad(int spriteIndex, TextureAtlasSprite sprite, boolean isItem) {
		return toBakedQuad(sprite);
	}
}