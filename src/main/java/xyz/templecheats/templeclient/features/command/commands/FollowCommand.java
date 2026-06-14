package xyz.templecheats.templeclient.features.command.commands;

import xyz.templecheats.templeclient.features.command.Command;
import xyz.templecheats.templeclient.util.baritone.BaritoneUtil;

public class FollowCommand extends Command {
    @Override
    public String getName() {
        return ".follow";
    }

    @Override
    public void execute(String[] args) {
        if (!BaritoneUtil.isPresent()) {
            sendMessage(BaritoneUtil.getStatus());
            return;
        }
        if (args.length != 2) {
            sendMessage("Usage: .follow <player>");
            return;
        }

        String player = args[1];
        if (BaritoneUtil.follow(player)) {
            sendMessage("Following " + player + ". Use .stop to cancel.");
        } else {
            sendMessage("Baritone error: " + BaritoneUtil.getLastError());
        }
    }
}
