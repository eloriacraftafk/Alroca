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

import dlindustries.uk.daduncs.Cl1ent;
import dlindustries.uk.daduncs.event.events.HudListener;
import dlindustries.uk.daduncs.gui.Anim;
import dlindustries.uk.daduncs.gui.HudEditor;
import dlindustries.uk.daduncs.gui.Theme;
import dlindustries.uk.daduncs.module.Category;
import dlindustries.uk.daduncs.module.Module;
import dlindustries.uk.daduncs.module.modules.client.ClickGUI;
import dlindustries.uk.daduncs.module.setting.Setting;
import dlindustries.uk.daduncs.module.setting.bind;
import dlindustries.uk.daduncs.module.setting.bool;
import dlindustries.uk.daduncs.module.setting.number;
import dlindustries.uk.daduncs.render.Render2D;
import dlindustries.uk.daduncs.utils.EncryptedString;
import dlindustries.uk.daduncs.utils.TextRenderer;
import net.minecraft.client.gui.DrawContext;

import java.util.ArrayList;
import java.util.List;

public final class ModuleHud extends Module implements HudListener {

	private static final int WIDTH = 136;
	private static final int ROW = 14;
	private static final int ROUND = 12;

	private final bool glow = new bool(EncryptedString.of("Glow"), true);
	private final number glowSize = new number(EncryptedString.of("Glow Size"), 4, 40, 18, 1).setVisibility(glow::getValue);
	private final bool outline = new bool(EncryptedString.of("Outline"), true);
	private final number size = new number(EncryptedString.of("Size"), 50, 200, 100, 5);

	private final Anim show = new Anim(0f, 7f);

	public ModuleHud() {
		super(EncryptedString.of("Module Hud"),
				EncryptedString.of("Shows enabled Sword modules"),
				-1,
				Category.RENDER);
		addSettings(glow, glowSize, outline, size);
		HudEditor.register(this);
	}

	@Override
	public void onEnable() {
		eventManager.add(HudListener.class, this);
		super.onEnable();
	}

	@Override
	public void onDisable() {
		eventManager.remove(HudListener.class, this);
		show.snap(0f);
		super.onDisable();
	}

	private static bind keyOf(Module module) {
		for (Setting<?> setting : module.getSettings())
			if (setting instanceof bind value && value.isModuleKey())
				return value;

		return null;
	}

	@Override
	public void onRenderHud(HudEvent event) {
		List<Module> active = new ArrayList<>();
		for (Module module : Cl1ent.INSTANCE.getModuleManager().getEnabledModules())
			if (module.getCategory() == Category.SWORD)
				active.add(module);

		active.sort((a, b) -> a.getName().toString().compareToIgnoreCase(b.getName().toString()));

		DrawContext context = event.context;
		float delta = mc.getRenderTickCounter().getDynamicDeltaTicks() / 20f;

		show.to(active.isEmpty() && !HudEditor.editing() ? 0f : 1f);
		show.update(delta);

		if (show.get() <= 0.06f)
			return;

		float fade = show.get();
		int x = HudEditor.x(this, 8);
		int y = HudEditor.y(this, 8);

		HudEditor.push(context, size.getValueFloat() / 100f, x, y);

		String title = EncryptedString.of("Modules").toString();
		int rows = Math.max(1, active.size());
		int height = Math.round((26 + rows * ROW + 4) * (0.9f + 0.1f * show.overshoot()));
		int width = Math.max(WIDTH, 18 + TextRenderer.smallBoldWidth(title) + 18);

		List<String> labels = new ArrayList<>();
		for (Module module : active) {
			bind key = keyOf(module);
			String label = key == null ? "" : "[" + key.getKeyName() + "]";
			labels.add(label);

			width = Math.max(width, 9 + TextRenderer.smallWidth(module.getName()) + 14 + TextRenderer.smallWidth(label) + 9);
		}

		if (glow.getValue())
			Render2D.drawFade(context, x, y, width, height, ROUND, glowSize.getValueFloat(), true, fade);

		if (ClickGUI.hudGlass.getValue())
			Render2D.drawBubble(context, x, y, width, height, ROUND, Theme.SHEEN, 7f, 0.35f, 0xCC101010, fade);
		else Render2D.drawGlassRoundRect(context, x, y, width, height, ROUND, Theme.SHEEN, Theme.BLUR, 0xFF101010, fade);

		if (outline.getValue())
			Render2D.drawRoundOutline(context, x, y, width, height, ROUND, 1, Theme.fade(Theme.accent, fade));

		HudEditor.bounds(this, x, y, width, height);

		int titleX = x + (width - TextRenderer.smallBoldWidth(title)) / 2;
		TextRenderer.drawSmallBold(title, context, titleX, y + 10, Theme.fade(Theme.text, fade));
		Render2D.drawRoundRect(context, x + 9, y + 20, width - 18, 1, 0.5f, Theme.fade(Theme.divider, fade));

		int row = y + 24;
		for (int i = 0; i < active.size(); i++) {
			Module module = active.get(i);
			String label = labels.get(i);

			TextRenderer.drawSmall(module.getName(), context, x + 9, row + 8, Theme.fade(Theme.text, fade));
			TextRenderer.drawSmall(label, context, x + width - 9 - TextRenderer.smallWidth(label), row + 8, Theme.fade(Theme.text, fade));

			row += ROW;
		}

		HudEditor.pop(context);
	}
}
