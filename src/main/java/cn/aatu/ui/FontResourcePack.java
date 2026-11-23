package cn.aatu.ui;

import cn.aatu.ui.font.FontQuality;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.Set;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.AbstractPackResources;
import net.minecraft.server.packs.PackLocationInfo;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.PackSelectionConfig;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackSource;
import net.minecraft.server.packs.resources.IoSupplier;
import net.neoforged.neoforge.event.AddPackFindersEvent;
import org.jetbrains.annotations.Nullable;

public final class FontResourcePack extends AbstractPackResources {
    private static final ResourceLocation FONT_DEFINITION = ResourceLocation.withDefaultNamespace("font/default.json");
    private static final ResourceLocation FONT_DATA = ResourceLocation.fromNamespaceAndPath("aatu_ui", "font/selected.ttf");
    private static final byte[] METADATA = "{\"pack\":{\"pack_format\":34,\"description\":\"AaTu UI font\"}}"
            .getBytes(StandardCharsets.UTF_8);

    private FontResourcePack(PackLocationInfo location) {
        super(location);
    }

    public static void register(AddPackFindersEvent event) {
        if (event.getPackType() != PackType.CLIENT_RESOURCES) return;
        event.addRepositorySource(consumer -> {
            PackLocationInfo info = new PackLocationInfo("aatu_ui/font", Component.literal("AaTu UI Font"),
                    PackSource.BUILT_IN, Optional.empty());
            Pack.ResourcesSupplier supplier = new Pack.ResourcesSupplier() {
                @Override
                public PackResources openPrimary(PackLocationInfo location) {
                    return new FontResourcePack(location);
                }

                @Override
                public PackResources openFull(PackLocationInfo location, Pack.Metadata metadata) {
                    return new FontResourcePack(location);
                }
            };
            Pack pack = Pack.readMetaAndCreate(info, supplier, PackType.CLIENT_RESOURCES,
                    new PackSelectionConfig(true, Pack.Position.TOP, true));
            if (pack != null) consumer.accept(pack.hidden());
        });
    }

    @Nullable
    @Override
    public IoSupplier<InputStream> getRootResource(String... path) {
        return path.length == 1 && "pack.mcmeta".equals(path[0]) ? () -> new ByteArrayInputStream(METADATA) : null;
    }

    @Nullable
    @Override
    public IoSupplier<InputStream> getResource(PackType type, ResourceLocation location) {
        if (type != PackType.CLIENT_RESOURCES || (!FONT_DEFINITION.equals(location) && !FONT_DATA.equals(location))) return null;
        Path font = FontFiles.selected();
        if (font == null) {
            if (FONT_DEFINITION.equals(location)) FontQuality.fontPrepared(0);
            return null;
        }
        if (FONT_DEFINITION.equals(location)) {
            int oversample = FontQuality.currentOversample();
            FontQuality.fontPrepared(oversample);
            byte[] definition = fontDefinition(oversample);
            return () -> new ByteArrayInputStream(definition);
        }
        if (FONT_DATA.equals(location)) return () -> Files.newInputStream(font);
        return null;
    }

    @Override
    public void listResources(PackType type, String namespace, String path, ResourceOutput output) {
        if (type != PackType.CLIENT_RESOURCES) return;
        listIfMatching(namespace, path, FONT_DEFINITION, output);
        listIfMatching(namespace, path, FONT_DATA, output);
    }

    private void listIfMatching(String namespace, String path, ResourceLocation location, ResourceOutput output) {
        if (location.getNamespace().equals(namespace) && location.getPath().startsWith(path + "/")) {
            IoSupplier<InputStream> resource = getResource(PackType.CLIENT_RESOURCES, location);
            if (resource != null) output.accept(location, resource);
        }
    }

    @Override
    public Set<String> getNamespaces(PackType type) {
        return type == PackType.CLIENT_RESOURCES ? Set.of("minecraft", "aatu_ui") : Set.of();
    }

    @Override
    public void close() {}

    private static byte[] fontDefinition(int oversample) {
        JsonObject provider = new JsonObject();
        provider.addProperty("type", "ttf");
        provider.addProperty("file", "aatu_ui:selected.ttf");
        provider.addProperty("size", UiSettings.validFontSize(UiSettings.get().fontSize));
        provider.addProperty("oversample", oversample);
        // Leave resource-pack icons in the private-use range to their existing providers.
        StringBuilder privateUse = new StringBuilder();
        for (int codePoint = 0xE000; codePoint <= 0xF8FF; codePoint++) privateUse.appendCodePoint(codePoint);
        provider.addProperty("skip", privateUse.toString());
        JsonArray providers = new JsonArray();
        providers.add(provider);
        JsonObject definition = new JsonObject();
        definition.add("providers", providers);
        return definition.toString().getBytes(StandardCharsets.UTF_8);
    }
}
