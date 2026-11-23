package cn.aatu.ui;

public enum StatBarStyle {
    ORIGINAL("original"),
    MODERN("modern"),
    COMPACT("compact"),
    THIN("thin");

    public final String id;

    StatBarStyle(String id) {
        this.id = id;
    }

    public StatBarStyle next() {
        return switch (this) {
            case ORIGINAL -> MODERN;
            case MODERN -> COMPACT;
            case COMPACT -> THIN;
            case THIN -> ORIGINAL;
        };
    }

    public static StatBarStyle from(String id) {
        if (id == null) return MODERN;
        return switch (id) {
            case "original" -> ORIGINAL;
            case "compact" -> COMPACT;
            case "thin" -> THIN;
            default -> MODERN;
        };
    }
}
