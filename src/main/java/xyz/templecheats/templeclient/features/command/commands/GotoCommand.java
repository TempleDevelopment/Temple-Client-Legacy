package xyz.templecheats.templeclient.features.command.commands;

import xyz.templecheats.templeclient.features.command.Command;
import xyz.templecheats.templeclient.util.baritone.BaritoneUtil;

public class GotoCommand extends Command {
    @Override
    public String getName() {
        return ".goto";
    }

    @Override
    public void execute(String[] args) {
        if (!BaritoneUtil.isPresent()) {
            sendMessage(BaritoneUtil.getStatus());
            return;
        }

        try {
            switch (args.length) {
                case 2: {
                    int y = Integer.parseInt(args[1]);
                    report(BaritoneUtil.gotoY(y), "Pathing to Y " + y + ".");
                    return;
                }
                case 3: {
                    int x = Integer.parseInt(args[1]);
                    int z = Integer.parseInt(args[2]);
                    report(BaritoneUtil.gotoXZ(x, z), "Pathing to " + x + ", " + z + ".");
                    return;
                }
                case 4: {
                    int x = Integer.parseInt(args[1]);
                    int y = Integer.parseInt(args[2]);
                    int z = Integer.parseInt(args[3]);
                    report(BaritoneUtil.gotoPos(x, y, z), "Pathing to " + x + ", " + y + ", " + z + ".");
                    return;
                }
                default:
                    sendMessage("Usage: .goto <x> <y> <z> | <x> <z> | <y>");
            }
        } catch (NumberFormatException e) {
            sendMessage("Coordinates must be whole numbers.");
        }
    }

    private void report(boolean ok, String success) {
        sendMessage(ok ? success : "Baritone error: " + BaritoneUtil.getLastError());
    }
}
