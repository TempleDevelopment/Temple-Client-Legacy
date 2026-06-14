package xyz.templecheats.templeclient.util.baritone;

import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import xyz.templecheats.templeclient.TempleClient;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.function.Predicate;

/**
 * Thin bridge to <a href="https://github.com/cabaletta/baritone">Baritone</a>, accessed purely
 * through reflection.
 * <p>
 * Reflection (instead of a compile-time {@code baritone-api} dependency) keeps the build
 * self-contained and lets the client run fine when Baritone isn't installed. Handles are resolved
 * once against the Baritone 1.2.x (Minecraft 1.12.2) API; classes are loaded <i>without</i>
 * initializing them, so {@link #isPresent()} reflects "API available" and the real
 * {@code BaritoneAPI} initialization (which fails if only the api-jar is installed) happens on
 * first use and is reported via {@link #getLastError()}.
 */
public final class BaritoneUtil {

    private static boolean present;
    private static String initError = "";
    private static String lastError = "";

    private static Method getProvider;
    private static Method getPrimaryBaritone;
    private static Method getCustomGoalProcess;
    private static Method getMineProcess;
    private static Method getFollowProcess;
    private static Method getPathingBehavior;
    private static Method setGoalAndPath;
    private static Method mineByName;
    private static Method mineCancel;
    private static Method follow;
    private static Method followCancel;
    private static Method cancelEverything;
    private static Constructor<?> goalBlock;
    private static Constructor<?> goalXZ;
    private static Constructor<?> goalYLevel;

    static {
        try {
            ClassLoader cl = BaritoneUtil.class.getClassLoader();
            Class<?> api = forName("baritone.api.BaritoneAPI", cl);
            Class<?> provider = forName("baritone.api.IBaritoneProvider", cl);
            Class<?> baritone = forName("baritone.api.IBaritone", cl);
            Class<?> customGoal = forName("baritone.api.process.ICustomGoalProcess", cl);
            Class<?> mine = forName("baritone.api.process.IMineProcess", cl);
            Class<?> followProc = forName("baritone.api.process.IFollowProcess", cl);
            Class<?> pathing = forName("baritone.api.behavior.IPathingBehavior", cl);
            Class<?> goal = forName("baritone.api.pathing.goals.Goal", cl);

            getProvider = api.getMethod("getProvider");
            getPrimaryBaritone = provider.getMethod("getPrimaryBaritone");
            getCustomGoalProcess = baritone.getMethod("getCustomGoalProcess");
            getMineProcess = baritone.getMethod("getMineProcess");
            getFollowProcess = baritone.getMethod("getFollowProcess");
            getPathingBehavior = baritone.getMethod("getPathingBehavior");

            setGoalAndPath = customGoal.getMethod("setGoalAndPath", goal);
            mineByName = mine.getMethod("mineByName", String[].class);
            mineCancel = mine.getMethod("cancel");
            follow = followProc.getMethod("follow", Predicate.class);
            followCancel = followProc.getMethod("cancel");
            cancelEverything = pathing.getMethod("cancelEverything");

            goalBlock = forName("baritone.api.pathing.goals.GoalBlock", cl).getConstructor(int.class, int.class, int.class);
            goalXZ = forName("baritone.api.pathing.goals.GoalXZ", cl).getConstructor(int.class, int.class);
            goalYLevel = forName("baritone.api.pathing.goals.GoalYLevel", cl).getConstructor(int.class);

            present = true;
        } catch (Throwable t) {
            present = false;
            initError = describe(t);
            if (TempleClient.logger != null) {
                TempleClient.logger.warn("Baritone API not available: " + initError);
            }
        }
    }

    private BaritoneUtil() {
    }

    /** Resolve a class without running its static initializer. */
    private static Class<?> forName(String name, ClassLoader cl) throws ClassNotFoundException {
        return Class.forName(name, false, cl);
    }

    /****************************************************************
     *                      Status
     ****************************************************************/

    public static boolean isPresent() {
        return present;
    }

    public static String getStatus() {
        if (present) {
            return "Baritone ready.";
        }
        if (initError.isEmpty()) {
            return "Baritone not found. Install Baritone for 1.12.2.";
        }
        return "Baritone unavailable (" + initError + "). Install the full Baritone for 1.12.2.";
    }

    public static String getLastError() {
        return lastError.isEmpty() ? "unknown error" : lastError;
    }

    /****************************************************************
     *                      Actions
     ****************************************************************/

    public static boolean gotoPos(int x, int y, int z) {
        return setGoal(newGoal(goalBlock, x, y, z));
    }

    public static boolean gotoXZ(int x, int z) {
        return setGoal(newGoal(goalXZ, x, z));
    }

    public static boolean gotoY(int y) {
        return setGoal(newGoal(goalYLevel, y));
    }

    public static boolean mine(String[] blockNames) {
        try {
            Object process = getMineProcess.invoke(primaryBaritone());
            mineByName.invoke(process, (Object) blockNames);
            return true;
        } catch (Throwable t) {
            lastError = describe(t);
            return false;
        }
    }

    public static boolean follow(String playerName) {
        try {
            Object process = getFollowProcess.invoke(primaryBaritone());
            final String target = playerName.toLowerCase();
            Predicate<Entity> filter = entity -> entity instanceof EntityPlayer
                    && entity.getName().toLowerCase().equals(target);
            follow.invoke(process, filter);
            return true;
        } catch (Throwable t) {
            lastError = describe(t);
            return false;
        }
    }

    public static boolean stop() {
        try {
            Object baritone = primaryBaritone();
            try {
                mineCancel.invoke(getMineProcess.invoke(baritone));
            } catch (Throwable ignored) {
            }
            try {
                followCancel.invoke(getFollowProcess.invoke(baritone));
            } catch (Throwable ignored) {
            }
            cancelEverything.invoke(getPathingBehavior.invoke(baritone));
            return true;
        } catch (Throwable t) {
            lastError = describe(t);
            return false;
        }
    }

    /****************************************************************
     *                      Internals
     ****************************************************************/

    private static Object primaryBaritone() throws Exception {
        Object provider = getProvider.invoke(null);
        return getPrimaryBaritone.invoke(provider);
    }

    private static Object newGoal(Constructor<?> ctor, Object... args) {
        try {
            return ctor.newInstance(args);
        } catch (Throwable t) {
            lastError = describe(t);
            return null;
        }
    }

    private static boolean setGoal(Object goalInstance) {
        if (goalInstance == null) {
            return false;
        }
        try {
            Object process = getCustomGoalProcess.invoke(primaryBaritone());
            setGoalAndPath.invoke(process, goalInstance);
            return true;
        } catch (Throwable t) {
            lastError = describe(t);
            return false;
        }
    }

    /** Unwraps reflection wrappers to the root cause for a readable message. */
    private static String describe(Throwable t) {
        Throwable cause = t;
        while (cause.getCause() != null && cause.getCause() != cause) {
            cause = cause.getCause();
        }
        String name = cause.getClass().getSimpleName();
        String message = cause.getMessage();
        return message == null ? name : name + ": " + message;
    }
}
