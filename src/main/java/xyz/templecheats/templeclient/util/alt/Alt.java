package xyz.templecheats.templeclient.util.alt;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * Represents a single saved account.
 * <p>
 * {@link Type#OFFLINE} alts are purely client side - logging in just swaps the in-game
 * {@link net.minecraft.util.Session} to the chosen name with an offline UUID and a dummy token.
 * They are not authenticated, so they only work on offline-mode / cracked servers, but they
 * never touch the network.
 * <p>
 * {@link Type#MICROSOFT} alts store a Microsoft OAuth refresh token. Logging in exchanges that
 * refresh token (via {@link MicrosoftAuth}) for a fresh Minecraft access token, so the account
 * is a real, online-capable session.
 */
public class Alt {

    public enum Type {
        OFFLINE,
        MICROSOFT
    }

    private Type type;
    private String username;
    private String uuid;
    private String refreshToken;

    /** Required for Gson deserialization. */
    public Alt() {
    }

    public Alt(Type type, String username, String uuid, String refreshToken) {
        this.type = type;
        this.username = username;
        this.uuid = uuid;
        this.refreshToken = refreshToken;
    }

    /****************************************************************
     *                      Factories
     ****************************************************************/

    public static Alt offline(String username) {
        return new Alt(Type.OFFLINE, username, offlineUuid(username).toString(), null);
    }

    public static Alt microsoft(String username, String uuid, String refreshToken) {
        return new Alt(Type.MICROSOFT, username, uuid, refreshToken);
    }

    /**
     * Mirrors vanilla's offline-mode UUID derivation so the player keeps a stable identity
     * (inventory, ender chest, etc.) across sessions on offline servers.
     */
    public static UUID offlineUuid(String username) {
        return UUID.nameUUIDFromBytes(("OfflinePlayer:" + username).getBytes(StandardCharsets.UTF_8));
    }

    /****************************************************************
     *                      Getters / Setters
     ****************************************************************/

    public Type getType() {
        return type == null ? Type.OFFLINE : type;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getUuid() {
        return uuid;
    }

    public void setUuid(String uuid) {
        this.uuid = uuid;
    }

    public String getRefreshToken() {
        return refreshToken;
    }

    public void setRefreshToken(String refreshToken) {
        this.refreshToken = refreshToken;
    }
}
