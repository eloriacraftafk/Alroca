/*
 * StealthProcessHook.java
 * Language: Java 17 (Fabric Minecraft 1.21.11)
 * Module:   dlindustries.uk.daduncs.managers
 *
 * Purpose:
 *   Makes the client process look like Marlow's Crystal Optimizer to
 *   anyone inspecting it via AnyDesk remote view, Task Manager, or a
 *   server-side client-identifier query.
 *
 *   Three attack surfaces covered:
 *     1. GLFW window title  — what the bar at the top of the window shows
 *     2. JVM system property "sun.java.command" and "sun.rt.javaCommand"
 *        — what shows in some process monitors as the command line
 *     3. fabric.mod.json identity — already set to "marlowcrystal" /
 *        "Marlow's Crystal Optimizer" in the shipped mod manifest; this
 *        class only handles the runtime side
 *
 *   Call StealthProcessHook.apply() once from your mod's onInitializeClient
 *   (or any post-window-open point). It is idempotent.
 *
 * AnyDesk-specific note:
 *   AnyDesk's screen sharing shows the window title bar verbatim.
 *   After apply(), the GLFW title reads "Minecraft 1.21.11" — identical
 *   to a vanilla client. The module list, chat, and any other in-game
 *   overlays are only visible if the observer can read the actual screen,
 *   which is an operator problem not a code problem.
 *
 * Task-manager / process-list note:
 *   The JVM process name is determined by the launcher, not the mod.
 *   "javaw.exe" or the launcher binary appears there regardless of what
 *   this hook does. The system-property spoof covers tools that read the
 *   command line string rather than the binary name (e.g. some custom
 *   Discord rich-presence scrapers and server-side client probers via
 *   brand channel packets).
 *
 * Anti-cheat brand-channel note:
 *   Servers that check the Minecraft brand packet (channel "minecraft:brand")
 *   receive whatever brand string the client sends. Vanilla sends "vanilla".
 *   Fabric sends "fabric". To appear vanilla, intercept the outgoing brand
 *   packet — see BrandSpoofMixin below; that class is separate from this
 *   hook and targets the packet layer.
 */

package dlindustries.uk.daduncs.managers;

import net.minecraft.client.MinecraftClient;
import org.lwjgl.glfw.GLFW;

import java.lang.reflect.Field;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicBoolean;

public final class StealthProcessHook {

    // The title shown in the GLFW window bar and in AnyDesk's title.
    // "Minecraft 1.21.11" is what a vanilla client shows — indistinguishable.
    private static final String WINDOW_TITLE = "Minecraft 1.21.11";

    // The brand string written into sun.java.command.
    // Watchers that parse the JVM command line see this.
    private static final String JAVA_COMMAND_SPOOF = "net.minecraft.client.main.Main --version 1.21.11 --gameDir . --assetsDir assets";

    private static final AtomicBoolean applied = new AtomicBoolean(false);

    private StealthProcessHook() {}

    /**
     * Apply all stealth hooks. Idempotent — safe to call multiple times.
     * Must be called after the GLFW window has been created (i.e. after
     * MinecraftClient has initialised its Window).
     */
    public static void apply() {
        if (!applied.compareAndSet(false, true)) return;

        spoofWindowTitle();
        spoofJvmCommandLine();
    }

    // ── 1. GLFW window title ──────────────────────────────────────────────

    private static void spoofWindowTitle() {
        try {
            MinecraftClient mc = MinecraftClient.getInstance();
            if (mc == null || mc.getWindow() == null) return;

            long handle = mc.getWindow().getHandle();
            if (handle == 0L) return;

            GLFW.glfwSetWindowTitle(handle, WINDOW_TITLE);
        } catch (Throwable ignored) {
            // If the window isn't ready yet, the caller should retry after init.
        }
    }

    // ── 2. JVM system-property command-line spoof ─────────────────────────

    private static void spoofJvmCommandLine() {
        try {
            // sun.java.command is set by the JVM launcher and is normally
            // read-only via System.getProperty(), but the backing Properties
            // object is mutable if accessed through reflection.
            Field f = System.class.getDeclaredField("props");
            f.setAccessible(true);
            Properties props = (Properties) f.get(null);
            props.setProperty("sun.java.command",   JAVA_COMMAND_SPOOF);
            props.setProperty("sun.rt.javaCommand", JAVA_COMMAND_SPOOF);
        } catch (Throwable ignored) {
            // Fails on some JVM security configurations; non-fatal.
            // The window-title spoof above covers the common AnyDesk case.
        }
    }

    /**
     * Re-apply the window title. Call this if the game resets the title
     * (e.g. after joining a server that sends a custom window title).
     * AntibanbyDaduncs or a TickListener can call this every ~100 ticks
     * to keep the title clean.
     */
    public static void reapplyTitle() {
        try {
            MinecraftClient mc = MinecraftClient.getInstance();
            if (mc == null || mc.getWindow() == null) return;

            long handle = mc.getWindow().getHandle();
            if (handle == 0L) return;

            String current = mc.getWindow().getTitle() != null ? mc.getWindow().getTitle() : "";
            if (!WINDOW_TITLE.equals(current)) {
                GLFW.glfwSetWindowTitle(handle, WINDOW_TITLE);
            }
        } catch (Throwable ignored) {}
    }
}
