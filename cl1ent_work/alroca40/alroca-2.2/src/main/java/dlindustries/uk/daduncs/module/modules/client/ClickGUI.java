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

package dlindustries.uk.daduncs.module.modules.client;

import dlindustries.uk.daduncs.Cl1ent;
import dlindustries.uk.daduncs.gui.ClickGui;
import dlindustries.uk.daduncs.gui.hoh.HohGui;
import dlindustries.uk.daduncs.gui.muon.MuonGui;
import dlindustries.uk.daduncs.gui.Theme;
import dlindustries.uk.daduncs.managers.MediaManager;
import dlindustries.uk.daduncs.module.Category;
import dlindustries.uk.daduncs.module.Module;
import dlindustries.uk.daduncs.module.setting.bool;
import dlindustries.uk.daduncs.module.setting.colour;
import dlindustries.uk.daduncs.module.setting.mode;
import dlindustries.uk.daduncs.module.setting.number;
import dlindustries.uk.daduncs.utils.EncryptedString;
import dlindustries.uk.daduncs.utils.Sounds;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.sound.SoundEvent;
import org.lwjgl.glfw.GLFW;

public final class ClickGUI extends Module {
	public static final mode<Language> language = new mode<>(EncryptedString.of("Language"), Language.English, Language.class);

	public static final mode<Palette> theme = new mode<>(EncryptedString.of("Theme"), Palette.Discord, Palette.class);

	public static final bool modern = new bool(EncryptedString.of("Modern"), true)
			.setDescription(EncryptedString.of("Off falls back to vanilla fonts, square corners and item icons"));

	public static final bool hudGlass = new bool(EncryptedString.of("HudGlass"), false);

	public static final bool sidebarGlass = new bool(EncryptedString.of("SidebarGlass"), false);

	public static final bool animatedIcons = new bool(EncryptedString.of("Animated Icons"), false);

	public static final bool randomisedTiming = new bool(EncryptedString.of("Randomised Timing"), true)
			.setDescription(EncryptedString.of("prevents 1.21.2+ TICK_END exploit"));

	public static final int VARIANCE = 10;

	public static final mode<Ui> ui = new mode<>(EncryptedString.of("Ui"), Ui.Hoh, Ui.class);

	public static final number guiSize = new number(EncryptedString.of("Gui Size"), 50, 150, 100, 5);

	public static final bool openCloseSounds = new bool(EncryptedString.of("Open Close Sounds"), true);

	public static final bool uiSounds = new bool(EncryptedString.of("Ui Sounds"), true);

	public static final colour yours = new colour(EncryptedString.of("Your Color"), 0xFF9B6DFF);

	public static final bool sliderGradient = new bool(EncryptedString.of("Slider Gradient"), true);

	public static final bool fade = new bool(EncryptedString.of("Fade"), false);
	public static final number fadeSize = new number(EncryptedString.of("Fade Size"), 4, 40, 18, 1).setVisibility(fade::getValue);
	public static final bool fadeGradient = new bool(EncryptedString.of("Fade Gradient"), true).setVisibility(fade::getValue);
	public static final number gradientSpeed = new number(EncryptedString.of("Gradient Speed"), 0, 5, 1, 0.1);
	public static final bool syncCover = new bool(EncryptedString.of("SyncVisualsToArtCover"), false).setCompact(true);

	public enum Ui {
		Cl1ent, Cl1entDuo, Argon, Muon, Hoh, Default
	}

	public enum Language {
		English, Turkish
	}

	public enum Palette {
			Discord, Cl1ent, Goon, Blood,
		Signal_Blue, Royal_Iris, Lime, Dragonfruit, Mint, Ultra_Violet, Your_Color
	}

