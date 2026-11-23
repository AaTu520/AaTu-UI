package cn.aatu.ui;

import java.util.Map;
import java.util.UUID;
import net.minecraft.client.gui.components.LerpingBossEvent;

public interface BossLayoutHook {
    Map<UUID, LerpingBossEvent> aatu$bossEvents();
}
