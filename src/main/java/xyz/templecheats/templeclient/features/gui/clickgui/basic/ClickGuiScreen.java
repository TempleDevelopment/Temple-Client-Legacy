package xyz.templecheats.templeclient.features.gui.clickgui.basic;

import net.minecraft.client.gui.GuiTextField;
import net.minecraft.client.gui.ScaledResolution;
import org.lwjgl.input.Keyboard;
import xyz.templecheats.templeclient.features.gui.clickgui.basic.panels.Panel;
import xyz.templecheats.templeclient.features.gui.clickgui.basic.panels.items.buttons.ModuleButton;
import xyz.templecheats.templeclient.features.module.Module;
import xyz.templecheats.templeclient.features.module.modules.client.ClickGUI;
import xyz.templecheats.templeclient.manager.ModuleManager;
import xyz.templecheats.templeclient.util.render.animation.Animation;
import xyz.templecheats.templeclient.util.render.animation.Easing;
import xyz.templecheats.templeclient.util.render.RenderUtil;
import xyz.templecheats.templeclient.util.render.shader.impl.GaussianBlur;

import static xyz.templecheats.templeclient.features.gui.font.Fonts.font18;
import static xyz.templecheats.templeclient.util.math.MathUtil.coerceIn;

public class ClickGuiScreen extends ClientGuiScreen {
    private static ClickGuiScreen instance;
    public final Animation animation = new Animation(Easing.InOutQuint, 500);
    public boolean open = true;
    private GuiTextField searchField;

    @Override
    public void load() {
        this.getPanels().clear();

        int x = -42;
        for (final Module.Category category : Module.Category.values()) {
            this.getPanels().add(new Panel(category.name(), x += 90, 25, true) {
                @Override
                public void setupItems() {
                    ModuleManager.getModules().forEach(module -> {
                        if (module.getCategory() == category && !module.submodule) {
                            this.addButton(new ModuleButton(module));
                        }
                    });
                }
            });
        }

        super.load();
    }

    @Override
    public void onGuiClosed() {
        open = false;
        animation.reset();
    }

    public static ClickGuiScreen getInstance() {
        return instance == null ? (instance = new ClickGuiScreen()) : instance;
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        if (ClickGUI.INSTANCE.blur.booleanValue()) {
            if (mc.currentScreen instanceof ClickGuiScreen || open) {
                animation.progress(getScale());
            }
            float progress = (float) animation.getProgress();
            float radius = coerceIn(ClickGUI.INSTANCE.radius.floatValue() * progress, (float) ClickGUI.INSTANCE.radius.min, (float) ClickGUI.INSTANCE.radius.max);
            float compression = coerceIn(ClickGUI.INSTANCE.compression.floatValue() * progress, (float) ClickGUI.INSTANCE.compression.min, (float) ClickGUI.INSTANCE.compression.max);

            GaussianBlur.startBlur();
            this.drawDefaultBackground();
            GaussianBlur.endBlur(radius, compression);
        }
        if (ClickGUI.INSTANCE.tint.booleanValue()) {
            this.drawDefaultBackground();
        }
        if (ClickGUI.INSTANCE.particles.booleanValue()) {
            ClickGUI.INSTANCE.particleUtil.drawParticles();
            ScaledResolution scaledResolution = new ScaledResolution(mc);
            ClickGUI.INSTANCE.snow.drawSnow(scaledResolution);
        }
        super.drawScreen(mouseX, mouseY, partialTicks);

        if (searchField != null) {
            final float w = 160.0f;
            final float h = 14.0f;
            final float x1 = this.width / 2.0f - 80.0f;
            final float y1 = 3.0f;
            final float x2 = x1 + w;
            final float y2 = y1 + h;
            final int accent = ClickGUI.INSTANCE.getStartColor().getRGB();

            RenderUtil.drawRect(x1, y1, x2, y2, 0x99000000);
            RenderUtil.drawOutlineRect(x1, y1, x2, y2, accent);

            final String text = searchField.getText();
            final float textY = y1 + (h - font18.getFontHeight()) / 2.0f + 1.0f;
            if (text.isEmpty() && !searchField.isFocused()) {
                font18.drawString("Search...", x1 + 4.0f, textY, 0xFF808080, false);
            } else {
                font18.drawString(text, x1 + 4.0f, textY, -1, false);
                if (searchField.isFocused() && System.currentTimeMillis() % 1000 < 500) {
                    final float caretX = x1 + 4.0f + font18.getStringWidth(text);
                    RenderUtil.drawRect(caretX + 1.0f, y1 + 3.0f, caretX + 2.0f, y2 - 3.0f, accent);
                }
            }
        }
    }

    @Override
    public void initGui() {
        super.initGui();
        this.searchField = new GuiTextField(0, this.fontRenderer, this.width / 2 - 80, 3, 160, 14);
        this.searchField.setMaxStringLength(40);
        this.searchField.setText("");
        applyFilter();
    }

    @Override
    public void updateScreen() {
        super.updateScreen();
        if (searchField != null) {
            searchField.updateCursorCounter();
        }
    }

    @Override
    public void mouseClicked(int unscaledMouseX, int unscaledMouseY, int clickedButton) {
        if (searchField != null) {
            searchField.mouseClicked(unscaledMouseX, unscaledMouseY, clickedButton);
        }
        super.mouseClicked(unscaledMouseX, unscaledMouseY, clickedButton);
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) {
        if (searchField != null && searchField.isFocused() && keyCode != Keyboard.KEY_ESCAPE) {
            searchField.textboxKeyTyped(typedChar, keyCode);
            applyFilter();
            return;
        }
        super.keyTyped(typedChar, keyCode);
    }

    private void applyFilter() {
        if (searchField == null) {
            return;
        }
        final String query = searchField.getText();
        final boolean searching = query != null && !query.isEmpty();
        for (Panel panel : getPanels()) {
            final boolean any = panel.filter(query);
            panel.setOpen(searching ? any : true);
        }
    }
}
