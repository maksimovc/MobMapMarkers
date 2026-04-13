package dev.thenexusgates.mobmapmarkers;

import java.util.Locale;

final class MobMarkerNames {

    private MobMarkerNames() {
    }

    static String formatRoleName(String roleName) {
        if (roleName == null || roleName.isBlank()) {
            return "Mob";
        }

        String[] parts = roleName.replace('-', '_').split("_");
        StringBuilder builder = new StringBuilder();
        for (String part : parts) {
            if (part == null || part.isBlank()) {
                continue;
            }

            if (!builder.isEmpty()) {
                builder.append(' ');
            }

            builder.append(Character.toUpperCase(part.charAt(0)));
            if (part.length() > 1) {
                builder.append(part.substring(1).toLowerCase(Locale.ROOT));
            }
        }

        return builder.isEmpty() ? "Mob" : builder.toString();
    }
}