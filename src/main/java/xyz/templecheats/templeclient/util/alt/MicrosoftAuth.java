package xyz.templecheats.templeclient.util.alt;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.apache.commons.io.IOUtils;

import java.io.IOException;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/**
 * Microsoft account authentication using the OAuth 2.0 device-code flow.
 * <p>
 * The user is never asked to type a password into the client. Instead we request a short
 * user code, the user enters it at <i>microsoft.com/link</i>, and we poll until Microsoft
 * hands us tokens. We then walk the standard chain:
 * <pre>
 *   MS token -&gt; Xbox Live (XBL) -&gt; XSTS -&gt; Minecraft services -&gt; profile
 * </pre>
 * <p>
 * This uses the well-known public Minecraft launcher client id, so it works without any Azure
 * setup. If Microsoft ever stops accepting it, register a free Azure application (public client,
 * "Allow public client flows" = yes) and replace {@link #CLIENT_ID}.
 */
public final class MicrosoftAuth {

    /** Public Minecraft launcher client id - lets the device-code flow work with no Azure setup. */
    public static final String CLIENT_ID = "00000000402b5328";

    private static final String DEVICE_CODE_URL = "https://login.live.com/oauth20_connect.srf";
    private static final String TOKEN_URL = "https://login.live.com/oauth20_token.srf";
    private static final String XBL_URL = "https://user.auth.xboxlive.com/user/authenticate";
    private static final String XSTS_URL = "https://xsts.auth.xboxlive.com/xsts/authorize";
    private static final String MC_LOGIN_URL = "https://api.minecraftservices.com/authentication/login_with_xbox";
    private static final String MC_PROFILE_URL = "https://api.minecraftservices.com/minecraft/profile";
    private static final String SCOPE = "service::user.auth.xboxlive.com::MBI_SSL";

    private static final int CONNECT_TIMEOUT = 15_000;
    private static final int READ_TIMEOUT = 15_000;

    private MicrosoftAuth() {
    }

    /****************************************************************
     *                      Result holders
     ****************************************************************/

    /** First step result: what the user has to do to approve the login. */
    public static class DeviceCode {
        public final String userCode;
        public final String deviceCode;
        public final String verificationUri;
        public final String message;
        public final int interval;
        public final int expiresIn;

        DeviceCode(String userCode, String deviceCode, String verificationUri, String message, int interval, int expiresIn) {
            this.userCode = userCode;
            this.deviceCode = deviceCode;
            this.verificationUri = verificationUri;
            this.message = message;
            this.interval = interval;
            this.expiresIn = expiresIn;
        }
    }

    /** Final result: a ready-to-use Minecraft session plus the refresh token to persist. */
    public static class MinecraftLogin {
        public final String username;
        public final String uuid;
        public final String accessToken;
        public final String refreshToken;

        MinecraftLogin(String username, String uuid, String accessToken, String refreshToken) {
            this.username = username;
            this.uuid = uuid;
            this.accessToken = accessToken;
            this.refreshToken = refreshToken;
        }
    }

    /** Raised when authentication fails, with a user-friendly message. */
    public static class AuthException extends Exception {
        public AuthException(String message) {
            super(message);
        }

        public AuthException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    /****************************************************************
     *                      Step 1: device code
     ****************************************************************/

    public static DeviceCode requestDeviceCode() throws AuthException {
        try {
            String body = "client_id=" + enc(CLIENT_ID)
                    + "&scope=" + enc(SCOPE)
                    + "&response_type=device_code";
            JsonObject json = parseObject(post(DEVICE_CODE_URL, "application/x-www-form-urlencoded", body, null));

            return new DeviceCode(
                    json.get("user_code").getAsString(),
                    json.get("device_code").getAsString(),
                    json.has("verification_uri") ? json.get("verification_uri").getAsString() : "https://www.microsoft.com/link",
                    json.has("message") ? json.get("message").getAsString() : "",
                    json.has("interval") ? json.get("interval").getAsInt() : 5,
                    json.has("expires_in") ? json.get("expires_in").getAsInt() : 900
            );
        } catch (AuthException e) {
            throw e;
        } catch (Exception e) {
            throw new AuthException("Could not start Microsoft login.", e);
        }
    }

    /****************************************************************
     *                      Steps 2-6: full login
     ****************************************************************/

