package xyz.templecheats.templeclient.features.gui.menu.alt;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiMainMenu;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.Session;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;
import xyz.templecheats.templeclient.TempleClient;
import xyz.templecheats.templeclient.manager.HeadManager;
import xyz.templecheats.templeclient.util.alt.Alt;
import xyz.templecheats.templeclient.util.alt.MicrosoftAuth;

import java.awt.Desktop;
import java.io.IOException;
import java.net.URI;
import java.util.List;
import java.util.UUID;

/**
 * Account manager screen: a scrollable list of saved alts with add / remove / login, plus a
 * quick "direct login" box and Microsoft device-code login.
 */
public class GuiAccountSelector extends GuiScreen {

    private static final int ROW_HEIGHT = 24;
    private static final HeadManager HEADS = new HeadManager();

    // Button ids
    private GuiButton loginButton;
    private GuiButton removeButton;
    private GuiButton addOfflineButton;
    private GuiButton addMicrosoftButton;
    private GuiButton directLoginButton;
    private GuiButton backButton;
    private GuiButton copyCodeButton;
    private GuiButton openPageButton;
    private GuiButton cancelButton;

    private GuiTextField nameField;

    private int selectedIndex = -1;
    private int scrollOffset = 0;

    // Microsoft device-code flow state (touched from a worker thread).
    private volatile boolean msInProgress = false;
    private volatile boolean msCancelRequested = false;
    private volatile MicrosoftAuth.DeviceCode pendingCode = null;
    private volatile String status = "";

    @Override
    public void initGui() {
        super.initGui();
        int cx = this.width / 2;

        this.nameField = new GuiTextField(0, this.fontRenderer, cx - 160, 34, 150, 20);
        this.nameField.setMaxStringLength(16);

        // Top row: add / direct-login from the text box.
        this.addOfflineButton = addButton(new GuiButton(1, cx - 5, 34, 80, 20, "Add Offline"));
        this.directLoginButton = addButton(new GuiButton(2, cx + 80, 34, 80, 20, "Quick Login"));
        this.addMicrosoftButton = addButton(new GuiButton(3, cx - 160, 58, 320, 20, "Add Microsoft Account"));

        // Bottom row: act on the selected alt.
        this.loginButton = addButton(new GuiButton(4, cx - 160, this.height - 52, 100, 20, "Login"));
        this.removeButton = addButton(new GuiButton(5, cx - 54, this.height - 52, 100, 20, "Remove"));
        this.backButton = addButton(new GuiButton(6, cx + 52, this.height - 52, 108, 20, "Back"));

        // Shown only while a Microsoft login is pending.
        this.copyCodeButton = addButton(new GuiButton(7, cx - 160, this.height - 52, 155, 20, "Copy Code"));
        this.openPageButton = addButton(new GuiButton(8, cx + 5, this.height - 52, 155, 20, "Open Login Page"));
        this.cancelButton = addButton(new GuiButton(9, cx - 50, this.height - 28, 100, 20, "Cancel"));

        clampSelection();
        clampScroll();
    }

    /****************************************************************
     *                      Rendering
     ****************************************************************/

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        this.drawDefaultBackground();
        int cx = this.width / 2;

        this.drawCenteredString(this.fontRenderer, "Alt Manager", cx, 12, 0xFFFFFF);
        Session session = this.mc.getSession();
        this.drawCenteredString(this.fontRenderer, "Current: " + (session == null ? "?" : session.getUsername()), cx, 23, 0xAAAAAA);

        if (msInProgress) {
            drawMicrosoftFlow(cx);
        } else {
            drawAltList(mouseX, mouseY);
            // Placeholder for the name box.
            if (!this.nameField.isFocused() && this.nameField.getText().isEmpty()) {
                this.fontRenderer.drawString("Username", this.nameField.x + 4, this.nameField.y + 6, 0x808080);
            }
            this.nameField.drawTextBox();
        }

        if (!status.isEmpty()) {
            this.drawCenteredString(this.fontRenderer, status, cx, this.height - 64, 0xFFFF55);
        }

