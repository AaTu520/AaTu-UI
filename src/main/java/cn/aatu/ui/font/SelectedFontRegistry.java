package cn.aatu.ui.font;

import com.mojang.blaze3d.font.GlyphProvider;
import java.util.Collections;
import java.util.Set;
import java.util.WeakHashMap;

public final class SelectedFontRegistry {
    private static final Set<GlyphProvider> PROVIDERS = Collections.synchronizedSet(
            Collections.newSetFromMap(new WeakHashMap<>()));

    private SelectedFontRegistry() {}

    public static void mark(GlyphProvider provider) {
        if (provider != null) PROVIDERS.add(provider);
    }

    public static boolean contains(GlyphProvider provider) {
        return PROVIDERS.contains(provider);
    }
}
