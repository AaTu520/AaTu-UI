package cn.aatu.ui.font;

import cn.aatu.ui.UiSettings;
import com.mojang.blaze3d.font.GlyphInfo;
import com.mojang.blaze3d.font.GlyphProvider;
import com.mojang.blaze3d.font.SheetGlyphInfo;
import com.mojang.logging.LogUtils;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.font.FontTexture;
import net.minecraft.client.gui.font.glyphs.BakedGlyph;
import net.minecraft.client.gui.font.providers.TrueTypeGlyphProviderDefinition;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

public final class ScaledGlyphCache implements AutoCloseable {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final ResourceLocation SELECTED_FONT = ResourceLocation.fromNamespaceAndPath("aatu_ui", "selected.ttf");
    private static final int MAX_OVERSAMPLE = 16;
    private static final int SCALE_MULTIPLIER = 4;
    private static final int MAX_GLYPHS = 256;
    private static final int MAX_TEXTURES = 96;
    private static final int TEXTURE_SIZE = 256;
    private final TextureManager textureManager;
    private final ResourceLocation fontName;
    private final Map<Integer, BakedGlyph> glyphs = new HashMap<>();
    private final Set<Integer> unavailableGlyphs = new HashSet<>();
    private final List<FontTexture> textures = new ArrayList<>();
    private final List<ResourceLocation> textureIds = new ArrayList<>();
    private CompletableFuture<GlyphProvider> pendingProvider;
    private GlyphProvider provider;
    private boolean failed;

    public ScaledGlyphCache(TextureManager textureManager, ResourceLocation fontName) {
        this.textureManager = textureManager;
        this.fontName = fontName;
    }

    @Nullable
    public BakedGlyph get(int codePoint) {
        BakedGlyph cached = glyphs.get(codePoint);
        if (cached != null) return cached;
        if (failed || glyphs.size() + unavailableGlyphs.size() >= MAX_GLYPHS || unavailableGlyphs.contains(codePoint)) return null;
        try {
            GlyphProvider active = provider();
            if (active == null) return null;
            GlyphInfo info = active.getGlyph(codePoint);
            BakedGlyph glyph = info == null ? null : info.bake(this::stitch);
            if (glyph != null) glyphs.put(codePoint, glyph);
            else unavailableGlyphs.add(codePoint);
            return glyph;
        } catch (RuntimeException exception) {
            unavailableGlyphs.add(codePoint);
            LOGGER.warn("Unable to prepare a high-resolution glyph U+{}", Integer.toHexString(codePoint), exception);
            return null;
        }
    }

    @Nullable
    private GlyphProvider provider() {
        if (provider != null) return provider;
        if (pendingProvider == null) {
            Minecraft client = Minecraft.getInstance();
            ResourceManager resources = client.getResourceManager();
            float size = UiSettings.validFontSize(UiSettings.get().fontSize);
            int oversample = Math.min(MAX_OVERSAMPLE, FontQuality.currentOversample() * SCALE_MULTIPLIER);
            try {
                pendingProvider = CompletableFuture.supplyAsync(() -> load(resources, size, oversample), Util.backgroundExecutor());
            } catch (RuntimeException exception) {
                failed = true;
                LOGGER.warn("High-resolution font loading could not start; using standard glyphs", exception);
            }
            return null;
        }
        if (!pendingProvider.isDone()) return null;
        try {
            provider = pendingProvider.join();
            return provider;
        } catch (RuntimeException exception) {
            failed = true;
            LOGGER.warn("High-resolution font loading failed; using standard glyphs", exception);
            return null;
        } finally {
            pendingProvider = null;
        }
    }

    private static GlyphProvider load(ResourceManager resources, float size, int oversample) {
        var definition = new TrueTypeGlyphProviderDefinition(SELECTED_FONT, size, oversample,
                TrueTypeGlyphProviderDefinition.Shift.NONE, "");
        try {
            return definition.unpack().left().orElseThrow().load(resources);
        } catch (IOException exception) {
            throw new CompletionException(exception);
        }
    }

    @Nullable
    private BakedGlyph stitch(SheetGlyphInfo info) {
        for (FontTexture texture : textures) {
            BakedGlyph baked = texture.add(info);
            if (baked != null) {
                texture.setFilter(true, false);
                return baked;
            }
        }
        if (info.isColored() || info.getPixelWidth() > TEXTURE_SIZE || info.getPixelHeight() > TEXTURE_SIZE
                || textures.size() >= MAX_TEXTURES) return null;
        ResourceLocation id = ResourceLocation.fromNamespaceAndPath("aatu_ui",
                "scaled/" + fontName.getNamespace() + "/" + fontName.getPath() + "/" + textures.size());
        FontTexture texture = new FontTexture(SmoothTextRenderTypes.create(id), false);
        try {
            textureManager.register(id, texture);
        } catch (RuntimeException exception) {
            texture.close();
            throw exception;
        }
        textures.add(texture);
        textureIds.add(id);
        BakedGlyph glyph = texture.add(info);
        if (glyph == null) {
            textures.removeLast();
            textureIds.removeLast();
            textureManager.release(id);
            return null;
        }
        texture.setFilter(true, false);
        return glyph;
    }

    @Override
    public void close() {
        for (ResourceLocation id : textureIds) textureManager.release(id);
        textureIds.clear();
        textures.clear();
        glyphs.clear();
        unavailableGlyphs.clear();
        if (provider != null) {
            closeProvider(provider);
            provider = null;
        }
        if (pendingProvider != null) {
            pendingProvider.thenAccept(ScaledGlyphCache::closeProvider);
            pendingProvider = null;
        }
    }

    private static void closeProvider(GlyphProvider provider) {
        try {
            provider.close();
        } catch (RuntimeException exception) {
            LOGGER.warn("Unable to release high-resolution font provider", exception);
        }
    }
}
