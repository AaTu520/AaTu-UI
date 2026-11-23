package cn.aatu.ui;

public enum HudStyle {
    ORIGINAL("original"),
    MODERN("modern"),
    COMPACT("compact");

    public final String id;

    HudStyle(String id) {
        this.id = id;
    }

    public HudStyle next() {
        return switch (this) {
            case ORIGINAL -> MODERN;
            case MODERN -> COMPACT;
            case COMPACT -> ORIGINAL;
        };
    }

    public static HudStyle from(String id) {
        if (ORIGINAL.id.equals(id)) return ORIGINAL;
        if (COMPACT.id.equals(id)) return COMPACT;
        return MODERN;
    }
}
