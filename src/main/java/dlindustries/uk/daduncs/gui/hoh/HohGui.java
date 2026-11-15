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

package dlindustries.uk.daduncs.gui.hoh;

import dlindustries.uk.daduncs.Cl1ent;
import dlindustries.uk.daduncs.gui.Theme;
import dlindustries.uk.daduncs.module.Category;
import dlindustries.uk.daduncs.module.Module;
import dlindustries.uk.daduncs.module.modules.client.ClickGUI;
import dlindustries.uk.daduncs.module.setting.Setting;
import dlindustries.uk.daduncs.module.setting.bind;
import dlindustries.uk.daduncs.module.setting.bool;
import dlindustries.uk.daduncs.module.setting.mode;
import dlindustries.uk.daduncs.module.setting.number;
import dlindustries.uk.daduncs.render.Draw;
import dlindustries.uk.daduncs.utils.Sounds;
import dlindustries.uk.daduncs.utils.TextRenderer;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.input.CharInput;
import net.minecraft.client.input.KeyInput;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

import static dlindustries.uk.daduncs.Cl1ent.mc;

/**
 * Single-window click GUI, a port of the HoH Utility HTML click GUI
 * (172 wide window, search box, module rows, expandable settings, footer popup).
 * Left click = toggle, right click = expand settings, middle click = bind key.
 */
public final class HohGui extends Screen {

	private static final int W = 172;
	private static final int TOP = 46;
	private static final int BOTTOM = 27;
	private static final int ROW_MOD = 18;
	private static final int ROW_SET = 14;
	private static final int ROW_HEAD = 12;

	private static final int WINDOW_FILL = 0x980C0C10;
	private static final int BORDER = 0x24FFFFFF;
	private static final int ICON = 0x80FFFFFF;
	private static final int SEARCH_BG = 0x22FFFFFF;
	private static final int SEARCH_BG_FOCUS = 0x3DFFFFFF;
	private static final int SEARCH_BORDER = 0x1AFFFFFF;
	private static final int SEARCH_TEXT = 0xE0FFFFFF;
	private static final int MOD_HOVER = 0x1AFFFFFF;
	private static final int MOD_OFF_NAME = 0x95FFFFFF;
	private static final int BOOL_OFF = 0x55FFFFFF;
	private static final int KEY_NORMAL = 0x70FFFFFF;
	private static final int BOOL_NAME_OFF = 0x80FFFFFF;
	private static final int NUM_NAME = 0xB0FFFFFF;
	private static final int FOOTER_TEXT = 0x75FFFFFF;
	private static final int FOOTER_HOVER = 0x18FFFFFF;
	private static final int SET_BG = 0x14000000;

	private enum Kind {HEAD, MODULE, SETTING}

	private static final class Entry {
		final Kind kind;
		final Category category;
		final Module module;
		final Setting<?> setting;
		int y;

		Entry(Kind kind, Category category, Module module, Setting<?> setting) {
			this.kind = kind;
			this.category = category;
			this.module = module;
			this.setting = setting;
		}

		int height() {
			return switch (kind) {
				case HEAD -> ROW_HEAD;
				case MODULE -> ROW_MOD;
				default -> ROW_SET;
			};
		}
	}

	private final List<Module> expanded = new ArrayList<>();
	private final List<Entry> entries = new ArrayList<>();

	private float guiX = 70;
	private float guiY = 40;
	private boolean placed;
	private boolean dragging;
	private float dragDX;
	private float dragDY;
	private boolean collapsed;
	private boolean popup;
	private String search = "";
	private boolean searching;
	private bind listening;
	private Module listeningModule;
	private number draggingNumber;
	private float numberX;
	private float numberW;
	private int scroll;
	private int windowHeight;
	private int viewTop;
	private int viewBottom;
	private float scale = 1.5f;
	private long lastFrame;

	public HohGui() {
		super(Text.literal("Cl1ent"));
	}

	@Override
	public void onDisplayed() {
		listening = null;
		listeningModule = null;
		draggingNumber = null;
		searching = false;
		dragging = false;
		lastFrame = java.lang.System.nanoTime();
	}

