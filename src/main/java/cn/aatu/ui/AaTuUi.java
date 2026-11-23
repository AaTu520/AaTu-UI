package cn.aatu.ui;

import com.mojang.blaze3d.platform.InputConstants;
import cn.aatu.ui.font.FontQuality;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.neoforged.neoforge.common.NeoForge;
import org.lwjgl.glfw.GLFW;

@Mod(value = AaTuUi.ID, dist = Dist.CLIENT)
public final class AaTuUi {
    public static final String ID = "aatu_ui";
    private static final KeyMapping OPEN_EDITOR = new KeyMapping("key.aatu_ui.editor", InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_F8, "key.categories.aatu_ui");
    private static final KeyMapping OPEN_RECAP = new KeyMapping("key.aatu_ui.recap", InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_F7, "key.categories.aatu_ui");

    public AaTuUi(IEventBus modBus, ModContainer container) {
        modBus.addListener(FontResourcePack::register);
        modBus.addListener(CombatUiLink::register);
        modBus.addListener(DamageRecapLink::register);
        modBus.addListener(AaTuUi::registerKeys);
        modBus.addListener(AaTuUi::registerLayers);
        modBus.addListener(WoundScreenEffect::registerReloadListener);
        container.registerExtensionPoint(IConfigScreenFactory.class, (mod, previous) -> new HudEditorScreen(previous));
        NeoForge.EVENT_BUS.addListener(AaTuUi::clientTick);
        NeoForge.EVENT_BUS.addListener(AaTuUi::clientLogout);
        NeoForge.EVENT_BUS.addListener(DamageRecapClient::screenInit);
        NeoForge.EVENT_BUS.addListener(DamageRecapHud::screenRender);
        NeoForge.EVENT_BUS.addListener(HudRenderer::layer);
        NeoForge.EVENT_BUS.addListener(WoundScreenEffect::render);
        NeoForge.EVENT_BUS.addListener(ServerPerformance::serverTick);
        NeoForge.EVENT_BUS.addListener(ServerPerformance::serverStopped);
        NeoForge.EVENT_BUS.addListener(EventPriority.HIGHEST, true, HudRenderer::bossGuard);
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, true, HudRenderer::boss);
        NeoForge.EVENT_BUS.addListener(HudRenderer::layerDone);
    }

    private static void registerKeys(RegisterKeyMappingsEvent event) {
        event.register(OPEN_EDITOR);
        event.register(OPEN_RECAP);
    }

    private static void registerLayers(RegisterGuiLayersEvent event) {
        event.registerAboveAll(ResourceLocation.fromNamespaceAndPath(ID, "stats"), StatsHud::render);
        event.registerAboveAll(ResourceLocation.fromNamespaceAndPath(ID, "damage_recap"), DamageRecapHud::render);
    }

    private static void clientTick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        FontQuality.tick(minecraft);
        ServerPerformance.clientTick();
        DamageRecapClient.tick();
        WoundScreenEffect.tick();
        while (OPEN_RECAP.consumeClick()) {
            if (minecraft.player != null && !minecraft.player.isDeadOrDying() && minecraft.screen == null) {
                boolean incident = DamageRecapHud.active();
                DamageRecapHud.clear();
                minecraft.setScreen(new DamageRecapScreen(null, incident));
            }
        }
        while (OPEN_EDITOR.consumeClick()) {
            if (minecraft.screen == null) minecraft.setScreen(new HudEditorScreen(null));
        }
    }

    static Component recapKeyName() {
        return OPEN_RECAP.getTranslatedKeyMessage();
    }

    private static void clientLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        EffectsHud.clear();
        StatsHud.clear();
        CombatUiLink.clear();
        DamageRecapClient.clear();
        ServerPerformance.clear();
        OriginalHudLayers.clear();
        HudColors.invalidate();
        WoundScreenEffect.clear();
    }
}
