package xyz.templecheats.templeclient.features.command;

import net.minecraft.client.Minecraft;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.TextFormatting;

public abstract class Command {
    protected static final Minecraft mc = Minecraft.getMinecraft();

    /****************************************************************
     *                      Abstract Methods
     ****************************************************************/
    public abstract String getName();
    public abstract void execute(String[] args);

    /****************************************************************
     *                      Helpers
     ****************************************************************/

    /** Sends a chat message to the player with the standard Temple prefix. */
    protected void sendMessage(String message) {
        if (mc.player == null) {
            return;
        }
        mc.player.sendMessage(new TextComponentString(
                TextFormatting.AQUA + "[Temple] " + TextFormatting.RESET + message));
    }
}
