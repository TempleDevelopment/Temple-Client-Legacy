package xyz.templecheats.templeclient.manager;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.util.ResourceLocation;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.net.URL;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Lazily downloads and caches player head avatars (face + hat overlay) for the alt list.
 * <p>
 * Mirrors {@link CapeManager}: the PNG is fetched on a background thread, then the texture is
 * registered on the client thread. Until a head is ready - or if the download fails (offline,
 * blocked, or a name that isn't a real account) - {@link #getHead(String)} returns null and the
 * caller falls back to the vanilla default skin, so the menu never blocks or breaks.
 */
public class HeadManager {

    /** %s = username; "helm" gives the face with the hat layer already composited. */
    private static final String AVATAR_URL = "https://minotar.net/helm/%s/8.png";

    private final Map<String, ResourceLocation> cache = new ConcurrentHashMap<>();
    private final Set<String> requested = ConcurrentHashMap.newKeySet();

    /**
     * @return the cached head texture for the name, or null if it isn't loaded yet
     *         (a download is kicked off on the first miss).
     */
    public ResourceLocation getHead(String username) {
        if (username == null || username.trim().isEmpty()) {
            return null;
        }
        String key = username.toLowerCase();

        ResourceLocation cached = cache.get(key);
        if (cached != null) {
            return cached;
        }
        if (requested.add(key)) {
            CompletableFuture.runAsync(() -> download(username, key));
        }
        return null;
    }

    private void download(String username, String key) {
        try {
            URL url = new URL(String.format(AVATAR_URL, username));
            BufferedImage image;
            try (InputStream stream = url.openStream()) {
                image = ImageIO.read(stream);
            }
            if (image == null) {
                return;
            }
            // Texture creation must happen on the render thread.
            Minecraft.getMinecraft().addScheduledTask(() -> {
                DynamicTexture texture = new DynamicTexture(image);
                ResourceLocation location = Minecraft.getMinecraft().getTextureManager()
                        .getDynamicTextureLocation("templeclient_head_" + key, texture);
                cache.put(key, location);
            });
        } catch (Exception e) {
            // Leave it absent (still flagged as requested) so we fall back to the default skin
            // for this session instead of hammering the avatar service every frame.
        }
    }
}
