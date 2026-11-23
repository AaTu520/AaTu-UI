package cn.aatu.ui.font;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.gui.font.GlyphRenderTypes;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;

public final class SmoothTextRenderTypes {
    private static final int INITIAL_BUFFER_SIZE = 1536;

    private SmoothTextRenderTypes() {}

    public static GlyphRenderTypes create(ResourceLocation texture) {
        return new GlyphRenderTypes(create(texture, false, false), create(texture, true, false), create(texture, false, true));
    }

    private static RenderType create(ResourceLocation texture, boolean seeThrough, boolean polygonOffset) {
        var builder = RenderType.CompositeState.builder()
                .setShaderState(seeThrough ? RenderStateShard.RENDERTYPE_TEXT_INTENSITY_SEE_THROUGH_SHADER
                        : RenderStateShard.RENDERTYPE_TEXT_INTENSITY_SHADER)
                .setTextureState(new RenderStateShard.TextureStateShard(texture, true, false))
                .setTransparencyState(RenderStateShard.TRANSLUCENT_TRANSPARENCY)
                .setLightmapState(RenderStateShard.LIGHTMAP);
        if (seeThrough) {
            builder.setDepthTestState(RenderStateShard.NO_DEPTH_TEST).setWriteMaskState(RenderStateShard.COLOR_WRITE);
        }
        if (polygonOffset) builder.setLayeringState(RenderStateShard.POLYGON_OFFSET_LAYERING);
        return RenderType.create("aatu_ui_smooth_text", DefaultVertexFormat.POSITION_COLOR_TEX_LIGHTMAP,
                VertexFormat.Mode.QUADS, INITIAL_BUFFER_SIZE, false, true, builder.createCompositeState(false));
    }
}