    /**
     * Polls Microsoft until the user approves the device code, then completes the whole chain.
     *
     * @param code      the device code returned by {@link #requestDeviceCode()}
     * @param cancelled checked between polls so the caller can abort
     * @param status    optional progress callback (e.g. for a GUI status line)
     */
    public static MinecraftLogin pollAndLogin(DeviceCode code, BooleanSupplier cancelled, Consumer<String> status) throws AuthException {
        long deadline = System.currentTimeMillis() + code.expiresIn * 1000L;
        int interval = Math.max(1, code.interval);

        while (System.currentTimeMillis() < deadline) {
            if (cancelled != null && cancelled.getAsBoolean()) {
                throw new AuthException("Login cancelled.");
            }

            sleep(interval * 1000L);

            String body = "client_id=" + enc(CLIENT_ID)
                    + "&grant_type=" + enc("urn:ietf:params:oauth:grant-type:device_code")
                    + "&device_code=" + enc(code.deviceCode);

            JsonObject json;
            try {
                json = parseObject(postRaw(TOKEN_URL, "application/x-www-form-urlencoded", body, null));
            } catch (Exception e) {
                throw new AuthException("Microsoft token request failed.", e);
            }

            if (json.has("error")) {
                String error = json.get("error").getAsString();
                switch (error) {
                    case "authorization_pending":
                        if (status != null) status.accept("Waiting for approval...");
                        continue;
                    case "slow_down":
                        interval += 5;
                        continue;
                    case "authorization_declined":
                        throw new AuthException("You declined the login.");
                    case "expired_token":
                    case "code_expired":
                        throw new AuthException("The code expired. Please try again.");
                    default:
                        throw new AuthException("Microsoft login failed: " + error);
                }
            }

            String accessToken = json.get("access_token").getAsString();
            String refreshToken = json.has("refresh_token") ? json.get("refresh_token").getAsString() : null;
            return completeChain(accessToken, refreshToken, status);
        }

        throw new AuthException("The code expired. Please try again.");
    }

    /**
     * Logs in again using a stored refresh token (no user interaction needed).
     * Returns a fresh session, including a possibly-rotated refresh token to persist.
     */
    public static MinecraftLogin loginWithRefreshToken(String refreshToken) throws AuthException {
        if (refreshToken == null || refreshToken.isEmpty()) {
            throw new AuthException("This account has no saved login. Remove and re-add it.");
        }
        try {
            String body = "client_id=" + enc(CLIENT_ID)
                    + "&grant_type=refresh_token"
                    + "&scope=" + enc(SCOPE)
                    + "&refresh_token=" + enc(refreshToken);
            JsonObject json = parseObject(postRaw(TOKEN_URL, "application/x-www-form-urlencoded", body, null));

            if (json.has("error")) {
                throw new AuthException("Session expired - please re-add this account.");
            }

            String accessToken = json.get("access_token").getAsString();
            String newRefresh = json.has("refresh_token") ? json.get("refresh_token").getAsString() : refreshToken;
            return completeChain(accessToken, newRefresh, null);
        } catch (AuthException e) {
            throw e;
        } catch (Exception e) {
            throw new AuthException("Could not refresh Microsoft session.", e);
        }
    }

    private static MinecraftLogin completeChain(String msAccessToken, String refreshToken, Consumer<String> status) throws AuthException {
        try {
            if (status != null) status.accept("Authenticating with Xbox Live...");
            JsonObject xbl = parseObject(post(XBL_URL, "application/json", xblBody(msAccessToken), null));
            String xblToken = xbl.get("Token").getAsString();
            String userHash = firstUserHash(xbl);

            if (status != null) status.accept("Authorizing (XSTS)...");
            JsonObject xsts;
            try {
                xsts = parseObject(post(XSTS_URL, "application/json", xstsBody(xblToken), null));
            } catch (HttpError e) {
                throw new AuthException(describeXstsError(e.body), e);
            }
            String xstsToken = xsts.get("Token").getAsString();

            if (status != null) status.accept("Logging into Minecraft...");
            JsonObject mc = parseObject(post(MC_LOGIN_URL, "application/json",
                    "{\"identityToken\":\"XBL3.0 x=" + userHash + ";" + xstsToken + "\"}", null));
            String mcAccessToken = mc.get("access_token").getAsString();

            if (status != null) status.accept("Fetching profile...");
            JsonObject profile;
            try {
                profile = parseObject(get(MC_PROFILE_URL, "Bearer " + mcAccessToken));
            } catch (HttpError e) {
                throw new AuthException("This Microsoft account does not own Minecraft.", e);
            }
            if (!profile.has("id") || !profile.has("name")) {
                throw new AuthException("This Microsoft account does not own Minecraft.");
            }

            return new MinecraftLogin(
                    profile.get("name").getAsString(),
                    dashUuid(profile.get("id").getAsString()),
                    mcAccessToken,
                    refreshToken
            );
        } catch (AuthException e) {
            throw e;
        } catch (Exception e) {
            throw new AuthException("Microsoft authentication failed.", e);
        }
    }

    /****************************************************************
     *                      Request bodies
     ****************************************************************/

    private static String xblBody(String msAccessToken) {
        // For the live.com MBI_SSL flow the RpsTicket is the raw access token (no "d=" prefix).
        return "{"
                + "\"Properties\":{"
                + "\"AuthMethod\":\"RPS\","
                + "\"SiteName\":\"user.auth.xboxlive.com\","
                + "\"RpsTicket\":\"" + msAccessToken + "\""
                + "},"
                + "\"RelyingParty\":\"http://auth.xboxlive.com\","
                + "\"TokenType\":\"JWT\""
                + "}";
    }

