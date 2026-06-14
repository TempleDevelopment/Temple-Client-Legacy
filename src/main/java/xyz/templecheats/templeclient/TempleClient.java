/************************************************************************************************
 *                                       Temple Client                                          *
 *                (c) 2023-2024 Temple Client Development Team. All rights reserved.            *
 ************************************************************************************************/

package xyz.templecheats.templeclient;

import net.minecraft.client.Minecraft;
import net.minecraft.util.Session;
import net.minecraftforge.client.event.ClientChatEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.Mod.EventHandler;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import org.apache.logging.log4j.Logger;
import org.lwjgl.opengl.Display;
import team.stiff.pomelo.impl.annotated.AnnotatedEventManager;
import xyz.templecheats.templeclient.config.ConfigManager;
import xyz.templecheats.templeclient.event.ForgeEventManager;
import xyz.templecheats.templeclient.features.gui.font.FontUtils;
import xyz.templecheats.templeclient.features.gui.menu.GuiEventsListener;
import xyz.templecheats.templeclient.manager.*;
import xyz.templecheats.templeclient.util.friend.FriendManager;
import xyz.templecheats.templeclient.util.keys.KeyUtil;
import xyz.templecheats.templeclient.util.setting.SettingsManager;

import java.lang.invoke.MethodHandles;
import java.lang.reflect.Field;

@Mod(modid = TempleClient.MODID, name = TempleClient.NAME, version = TempleClient.VERSION)
public class TempleClient {
    public static final String MODID = "templeclient";
    public static final String NAME = "Temple Client";
    public static final String VERSION = "1.9.8";
    public static final MethodHandles.Lookup LOOKUP = MethodHandles.lookup();
    public static String name = NAME + " " + VERSION;
    public static AnnotatedEventManager eventBus;
    public static AltManager altManager;
    public static SettingsManager settingsManager;
    public static ModuleManager moduleManager;
    public static ForgeEventManager clientForgeEventManager;
    public static CommandManager commandManager;
    public static ConfigManager configManager;
    public static CapeManager capeManager;
    public static FriendManager friendManager;
    public static HoleManager holeManager = new HoleManager();
    public static InventoryManager inventoryManager = new InventoryManager();
    public static RotationManager rotationManager = new RotationManager();
    public static ThreadManager threadManager;
    public static Logger logger;

    @EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        Display.setTitle("Loading " + name);
        logger = event.getModLog();
        logger.info("Pre-initialization complete.");
    }

    @EventHandler
    public void init(FMLInitializationEvent event) {
        Display.setTitle(name);
        MinecraftForge.EVENT_BUS.register(this);

        eventBus = new AnnotatedEventManager();
        logger.info("EventBus initialized.");

        clientForgeEventManager = new ForgeEventManager();
        MinecraftForge.EVENT_BUS.register(clientForgeEventManager);
        logger.info("Forge Event Manager registered.");

        configManager = new ConfigManager();
        logger.info("Config Manager initialized.");

        altManager = new AltManager();
        logger.info("Alt Manager initialized.");

        settingsManager = new SettingsManager();
        logger.info("Settings Manager initialized.");

        moduleManager = new ModuleManager();
        logger.info("Module Manager initialized.");

        commandManager = new CommandManager();
        logger.info("Command Manager initialized.");

        capeManager = new CapeManager();
        logger.info("Cape Manager initialized.");

        friendManager = new FriendManager();
        logger.info("Friend Manager initialized.");

        threadManager = new ThreadManager();
        logger.info("Thread Manager initialized.");

        MinecraftForge.EVENT_BUS.register(commandManager);
        MinecraftForge.EVENT_BUS.register(new KeyUtil());
        MinecraftForge.EVENT_BUS.register(new GuiEventsListener());
        logger.info("KeyUtil and GuiEventsListener registered.");

        FontUtils.setupFonts();
        FontUtils.setupIcons();
        logger.info("Fonts and icons setup.");

        configManager.loadAll();
        logger.info("Configurations loaded.");


        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            configManager.saveAll();
        }));
        logger.info("Shutdown hook added for saving configurations and stopping Discord Presence.");
    }

    @SubscribeEvent
    public void onChat(ClientChatEvent event) {
        if (event.getMessage().startsWith(".")) {
            if (commandManager.executeCommand(event.getMessage())) {
                event.setCanceled(true);
            }
        }
    }

    public static void setSession(Session s) {
        try {
            // Match by exact type rather than the first assignable field so this keeps working in
            // an obfuscated build (where the field is renamed but its Session type is preserved).
            Field session = null;
            for (Field f : Minecraft.class.getDeclaredFields()) {
                if (f.getType() == Session.class) {
                    session = f;
                    break;
                }
            }
            if (session == null) {
                throw new IllegalStateException("Session field not found.");
            }
            session.setAccessible(true);
            session.set(Minecraft.getMinecraft(), s);
            name = "TempleClient 1.12.2 | User: " + Minecraft.getMinecraft().getSession().getUsername();
            Display.setTitle(name);
            logger.info("Session set successfully.");
        } catch (Exception e) {
            if (logger != null) {
                logger.error("Failed to set session.", e);
            }
        }
    }

    public static ModuleManager getModuleManager() {
        return moduleManager;
    }
}
