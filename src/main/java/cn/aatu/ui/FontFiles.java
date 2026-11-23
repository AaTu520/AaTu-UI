package cn.aatu.ui;

import cn.aatu.ui.font.SystemFontCatalog;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;
import net.neoforged.fml.loading.FMLPaths;

public final class FontFiles {
    private static final long MAX_FONT_BYTES = 128L * 1024 * 1024;

    private FontFiles() {}

    public static Path directory() {
        return FMLPaths.CONFIGDIR.get().resolve("aatu-ui").resolve("fonts");
    }

    public static List<String> list() throws IOException {
        Files.createDirectories(directory());
        try (Stream<Path> files = Files.list(directory())) {
            return files.filter(Files::isRegularFile)
                    .filter(FontFiles::isSupported)
                    .map(path -> path.getFileName().toString())
                    .sorted(String.CASE_INSENSITIVE_ORDER.thenComparing(Comparator.naturalOrder()))
                    .toList();
        }
    }

    public static Path selected() {
        UiSettings settings = UiSettings.get();
        if (settings.systemFontReference) {
            SystemFontCatalog.Resolved font = SystemFontCatalog.resolve(settings.systemFontFile, settings.systemFontName, settings.selectedFontFace);
            return font == null ? null : font.path();
        }
        String name = settings.selectedFont;
        if (name == null || name.isBlank()) return null;
        try {
            Path relative = Path.of(name);
            if (relative.isAbsolute() || relative.getNameCount() != 1 || !relative.toString().equals(name)) return null;
            Path font = directory().resolve(relative);
            return Files.isRegularFile(font) && isSupported(font) ? font : null;
        } catch (RuntimeException exception) {
            return null;
        }
    }

    public static int selectedFaceIndex() {
        UiSettings settings = UiSettings.get();
        if (!settings.systemFontReference) return settings.selectedFontFace;
        SystemFontCatalog.Resolved font = SystemFontCatalog.resolve(settings.systemFontFile, settings.systemFontName, settings.selectedFontFace);
        return font == null ? 0 : font.index();
    }

    public static String importFile(Path source) throws IOException {
        if (!Files.isRegularFile(source) || !isSupported(source)) {
            throw new IOException("The selected file is not a supported font.");
        }
        if (Files.size(source) > MAX_FONT_BYTES) throw new IOException("The selected font is too large.");
        Files.createDirectories(directory());
        Path fileName = source.getFileName();
        if (fileName == null) throw new IOException("The selected font has no file name.");
        Path target = directory().resolve(fileName.toString());
        String original = fileName.toString();
        String stem = original.substring(0, original.length() - 4);
        String extension = original.substring(original.length() - 4);
        for (int suffix = 1; Files.exists(target); suffix++) {
            if (Files.isSameFile(source, target) || (Files.size(source) == Files.size(target) && Files.mismatch(source, target) == -1)) {
                return target.getFileName().toString();
            }
            target = directory().resolve(stem + "-" + suffix + extension);
        }
        Path temporary = Files.createTempFile(directory(), ".aatu-font-", ".tmp");
        try {
            Files.copy(source, temporary, StandardCopyOption.REPLACE_EXISTING);
            Files.move(temporary, target);
        } finally {
            Files.deleteIfExists(temporary);
        }
        return target.getFileName().toString();
    }

    public static boolean isSupported(Path path) {
        String name = path.getFileName().toString().toLowerCase(Locale.ROOT);
        if (!(name.endsWith(".ttf") || name.endsWith(".otf") || name.endsWith(".ttc") || name.endsWith(".otc"))) return false;
        try {
            if (Files.size(path) > MAX_FONT_BYTES) return false;
        } catch (IOException exception) {
            return false;
        }
        try (InputStream input = Files.newInputStream(path)) {
            byte[] signature = input.readNBytes(4);
            return signature.length == 4 && ((signature[0] == 0 && signature[1] == 1 && signature[2] == 0 && signature[3] == 0)
                    || (signature[0] == 't' && signature[1] == 'r' && signature[2] == 'u' && signature[3] == 'e')
                    || (signature[0] == 'O' && signature[1] == 'T' && signature[2] == 'T' && signature[3] == 'O')
                    || (signature[0] == 't' && signature[1] == 't' && signature[2] == 'c' && signature[3] == 'f'));
        } catch (IOException exception) {
            return false;
        }
    }
}