        // Draws the buttons (their visibility is updated in updateScreen).
        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    private void drawAltList(int mouseX, int mouseY) {
        int listTop = 86;
        int listBottom = this.height - 60;
        int left = this.width / 2 - 160;
        int right = this.width / 2 + 160;

        drawRect(left, listTop, right, listBottom, 0x60000000);

        List<Alt> alts = TempleClient.altManager.getAlts();
        if (alts.isEmpty()) {
            this.drawCenteredString(this.fontRenderer, "No saved accounts - add one above.", this.width / 2, (listTop + listBottom) / 2 - 4, 0x999999);
            return;
        }

        int visibleRows = (listBottom - listTop) / ROW_HEIGHT;
        for (int row = 0; row < visibleRows; row++) {
            int index = scrollOffset + row;
            if (index >= alts.size()) break;

            Alt alt = alts.get(index);
            int y = listTop + row * ROW_HEIGHT;
            boolean selected = index == selectedIndex;
            boolean hovered = mouseX >= left && mouseX <= right && mouseY >= y && mouseY < y + ROW_HEIGHT;

            if (selected) {
                drawRect(left, y, right, y + ROW_HEIGHT - 1, 0x803C78FF);
            } else if (hovered) {
                drawRect(left, y, right, y + ROW_HEIGHT - 1, 0x40FFFFFF);
            }

            drawHead(alt, left + 4, y + 4);

            int textX = left + 26;
            this.fontRenderer.drawStringWithShadow(alt.getUsername(), textX, y + 4, 0xFFFFFF);
            boolean microsoft = alt.getType() == Alt.Type.MICROSOFT;
            String tag = microsoft ? "Microsoft" : "Offline";
            int tagColor = microsoft ? 0x55FF55 : 0xAAAAAA;
            this.fontRenderer.drawString(tag, textX, y + 14, tagColor);
        }

        // Simple scroll hint.
        if (scrollOffset + visibleRows < alts.size()) {
            this.drawCenteredString(this.fontRenderer, "v", right - 8, listBottom - 10, 0xCCCCCC);
        }
        if (scrollOffset > 0) {
            this.drawCenteredString(this.fontRenderer, "^", right - 8, listTop + 2, 0xCCCCCC);
        }
    }

    private void drawMicrosoftFlow(int cx) {
        int y = 100;
        MicrosoftAuth.DeviceCode code = pendingCode;
        if (code == null) {
            this.drawCenteredString(this.fontRenderer, "Contacting Microsoft...", cx, y, 0xFFFFFF);
            return;
        }
        this.drawCenteredString(this.fontRenderer, "Sign in to add a Microsoft account", cx, y, 0xFFFFFF);
        this.drawCenteredString(this.fontRenderer, "1. Open  " + code.verificationUri, cx, y + 18, 0xCCCCCC);
        this.drawCenteredString(this.fontRenderer, "2. Enter this code:", cx, y + 34, 0xCCCCCC);
        this.drawCenteredString(this.fontRenderer, code.userCode, cx, y + 48, 0x55FFFF);
        this.drawCenteredString(this.fontRenderer, "(code copied to clipboard)", cx, y + 64, 0x808080);
    }