    private static String xstsBody(String xblToken) {
        return "{"
                + "\"Properties\":{"
                + "\"SandboxId\":\"RETAIL\","
                + "\"UserTokens\":[\"" + xblToken + "\"]"
                + "},"
                + "\"RelyingParty\":\"rp://api.minecraftservices.com/\","
                + "\"TokenType\":\"JWT\""
                + "}";
    }

    private static String firstUserHash(JsonObject xboxResponse) throws AuthException {
        JsonObject claims = xboxResponse.getAsJsonObject("DisplayClaims");
        if (claims != null) {
            JsonArray xui = claims.getAsJsonArray("xui");
            if (xui != null && xui.size() > 0) {
                JsonElement uhs = xui.get(0).getAsJsonObject().get("uhs");
                if (uhs != null) {
                    return uhs.getAsString();
                }
            }
        }
        throw new AuthException("Xbox Live did not return a user hash.");
    }

    private static String describeXstsError(String body) {
        // https://wiki.vg/Microsoft_Authentication_Scheme XErr codes
        if (body != null) {
            if (body.contains("2148916233")) return "This account has no Xbox profile. Sign in once at minecraft.net first.";
            if (body.contains("2148916235")) return "Xbox Live is not available in this account's region.";
            if (body.contains("2148916238")) return "This is a child account and must be added to a Family.";
        }
        return "Xbox authorization failed (XSTS).";
    }

    /****************************************************************
     *                      HTTP helpers
     ****************************************************************/

    private static String get(String url, String authHeader) throws IOException {
        HttpURLConnection connection = open(url, "GET", null, authHeader);
        return readChecked(connection);
    }

    /** POST that throws {@link HttpError} on non-2xx (so callers can inspect the body). */
    private static String post(String url, String contentType, String body, String authHeader) throws IOException {
        HttpURLConnection connection = open(url, "POST", contentType, authHeader);
        writeBody(connection, body);
        return readChecked(connection);
    }

    /** POST that returns the body for any status code (used for the token endpoint's expected 400s). */
    private static String postRaw(String url, String contentType, String body, String authHeader) throws IOException {
        HttpURLConnection connection = open(url, "POST", contentType, authHeader);
        writeBody(connection, body);
        return readBody(connection);
    }

    private static HttpURLConnection open(String url, String method, String contentType, String authHeader) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
        connection.setRequestMethod(method);
        connection.setConnectTimeout(CONNECT_TIMEOUT);
        connection.setReadTimeout(READ_TIMEOUT);
        connection.setRequestProperty("Accept", "application/json");
        if (contentType != null) connection.setRequestProperty("Content-Type", contentType);
        if (authHeader != null) connection.setRequestProperty("Authorization", authHeader);
        return connection;
    }

    private static void writeBody(HttpURLConnection connection, String body) throws IOException {
        connection.setDoOutput(true);
        try (OutputStream os = connection.getOutputStream()) {
            os.write(body.getBytes(StandardCharsets.UTF_8));
        }
    }

    private static String readChecked(HttpURLConnection connection) throws IOException {
        int code = connection.getResponseCode();
        String body = readBody(connection);
        if (code < 200 || code >= 300) {
            throw new HttpError(code, body);
        }
        return body;
    }

    private static String readBody(HttpURLConnection connection) throws IOException {
        int code = connection.getResponseCode();
        java.io.InputStream stream = (code >= 200 && code < 300)
                ? connection.getInputStream()
                : connection.getErrorStream();
        if (stream == null) return "";
        return IOUtils.toString(stream, StandardCharsets.UTF_8);
    }

    private static class HttpError extends IOException {
        final int code;
        final String body;

        HttpError(int code, String body) {
            super("HTTP " + code + ": " + body);
            this.code = code;
            this.body = body;
        }
    }

    /****************************************************************
     *                      Misc helpers
     ****************************************************************/

    private static JsonObject parseObject(String json) throws AuthException {
        try {
            JsonElement element = new JsonParser().parse(json);
            if (!element.isJsonObject()) {
                throw new AuthException("Unexpected response from Microsoft.");
            }
            return element.getAsJsonObject();
        } catch (AuthException e) {
            throw e;
        } catch (Exception e) {
            throw new AuthException("Could not read Microsoft response.", e);
        }
    }

    private static String enc(String value) {
        try {
            return URLEncoder.encode(value, "UTF-8");
        } catch (Exception e) {
            return value;
        }
    }

    private static String dashUuid(String uuid) {
        if (uuid == null) return null;
        if (uuid.contains("-")) return uuid;
        if (uuid.length() != 32) return uuid;
        return uuid.replaceFirst(
                "(\\p{XDigit}{8})(\\p{XDigit}{4})(\\p{XDigit}{4})(\\p{XDigit}{4})(\\p{XDigit}+)",
                "$1-$2-$3-$4-$5");
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
