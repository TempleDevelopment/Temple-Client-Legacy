package xyz.templecheats.templeclient.features.command.commands;

import xyz.templecheats.templeclient.features.command.Command;
import xyz.templecheats.templeclient.util.baritone.BaritoneUtil;

import java.util.Arrays;

public class MineCommand extends Command {
    @Override
    public String getName() {
        return ".mine";
    }

    @Override
    public void execute(String[] args) {
        if (!BaritoneUtil.isPresent()) {
            sendMessage(BaritoneUtil.getStatus());
            return;
        }
        if (args.length < 2) {
            sendMessage("Usage: .mine <block> [block2 ...]  (e.g. .mine diamond_ore)");
            return;
        }

        String[] blocks = Arrays.copyOfRange(args, 1, args.length);
        if (BaritoneUtil.mine(blocks)) {
            sendMessage("Mining: " + String.join(", ", blocks) + ". Use .stop to cancel.");
        } else {
            sendMessage("Baritone error: " + BaritoneUtil.getLastError());
        }
    }
}
