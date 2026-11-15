/*
 * Patch: ElytraHelper — Bypass hardening v2
 * Changes over v1:
 *   1. interactItem (USE) deferred to tick N+1 alongside restore-SWAP.
 *      Sequence: tick N → SWAP only, tick N+1 → USE then restore-SWAP.
 *      Eliminates the SWAP→USE same-tick sequence that strict NCP logs.
 *   2. repeatDelay lower bound 2 ticks preserved.
 *   3. Fast paths (hand/offhand already holds rocket) unchanged — no inventory touch.
 */

package dlindustries.uk.daduncs.module.modules.sword;

import dlindustries.uk.daduncs.event.events.TickListener;
import dlindustries.uk.daduncs.module.Category;
import dlindustries.uk.daduncs.module.Module;
import dlindustries.uk.daduncs.module.setting.bind;
import dlindustries.uk.daduncs.module.setting.bool;
import dlindustries.uk.daduncs.module.setting.number;
import dlindustries.uk.daduncs.utils.EncryptedString;
import dlindustries.uk.daduncs.utils.KeyUtils;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.item.Items;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.util.Hand;

/**
 * ElytraHelper — Uses a firework from your inventory on key press without
 * permanently holding it. The full slot-swap sequence is split across two ticks
 * to avoid NCP InventoryMove.SameTickSwap and ItemUse-speed flags.
 *
 * Tick N   : SWAP (rocket → selected hotbar slot)
 * Tick N+1 : USE (interactItem) then SWAP-back (restore)
 *
 * Server sees two clean, separated inventory events — no same-tick triple.
 */
public final class ElytraHelper extends Module implements TickListener {

    private final bind fireworkKey = new bind(EncryptedString.of("Firework Key"), -1, false)
            .setDescription(EncryptedString.of("Press this key to use a firework from your inventory"));

    private final bool onlyWhileGliding = new bool(EncryptedString.of("Only While Gliding"), false)
            .setDescription(EncryptedString.of("Only use the firework while flying with an elytra"));

    private final bool holdToRepeat = new bool(EncryptedString.of("Hold To Repeat"), false)
            .setDescription(EncryptedString.of("Keep using fireworks while the key is held"));

    private final number repeatDelay = new number(EncryptedString.of("Repeat Delay"), 2, 40, 10, 1)
            .setDescription(EncryptedString.of("Ticks between fireworks when holding the key (20 ticks = 1 second)"));

    private boolean wasPressed;
    private int cooldown;

    // Deferred state — all three fields set together, all cleared together.
    private int  pendingRestoreSlotId     = -1;
    private int  pendingRestoreTargetSlot = -1;
    private Hand pendingUseHand           = null;

    public ElytraHelper() {
        super(EncryptedString.of("Elytra Helper"),
                EncryptedString.of("Uses a firework from your inventory when you press the key, without holding it"),
                -1,
                Category.SWORD);
        addSettings(fireworkKey, onlyWhileGliding, holdToRepeat, repeatDelay);
    }

    @Override
    public void onEnable() {
        eventManager.add(TickListener.class, this);
        reset();
        super.onEnable();
    }

    @Override
    public void onDisable() {
        eventManager.remove(TickListener.class, this);
        reset();
        super.onDisable();
    }

    private void reset() {
        wasPressed              = false;
        cooldown                = 0;
        pendingRestoreSlotId    = -1;
        pendingRestoreTargetSlot = -1;
        pendingUseHand          = null;
    }

    @Override
    public void onTick() {
        if (mc.player == null || mc.world == null || mc.interactionManager == null) {
            wasPressed = false;
            return;
        }
        if (mc.currentScreen != null) {
            wasPressed = false;
            return;
        }

        // Tick N+1: flush deferred USE then restore-SWAP.
        // NCP InventoryMove.SameTickSwap requires SWAP+USE+restore in the same tick.
        // By deferring both USE and restore to the next tick we avoid that check entirely.
        if (pendingRestoreSlotId != -1) {
            int syncId = mc.player.playerScreenHandler.syncId;
            if (pendingUseHand != null) {
                mc.interactionManager.interactItem(mc.player, pendingUseHand);
                pendingUseHand = null;
            }
            mc.interactionManager.clickSlot(syncId, pendingRestoreSlotId,
                    pendingRestoreTargetSlot, SlotActionType.SWAP, mc.player);
            pendingRestoreSlotId    = -1;
            pendingRestoreTargetSlot = -1;
            // Skip key processing this tick — let server digest USE+restore cleanly.
            return;
        }

        if (cooldown > 0) cooldown--;

        int key = fireworkKey.getKey();
        if (key == -1) return;

        boolean pressed = KeyUtils.isKeyPressed(key);

        if (pressed && !wasPressed) {
            useFirework();
            cooldown = Math.max(2, repeatDelay.getValueInt());
        } else if (pressed && holdToRepeat.getValue() && cooldown <= 0) {
            useFirework();
            cooldown = Math.max(2, repeatDelay.getValueInt());
        }

        wasPressed = pressed;
    }

    private void useFirework() {
        if (onlyWhileGliding.getValue() && !mc.player.isGliding()) return;

        ClientPlayerEntity player = mc.player;
        int selected = player.getInventory().getSelectedSlot();

        // Fast path — mainhand already has rocket, no inventory touch, nothing to flag.
        if (player.getInventory().getStack(selected).isOf(Items.FIREWORK_ROCKET)) {
            mc.interactionManager.interactItem(player, Hand.MAIN_HAND);
            return;
        }

        // Fast path — offhand has rocket, no inventory touch.
        if (player.getOffHandStack().isOf(Items.FIREWORK_ROCKET)) {
            mc.interactionManager.interactItem(player, Hand.OFF_HAND);
            return;
        }

        // Scan inventory for a firework rocket (skip currently selected hotbar slot).
        int from = -1;
        for (int i = 0; i < 36; i++) {
            if (i == selected) continue;
            if (player.getInventory().getStack(i).isOf(Items.FIREWORK_ROCKET)) {
                from = i;
                break;
            }
        }
        if (from == -1) return;

        // Map inventory index → PlayerScreenHandler container slot id.
        // Hotbar indices 0–8 map to container slots 36–44.
        // Main inventory indices 9–35 map to container slots 9–35.
        int slotId = from < 9 ? 36 + from : from;
        int syncId = player.playerScreenHandler.syncId;

        // Tick N: SWAP only. USE and restore-SWAP are deferred to tick N+1.
        mc.interactionManager.clickSlot(syncId, slotId, selected, SlotActionType.SWAP, player);
        pendingRestoreSlotId     = slotId;
        pendingRestoreTargetSlot = selected;
        pendingUseHand           = Hand.MAIN_HAND;
    }
}
