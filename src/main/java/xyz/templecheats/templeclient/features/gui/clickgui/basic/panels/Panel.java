package xyz.templecheats.templeclient.features.gui.clickgui.basic.panels;

import net.minecraft.client.Minecraft;
import net.minecraft.client.audio.PositionedSoundRecord;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.init.SoundEvents;
import net.minecraft.util.ResourceLocation;
import xyz.templecheats.templeclient.features.gui.clickgui.basic.ClickGuiScreen;
import xyz.templecheats.templeclient.features.gui.clickgui.basic.panels.items.Item;
import xyz.templecheats.templeclient.features.gui.clickgui.basic.panels.items.buttons.Button;
import xyz.templecheats.templeclient.features.module.modules.client.ClickGUI;
import xyz.templecheats.templeclient.util.math.Vec2d;
import xyz.templecheats.templeclient.util.render.RenderUtil;
import xyz.templecheats.templeclient.util.render.StencilUtil;
import xyz.templecheats.templeclient.util.render.shader.impl.RectBuilder;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import static xyz.templecheats.templeclient.features.gui.font.Fonts.font20;

public abstract class Panel {
    public static float[] counter1 = new float[]{1};
    private final List<Item> items = new ArrayList<>();
    private final List<Item> allItems = new ArrayList<>();
    private final Minecraft mc = Minecraft.getMinecraft();
    //public Animation animation = new Animation(Easing.OutExpo, 300);
    private final String label;
    private int angle;
    private int x, y;
    private int dragX, dragY;
    private final int width, height;
    private boolean open;
    public boolean drag;

    public Panel(String label, int x, int y, boolean open) {
        this.label = label;
        this.x = x;
        this.y = y;
        this.angle = 180;
        this.width = 88;
        this.height = 18;
        this.open = open;
        this.setupItems();
    }

    public abstract void setupItems();

    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        this.drag(mouseX, mouseY);
        counter1 = new float[]{1};
        //animation.setEasing(open ? Easing.OutExpo : Easing.InExpo);
        //animation.progress(open ? 1 : 0);

        /* * animation.getProgress()*/
        float totalItemHeight = (this.getTotalItemHeight() - 2.0f);
        GlStateManager.pushMatrix();
        new RectBuilder(new Vec2d(this.x, (float) this.y - 1.5f), new Vec2d(this.x + this.width, this.y + this.height - 6))
                .color(ClickGUI.INSTANCE.getStartColor())
                .draw();

        if (this.open) {
            RenderUtil.drawRect(this.x, (float) this.y + 12.5f, this.x + this.width, (float) (this.y + this.height) + totalItemHeight, 0x77000000);
        }

        font20.drawString(this.getLabel(), (float) this.x + 3.0f, (float) this.y, -1, false);

        if (!open) {
            if (this.angle > 0) {
                this.angle -= 6;
            }
        } else if (this.angle < 180) {
            this.angle += 6;
        }

        GlStateManager.pushMatrix();
        GlStateManager.enableBlend();
        mc.getTextureManager().bindTexture(new ResourceLocation("textures/icons/arrow.png"));
        GlStateManager.translate(getX() + getWidth() - 7, (getY() + 6) - 0.3F, 0.0F);
        GlStateManager.rotate(calculateRotation(angle), 0.0F, 0.0F, 1.0F);
        Gui.drawScaledCustomSizeModalRect(-5, -5, 0.0F, 0.0F, 10, 10, 10, 10, 10.0F, 10.0F);
        GlStateManager.disableBlend();
        GlStateManager.popMatrix();

        StencilUtil.initStencilToWrite();
        if (this.open) {
            RenderUtil.drawRect(this.x, (float) this.y + 12.5f, this.x + this.width, (float) (this.y + this.height) + totalItemHeight, 0x77000000);
        }
        StencilUtil.readStencilBuffer(1);

