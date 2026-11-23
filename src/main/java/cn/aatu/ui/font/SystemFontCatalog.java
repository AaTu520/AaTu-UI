package cn.aatu.ui.font;

import cn.aatu.ui.FontFiles;
import cn.aatu.ui.UiSettings;
import com.sun.jna.platform.win32.Advapi32Util;
import com.sun.jna.platform.win32.WinReg;
import java.awt.Font;
import java.awt.FontFormatException;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;
import net.minecraft.client.Minecraft;

public final class SystemFontCatalog {
    private static final String WINDOWS_FONT_KEY = "SOFTWARE\\Microsoft\\Windows NT\\CurrentVersion\\Fonts";
    private static final int MAX_CANDIDATES = 8192;
    private static volatile List<Candidate> candidates;
    private static volatile List<Face> installedFaces;

    public record Face(Path path, String displayName, String postScriptName, int index) {}
    public record Resolved(Path path, int index) {}
    private record Candidate(Path path, String label) {}
    private record ResolutionKey(String fileName, String fontName, int index) {}
    private static volatile ResolutionKey lastKey;
    private static volatile Resolved lastResolution;
    private static volatile ResolutionKey pendingLookup;

    private SystemFontCatalog() {}

    public static List<Face> bundled() throws IOException {
        Files.createDirectories(FontFiles.directory());
        try (Stream<Path> paths = Files.list(FontFiles.directory())) {
            return parse(paths.filter(Files::isRegularFile).filter(FontFiles::isSupported).toList());
        }
    }

    public static synchronized List<Face> installed(boolean refresh) {
        if (refresh) {
            candidates = null;
            installedFaces = null;
            lastKey = null;
            lastResolution = null;
            pendingLookup = null;
        }
        if (installedFaces == null) {
            installedFaces = parse(candidatePaths().stream().map(Candidate::path).toList());
        }
        return installedFaces;
    }

    public static synchronized Resolved resolve(String fileName, String fontName, int requestedIndex) {
        if (fileName == null || fileName.isBlank()) return null;
        ResolutionKey key = new ResolutionKey(fileName, fontName, requestedIndex);
        if (key.equals(lastKey) && lastResolution != null && FontFiles.isSupported(lastResolution.path())) return lastResolution;
        List<Face> cached = installedFaces;
        if (cached != null && fontName != null && !fontName.isBlank()) {
            for (Face face : cached) {
                if (fontName.equalsIgnoreCase(face.postScriptName()) && face.path().getFileName().toString().equalsIgnoreCase(fileName)
                        && face.index() == requestedIndex && FontFiles.isSupported(face.path())) {
                    return remember(key, new Resolved(face.path(), face.index()));
                }
            }
        }
        for (Candidate candidate : candidatePaths()) {
            if (candidate.path().getFileName().toString().equalsIgnoreCase(fileName)
                    && FontFiles.isSupported(candidate.path())) {
                for (Face face : parse(List.of(candidate.path()))) {
                    if (fontName == null || fontName.isBlank() || fontName.equalsIgnoreCase(face.postScriptName())) {
                        return remember(key, new Resolved(face.path(), face.index()));
                    }
                }
            }
        }
        if (cached != null && fontName != null && !fontName.isBlank()) {
            for (Face face : cached) {
                if (fontName.equalsIgnoreCase(face.postScriptName()) && FontFiles.isSupported(face.path())) {
                    return remember(key, new Resolved(face.path(), face.index()));
                }
            }
        }
        if (cached == null && fontName != null && !fontName.isBlank()) searchByNameAsync(key);
        return null;
    }

    private static void searchByNameAsync(ResolutionKey key) {
        if (key.equals(pendingLookup)) return;
        pendingLookup = key;
        CompletableFuture.runAsync(() -> installed(false)).whenComplete((unused, failure) -> {
            Minecraft client = Minecraft.getInstance();
            if (failure != null || client == null) return;
            client.execute(() -> {
                UiSettings settings = UiSettings.get();
                if (settings.systemFontReference && key.fileName().equals(settings.systemFontFile)
                        && key.fontName().equals(settings.systemFontName)
                        && resolve(key.fileName(), key.fontName(), key.index()) != null) {
                    FontQuality.refresh(client);
                }
            });
        });
    }