	public static void syncTheme() {
		int accent;
		int secondary;

		switch (theme.getMode()) {
			case Cl1ent -> {
				accent = 0xFF9B6DFF;
				secondary = 0xFFFFFFFF;
			}
			case Goon -> {
				accent = 0xFFEFEFEF;
				secondary = 0xFF8A8A8E;
			}
			case Blood -> {
				accent = 0xFFCE3540;
				secondary = 0xFF1A1A1A;
			}
			case Signal_Blue -> {
				accent = 0xFF0057FF;
				secondary = 0xFFF8F7F4;
			}
			case Royal_Iris -> {
				accent = 0xFFFFF275;
				secondary = 0xFF3A0CA3;
			}
			case Lime -> {
				accent = 0xFFB6FF2E;
				secondary = 0xFF23262F;
			}
			case Dragonfruit -> {
				accent = 0xFFFF4696;
				secondary = 0xFF1E1033;
			}
			case Mint -> {
				accent = 0xFF064E3B;
				secondary = 0xFFF8E7C9;
			}
			case Your_Color -> {
				accent = yours.getPicked();
				secondary = 0xFFFFFFFF;
			}
			case Ultra_Violet -> {
				accent = 0xFF6A00F4;
				secondary = 0xFFFFD6A5;
			}
			default -> {
				accent = dlindustries.uk.daduncs.Cl1ent.INSTANCE.getAccentManager().getAccent();
				secondary = 0xFFFFFFFF;
			}
		}

		MediaManager media = dlindustries.uk.daduncs.Cl1ent.INSTANCE.getMediaManager();
		if (syncCover.getValue()) media.start();

		boolean covered = syncCover.getValue() && !media.getTrack().empty() && media.getHot() != 0;
		Theme.target(covered ? Theme.lift(media.getHot()) : accent, covered ? Theme.lift(media.getCold()) : secondary, covered);
	}

	public ClickGUI() {
		super(EncryptedString.of("Cl1ent"),
				EncryptedString.of("Settings for the client"),
				GLFW.GLFW_KEY_RIGHT_SHIFT,
				Category.CLIENT);

		addSettings(language, ui, guiSize, openCloseSounds, uiSounds, theme, yours, sliderGradient, fade, fadeSize, fadeGradient, gradientSpeed, syncCover, modern, hudGlass, sidebarGlass, animatedIcons, randomisedTiming);
	}

	@Override
	public void onEnable() {
		if (mc == null || mc.world == null || mc.player == null || mc.currentScreen instanceof TitleScreen) {
			setEnabledStatus(false);
			return;
		}

		if (!ours(mc.currentScreen))
			Cl1ent.INSTANCE.previousScreen = mc.currentScreen;

		Screen screen = screen();
		if (screen != null) {
			if (!ours(mc.currentScreen))
				uiSound(Sounds.OPEN);
			mc.setScreen(screen);
		} else if (mc.currentScreen instanceof InventoryScreen) {
			Cl1ent.INSTANCE.guiInitialized = true;
		}

		super.onEnable();
	}

	@Override
	public void onDisable() {
		if (mc.currentScreen instanceof ClickGui) {
			Cl1ent.INSTANCE.clickGui.onGuiClose();
		} else if (mc.currentScreen instanceof dlindustries.uk.daduncs.argonsui.ClickGui) {
			Cl1ent.INSTANCE.argonGui.onGuiClose();
		} else if (mc.currentScreen instanceof MuonGui muon) {
			muon.close();
		} else if (mc.currentScreen instanceof dlindustries.uk.daduncs.gui.DefaultGui dfg) {
			dfg.close();
		} else if (mc.currentScreen instanceof HohGui hoh) {
			hoh.close();
		} else if (mc.currentScreen instanceof InventoryScreen) {
			Cl1ent.INSTANCE.guiInitialized = false;
		}

		super.onDisable();
	}

	public static Screen screen() {
		return switch (ui.getMode()) {
			case Argon -> Cl1ent.INSTANCE.argonGui;
			case Muon -> Cl1ent.INSTANCE.muonGui();
			case Hoh -> Cl1ent.INSTANCE.hohGui();
			case Default -> Cl1ent.INSTANCE.defaultGui();
			default -> Cl1ent.INSTANCE.clickGui;
		};
	}

	public static boolean ours(Screen screen) {
		return screen instanceof ClickGui || screen instanceof dlindustries.uk.daduncs.argonsui.ClickGui || screen instanceof MuonGui || screen instanceof HohGui || screen instanceof dlindustries.uk.daduncs.gui.DefaultGui;
	}

	public static void uiSound(SoundEvent sound) {
		if (openCloseSounds.getValue())
			Sounds.play(sound);
	}
}
