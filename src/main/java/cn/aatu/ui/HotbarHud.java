package cn.aatu.ui;

import cn.aatu.ui.mixin.GuiSlotInvoker;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.math.Axis;
import net.minecraft.client.AttackIndicatorStatus;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

public final class HotbarHud {
    private static final int SLOT_COUNT = 9;
    private static final int SLOT_STEP = 20;
    private static final int BAR_LENGTH = 182;
    private static final int BAR_THICKNESS = 22;
    private static final int VERTICAL_WIDTH = 42;
    private static final int VERTICAL_MARGIN = 1;
    private static final int OFFHAND_HEIGHT = 24;
    private static final ResourceLocation HOTBAR = ResourceLocation.withDefaultNamespace("hud/hotbar");
    private static final ResourceLocation SELECTION = ResourceLocation.withDefaultNamespace("hud/hotbar_selection");
    private static final ResourceLocation OFFHAND_LEFT = ResourceLocation.withDefaultNamespace("hud/hotbar_offhand_left");
    private static final ResourceLocation OFFHAND_RIGHT = ResourceLocation.withDefaultNamespace("hud/hotbar_offhand_right");
    private static final ResourceLocation ATTACK_BACKGROUND = ResourceLocation.withDefaultNamespace("hud/hotbar_attack_indicator_background");
    private static final ResourceLocation ATTACK_PROGRESS = ResourceLocation.withDefaultNamespace("hud/hotbar_attack_indicator_progress");

    private HotbarHud() {}

    public static int pixelWidth() {
        return UiSettings.get().hotbarVertical ? VERTICAL_WIDTH : BAR_LENGTH;
    }

    public static int pixelHeight() {
        Player player = Minecraft.getInstance().player;
        boolean hasOffhand = player != null && !player.getOffhandItem().isEmpty();
        return UiSettings.get().hotbarVertical ? BAR_LENGTH + 2 * VERTICAL_MARGIN + (hasOffhand ? OFFHAND_HEIGHT : 0) : BAR_THICKNESS;
    }

    public static void render(GuiGraphics graphics, DeltaTracker delta) {
        Minecraft minecraft = Minecraft.getInstance();
        if (!(minecraft.getCameraEntity() instanceof Player player)) return;
        int x = HudPart.HOTBAR.x(graphics.guiWidth(), graphics.guiHeight());
        int y = HudPart.HOTBAR.y(graphics.guiWidth(), graphics.guiHeight());
        graphics.pose().pushPose();
        try {
            HudTransform.apply(graphics, HudPart.HOTBAR, x, y);
            draw(graphics, delta, player, x, y);
        } finally {
            graphics.pose().popPose();
        }
    }

    private static void draw(GuiGraphics graphics, DeltaTracker delta, Player player, int x, int y) {
        Minecraft minecraft = Minecraft.getInstance();
        boolean vertical = UiSettings.get().hotbarVertical;
        int barY = y + (vertical ? VERTICAL_MARGIN : 0);
        int selected = player.getInventory().selected;
        drawBackground(graphics, x, barY, vertical, selected, HudPart.HOTBAR.placement().style());
        for (int slot = 0; slot < SLOT_COUNT; slot++) {
            int slotX = x + 3 + (vertical ? 0 : slot * SLOT_STEP);
            int slotY = barY + 3 + (vertical ? slot * SLOT_STEP : 0);
            drawSlot(graphics, delta, player, player.getInventory().items.get(slot), slotX, slotY, slot + 1);
        }

        ItemStack offhand = player.getOffhandItem();
        if (!offhand.isEmpty()) {
            RenderSystem.enableBlend();
            if (vertical) {
                graphics.blitSprite(OFFHAND_LEFT, x, barY + BAR_LENGTH - 1, 29, OFFHAND_HEIGHT);
                RenderSystem.disableBlend();
                drawSlot(graphics, delta, player, offhand, x + 3, barY + BAR_LENGTH + 3, SLOT_COUNT + 1);
            } else {
                boolean onLeft = player.getMainArm().getOpposite() == HumanoidArm.LEFT;
                graphics.blitSprite(onLeft ? OFFHAND_LEFT : OFFHAND_RIGHT, onLeft ? x - 29 : x + BAR_LENGTH, y - 1, 29, OFFHAND_HEIGHT);
                RenderSystem.disableBlend();
                drawSlot(graphics, delta, player, offhand, onLeft ? x - 26 : x + BAR_LENGTH + 10, y + 3, SLOT_COUNT + 1);
            }
        }

        if (minecraft.options.attackIndicator().get() == AttackIndicatorStatus.HOTBAR && minecraft.player != null) {
            float strength = minecraft.player.getAttackStrengthScale(0.0F);
            if (strength < 1.0F) {
                RenderSystem.enableBlend();
                int attackX = vertical ? x + 24 : (player.getMainArm().getOpposite() == HumanoidArm.RIGHT ? x - 22 : x + BAR_LENGTH + 6);
                int attackY = vertical ? barY + selected * SLOT_STEP + 2 : y + 2;
                int progress = Mth.clamp((int) (strength * 19.0F), 0, 18);
                graphics.blitSprite(ATTACK_BACKGROUND, attackX, attackY, 18, 18);
                if (progress > 0) graphics.blitSprite(ATTACK_PROGRESS, 18, 18, 0, 18 - progress, attackX, attackY + 18 - progress, 18, progress);
                RenderSystem.disableBlend();
            }
        }
    }