        if (this.open) {
            float y = (float) (this.getY() + this.getHeight()) - 3.0f;
            for (Item item : getItems()) {
                Panel.counter1[0] = counter1[0] + 0.5f;
                item.setLocation((float) this.x + 2.0f, y);
                item.setWidth(this.getWidth() - 4);
                item.drawScreen(mouseX, mouseY, partialTicks);
                y += (float) item.getHeight() + 1.5f;
            }
        }
        StencilUtil.uninitStencilBuffer();
        GlStateManager.popMatrix();

    }

    private void drag(int mouseX, int mouseY) {
        if (!this.drag) {
            return;
        }
        this.x = this.dragX + mouseX;
        this.y = this.dragY + mouseY;
    }

    public void drawScreenPost(int mouseX, int mouseY) {
        if (this.open) {
            for (Item item : getItems()) {
                item.drawScreenPost(mouseX, mouseY);
            }
        }
    }

    public void mouseClicked(int mouseX, int mouseY, int mouseButton) {
        if (mouseButton == 0 && this.isHovering(mouseX, mouseY)) {
            this.dragX = this.x - mouseX;
            this.dragY = this.y - mouseY;
            ClickGuiScreen.getInstance().getPanels().forEach(panel -> {
                if (panel.drag) {
                    panel.drag = false;
                }
            });
            this.drag = true;
            return;
        }
        if (mouseButton == 1 && this.isHovering(mouseX, mouseY)) {
            this.open = !this.open;
            mc.getSoundHandler().playSound(PositionedSoundRecord.getMasterRecord(SoundEvents.UI_BUTTON_CLICK, 1.0F));
            return;
        }
        if (!this.open) {
            return;
        }
        this.getItems().forEach(item -> item.mouseClicked(mouseX, mouseY, mouseButton));
    }

    public void addButton(Button button) {
        this.items.add(button);
        this.allItems.add(button);
    }

    /**
     * Filters the visible items by a search query (matched against the item label).
     * An empty query restores every item.
     *
     * @return true if at least one item matches.
     */
    public boolean filter(String query) {
        this.items.clear();
        if (query == null || query.isEmpty()) {
            this.items.addAll(this.allItems);
        } else {
            final String lower = query.toLowerCase();
            for (Item item : this.allItems) {
                final String label = item.getLabel();
                if (label != null && label.toLowerCase().contains(lower)) {
                    this.items.add(item);
                }
            }
        }
        this.items.sort(Comparator.comparing(Item::getLabel));
        return !this.items.isEmpty();
    }

    public void mouseReleased(int mouseX, int mouseY, int releaseButton) {
        if (releaseButton == 0) {
            this.drag = false;
        }
        if (!this.open) {
            return;
        }
        this.getItems().forEach(item -> item.mouseReleased(mouseX, mouseY, releaseButton));
    }

    public void keyTyped(char typedChar, int keyCode) throws IOException {
        for (Item item : getItems()) {
            item.keyTyped(typedChar, keyCode);
        }
    }

    public final String getLabel() {
        return this.label;
    }

    public int getX() {
        return this.x;
    }

    public int getY() {
        return this.y;
    }

    public void setOpen(boolean open) {
        this.open = open;
    }

    public int getWidth() {
        return this.width;
    }

    public int getHeight() {
        return this.height;
    }

    public boolean getOpen() {
        return this.open;
    }

    public final List<Item> getItems() {
        return this.items;
    }

    private boolean isHovering(int mouseX, int mouseY) {
        return mouseX >= this.getX() && mouseX <= this.getX() + this.getWidth() && mouseY >= this.getY() && mouseY <= this.getY() + this.getHeight() - (this.open ? 2 : 0);
    }

    //added this method in, just to fix shit. It is from uz1 class in future
    public static float calculateRotation(float var0) {
        if ((var0 %= 360.0F) >= 180.0F) {
            var0 -= 360.0F;
        }

        if (var0 < -180.0F) {
            var0 += 360.0F;
        }

        return var0;
    }

    private float getTotalItemHeight() {
        float height = 0.0f;
        for (Item item : getItems()) {
            height += (float) item.getHeight() + 1.5f;
        }
        return height;
    }

    public void setX(int dragX) {
        this.x = dragX;
    }

    public void setY(int dragY) {
        this.y = dragY;
    }
}