	@Override
	protected void init() {
		if (!placed) {
			placed = true;
			guiX = 70;
			guiY = 40;
		}
	}

	private float scale() {
		return 1.5f * ClickGUI.guiSize.getValueFloat() / 100f;
	}

	private int accent() {
		return Theme.accent;
	}

	private void build() {
		entries.clear();
		String filter = search.trim().toLowerCase();
		for (Category category : Category.values()) {
			List<Module> modules = Cl1ent.INSTANCE.getModuleManager().getModulesInCategory(category);
			boolean head = false;
			for (Module module : modules) {
				if (!filter.isEmpty() && !module.getName().toString().toLowerCase().contains(filter))
					continue;

				if (!head) {
					head = true;
					entries.add(new Entry(Kind.HEAD, category, null, null));
				}
				entries.add(new Entry(Kind.MODULE, category, module, null));

				if (expanded.contains(module)) {
					for (Setting<?> setting : module.getSettings()) {
						if (!setting.isVisible()) continue;
						if (setting instanceof bind b && b.isModuleKey()) continue;
						if (setting instanceof bool || setting instanceof number || setting instanceof mode<?> || setting instanceof bind)
							entries.add(new Entry(Kind.SETTING, category, module, setting));
					}
				}
			}
		}
	}

	private int contentHeight() {
		int total = 0;
		for (Entry e : entries) total += e.height();
		return total;
	}

	private void layout() {
		build();
		int content = contentHeight();
		int max = (int) (height / scale) - 12;
		int natural = content + TOP + BOTTOM;
		windowHeight = collapsed ? 24 : Math.min(natural, Math.max(120, max));
		viewTop = TOP;
		viewBottom = windowHeight - BOTTOM;
		int visible = viewBottom - viewTop;
		int maxScroll = Math.max(0, content - visible);
		scroll = Math.clamp(scroll, 0, maxScroll);

		int y = viewTop - scroll;
		for (Entry e : entries) {
			e.y = y;
			y += e.height();
		}
	}

	private boolean fits(Entry e) {
		return e.y >= viewTop && e.y + e.height() <= viewBottom;
	}

	private String keyText(Module module) {
		if (listeningModule == module && listening != null) return "[...]";
		for (Setting<?> s : module.getSettings())
			if (s instanceof bind b && b.isModuleKey()) {
				if (b.getKey() == GLFW.GLFW_KEY_UNKNOWN) return "";
				return "[" + b.getKeyName() + "]";
			}
		return "";
	}

	private static String numberText(number n) {
		double inc = n.getIncrement();
		if (inc >= 1) return String.valueOf((int) Math.round(n.getValue()));
		if (inc >= 0.1) return String.format("%.1f", n.getValue());
		return String.format("%.2f", n.getValue());
	}