    /****************************************************************
     *                      Input
     ****************************************************************/

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) throws IOException {
        super.mouseClicked(mouseX, mouseY, mouseButton);
        if (msInProgress) return;

        this.nameField.mouseClicked(mouseX, mouseY, mouseButton);

        int listTop = 86;
        int listBottom = this.height - 60;
        int left = this.width / 2 - 160;
        int right = this.width / 2 + 160;
        if (mouseX >= left && mouseX <= right && mouseY >= listTop && mouseY < listBottom) {
            int index = scrollOffset + (mouseY - listTop) / ROW_HEIGHT;
            if (index >= 0 && index < TempleClient.altManager.getAlts().size()) {
                selectedIndex = index;
            }
        }
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) throws IOException {
        if (msInProgress) {
            if (keyCode == Keyboard.KEY_ESCAPE) {
                msCancelRequested = true;
            }
            return;
        }
        if (this.nameField.isFocused() && keyCode == Keyboard.KEY_RETURN) {
            directLogin();
            return;
        }
        super.keyTyped(typedChar, keyCode);
        this.nameField.textboxKeyTyped(typedChar, keyCode);
    }

    @Override
    public void handleMouseInput() throws IOException {
        super.handleMouseInput();
        int wheel = Mouse.getEventDWheel();
        if (wheel != 0 && !msInProgress) {
            scrollOffset += wheel > 0 ? -1 : 1;
            clampScroll();
        }
    }

    @Override
    public void updateScreen() {
        this.nameField.updateCursorCounter();

        boolean pending = msInProgress;
        boolean hasCode = pendingCode != null;
        addOfflineButton.visible = !pending;
        directLoginButton.visible = !pending;
        addMicrosoftButton.visible = !pending;
        loginButton.visible = !pending;
        removeButton.visible = !pending;
        backButton.visible = !pending;
        copyCodeButton.visible = pending && hasCode;
        openPageButton.visible = pending && hasCode;
        cancelButton.visible = pending;
    }

    @Override
    protected void actionPerformed(GuiButton button) throws IOException {
        if (button == addOfflineButton) {
            addOffline();
        } else if (button == directLoginButton) {
            directLogin();
        } else if (button == addMicrosoftButton) {
            startMicrosoftLogin();
        } else if (button == loginButton) {
            loginSelected();
        } else if (button == removeButton) {
            removeSelected();
        } else if (button == backButton) {
            this.mc.displayGuiScreen(new GuiMainMenu());
        } else if (button == copyCodeButton) {
            if (pendingCode != null) setClipboardString(pendingCode.userCode);
        } else if (button == openPageButton) {
            if (pendingCode != null) openLink(pendingCode.verificationUri);
        } else if (button == cancelButton) {
            msCancelRequested = true;
        }
    }

    /****************************************************************
     *                      Actions
     ****************************************************************/

    private void addOffline() {
        String name = this.nameField.getText().trim();
        if (name.isEmpty()) {
            status = "Enter a username first.";
            return;
        }
        TempleClient.altManager.addAlt(Alt.offline(name));
        this.nameField.setText("");
        selectedIndex = TempleClient.altManager.getAlts().size() - 1;
        clampScroll();
        status = "Added " + name + ".";
    }

    private void directLogin() {
        String name = this.nameField.getText().trim();
        String error = TempleClient.altManager.loginOffline(name);
        status = error == null ? "Logged in as " + name : error;
    }

    private void loginSelected() {
        final Alt alt = selectedAlt();
        if (alt == null) {
            status = "Select an account first.";
            return;
        }
        if (alt.getType() == Alt.Type.MICROSOFT) {
            // Refreshing the Microsoft session hits the network - keep it off the client thread.
            status = "Refreshing " + alt.getUsername() + "...";
            Thread worker = new Thread(() -> {
                String error = TempleClient.altManager.login(alt);
                status = error == null ? "Logged in as " + alt.getUsername() : error;
            }, "TempleClient-AltLogin");
            worker.setDaemon(true);
            worker.start();
        } else {
            String error = TempleClient.altManager.login(alt);
            status = error == null ? "Logged in as " + alt.getUsername() : error;
        }
    }

    private void removeSelected() {
        Alt alt = selectedAlt();
        if (alt == null) {
            status = "Select an account first.";
            return;
        }
        TempleClient.altManager.removeAlt(alt);
        status = "Removed " + alt.getUsername() + ".";
        clampSelection();
        clampScroll();
    }

    private void startMicrosoftLogin() {
        if (msInProgress) return;
        msInProgress = true;
        msCancelRequested = false;
        pendingCode = null;
        status = "";

        Thread worker = new Thread(() -> {
            try {
                MicrosoftAuth.DeviceCode code = MicrosoftAuth.requestDeviceCode();
                pendingCode = code;
                setClipboardString(code.userCode);
                openLink(code.verificationUri);

                MicrosoftAuth.MinecraftLogin login = MicrosoftAuth.pollAndLogin(
                        code, () -> msCancelRequested, message -> status = message);

                TempleClient.altManager.addAlt(Alt.microsoft(login.username, login.uuid, login.refreshToken));
                Minecraft.getMinecraft().addScheduledTask(() ->
                        TempleClient.setSession(new Session(login.username, login.uuid, login.accessToken, "mojang")));
                status = "Added & logged in as " + login.username;
            } catch (MicrosoftAuth.AuthException e) {
                status = e.getMessage();
            } finally {
                msInProgress = false;
                pendingCode = null;
            }
        }, "TempleClient-MicrosoftAuth");
        worker.setDaemon(true);
        worker.start();
    }

    /****************************************************************
     *                      Helpers
     ****************************************************************/

    private void drawHead(Alt alt, int x, int y) {
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
        ResourceLocation head = HEADS.getHead(alt.getUsername());
        if (head != null) {
            // minotar "helm" avatar: an 8x8 image with the hat layer already composited in.
            this.mc.getTextureManager().bindTexture(head);
            drawScaledCustomSizeModalRect(x, y, 0, 0, 8, 8, 16, 16, 8, 8);
        } else {
            // Fallback: render the face (+ hat) from the vanilla default skin (Steve/Alex).
            this.mc.getTextureManager().bindTexture(DefaultPlayerSkin.getDefaultSkin(uuidFor(alt)));
            drawScaledCustomSizeModalRect(x, y, 8, 8, 8, 8, 16, 16, 64, 64);
            GlStateManager.enableBlend();
            drawScaledCustomSizeModalRect(x, y, 40, 8, 8, 8, 16, 16, 64, 64);
            GlStateManager.disableBlend();
        }
    }

    private static UUID uuidFor(Alt alt) {
        try {
            if (alt.getUuid() != null && !alt.getUuid().isEmpty()) {
                return UUID.fromString(alt.getUuid());
            }
        } catch (IllegalArgumentException ignored) {
            // fall through to a derived offline UUID
        }
        return Alt.offlineUuid(alt.getUsername());
    }

    private Alt selectedAlt() {
        List<Alt> alts = TempleClient.altManager.getAlts();
        if (selectedIndex < 0 || selectedIndex >= alts.size()) {
            return null;
        }
        return alts.get(selectedIndex);
    }

    private void clampSelection() {
        int size = TempleClient.altManager.getAlts().size();
        if (selectedIndex >= size) {
            selectedIndex = size - 1;
        }
    }

    private void clampScroll() {
        int listTop = 86;
        int listBottom = this.height - 60;
        int visibleRows = Math.max(1, (listBottom - listTop) / ROW_HEIGHT);
        int max = Math.max(0, TempleClient.altManager.getAlts().size() - visibleRows);
        if (scrollOffset > max) scrollOffset = max;
        if (scrollOffset < 0) scrollOffset = 0;
    }

    private void openLink(String url) {
        try {
            if (Desktop.isDesktopSupported()) {
                Desktop.getDesktop().browse(new URI(url));
            }
        } catch (Throwable ignored) {
            // The user can still open the URL manually.
        }
    }

    @Override
    public boolean doesGuiPauseGame() {
        return true;
    }
}
