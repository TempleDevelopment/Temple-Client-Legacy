package xyz.templecheats.templeclient.manager;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Session;
import xyz.templecheats.templeclient.TempleClient;
import xyz.templecheats.templeclient.util.alt.Alt;
import xyz.templecheats.templeclient.util.alt.MicrosoftAuth;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Stores the user's saved accounts and applies them to the running game.
 * <p>
 * Offline alts are applied instantly (no network). Microsoft alts are exchanged for a fresh
 * Minecraft session via {@link MicrosoftAuth} - that involves network calls, so callers should
 * run {@link #login(Alt)} off the client thread (see the GUI).
 * <p>
 * The session swap goes through {@link TempleClient#setSession(Session)} which finds the field by
 * <i>type</i>, so it keeps working in an obfuscated production build (where the field is renamed).
 */
public class AltManager {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private final File altsFile;
    private final List<Alt> alts = new CopyOnWriteArrayList<>();

    public AltManager() {
        File versionDirectory = new File(System.getProperty("user.home"),
                ".templeclient" + File.separator + "1.12.2");
        if (!versionDirectory.exists()) {
            versionDirectory.mkdirs();
        }
        this.altsFile = new File(versionDirectory, "alts.json");
        load();
    }

    /****************************************************************
     *                      Alt list management
     ****************************************************************/

    public List<Alt> getAlts() {
        return alts;
    }

    public void addAlt(Alt alt) {
        if (alt == null || alt.getUsername() == null || alt.getUsername().trim().isEmpty()) {
            return;
        }
        // Replace an existing entry of the same type + name instead of duplicating.
        alts.removeIf(existing -> existing.getType() == alt.getType()
                && existing.getUsername().equalsIgnoreCase(alt.getUsername()));
        alts.add(alt);
        save();
    }

    public void removeAlt(Alt alt) {
        if (alts.remove(alt)) {
            save();
        }
    }

    /****************************************************************
     *                      Login
     ****************************************************************/

    /**
     * Applies the given alt to the running game.
     *
     * @return null on success, or a user-facing error message on failure.
     */
    public String login(Alt alt) {
        if (alt == null) {
            return "No account selected.";
        }
        if (alt.getType() == Alt.Type.MICROSOFT) {
            try {
                MicrosoftAuth.MinecraftLogin result = MicrosoftAuth.loginWithRefreshToken(alt.getRefreshToken());
                // Keep the stored account fresh (name changes, rotated refresh token).
                alt.setUsername(result.username);
                alt.setUuid(result.uuid);
                alt.setRefreshToken(result.refreshToken);
                save();
                applySession(new Session(result.username, result.uuid, result.accessToken, "mojang"));
                return null;
            } catch (MicrosoftAuth.AuthException e) {
                return e.getMessage();
            }
        }
        return loginOffline(alt.getUsername());
    }

    /**
     * Swaps to a client-side offline session for the given name without saving it as an alt.
     *
     * @return null on success, or an error message.
     */
    public String loginOffline(String username) {
        if (username == null || username.trim().isEmpty()) {
            return "Enter a username first.";
        }
        username = username.trim();
        applySession(new Session(username, Alt.offlineUuid(username).toString(), "0", "legacy"));
        return null;
    }

    private void applySession(Session session) {
        // login() may be called from a worker thread (Microsoft refresh). Setting the session
        // touches the LWJGL Display title, so apply it on the client thread.
        Minecraft.getMinecraft().addScheduledTask(() -> TempleClient.setSession(session));
    }

    /****************************************************************
     *                      Persistence
     ****************************************************************/

    private void load() {
        if (!altsFile.exists()) {
            return;
        }
        try (FileReader reader = new FileReader(altsFile)) {
            Alt[] loaded = GSON.fromJson(reader, Alt[].class);
            if (loaded != null) {
                List<Alt> sanitized = new ArrayList<>();
                for (Alt alt : loaded) {
                    if (alt != null && alt.getUsername() != null && !alt.getUsername().trim().isEmpty()) {
                        sanitized.add(alt);
                    }
                }
                alts.addAll(sanitized);
            }
        } catch (Exception e) {
            if (TempleClient.logger != null) {
                TempleClient.logger.error("Could not load alts.", e);
            }
        }
    }

    private void save() {
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(altsFile))) {
            writer.write(GSON.toJson(new ArrayList<>(alts)));
        } catch (IOException e) {
            if (TempleClient.logger != null) {
                TempleClient.logger.error("Could not save alts.", e);
            }
        }
    }
}