	@Override
	public void render(DrawContext context, int mouseX, int mouseY, float partialTick) {
		scale = scale();
		long now = java.lang.System.nanoTime();
		float delta = Math.min(0.1f, (now - lastFrame) / 1.0E9f);
		lastFrame = now;
		Theme.ease(delta, 8f);
		layout();
		Draw.warmup(context);

		float mx = mouseX / scale - guiX;
		float my = mouseY / scale - guiY;

		var m = context.getMatrices();
		m.pushMatrix();
		m.scale(scale, scale);
		m.translate(guiX, guiY);

		int n = windowHeight;
		context.fill(0, 0, W, n, WINDOW_FILL);
		context.fill(0, 0, W, 1, BORDER);
		context.fill(0, n - 1, W, n, BORDER);
		context.fill(0, 1, 1, n - 1, BORDER);
		context.fill(W - 1, 1, W, n - 1, BORDER);

		TextRenderer.drawTiny("Cl1ent", context, 8, 8, accent());
		TextRenderer.drawTiny("*", context, 134, 8, ICON);
		TextRenderer.drawTiny("-", context, 146, 8, ICON);
		TextRenderer.drawTiny("x", context, 158, 8, ICON);

		if (collapsed) {
			m.popMatrix();
			TextRenderer.drawTiny("@alexnumberone & @TersUga", context, 6, height - 12, 0xFFFFFFFF);
			return;
		}

		context.fill(6, 24, 166, 41, searching ? SEARCH_BG_FOCUS : SEARCH_BG);
		int sb = searching ? Theme.fade(accent(), 0.38f) : SEARCH_BORDER;
		context.fill(6, 24, 166, 25, sb);
		context.fill(6, 40, 166, 41, sb);
		context.fill(6, 25, 7, 40, sb);
		context.fill(165, 25, 166, 40, sb);
		if (search.isEmpty() && !searching)
			TextRenderer.drawTiny("search...", context, 10, 28, 0x55FFFFFF);
		else
			TextRenderer.drawTiny(search + (searching && (java.lang.System.currentTimeMillis() / 500) % 2 == 0 ? "|" : ""), context, 10, 28, SEARCH_TEXT);

		for (Entry e : entries) {
			if (!fits(e)) continue;
			switch (e.kind) {
				case HEAD -> TextRenderer.drawTiny(e.category.name.toString().toUpperCase(), context, 8, e.y + 2, Theme.fade(accent(), 0.7f));
				case MODULE -> drawModule(context, e, mx, my);
				default -> drawSetting(context, e, mx, my);
			}
		}

		int footerY = n - 20;
		boolean footerHover = mx >= 4 && mx <= 168 && my >= footerY && my <= footerY + 17;
		if (footerHover) context.fill(4, footerY, 168, footerY + 17, FOOTER_HOVER);
		TextRenderer.drawTiny("Settings / Theme", context, 9, footerY + 5, FOOTER_TEXT);
		TextRenderer.drawTiny(">", context, 159, footerY + 5, BOOL_OFF);

		if (popup) drawPopup(context, mx, my);

		m.popMatrix();

		TextRenderer.drawTiny("@alexnumberone & @TersUga", context, 6, height - 12, 0xFFFFFFFF);
	}

	private void drawModule(DrawContext context, Entry e, float mx, float my) {
		Module module = e.module;
		boolean on = module.isEnabled();
		int y = e.y;
		boolean hover = !popup && mx >= 4 && mx <= 168 && my >= y && my <= y + ROW_MOD;
		if (hover) context.fill(4, y, 168, y + ROW_MOD, MOD_HOVER);

		int dot = on ? accent() : BOOL_OFF;
		context.fill(9, y + 5, 17, y + 6, dot);
		context.fill(9, y + 12, 17, y + 13, dot);
		context.fill(9, y + 6, 10, y + 12, dot);
		context.fill(16, y + 6, 17, y + 12, dot);
		if (on) context.fill(11, y + 7, 15, y + 11, dot);

		TextRenderer.drawTiny(module.getName(), context, 22, y + 5, on ? 0xFFFFFFFF : MOD_OFF_NAME);

		String key = keyText(module);
		if (!key.isEmpty()) {
			boolean waiting = listeningModule == module && listening != null;
			TextRenderer.drawTiny(key, context, 163 - TextRenderer.tinyWidth(key), y + 5, waiting ? accent() : KEY_NORMAL);
		}
	}

	private void drawSetting(DrawContext context, Entry e, float mx, float my) {
		Setting<?> s = e.setting;
		int y = e.y;
		context.fill(16, y, 164, y + ROW_SET, SET_BG);

		if (s instanceof bool b) {
			boolean v = b.getValue();
			TextRenderer.drawTiny(b.getName(), context, 20, y + 3, v ? accent() : BOOL_NAME_OFF);
			String t = v ? "ON" : "OFF";
			TextRenderer.drawTiny(t, context, 160 - TextRenderer.tinyWidth(t), y + 3, v ? accent() : BOOL_OFF);
		} else if (s instanceof number num) {
			double frac = (num.getValue() - num.getMin()) / Math.max(1e-9, num.getMax() - num.getMin());
			context.fill(16, y + ROW_SET - 1, 16 + (int) (148 * frac), y + ROW_SET, Theme.fade(accent(), 0.8f));
			TextRenderer.drawTiny(num.getName(), context, 20, y + 3, NUM_NAME);
			String t = numberText(num);
			TextRenderer.drawTiny(t, context, 160 - TextRenderer.tinyWidth(t), y + 3, accent());
		} else if (s instanceof mode<?> md) {
			TextRenderer.drawTiny(md.getName(), context, 20, y + 3, NUM_NAME);
			String t = md.getMode().toString().replace('_', ' ');
			TextRenderer.drawTiny(t, context, 160 - TextRenderer.tinyWidth(t), y + 3, accent());
		} else if (s instanceof bind b) {
			TextRenderer.drawTiny(b.getName(), context, 20, y + 3, NUM_NAME);
			String t = listening == b ? "[...]" : "[" + b.getKeyName() + "]";
			TextRenderer.drawTiny(t, context, 160 - TextRenderer.tinyWidth(t), y + 3, listening == b ? accent() : KEY_NORMAL);
		}
	}

