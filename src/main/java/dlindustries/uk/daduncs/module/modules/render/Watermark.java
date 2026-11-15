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

import dlindustries.uk.daduncs.event.events.HudListener;
import dlindustries.uk.daduncs.event.events.PacketReceiveListener;
import dlindustries.uk.daduncs.gui.Frame;
import dlindustries.uk.daduncs.gui.HudEditor;
import dlindustries.uk.daduncs.gui.Theme;
import dlindustries.uk.daduncs.module.Category;
import dlindustries.uk.daduncs.module.Module;
import dlindustries.uk.daduncs.module.modules.client.ClickGUI;
import dlindustries.uk.daduncs.module.setting.bool;
import dlindustries.uk.daduncs.module.setting.number;
import dlindustries.uk.daduncs.render.Render2D;
import dlindustries.uk.daduncs.utils.EncryptedString;
import dlindustries.uk.daduncs.utils.TextRenderer;
import dlindustries.uk.daduncs.utils.Utils;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.network.packet.s2c.play.WorldTimeUpdateS2CPacket;
import net.minecraft.util.Identifier;

/**
 * Watermark: [logo Cl1ent] [nick | fps | ping | tps] / [x y z | bps]
 */
public final class Watermark extends Module implements HudListener, PacketReceiveListener {

	private static final String CLIENT_NAME = "Cl1ent";
	private static final Identifier LOGO = Identifier.of("cl1ent", "textures/gui/client_icon.png");

	private final number size = new number(EncryptedString.of("Size"), 50, 200, 100, 5);
	private final bool showPing = new bool(EncryptedString.of("Ping"), true);
	private final bool showTps = new bool(EncryptedString.of("TPS"), true);
	private final bool showCoords = new bool(EncryptedString.of("Coords"), true);
	private final bool showBps = new bool(EncryptedString.of("BPS"), true);

	private long lastTimePacket = -1L;
	private float tps = 20f;
	private double bps;
	private double lastX, lastZ;
	private long lastBpsNano;

	public Watermark() {
		super(EncryptedString.of("Watermark"),
				EncryptedString.of("Shows the client name, nick, fps, ping, tps and coords"),
				-1,
				Category.RENDER);
		addSettings(size, showPing, showTps, showCoords, showBps);
		HudEditor.register(this);
	}

	@Override
	public void onEnable() {
		eventManager.add(HudListener.class, this);
		eventManager.add(PacketReceiveListener.class, this);
		lastTimePacket = -1L;
		super.onEnable();
	}

	@Override
	public void onDisable() {
		eventManager.remove(HudListener.class, this);
		eventManager.remove(PacketReceiveListener.class, this);
		super.onDisable();
	}

	// TPS: sunucu ~1 sn'de bir zaman paketi gonderir (20 tick).
	@Override
	public void onPacketReceive(PacketReceiveEvent event) {
		if (!(event.packet instanceof WorldTimeUpdateS2CPacket)) return;
		long now = System.nanoTime();
		if (lastTimePacket > 0) {
			float seconds = (now - lastTimePacket) / 1_000_000_000f;
			if (seconds > 0.05f) {
				float sample = Math.max(0f, Math.min(20f, 20f / seconds));
				tps = tps * 0.7f + sample * 0.3f;
			}
		}
		lastTimePacket = now;
	}

	private void updateBps() {
		if (mc.player == null) { bps = 0; return; }
		long now = System.nanoTime();
		if (lastBpsNano == 0) {
			lastBpsNano = now; lastX = mc.player.getX(); lastZ = mc.player.getZ();
			return;
		}
		if (now - lastBpsNano >= 250_000_000L) {
			double dx = mc.player.getX() - lastX;
			double dz = mc.player.getZ() - lastZ;
			double dt = (now - lastBpsNano) / 1_000_000_000.0;
			bps = Math.sqrt(dx * dx + dz * dz) / dt;
			lastX = mc.player.getX(); lastZ = mc.player.getZ(); lastBpsNano = now;
		}
	}

