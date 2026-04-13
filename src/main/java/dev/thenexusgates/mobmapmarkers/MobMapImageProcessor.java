package dev.thenexusgates.mobmapmarkers;

import javax.imageio.ImageIO;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.Polygon;
import java.awt.RenderingHints;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Locale;
import java.util.logging.Logger;

final class MobMapImageProcessor {

    private static final Logger LOGGER = Logger.getLogger(MobMapImageProcessor.class.getName());

    private MobMapImageProcessor() {
    }

    static byte[] createFallbackMarkerPng(int size, int contentScalePercent) {
        try {
            int iconSize = Math.max(16, size);
            double fillRatio = Math.max(0.5D, Math.min(1.0D, contentScalePercent / 100.0D));
            BufferedImage out = new BufferedImage(iconSize, iconSize, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = out.createGraphics();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);

            int contentSize = Math.max(10, (int) Math.round(iconSize * fillRatio));
            int inset = Math.max(1, (iconSize - contentSize) / 2);

            g.setColor(new Color(28, 33, 40, 220));
            g.fillOval(inset, inset, contentSize, contentSize);

            g.setColor(new Color(110, 123, 139, 255));
            int headSize = Math.max(6, contentSize / 3);
            int headX = (iconSize - headSize) / 2;
            int headY = inset + Math.max(2, contentSize / 10);
            g.fillOval(headX, headY, headSize, headSize);

            int torsoWidth = Math.max(8, contentSize / 2);
            int torsoHeight = Math.max(6, contentSize / 3);
            int torsoX = (iconSize - torsoWidth) / 2;
            int torsoY = headY + headSize - Math.max(1, contentSize / 16);
            g.fill(new RoundRectangle2D.Float(
                    torsoX,
                    torsoY,
                    torsoWidth,
                    torsoHeight,
                    Math.max(4, contentSize / 5f),
                    Math.max(4, contentSize / 5f)));

            g.setColor(new Color(255, 255, 255, 38));
            g.setStroke(new BasicStroke(Math.max(1.5f, contentSize / 20f), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g.drawOval(inset, inset, contentSize, contentSize);
            g.dispose();

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ImageIO.write(out, "PNG", baos);
            return baos.toByteArray();
        } catch (IOException e) {
            LOGGER.warning("[MobMapMarkers] Failed to create fallback marker PNG: " + e.getMessage());
            return new byte[0];
        }
    }

    static byte[] createFallbackMarkerPng(int size) {
        return createFallbackMarkerPng(size, 96);
    }

    /**
     * Renders a plain circle badge (no map-pin tail) for use on the minimap, where
     * the icon is centred directly on the mob's world position.
     * The big-map marker ({@link #createMobMarkerPng}) is intentionally NOT used here
     * because its downward tail shifts the circle away from the true mob position.
     */
    static byte[] createMinimapBadgePng(String roleName, String displayName, int size) {
        try {
            int iconSize = Math.max(16, size);
            BufferedImage out = new BufferedImage(iconSize, iconSize, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = out.createGraphics();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

            Color fill = colorFromSeed(roleName);
            Color border = fill.darker().darker();

            int inset = Math.max(1, iconSize / 12);
            int circleSize = iconSize - inset * 2;

            // Shadow
            g.setColor(new Color(18, 22, 28, 135));
            g.fillOval(inset, inset + 1, circleSize, circleSize);

            // Fill
            g.setColor(fill);
            g.fillOval(inset, inset, circleSize, circleSize);

            // Gloss highlight
            g.setColor(new Color(255, 255, 255, 45));
            g.fillOval(inset + Math.max(1, iconSize / 10), inset + Math.max(1, iconSize / 10),
                    Math.max(3, iconSize / 4), Math.max(3, iconSize / 5));

            // Border
            g.setColor(border);
            g.setStroke(new BasicStroke(Math.max(1f, iconSize / 18f), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g.drawOval(inset, inset, circleSize, circleSize);

            // Text — centred in the circle
            String text = abbreviation(displayName != null ? displayName : roleName);
            int fontSize = text.length() > 1 ? Math.max(8, iconSize / 2) : Math.max(10, (int) (iconSize / 1.85f));
            g.setFont(new Font("SansSerif", Font.BOLD, fontSize));
            FontMetrics metrics = g.getFontMetrics();
            int textX = (iconSize - metrics.stringWidth(text)) / 2;
            int textY = (iconSize - metrics.getHeight()) / 2 + metrics.getAscent();

            g.setColor(new Color(0, 0, 0, 90));
            g.drawString(text, textX + 1, textY + 1);
            g.setColor(Color.WHITE);
            g.drawString(text, textX, textY);
            g.dispose();

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ImageIO.write(out, "PNG", baos);
            return baos.toByteArray();
        } catch (IOException e) {
            LOGGER.warning("[MobMapMarkers] Failed to create minimap badge PNG: " + e.getMessage());
            return createFallbackMarkerPng(size);
        }
    }

    static byte[] createMobMarkerPng(String roleName, String displayName, int size) {
        try {
            int iconSize = Math.max(20, size);
            BufferedImage out = new BufferedImage(iconSize, iconSize, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = out.createGraphics();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

            Color fill = colorFromSeed(roleName);
            Color shadow = new Color(18, 22, 28, 135);
            Color border = fill.darker().darker();

            int inset = Math.max(2, iconSize / 18);
            int bodySize = iconSize - inset * 2;
            int bodyY = inset;
            int tailHeight = Math.max(5, iconSize / 5);

            g.setColor(shadow);
            g.fillOval(inset, bodyY + 1, bodySize, bodySize);
            Polygon tail = new Polygon();
            tail.addPoint(iconSize / 2, iconSize - inset);
            tail.addPoint(iconSize / 2 - tailHeight, iconSize - inset - tailHeight);
            tail.addPoint(iconSize / 2 + tailHeight, iconSize - inset - tailHeight);
            g.fillPolygon(tail);

            g.setColor(fill);
            g.fillOval(inset, bodyY, bodySize, bodySize);
            g.fillPolygon(tail);

            g.setColor(new Color(255, 255, 255, 45));
            g.fillOval(inset + Math.max(1, iconSize / 10), bodyY + Math.max(1, iconSize / 10),
                    Math.max(4, iconSize / 4), Math.max(4, iconSize / 5));

            g.setColor(border);
            g.setStroke(new BasicStroke(Math.max(1.5f, iconSize / 18f), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g.drawOval(inset, bodyY, bodySize, bodySize);
            g.drawLine(iconSize / 2 - tailHeight, iconSize - inset - tailHeight, iconSize / 2, iconSize - inset);
            g.drawLine(iconSize / 2, iconSize - inset, iconSize / 2 + tailHeight, iconSize - inset - tailHeight);

            String text = abbreviation(displayName != null ? displayName : roleName);
            int fontSize = text.length() > 1 ? Math.max(10, iconSize / 2) : Math.max(12, (int) (iconSize / 1.85f));
            g.setFont(new Font("SansSerif", Font.BOLD, fontSize));
            FontMetrics metrics = g.getFontMetrics();
            int textWidth = metrics.stringWidth(text);
            int textX = (iconSize - textWidth) / 2;
            int textY = bodyY + ((bodySize - tailHeight) / 2) + (metrics.getAscent() / 2) - 1;

            g.setColor(new Color(0, 0, 0, 90));
            g.drawString(text, textX + 1, textY + 1);
            g.setColor(Color.WHITE);
            g.drawString(text, textX, textY);
            g.dispose();

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ImageIO.write(out, "PNG", baos);
            return baos.toByteArray();
        } catch (IOException e) {
            LOGGER.warning("[MobMapMarkers] Failed to create generated mob marker PNG: " + e.getMessage());
            return createFallbackMarkerPng(size);
        }
    }

    static byte[] createMobPortraitMarkerPng(byte[] rawPng, int size, boolean facingRight, int contentScalePercent) {
        if (rawPng == null || rawPng.length == 0) {
            return createFallbackMarkerPng(size, contentScalePercent);
        }

        try {
            BufferedImage src = ImageIO.read(new ByteArrayInputStream(rawPng));
            if (src == null) {
                return createFallbackMarkerPng(size, contentScalePercent);
            }

            BufferedImage cropped = cropToOpaqueBounds(src);
            if (cropped == null) {
                return createFallbackMarkerPng(size, contentScalePercent);
            }

            int iconSize = Math.max(20, size);
            double fillRatio = Math.max(0.5D, Math.min(1.0D, contentScalePercent / 100.0D));
            int targetSize = Math.max(8, (int) Math.round(iconSize * fillRatio));
            double scale = Math.min(targetSize / (double) cropped.getWidth(), targetSize / (double) cropped.getHeight());
            int drawWidth = Math.max(1, (int) Math.round(cropped.getWidth() * scale));
            int drawHeight = Math.max(1, (int) Math.round(cropped.getHeight() * scale));
            int drawX = (iconSize - drawWidth) / 2;
            int drawY = (iconSize - drawHeight) / 2;

            BufferedImage out = new BufferedImage(iconSize, iconSize, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = out.createGraphics();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);

            g.setColor(new Color(0, 0, 0, 52));
            g.fillOval(Math.max(1, drawX - 1), Math.max(1, iconSize - Math.max(6, iconSize / 5)),
                    Math.max(8, drawWidth - 2), Math.max(4, iconSize / 8));
            if (facingRight) {
                g.drawImage(cropped, drawX + drawWidth, drawY, -drawWidth, drawHeight, null);
            } else {
                g.drawImage(cropped, drawX, drawY, drawWidth, drawHeight, null);
            }
            g.dispose();

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ImageIO.write(out, "PNG", baos);
            return baos.toByteArray();
        } catch (IOException e) {
            LOGGER.warning("[MobMapMarkers] Failed to create portrait mob marker PNG: " + e.getMessage());
            return createFallbackMarkerPng(size, contentScalePercent);
        }
    }

    private static BufferedImage cropToOpaqueBounds(BufferedImage src) {
        int minX = src.getWidth();
        int minY = src.getHeight();
        int maxX = -1;
        int maxY = -1;

        for (int y = 0; y < src.getHeight(); y++) {
            for (int x = 0; x < src.getWidth(); x++) {
                int alpha = (src.getRGB(x, y) >>> 24) & 0xFF;
                if (alpha < 8) {
                    continue;
                }

                if (x < minX) {
                    minX = x;
                }
                if (y < minY) {
                    minY = y;
                }
                if (x > maxX) {
                    maxX = x;
                }
                if (y > maxY) {
                    maxY = y;
                }
            }
        }

        if (maxX < minX || maxY < minY) {
            return null;
        }

        int margin = Math.max(1, Math.min(src.getWidth(), src.getHeight()) / 64);
        minX = Math.max(0, minX - margin);
        minY = Math.max(0, minY - margin);
        maxX = Math.min(src.getWidth() - 1, maxX + margin);
        maxY = Math.min(src.getHeight() - 1, maxY + margin);
        return src.getSubimage(minX, minY, maxX - minX + 1, maxY - minY + 1);
    }

    private static Color colorFromSeed(String seed) {
        int hash = seed != null ? seed.hashCode() : 0;
        float hue = ((hash & 0x7fffffff) % 360) / 360f;
        return Color.getHSBColor(hue, 0.55f, 0.9f);
    }

    private static String abbreviation(String name) {
        if (name == null || name.isBlank()) {
            return "?";
        }

        String cleaned = name.replaceAll("[^\\p{L}\\p{N} ]", " ").trim();
        if (cleaned.isEmpty()) {
            return "?";
        }

        String[] parts = cleaned.split("\\s+");
        if (parts.length >= 2) {
            return (firstCodePoint(parts[0]) + firstCodePoint(parts[1])).toUpperCase(Locale.ROOT);
        }

        String compact = parts[0];
        return leadingCodePoints(compact, 2).toUpperCase(Locale.ROOT);
    }

    private static String firstCodePoint(String value) {
        return leadingCodePoints(value, 1);
    }

    private static String leadingCodePoints(String value, int count) {
        if (value == null || value.isEmpty() || count <= 0) {
            return "";
        }

        int endIndex = value.offsetByCodePoints(0, Math.min(count, value.codePointCount(0, value.length())));
        return value.substring(0, endIndex);
    }
}