	private static final int POP_X = 6;
	private static final int POP_Y = 30;
	private static final int POP_W = 160;

	private List<int[]> chips() {
		List<int[]> out = new ArrayList<>();
		ClickGUI.Palette[] all = ClickGUI.Palette.values();
		int x = 0;
		int y = 0;
		for (int i = 0; i < all.length; i++) {
			int w = TextRenderer.tinyWidth(all[i].name().replace('_', ' ')) + 10;
			if (x + w > POP_W - 16) {
				x = 0;
				y += 15;
			}
			out.add(new int[]{x, y, w, i});
			x += w + 3;
		}
		return out;
	}

	private int popupHeight() {
		List<int[]> c = chips();
		int rows = c.isEmpty() ? 0 : c.get(c.size() - 1)[1] / 15 + 1;
		return 8 + 12 + 10 + rows * 15 + 8 + 10 + 16 + 16 + 20 + 8;
	}

	private void drawPopup(DrawContext context, float mx, float my) {
		int h = popupHeight();
		context.fill(POP_X, POP_Y, POP_X + POP_W, POP_Y + h, 0xF70A0A10);
		context.fill(POP_X, POP_Y, POP_X + POP_W, POP_Y + 1, 0x2EFFFFFF);
		context.fill(POP_X, POP_Y + h - 1, POP_X + POP_W, POP_Y + h, 0x2EFFFFFF);
		context.fill(POP_X, POP_Y, POP_X + 1, POP_Y + h, 0x2EFFFFFF);
		context.fill(POP_X + POP_W - 1, POP_Y, POP_X + POP_W, POP_Y + h, 0x2EFFFFFF);

		int x = POP_X + 8;
		int y = POP_Y + 8;
		TextRenderer.drawTiny("Settings / Theme", context, x, y, accent());
		y += 14;
		TextRenderer.drawTiny("COLOR THEME", context, x, y, 0x73FFFFFF);
		y += 10;

		int active = ClickGUI.theme.getModeIndex();
		for (int[] c : chips()) {
			int cx = x + c[0];
			int cy = y + c[1];
			boolean on = c[3] == active;
			context.fill(cx, cy, cx + c[2], cy + 13, on ? Theme.fade(accent(), 0.25f) : 0x0AFFFFFF);
			int border = on ? accent() : 0x26FFFFFF;
			context.fill(cx, cy, cx + c[2], cy + 1, border);
			context.fill(cx, cy + 12, cx + c[2], cy + 13, border);
			context.fill(cx, cy, cx + 1, cy + 13, border);
			context.fill(cx + c[2] - 1, cy, cx + c[2], cy + 13, border);
			TextRenderer.drawTiny(ClickGUI.Palette.values()[c[3]].name().replace('_', ' '), context, cx + 5, cy + 3, on ? 0xFFFFFFFF : 0xBFFFFFFF);
		}
		List<int[]> cs = chips();
		int rows = cs.isEmpty() ? 0 : cs.get(cs.size() - 1)[1] / 15 + 1;
		y += rows * 15 + 8;

		TextRenderer.drawTiny("MENU", context, x, y, 0x73FFFFFF);
		y += 10;
		drawOption(context, x, y, "Ui", ClickGUI.ui.getMode().name());
		y += 16;
		drawOption(context, x, y, "Language", ClickGUI.language.getMode().name());
		y += 20;

		context.fill(x, y, x + POP_W - 16, y + 14, 0x0FFFFFFF);
		TextRenderer.drawTiny("Close", context, x + (POP_W - 16) / 2 - TextRenderer.tinyWidth("Close") / 2, y + 4, 0xDDFFFFFF);
	}