    private static Resolved remember(ResolutionKey key, Resolved result) {
        lastKey = key;
        lastResolution = result;
        return result;
    }

    private static synchronized List<Candidate> candidatePaths() {
        if (candidates != null) return candidates;
        Map<Path, String> found = new LinkedHashMap<>();
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        if (os.contains("win")) {
            WindowsRegistry.addFonts(found);
            String windows = System.getenv("WINDIR");
            String local = System.getenv("LOCALAPPDATA");
            if (windows != null) addDirectory(found, Path.of(windows, "Fonts"));
            if (local != null) addDirectory(found, Path.of(local, "Microsoft", "Windows", "Fonts"));
        } else if (os.contains("mac")) {
            addDirectory(found, Path.of("/System/Library/Fonts"));
            addDirectory(found, Path.of("/Library/Fonts"));
            addDirectory(found, Path.of(System.getProperty("user.home"), "Library", "Fonts"));
        } else {
            addDirectory(found, Path.of("/usr/share/fonts"));
            addDirectory(found, Path.of("/usr/local/share/fonts"));
            addDirectory(found, Path.of(System.getProperty("user.home"), ".local", "share", "fonts"));
            addDirectory(found, Path.of(System.getProperty("user.home"), ".fonts"));
        }
        candidates = found.entrySet().stream().limit(MAX_CANDIDATES).map(entry -> new Candidate(entry.getKey(), entry.getValue())).toList();
        return candidates;
    }

    private static void addDirectory(Map<Path, String> found, Path directory) {
        if (!Files.isDirectory(directory) || found.size() >= MAX_CANDIDATES) return;
        try (Stream<Path> paths = Files.walk(directory, 6)) {
            paths.filter(Files::isRegularFile).filter(FontFiles::isSupported)
                    .limit(MAX_CANDIDATES - found.size())
                    .forEach(path -> found.putIfAbsent(path.toAbsolutePath().normalize(), path.getFileName().toString()));
        } catch (IOException | UncheckedIOException | SecurityException ignored) {
            // Inaccessible folders do not prevent using the remaining installed fonts.
        }
    }

    private static List<Face> parse(List<Path> paths) {
        List<Face> faces = new ArrayList<>();
        for (Path path : paths) {
            if (!FontFiles.isSupported(path)) continue;
            try {
                Font[] fonts = Font.createFonts(path.toFile());
                for (int index = 0; index < fonts.length; index++) {
                    Font font = fonts[index];
                    faces.add(new Face(path, font.getFontName(Locale.getDefault()), font.getPSName(), index));
                }
            } catch (IOException | FontFormatException | RuntimeException ignored) {
                // A damaged or unsupported font should not hide the rest of the catalog.
            }
        }
        faces.sort(Comparator.comparing(Face::displayName, String.CASE_INSENSITIVE_ORDER)
                .thenComparing(face -> face.path().toString(), String.CASE_INSENSITIVE_ORDER)
                .thenComparingInt(Face::index));
        return List.copyOf(faces);
    }

    private static final class WindowsRegistry {
        private static void addFonts(Map<Path, String> found) {
            addRoot(found, WinReg.HKEY_LOCAL_MACHINE);
            addRoot(found, WinReg.HKEY_CURRENT_USER);
        }

        private static void addRoot(Map<Path, String> found, WinReg.HKEY root) {
            try {
                for (Map.Entry<String, Object> entry : Advapi32Util.registryGetValues(root, WINDOWS_FONT_KEY).entrySet()) {
                    try {
                        if (!(entry.getValue() instanceof String value) || value.isBlank()) continue;
                        String windows = System.getenv("WINDIR");
                        if (windows != null) value = value.replace("%SystemRoot%", windows).replace("%WINDIR%", windows);
                        Path path = Path.of(value);
                        if (!path.isAbsolute()) {
                            if (windows == null) continue;
                            path = Path.of(windows, "Fonts", value);
                        }
                        path = path.toAbsolutePath().normalize();
                        if (Files.isRegularFile(path) && FontFiles.isSupported(path)) found.putIfAbsent(path, entry.getKey());
                    } catch (RuntimeException ignored) {
                        // Ignore one malformed registry value and continue with the others.
                    }
                }
            } catch (RuntimeException | LinkageError ignored) {
                // System font directories remain available if a registry key is unavailable.
            }
        }
    }
}
