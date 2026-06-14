package xyz.templecheats.templeclient.features.command.commands;

import xyz.templecheats.templeclient.features.command.Command;
import xyz.templecheats.templeclient.util.baritone.BaritoneUtil;

public class StopCommand extends Command {
    @Override
    public String getName() {
        return ".stop";
    }

    @Override
    public void execute(String[] args) {
        if (!BaritoneUtil.isPresent()) {
            sendMessage(BaritoneUtil.getStatus());
            return;
        }
        if (BaritoneUtil.stop()) {
            sendMessage("Stopped Baritone.");
        } else {
            sendMessage("Baritone error: " + BaritoneUtil.getLastError());
        }
    }
}