	private void drawOption(DrawContext context, int x, int y, String name, String value) {
		TextRenderer.drawTiny(name, context, x, y + 2, 0xB3FFFFFF);
		TextRenderer.drawTiny(value, context, x + POP_W - 16 - TextRenderer.tinyWidth(value), y + 2, accent());
	}

	@Override
	public void close() {
		Cl1ent.INSTANCE.getModuleManager().getModule(ClickGUI.class).setEnabledStatus(false);
		ClickGUI.uiSound(Sounds.CLOSE);
		listening = null;
		listeningModule = null;
		mc.setScreen(Cl1ent.INSTANCE.previousScreen instanceof TitleScreen ? null : Cl1ent.INSTANCE.previousScreen);
		Cl1ent.INSTANCE.previousScreen = null;
	}

	@Override
	public void renderBackground(DrawContext context, int mouseX, int mouseY, float partialTick) {
	}

	@Override
	public void renderInGameBackground(DrawContext context) {
	}

	@Override
	protected void applyBlur(DrawContext context) {
	}

	@Override
	protected void renderDarkening(DrawContext context) {
	}

	@Override
	public boolean mouseClicked(Click click, boolean doubled) {
		float mx = (float) click.x() / scale - guiX;
		float my = (float) click.y() / scale - guiY;
		int button = click.button();

		if (listening != null) {
			if (button != GLFW.GLFW_MOUSE_BUTTON_LEFT) listening.setKey(button);
			listening.setListening(false);
			listening = null;
			listeningModule = null;
			return true;
		}

		if (mx < 0 || mx > W || my < 0 || my > windowHeight) {
			searching = false;
			popup = false;
			return true;
		}

		if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && my >= 6 && my <= 22) {
			if (mx >= 154) {
				close();
				return true;
			}
			if (mx >= 142) {
				collapsed = !collapsed;
				popup = false;
				return true;
			}
			if (mx >= 130) {
				popup = !popup && !collapsed;
				return true;
			}
		}

		if (my < 22) {
			dragging = true;
			dragDX = (float) click.x() / scale - guiX;
			dragDY = (float) click.y() / scale - guiY;
			return true;
		}

		if (collapsed) return true;

		if (popup) {
			clickPopup(mx, my, button);
			return true;
		}

		searching = button == GLFW.GLFW_MOUSE_BUTTON_LEFT && mx >= 6 && mx <= 166 && my >= 24 && my <= 41;
		if (my >= 24 && my <= 41) return true;

		int footerY = windowHeight - 20;
		if (my >= footerY && my <= footerY + 17) {
			popup = true;
			return true;
		}

		for (Entry e : entries) {
			if (!fits(e) || e.kind == Kind.HEAD) continue;
			if (my < e.y || my >= e.y + e.height()) continue;

			if (e.kind == Kind.MODULE) {
				if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
					e.module.toggle();
					Sounds.play(Sounds.TOGGLE, 1f, e.module.isEnabled() ? 1.1f : 0.9f);
				} else if (button == GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
					if (!expanded.remove(e.module)) expanded.add(e.module);
					Sounds.play(Sounds.COLLAPSE);
				} else if (button == GLFW.GLFW_MOUSE_BUTTON_MIDDLE) {
					for (Setting<?> s : e.module.getSettings())
						if (s instanceof bind b && b.isModuleKey()) {
							listening = b;
							listeningModule = e.module;
							b.setListening(true);
						}
				}
				return true;
			}

