package xyz.templecheats.templeclient.features.command.commands;

import net.minecraft.client.Minecraft;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.TextFormatting;
import xyz.templecheats.templeclient.features.command.Command;

public class HelpCommand extends Command {
    @Override
    public String getName() {
        return ".help";
    }

    public void execute(String[] args) {
        String templePrefix = TextFormatting.AQUA + "[Temple] " + TextFormatting.RESET;
        String commandPrefix = TextFormatting.AQUA + "- " + TextFormatting.RESET;

        Minecraft.getMinecraft().player.sendMessage(new TextComponentString(templePrefix + "Available Commands:"));

        Minecraft.getMinecraft().player.sendMessage(new TextComponentString(commandPrefix + ".bind - Binds a module to a key"));
        Minecraft.getMinecraft().player.sendMessage(new TextComponentString(commandPrefix + ".clickgui - Adjusts the scale of the ClickGUI"));
        Minecraft.getMinecraft().player.sendMessage(new TextComponentString(commandPrefix + ".config - Load / Save configs"));
        Minecraft.getMinecraft().player.sendMessage(new TextComponentString(commandPrefix + ".coords - Copies your coordinates to your clipboard"));
        Minecraft.getMinecraft().player.sendMessage(new TextComponentString(commandPrefix + ".deathcoords - Shows the coordinates of your last death"));
        Minecraft.getMinecraft().player.sendMessage(new TextComponentString(commandPrefix + ".disconnect - Disconnects from game session"));
        Minecraft.getMinecraft().player.sendMessage(new TextComponentString(commandPrefix + ".friend - Adds, removes & lists your friends"));
        Minecraft.getMinecraft().player.sendMessage(new TextComponentString(commandPrefix + ".fakeplayer - Adds a FakePlayer"));
        Minecraft.getMinecraft().player.sendMessage(new TextComponentString(commandPrefix + ".follow - Baritone: follow a player"));
        Minecraft.getMinecraft().player.sendMessage(new TextComponentString(commandPrefix + ".goto - Baritone: path to coordinates"));
        Minecraft.getMinecraft().player.sendMessage(new TextComponentString(commandPrefix + ".help - Shows this help message"));
        Minecraft.getMinecraft().player.sendMessage(new TextComponentString(commandPrefix + ".ip - Copies current server ip to your clipboard"));
        Minecraft.getMinecraft().player.sendMessage(new TextComponentString(commandPrefix + ".mine - Baritone: mine blocks by name"));
        Minecraft.getMinecraft().player.sendMessage(new TextComponentString(commandPrefix + ".namemc - Opens the NameMC profile of a player"));
        Minecraft.getMinecraft().player.sendMessage(new TextComponentString(commandPrefix + ".openfolder - Opens the Temple Client folder"));
        Minecraft.getMinecraft().player.sendMessage(new TextComponentString(commandPrefix + ".panic - Toggles the Panic module"));
        Minecraft.getMinecraft().player.sendMessage(new TextComponentString(commandPrefix + ".stop - Baritone: cancel all pathing"));
        Minecraft.getMinecraft().player.sendMessage(new TextComponentString(commandPrefix + ".toggle - Toggles a module"));
    }
}