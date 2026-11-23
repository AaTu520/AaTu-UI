package cn.aatu.ui;

import com.mojang.logging.LogUtils;
import java.lang.reflect.Method;
import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

public final class CombatUiLink {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final CustomPacketPayload.Type<Payload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath("aatu_combat", "ui_state"));
    private static Method combatClientState;
    private static Method clearCombatClientState;
    private static boolean checkedCombat;
    private static boolean reportedFailure;
    private static ClientSnapshot received;

    private CombatUiLink() {}

    public static void register(RegisterPayloadHandlersEvent event) {
        findCombatClientState();
        // The combat mod owns the same channel when installed on this client.
        if (combatClientState == null) event.registrar("1").optional().playToClient(TYPE, Payload.CODEC, CombatUiLink::receive);
    }

    private static void receive(Payload payload, IPayloadContext context) {
        received = new ClientSnapshot(context.connection(), payload.data().copy(), System.nanoTime());
    }

    public static boolean hasChannel() {
        Minecraft minecraft = Minecraft.getInstance();
        findCombatClientState();
        if (minecraft.hasSingleplayerServer() && combatClientState == null) return false;
        var listener = minecraft.getConnection();
        return listener != null && listener.hasChannel(TYPE.id());
    }

    public static boolean outdatedSingleplayerCombat() {
        findCombatClientState();
        return Minecraft.getInstance().hasSingleplayerServer() && ModList.get().isLoaded("aatu_combat")
                && combatClientState == null;
    }

    @Nullable
    public static CompoundTag state() {
        var listener = Minecraft.getInstance().getConnection();
        if (listener == null || !hasChannel()) return null;
        Connection connection = listener.getConnection();
        findCombatClientState();
        if (combatClientState != null) {
            try {
                Object value = combatClientState.invoke(null, connection);
                return value instanceof CompoundTag data ? data : null;
            } catch (ReflectiveOperationException | RuntimeException exception) {
                if (!reportedFailure) {
                    reportedFailure = true;
                    LOGGER.warn("Unable to read combat UI data", exception);
                }
                CompoundTag unavailable = new CompoundTag();
                unavailable.putInt("status", 2);
                return unavailable;
            }
        }
        if (received == null || received.connection != connection) return null;
        CompoundTag data = received.data.copy();
        data.putLong("_uiReceivedAt", received.receivedAt);
        return data;
    }

    public static void clear() {
        received = null;
        reportedFailure = false;
        if (clearCombatClientState != null) {
            try {
                clearCombatClientState.invoke(null);
            } catch (ReflectiveOperationException | RuntimeException exception) {
                LOGGER.debug("Unable to clear combat UI data", exception);
            }
        }
    }

    private static void findCombatClientState() {
        if (checkedCombat) return;
        checkedCombat = true;
        try {
            combatClientState = Class.forName("cn.aatu.combat.CombatUiSync").getMethod("clientState", Connection.class);
            try {
                clearCombatClientState = combatClientState.getDeclaringClass().getMethod("clearClientState");
            } catch (NoSuchMethodException ignored) {
                clearCombatClientState = null;
            }
        } catch (ReflectiveOperationException | LinkageError | SecurityException ignored) {
            combatClientState = null;
        }
    }

    private record ClientSnapshot(Connection connection, CompoundTag data, long receivedAt) {}

    private record Payload(CompoundTag data) implements CustomPacketPayload {
        private static final StreamCodec<RegistryFriendlyByteBuf, Payload> CODEC =
                StreamCodec.composite(ByteBufCodecs.COMPOUND_TAG, Payload::data, Payload::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }
}