			Setting<?> s = e.setting;
			if (s instanceof bool b) {
				b.toggle();
				Sounds.play(Sounds.CLICK);
			} else if (s instanceof mode<?> md) {
				md.cycle();
				Sounds.play(Sounds.CLICK);
			} else if (s instanceof bind b) {
				listening = b;
				listeningModule = null;
				b.setListening(true);
			} else if (s instanceof number num && button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
				draggingNumber = num;
				numberX = guiX + 16;
				numberW = 148;
				setNumber((float) click.x() / scale);
			}
			return true;
		}
		return true;
	}

	private void clickPopup(float mx, float my, int button) {
		if (button != GLFW.GLFW_MOUSE_BUTTON_LEFT) return;

		int x = POP_X + 8;
		int y = POP_Y + 8 + 14 + 10;

		for (int[] c : chips()) {
			int cx = x + c[0];
			int cy = y + c[1];
			if (mx >= cx && mx <= cx + c[2] && my >= cy && my <= cy + 13) {
				ClickGUI.theme.setModeIndex(c[3]);
				ClickGUI.syncTheme();
				Sounds.play(Sounds.CLICK);
				return;
			}
		}

		List<int[]> cs = chips();
		int rows = cs.isEmpty() ? 0 : cs.get(cs.size() - 1)[1] / 15 + 1;
		y += rows * 15 + 8 + 10;

		if (my >= y && my <= y + 14) {
			ClickGUI.ui.cycle();
			Sounds.play(Sounds.CLICK);
			applyUi();
			return;
		}
		y += 16;
		if (my >= y && my <= y + 14) {
			ClickGUI.language.cycle();
			Sounds.play(Sounds.CLICK);
			return;
		}
		y += 20;
		if (my >= y && my <= y + 14) popup = false;
	}

	private void applyUi() {
		Screen next = ClickGUI.screen();
		if (next != null && next != this) mc.setScreen(next);
	}

	private void setNumber(float rawX) {
		if (draggingNumber == null) return;
		float frac = Math.clamp((rawX - numberX) / numberW, 0f, 1f);
		draggingNumber.setValue(draggingNumber.getMin() + frac * (draggingNumber.getMax() - draggingNumber.getMin()));
	}

	@Override
	public boolean mouseDragged(Click click, double deltaX, double deltaY) {
		if (draggingNumber != null) {
			numberX = guiX + 16;
			setNumber((float) click.x() / scale);
			return true;
		}
		if (dragging) {
			guiX = Math.clamp((float) click.x() / scale - dragDX, 0, Math.max(0, width / scale - W));
			guiY = Math.clamp((float) click.y() / scale - dragDY, 0, Math.max(0, height / scale - 24));
			return true;
		}
		return true;
	}

	@Override
	public boolean mouseReleased(Click click) {
		dragging = false;
		draggingNumber = null;
		return true;
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
		if (collapsed || popup) return true;
		scroll -= (int) (verticalAmount * 18);
		return true;
	}

	@Override
	public boolean keyPressed(KeyInput input) {
		int key = input.key();

		if (listening != null) {
			if (key == GLFW.GLFW_KEY_ESCAPE || key == GLFW.GLFW_KEY_DELETE || key == GLFW.GLFW_KEY_BACKSPACE)
				listening.setKey(GLFW.GLFW_KEY_UNKNOWN);
			else
				listening.setKey(key);
			listening.setListening(false);
			listening = null;
			listeningModule = null;
			return true;
		}

		if (searching) {
			if (key == GLFW.GLFW_KEY_ESCAPE || key == GLFW.GLFW_KEY_ENTER) {
				searching = false;
				return true;
			}
			if (key == GLFW.GLFW_KEY_BACKSPACE && !search.isEmpty()) {
				search = search.substring(0, search.length() - 1);
				scroll = 0;
			}
			return true;
		}

		if (popup && key == GLFW.GLFW_KEY_ESCAPE) {
			popup = false;
			return true;
		}

		return super.keyPressed(input);
	}

	@Override
	public boolean charTyped(CharInput input) {
		if (!searching || !input.isValidChar()) return super.charTyped(input);

		if (search.length() < 24) {
			search += input.asString();
			scroll = 0;
		}
		return true;
	}

	@Override
	public void removed() {
		if (listening != null) listening.setListening(false);
		listening = null;
		listeningModule = null;
		draggingNumber = null;
		dragging = false;
	}

	@Override
	public boolean shouldPause() {
		return false;
	}
}
