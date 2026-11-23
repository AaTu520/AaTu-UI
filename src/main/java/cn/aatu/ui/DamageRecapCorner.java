package cn.aatu.ui;

public enum DamageRecapCorner {
    TOP_LEFT("top_left", false, false),
    TOP_RIGHT("top_right", true, false),
    BOTTOM_RIGHT("bottom_right", true, true),
    BOTTOM_LEFT("bottom_left", false, true);

    public final String id;
    public final boolean right;
    public final boolean bottom;

    DamageRecapCorner(String id, boolean right, boolean bottom) {
        this.id = id;
        this.right = right;
        this.bottom = bottom;
    }

    public DamageRecapCorner next() {
        return values()[(ordinal() + 1) % values().length];
    }

    public static DamageRecapCorner from(String id) {
        if (id == null) return TOP_RIGHT;
        return switch (id) {
            case "top_left" -> TOP_LEFT;
            case "bottom_right" -> BOTTOM_RIGHT;
            case "bottom_left" -> BOTTOM_LEFT;
            default -> TOP_RIGHT;
        };
    }
}
