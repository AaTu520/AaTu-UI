package cn.aatu.ui;

import com.mojang.logging.LogUtils;
import java.util.function.BiConsumer;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

final class DamageRecapLink {
    private static final CustomPacketPayload.Type<Payload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath("aatu_combat", "damage_recap"));

    private DamageRecapLink() {}

    static void register(RegisterPayloadHandlersEvent event) {
        try {
            Class<?> bridge = Class.forName("cn.aatu.combat.DamageRecapSync");
            BiConsumer<Connection, CompoundTag> receiver = DamageRecapClient::receive;
            bridge.getMethod("setClientReceiver", BiConsumer.class).invoke(null, receiver);
            return;
        } catch (ClassNotFoundException ignored) {
            // A UI-only client owns the optional channel itself.
        } catch (ReflectiveOperationException | LinkageError | RuntimeException exception) {
            LogUtils.getLogger().warn("Unable to connect to the installed combat recap channel", exception);
            return;
        }
        event.registrar("1").optional().playToClient(TYPE, Payload.CODEC,
                (payload, context) -> context.enqueueWork(() -> DamageRecapClient.receive(context.connection(), payload.data())));
    }

    private record Payload(CompoundTag data) implements CustomPacketPayload {
        private static final StreamCodec<RegistryFriendlyByteBuf, Payload> CODEC =
                StreamCodec.composite(ByteBufCodecs.COMPOUND_TAG, Payload::data, Payload::new);
        @Override
        public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }
}