	@Override
	public void onRenderHud(HudEvent event) {
		if (mc.player == null) return;
		DrawContext context = event.context;
		int x = HudEditor.x(this, 8);
		int y = HudEditor.y(this, 8);

		updateBps();
		HudEditor.push(context, size.getValueFloat() / 100f, x, y);

		final int tall = 20;
		final int pad = 8;
		final int gap = 4;
		final int logoSize = 12;

		// --- 1. pill: logo + Cl1ent ---
		int nameW = TextRenderer.menuWidth(CLIENT_NAME);
		int w1 = pad + logoSize + 5 + nameW + pad;
		pill(context, x, y, w1, tall);
		Render2D.drawLogo(context, x + pad, y + (tall - logoSize) / 2f, logoSize, logoSize, LOGO, Theme.fade(Theme.accent, 1f));
		TextRenderer.drawMenu(CLIENT_NAME, context, x + pad + logoSize + 5, y + tall / 2 - 2, Theme.text);

		// --- 2. pill: nick | fps | ping | tps ---
		String nick = mc.getSession().getUsername();
		String fps = mc.getCurrentFps() + "fps";
		StringBuilder rest = new StringBuilder(fps);
		if (showPing.getValue()) rest.append("  ").append(Utils.getPing(mc.player)).append("ms");
		if (showTps.getValue()) rest.append("  ").append(String.format(java.util.Locale.ROOT, "%.1f", tps)).append("tps");
		String restText = rest.toString();

		int x2 = x + w1 + gap;
		int nickW = TextRenderer.menuWidth(nick);
		int w2 = pad + nickW + 6 + 1 + 6 + TextRenderer.smallWidth(restText) + pad;
		pill(context, x2, y, w2, tall);
		TextRenderer.drawMenu(nick, context, x2 + pad, y + tall / 2 - 2, Theme.text);
		Render2D.drawRoundRect(context, x2 + pad + nickW + 6, y + 6, 1, 8, 0.5f, Theme.fade(Theme.divider, 0.8f));
		TextRenderer.drawSmall(restText, context, x2 + pad + nickW + 13, y + tall / 2 - 1, Theme.muted);

		int totalW = w1 + gap + w2;
		int totalH = tall;

		// --- 3. alt pill: koordinat | bps ---
		if (showCoords.getValue() || showBps.getValue()) {
			int y3 = y + tall + gap;
			StringBuilder info = new StringBuilder();
			if (showCoords.getValue()) {
				info.append("x").append((int) Math.floor(mc.player.getX()))
						.append(" y").append((int) Math.floor(mc.player.getY()))
						.append(" z").append((int) Math.floor(mc.player.getZ()));
			}
			if (showBps.getValue()) {
				if (info.length() > 0) info.append("   ");
				info.append(String.format(java.util.Locale.ROOT, "%.1fbps", bps));
			}
			String infoText = info.toString();
			int w3 = pad + TextRenderer.smallWidth(infoText) + pad;
			pill(context, x, y3, w3, tall);
			TextRenderer.drawSmall(infoText, context, x + pad, y3 + tall / 2 - 1, Theme.text);
			totalH = tall * 2 + gap;
			totalW = Math.max(totalW, w3);
		}

		HudEditor.bounds(this, x, y, totalW, totalH);
		HudEditor.pop(context);
	}

	private void pill(DrawContext context, int x, int y, int width, int tall) {
		if (ClickGUI.hudGlass.getValue()) {
			Render2D.drawBubble(context, x, y, width, tall, tall / 2f, Theme.SHEEN, 7f, 0.35f, 0x66101010, 1f);
		} else {
			Render2D.drawGlassRoundRect(context, x, y, width, tall, tall / 2f, Theme.SHEEN, Theme.BLUR, Theme.surface, 1f);
		}
	}
}