    private static void drawSlot(GuiGraphics graphics, DeltaTracker delta, Player player, ItemStack stack, int x, int y, int seed) {
        if (stack.isEmpty()) return;
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.gui instanceof GuiSlotInvoker invoker) {
            invoker.aatu$renderSlot(graphics, x, y, delta, player, stack, seed);
        } else {
            graphics.renderItem(player, stack, x, y, seed);
            graphics.renderItemDecorations(minecraft.font, stack, x, y);
        }
    }

    private static void drawBackground(GuiGraphics graphics, int x, int y, boolean vertical, int selected, HudStyle style) {
        if (style == HudStyle.ORIGINAL) {
            RenderSystem.enableBlend();
            graphics.pose().pushPose();
            try {
                if (vertical) {
                    graphics.pose().translate(x + BAR_THICKNESS, y, 0);
                    graphics.pose().mulPose(Axis.ZP.rotationDegrees(90.0F));
                    x = 0;
                    y = 0;
                }
                graphics.blitSprite(HOTBAR, x, y, BAR_LENGTH, BAR_THICKNESS);
                graphics.blitSprite(SELECTION, x - 1 + selected * SLOT_STEP, y - 1, 24, 23);
            } finally {
                graphics.pose().popPose();
                RenderSystem.disableBlend();
            }
            return;
        }
        int width = vertical ? BAR_THICKNESS : BAR_LENGTH;
        int height = vertical ? BAR_LENGTH : BAR_THICKNESS;
        graphics.fill(x, y, x + width, y + height, style == HudStyle.COMPACT ? 0xD0394659 : 0xB0181B20);
        for (int slot = 0; slot < SLOT_COUNT; slot++) {
            int slotX = x + 1 + (vertical ? 0 : slot * SLOT_STEP);
            int slotY = y + 1 + (vertical ? slot * SLOT_STEP : 0);
            graphics.fill(slotX, slotY, slotX + 20, slotY + 20, 0x443C4657);
            if (slot == selected) graphics.renderOutline(slotX, slotY, 20, 20, 0xFFE4EAF5);
        }
    }

    public static void preview(GuiGraphics graphics, int x, int y) {
        Player player = Minecraft.getInstance().player;
        int selected = player == null ? 0 : player.getInventory().selected;
        boolean vertical = UiSettings.get().hotbarVertical;
        int barY = y + (vertical ? VERTICAL_MARGIN : 0);
        drawBackground(graphics, x, barY, vertical, selected, HudPart.HOTBAR.placement().style());
        if (player == null) return;
        for (int slot = 0; slot < SLOT_COUNT; slot++) {
            ItemStack stack = player.getInventory().items.get(slot);
            if (stack.isEmpty()) continue;
            int slotX = x + 3 + (vertical ? 0 : slot * SLOT_STEP);
            int slotY = barY + 3 + (vertical ? slot * SLOT_STEP : 0);
            graphics.renderItem(player, stack, slotX, slotY, slot + 1);
            graphics.renderItemDecorations(Minecraft.getInstance().font, stack, slotX, slotY);
        }
        if (!player.getOffhandItem().isEmpty()) {
            boolean onLeft = player.getMainArm().getOpposite() == HumanoidArm.LEFT;
            int offhandX = vertical ? x + 3 : (onLeft ? x - 26 : x + BAR_LENGTH + 10);
            int offhandY = vertical ? barY + BAR_LENGTH + 3 : y + 3;
            graphics.blitSprite(vertical || onLeft ? OFFHAND_LEFT : OFFHAND_RIGHT,
                    vertical ? x : (onLeft ? x - 29 : x + BAR_LENGTH), vertical ? barY + BAR_LENGTH - 1 : y - 1, 29, OFFHAND_HEIGHT);
            graphics.renderItem(player, player.getOffhandItem(), offhandX, offhandY, SLOT_COUNT + 1);
            graphics.renderItemDecorations(Minecraft.getInstance().font, player.getOffhandItem(), offhandX, offhandY);
        }
    }
}
