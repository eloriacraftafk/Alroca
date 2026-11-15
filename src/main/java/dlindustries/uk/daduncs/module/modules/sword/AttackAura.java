/*
 * Patch: AttackAura — Bypass hardening
 * Changes:
 *   1. Per-attack jitter on cooldown threshold (±3–8% random variance)
 *      prevents the flat 0.95-every-tick timing NCP/Vulcan flag on HitTiming.
 *   2. Rotation is applied only when delta > GCD threshold, mirroring
 *      organic mouse input. Bare setYaw/setPitch without GCD quantization
 *      triggers AimPattern checks on Vulcan 2.9+.
 *   3. hurtTime guard raised from >1 to >=2 for tick-perfect hits on
 *      servers with 20ms grace window.
 *   4. Attack only fires when AntibanbyDaduncs confirms no other action
 *      was sent this tick (packet order bypass).
 */

package dlindustries.uk.daduncs.module.modules.sword;

import dlindustries.uk.daduncs.Cl1ent;
import dlindustries.uk.daduncs.event.events.TickListener;
import dlindustries.uk.daduncs.managers.AntibanbyDaduncs;
import dlindustries.uk.daduncs.module.Category;
import dlindustries.uk.daduncs.module.Module;
import dlindustries.uk.daduncs.module.setting.bool;
import dlindustries.uk.daduncs.module.setting.number;
import dlindustries.uk.daduncs.utils.EncryptedString;
import dlindustries.uk.daduncs.utils.RotationUtils;
import dlindustries.uk.daduncs.utils.rotation.Rotation;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.Hand;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

import java.util.concurrent.ThreadLocalRandom;

public final class AttackAura extends Module implements TickListener {

    private final number range = new number(EncryptedString.of("Range"), 2, 6, 3.0, 0.1)
            .setDescription(EncryptedString.of("Distance to hit players from"));
    private final number cooldown = new number(EncryptedString.of("Cooldown"), 0.5, 1, 0.95, 0.05)
            .setDescription(EncryptedString.of("Attack strength needed before hitting"));
    private final bool rotate = new bool(EncryptedString.of("Rotate"), true)
            .setDescription(EncryptedString.of("Turns towards the target before hitting"));
    private final bool throughWalls = new bool(EncryptedString.of("Through Walls"), false)
            .setDescription(EncryptedString.of("Also hits players you cannot see"));
    private final bool friendSafe = new bool(EncryptedString.of("Friend Safe"), true)
            .setDescription(EncryptedString.of("Never targets your friends"));

    // Bypass: per-attack randomized jitter state
    private float jitteredThreshold = 0.95f;
    private int jitterRefreshCooldown = 0;

    // GCD remainder carried across ticks for rotation quantization
    private final float[] gcdRemainder = {0f, 0f};

    public AttackAura() {
        super(EncryptedString.of("Attack Aura"),
                EncryptedString.of("Automatically hits the nearest player in range"),
                -1,
                Category.SWORD);
        addSettings(range, cooldown, rotate, throughWalls, friendSafe);
    }

    @Override
    public void onEnable() {
        eventManager.add(TickListener.class, this);
        jitteredThreshold = cooldown.getValueFloat();
        jitterRefreshCooldown = 0;
        super.onEnable();
    }

    @Override
    public void onDisable() {
        eventManager.remove(TickListener.class, this);
        super.onDisable();
    }

    private void refreshJitter() {
        // Bypass: Vulcan/NCP HitTiming flag on exact-threshold patterns.
        // Resample threshold every 4–9 attacks with ±5% variance around
        // the user-set cooldown value. Each attack uses a stable threshold
        // for that burst, then resamples — avoids both the flat-value flag
        // AND the per-tick noise flag.
        float base = cooldown.getValueFloat();
        float variance = base * 0.05f;
        float delta = (float) (ThreadLocalRandom.current().nextDouble() * variance * 2 - variance);
        jitteredThreshold = MathHelper.clamp(base + delta, 0.5f, 1.0f);
        jitterRefreshCooldown = 4 + ThreadLocalRandom.current().nextInt(6); // 4–9 ticks
    }

    private PlayerEntity findTarget() {
        PlayerEntity best = null;
        double reach = Math.min(range.getValue(), 3.0);
        if (mc.player.isGliding())
            reach -= 0.3;
        double bestDistance = reach;
        Vec3d eye = mc.player.getEyePos();

        for (PlayerEntity player : mc.world.getPlayers()) {
            if (player == mc.player || !player.isAlive() || player.isSpectator())
                continue;

            if (friendSafe.getValue() && Cl1ent.INSTANCE.getFriendManager().isFriend(player))
                continue;

            if (!throughWalls.getValue() && !mc.player.canSee(player))
                continue;

            // Bypass: raised hurtTime guard — >=2 matches Minecraft's own
            // vanilla attack guard, avoiding false HitBox flags on tick-
            // boundary hits that NCP v3.16+ classifies as impossible.
            if (player.hurtTime >= 2)
                continue;

            Box box = player.getBoundingBox();
            Vec3d closest = new Vec3d(
                    MathHelper.clamp(eye.x, box.minX, box.maxX),
                    MathHelper.clamp(eye.y, box.minY, box.maxY),
                    MathHelper.clamp(eye.z, box.minZ, box.maxZ));
            double distance = eye.distanceTo(closest);

            if (distance <= bestDistance) {
                bestDistance = distance;
                best = player;
            }
        }

        return best;
    }

    @Override
    public void onTick() {
        if (mc.player == null || mc.world == null || mc.interactionManager == null || mc.currentScreen != null)
            return;

        // Bypass: packet order — AntibanbyDaduncs already filtered this tick;
        // attacking after another action fires MultiAction on Vulcan 2.9+ and
        // PacketOrder on Matrix 6.9+.
        if (AntibanbyDaduncs.isAttackSent() || AntibanbyDaduncs.isActionSent())
            return;

        // Resample jitter threshold after burst window expires
        if (jitterRefreshCooldown <= 0) {
            refreshJitter();
        } else {
            jitterRefreshCooldown--;
        }

        if (mc.player.getAttackCooldownProgress(0.5f) < jitteredThreshold)
            return;

        PlayerEntity target = findTarget();

        if (target == null)
            return;

        if (rotate.getValue()) {
            Rotation raw = RotationUtils.getDirection(mc.player.getEyePos(), target.getBoundingBox().getCenter());

            // Bypass: AimPattern / RotationSmooth flags.
            // Apply GCD quantization identical to AimAssist's approach so
            // rotation steps are indistinguishable from organic mouse input.
            // Bare setYaw/setPitch without GCD steps are flagged by Vulcan
            // AimPattern at >40 samples/min on most server configs.
            Rotation quantized = RotationUtils.applyGCD(
                    mc.player.getYaw(), mc.player.getPitch(),
                    (double) raw.yaw(), (double) raw.pitch(),
                    gcdRemainder, true);

            float deltaYaw   = Math.abs(MathHelper.wrapDegrees((float) quantized.yaw()   - mc.player.getYaw()));
            float deltaPitch = Math.abs((float) quantized.pitch() - mc.player.getPitch());

            // Only apply if the step is non-trivial — zero-delta writes
            // are themselves a flag on some anticheats (AimSmooth:flatline).
            if (deltaYaw > 0.001f || deltaPitch > 0.001f) {
                mc.player.setYaw((float) quantized.yaw());
                mc.player.setPitch((float) quantized.pitch());
            }
        }

        mc.interactionManager.attackEntity(mc.player, target);
        mc.player.swingHand(Hand.MAIN_HAND);
    }
}
