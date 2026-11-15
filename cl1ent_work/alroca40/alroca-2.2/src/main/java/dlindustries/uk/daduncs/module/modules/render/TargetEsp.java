/*
 * This file is part of System Client (https://github.com/daduncs/system)
 * Website: https://download.daduncs.cloud/
 *
 * Copyright (c) 2024 - 2026 DLindustries and project contributors.
 *
 * System Client is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * System Client is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with System Client. If not, see <https://www.gnu.org/licenses/>.
 */

package dlindustries.uk.daduncs.module.modules.render;

import dlindustries.uk.daduncs.event.events.AttackListener;
import dlindustries.uk.daduncs.event.events.GameRenderListener;
import dlindustries.uk.daduncs.module.Category;
import dlindustries.uk.daduncs.module.Module;
import dlindustries.uk.daduncs.utils.EncryptedString;
import dlindustries.uk.daduncs.utils.RenderUtils;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.RenderLayers;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;

/**
 * Baktığımız veya son vurduğumuz oyuncunun hitbox altına beyaz halka çizer.
 * Ne baktığımız ne de vurduğumuz yoksa halka 2 saniyede alpha fade ile solar.
 */
public final class TargetEsp extends Module implements GameRenderListener, AttackListener {

    private static final int RING_SEGMENTS = 48;
    // Fade süresi: 2000 ms
    private static final long FADE_MS = 2000L;

    // Son aktif hedef ve o hedefe en son ne zaman "dokunuldu" (baktık ya da vurduk)
    private PlayerEntity lastTarget = null;
    private long lastActiveMs = 0L;

    public TargetEsp() {
        super(EncryptedString.of("Target ESP"),
                EncryptedString.of("Baktığın/vurduğun oyuncunun altına beyaz halka çizer, 2 sn sonra solar"),
                -1,
                Category.RENDER);
        // Settings yok — sade, beyaz halka
    }

    @Override
    public void onEnable() {
        eventManager.add(GameRenderListener.class, this);
        eventManager.add(AttackListener.class, this);
        super.onEnable();
    }

    @Override
    public void onDisable() {
        eventManager.remove(GameRenderListener.class, this);
        eventManager.remove(AttackListener.class, this);
        lastTarget = null;
        super.onDisable();
    }

    // Vurma anında crosshairTarget'ı yakala
    @Override
    public void onAttack(AttackEvent event) {
        if (mc.crosshairTarget instanceof EntityHitResult hit
                && hit.getEntity() instanceof PlayerEntity player) {
            lastTarget = player;
            lastActiveMs = System.currentTimeMillis();
        }
    }

    @Override
    public void onGameRender(GameRenderEvent event) {
        if (mc.world == null || mc.player == null) return;

        long now = System.currentTimeMillis();

        // Şu an baktığımız oyuncu var mı?
        PlayerEntity looking = null;
        if (mc.crosshairTarget instanceof EntityHitResult hit
                && hit.getEntity() instanceof PlayerEntity p) {
            looking = p;
        }

        // Aktif hedef: bakıyorsak o, yoksa son vurulan
        PlayerEntity active;
        float alpha;

        if (looking != null) {
            // Bakıyoruz — canlı, tam opaklık; lastTarget'ı güncelle
            active = looking;
            lastTarget = looking;
            lastActiveMs = now;
            alpha = 1.0f;
        } else if (lastTarget != null && lastTarget.isAlive() && !lastTarget.isRemoved()) {
            // Artık bakmıyoruz ama yakın zamanda vurduk/baktık
            long elapsed = now - lastActiveMs;
            if (elapsed >= FADE_MS) {
                // Tamamen soldu, temizle
                lastTarget = null;
                return;
            }
            active = lastTarget;
            // 0 → 1 arası: 0 = yeni kayboldu (tam görünür), 1 = tamamen soldu
            float t = (float) elapsed / FADE_MS;
            alpha = 1.0f - t;
        } else {
            lastTarget = null;
            return;
        }

        Camera camera = mc.gameRenderer.getCamera();
        if (camera == null) return;

        event.matrices.push();
        Vec3d cameraPos = camera.getCameraPos();
        event.matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(camera.getPitch()));
        event.matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(camera.getYaw() + 180.0F));
        event.matrices.translate(-cameraPos.x, -cameraPos.y, -cameraPos.z);

        Vec3d pos = active.getLerpedPos(RenderUtils.tickProgress());
        // Halka hitbox tabanında, biraz üstte yerde yüzer
        float cx = (float) pos.x;
        float cy = (float) pos.y + 0.04f;
        float cz = (float) pos.z;
        float radius = Math.max(0.5f, active.getWidth() * 0.6f);
        float half  = 0.045f; // halka kalınlığı

        // Hafif tilt için zaman bazlı faz (TargetCrystal'daki gibi)
        double time  = (System.currentTimeMillis() % 100000L) / 1000.0;
        double phase = time * 0.6;
        float  tilt  = (float) Math.toRadians(14.0);

        final Matrix4f matrix = event.matrices.peek().getPositionMatrix();
        final float a = alpha;

        RenderUtils.drawLayer(RenderLayers.debugFilledBox(), buf -> {
            for (int i = 0; i < RING_SEGMENTS; i++) {
                double t1 = i       * (Math.PI * 2.0 / RING_SEGMENTS);
                double t2 = (i + 1) * (Math.PI * 2.0 / RING_SEGMENTS);

                float o = radius + half;
                float n = radius - half;

                float ox1 = cx + (float) Math.cos(t1) * o, oz1 = cz + (float) Math.sin(t1) * o;
                float ox2 = cx + (float) Math.cos(t2) * o, oz2 = cz + (float) Math.sin(t2) * o;
                float ix1 = cx + (float) Math.cos(t1) * n, iz1 = cz + (float) Math.sin(t1) * n;
                float ix2 = cx + (float) Math.cos(t2) * n, iz2 = cz + (float) Math.sin(t2) * n;

                float y1 = cy + (float) (Math.sin(t1 - phase) * Math.sin(tilt) * radius);
                float y2 = cy + (float) (Math.sin(t2 - phase) * Math.sin(tilt) * radius);

                // Dış kenar: tam beyaz
                buf.vertex(matrix, ox1, y1, oz1).color(1f, 1f, 1f, a);
                buf.vertex(matrix, ox2, y2, oz2).color(1f, 1f, 1f, a);
                // İç kenar: şeffaf geçiş
                buf.vertex(matrix, ix2, y2, iz2).color(1f, 1f, 1f, a * 0.25f);
                buf.vertex(matrix, ix1, y1, iz1).color(1f, 1f, 1f, a * 0.25f);
            }
        });

        event.matrices.pop();
    }
}
