package xyz.templecheats.templeclient.manager;

import xyz.templecheats.templeclient.features.command.Command;
import xyz.templecheats.templeclient.features.command.commands.*;

import java.util.HashMap;
import java.util.Map;
public class CommandManager {

    private final Map<String, Command> commands = new HashMap<>();

    public CommandManager() {
        commandInit();
    }

    /****************************************************************
     *                      Command Registration
     ****************************************************************/

    public void registerCommand(Command command) {
        commands.put(command.getName(), command);
    }

    /****************************************************************
     *                      Command Execution
     ****************************************************************/

    public boolean executeCommand(String message) {
        String[] parts = message.split(" ");
        Command command = commands.get(parts[0]);
        if (command != null) {
            command.execute(parts);
            return true;
        }
        return false;
    }

    /****************************************************************
     *                      Command Initialization
     ****************************************************************/

    public void commandInit() {
        registerCommand(new BindCommand());
        registerCommand(new ClickguiCommand());
        registerCommand(new ConfigCommand());
        registerCommand(new CoordsCommand());
        registerCommand(new DeathCoordsCommand());
        registerCommand(new DisconnectCommand());
        registerCommand(new FakePlayerCommand());
        registerCommand(new FollowCommand());
        registerCommand(new FriendCommand());
        registerCommand(new GotoCommand());
        registerCommand(new HelpCommand());
        registerCommand(new IpCommand());
        registerCommand(new MineCommand());
        registerCommand(new NameMcCommand());
        registerCommand(new OpenFolderCommand());
        registerCommand(new PanicCommand());
        registerCommand(new StopCommand());
        registerCommand(new ToggleCommand());
    }
}
