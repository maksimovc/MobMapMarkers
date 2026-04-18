package dev.thenexusgates.mobmapmarkers.ui;

import com.hypixel.hytale.protocol.SoundCategory;
import com.hypixel.hytale.server.core.asset.type.soundevent.config.SoundEvent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.SoundUtil;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;

public final class MobMapUiSounds {

    public enum Cue {
        NAVIGATE,
        APPLY,
        POSITIVE,
        NEGATIVE
    }

    private final Map<Cue, Integer> resolved = new EnumMap<>(Cue.class);

    public void play(PlayerRef playerRef, Cue cue) {
        if (playerRef == null || cue == null) {
            return;
        }

        OptionalInt soundIndex = resolve(cue);
        if (soundIndex.isEmpty()) {
            return;
        }

        SoundUtil.playSoundEvent2dToPlayer(playerRef, soundIndex.getAsInt(), category(cue));
    }

    private OptionalInt resolve(Cue cue) {
        Integer cached = resolved.get(cue);
        if (cached != null && cached > 0) {
            return OptionalInt.of(cached);
        }

        try {
            var assetMap = SoundEvent.getAssetMap();
            for (String candidate : candidates(cue)) {
                int index = assetMap.getIndex(candidate);
                if (index > 0) {
                    resolved.put(cue, index);
                    return OptionalInt.of(index);
                }
            }
        } catch (Throwable ignored) {
        }

        return OptionalInt.empty();
    }

    private List<String> candidates(Cue cue) {
        return switch (cue) {
            case NAVIGATE -> List.of("SFX_Attn_Quiet", "SFX_Chest_Wooden_Open");
            case APPLY -> List.of("SFX_Attn_Moderate", "SFX_Capture_Crate_Capture_Succeed", "SFX_Coins_Land");
            case POSITIVE -> List.of("SFX_Capture_Crate_Capture_Succeed", "SFX_Attn_Moderate", "SFX_Coins_Land");
            case NEGATIVE -> List.of("SFX_Cactus_Large_Hit", "SFX_Attn_Quiet");
        };
    }

    private SoundCategory category(Cue cue) {
        return cue == MobMapUiSounds.Cue.NEGATIVE ? SoundCategory.SFX : SoundCategory.UI;
    }
}