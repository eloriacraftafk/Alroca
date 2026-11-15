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

package dlindustries.uk.daduncs.gui;

import dlindustries.uk.daduncs.Cl1ent;
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

import java.util.*;

import static dlindustries.uk.daduncs.Cl1ent.mc;

/**
 * DefaultGui — Fotoğraftakiyle birebir eşleşen klasik çok-kolonlu ClickGUI.
 * Her Category için bir kolon. Sol tık = toggle, Sağ tık = ayarları aç,
 * Orta tık = tuş bağla. Altta arama kutusu (Ctrl+F veya doğrudan yazma).
 */
public final class DefaultGui extends Screen {

    // ── layout ──────────────────────────────────────────────────────────────
    private static final int COL_W      = 142;  // kolon genişliği
    private static final int COL_GAP    = 5;    // kolonlar arası boşluk
    private static final int HEAD_H     = 30;   // kategori başlık yüksekliği
    private static final int ROW_H      = 22;   // modül satırı yüksekliği
    private static final int SET_H      = 15;   // ayar satırı yüksekliği
    private static final int PAD        = 10;   // yatay iç boşluk
    private static final int BOTTOM_GAP = 44;   // arama kutusu için rezerve
    private static final int SEARCH_W   = 280;  // arama kutusu genişliği

    // ── renkler ─────────────────────────────────────────────────────────────
    private static final int COL_BG     = 0xCC111116;
    private static final int COL_BOR    = 0x22FFFFFF;
    private static final int HEAD_COL   = 0xFFFFFFFF;
    private static final int HEAD_SEP   = 0x1AFFFFFF;
    private static final int MOD_ON     = 0xFFFFFFFF;
    private static final int MOD_OFF    = 0x90FFFFFF;
    private static final int DOT_OFF    = 0x45FFFFFF;
    private static final int HOVER_BG   = 0x14FFFFFF;
    private static final int BTN_COL    = 0x55FFFFFF;
    private static final int BTN_HOV    = 0x22FFFFFF;
    private static final int SET_BG     = 0x10000000;
    private static final int BOOL_OFF   = 0x50FFFFFF;
    private static final int NUM_NAME   = 0xAAFFFFFF;
    private static final int SRCH_BG    = 0xCC111116;
    private static final int SRCH_BOR   = 0x28FFFFFF;
    private static final int SRCH_PH    = 0x40FFFFFF;
    private static final int SRCH_TXT   = 0xE0FFFFFF;

    // ── state ────────────────────────────────────────────────────────────────
    private final Map<Category, List<Module>>  mods     = new EnumMap<>(Category.class);
    private final Map<Category, Set<Module>>   expanded = new EnumMap<>(Category.class);
    private final Map<Category, Integer>       scrolls  = new EnumMap<>(Category.class);

    private String  search       = "";
    private boolean searching    = false;
    private bind    listening    = null;
    private Module  listeningMod = null;
    private number  draggingNum  = null;
    private float   numBarX, numBarW;
    private long    lastFrame;
    private float   scale;

    private static final Category[] COLS = Category.values();

    public DefaultGui() {
        super(Text.literal("DefaultGui"));
        for (Category cat : COLS) {
            mods.put(cat, Cl1ent.INSTANCE.getModuleManager().getModulesInCategory(cat));
            expanded.put(cat, new LinkedHashSet<>());
            scrolls.put(cat, 0);
        }
    }

    // ── lifecycle ────────────────────────────────────────────────────────────

    @Override
    public void onDisplayed() {
        listening    = null;
        listeningMod = null;
        draggingNum  = null;
        searching    = false;
        lastFrame    = System.nanoTime();
    }

    @Override protected void init() {}
    @Override public void renderBackground(DrawContext c, int mx, int my, float dt) {}
    @Override public void renderInGameBackground(DrawContext c) {}
    @Override protected void applyBlur(DrawContext c) {}
    @Override protected void renderDarkening(DrawContext c) {}
    @Override public boolean shouldPause() { return false; }

    // ── geometry yardımcıları ────────────────────────────────────────────────

    private float scale()       { return ClickGUI.guiSize.getValueFloat() / 100f; }
    private int   accent()      { return Theme.accent; }

    private int totalW()        { return COLS.length * COL_W + (COLS.length - 1) * COL_GAP; }
    private int startX(int sw)  { return (sw - totalW()) / 2; }
    private int colX(int sw, int i) { return startX(sw) + i * (COL_W + COL_GAP); }

    private int topY(int sh) {
        int usable = sh - BOTTOM_GAP;
        return Math.max(10, (usable - colH(sh)) / 2);
    }

    private int colH(int sh) {
        // maksimum kullanılabilir yükseklik
        return Math.min(sh - BOTTOM_GAP - 20, 420);
    }

    /** Bir kolonun tam içerik yüksekliği (scroll için). */
    private int contentH(Category cat, String filter) {
        int h = HEAD_H;
        for (Module mod : mods.get(cat)) {
            if (!filter.isEmpty() && !mod.getName().toString().toLowerCase().contains(filter)) continue;
            h += ROW_H;
            if (expanded.get(cat).contains(mod)) {
                for (Setting<?> s : mod.getSettings()) {
                    if (!s.isVisible()) continue;
                    if (s instanceof bind b && b.isModuleKey()) continue;
                    if (s instanceof bool || s instanceof number || s instanceof mode<?> || s instanceof bind)
                        h += SET_H;
                }
            }
        }
        return h;
    }

    private static String numFmt(number n) {
        double inc = n.getIncrement();
        if (inc >= 1)   return String.valueOf((int) Math.round(n.getValue()));
        if (inc >= 0.1) return String.format("%.1f", n.getValue());
        return String.format("%.2f", n.getValue());
    }

    // ── render ───────────────────────────────────────────────────────────────

    @Override
    public void render(DrawContext ctx, int rawMX, int rawMY, float partialTick) {
        long now = System.nanoTime();
        float delta = Math.min(0.1f, (now - lastFrame) / 1.0E9f);
        lastFrame = now;

        scale = scale();
        Theme.ease(delta, 8f);
        Draw.warmup(ctx);

        var m = ctx.getMatrices();
        m.pushMatrix();
        m.scale(scale, scale);

        int sw = (int)(width  / scale);
        int sh = (int)(height / scale);
        int mx = (int)(rawMX  / scale);
        int my = (int)(rawMY  / scale);

        String filter = search.trim().toLowerCase();
        int cH  = colH(sh);
        int topY = topY(sh);

        // ── her kolon ────────────────────────────────────────────────────────
        for (int ci = 0; ci < COLS.length; ci++) {
            Category cat = COLS[ci];
            int cx = colX(sw, ci);
            int scroll = scrolls.get(cat);

            // arka plan
            ctx.fill(cx,       topY,       cx + COL_W, topY + cH, COL_BG);
            ctx.fill(cx,       topY,       cx + 1,     topY + cH, COL_BOR);
            ctx.fill(cx+COL_W-1, topY,    cx + COL_W, topY + cH, COL_BOR);
            ctx.fill(cx,       topY,       cx + COL_W, topY + 1,  COL_BOR);
            ctx.fill(cx,       topY+cH-1,  cx + COL_W, topY + cH, COL_BOR);

            // kategori başlığı
            String catLabel = cat.name.toString();
            // ilk harf büyük, geri kalanı küçük
            String displayed = catLabel.length() > 1
                ? catLabel.charAt(0) + catLabel.substring(1).toLowerCase()
                : catLabel;
            TextRenderer.drawSmall(displayed, ctx,
                cx + PAD, topY + (HEAD_H - 7) / 2, HEAD_COL);

            // başlık ayırıcı
            ctx.fill(cx + 4, topY + HEAD_H - 1, cx + COL_W - 4, topY + HEAD_H, HEAD_SEP);

            // içerik scissor
            ctx.enableScissor(cx + 1, topY + HEAD_H, cx + COL_W - 1, topY + cH - 1);

            int ry = topY + HEAD_H - scroll;

            for (Module mod : mods.get(cat)) {
                if (!filter.isEmpty() && !mod.getName().toString().toLowerCase().contains(filter)) continue;

                boolean on      = mod.isEnabled();
                boolean hovered = !searching
                    && mx >= cx     && mx < cx + COL_W - 20
                    && my >= ry     && my < ry + ROW_H;

                if (hovered)
                    ctx.fill(cx + 1, ry, cx + COL_W - 1, ry + ROW_H, HOVER_BG);

                // toggle noktası (kare)
                int dotClr = on ? accent() : DOT_OFF;
                ctx.fill(cx + PAD, ry + ROW_H/2 - 3, cx + PAD + 6, ry + ROW_H/2 + 3, dotClr);

                // modül adı
                TextRenderer.drawSmall(mod.getName(), ctx,
                    cx + PAD + 10, ry + (ROW_H - 7) / 2,
                    on ? MOD_ON : MOD_OFF);

                // "..." genişlet butonu
                boolean btnHov = !searching
                    && mx >= cx + COL_W - 20 && mx < cx + COL_W - 2
                    && my >= ry              && my < ry + ROW_H;
                if (btnHov)
                    ctx.fill(cx + COL_W - 20, ry, cx + COL_W - 1, ry + ROW_H, BTN_HOV);

                TextRenderer.drawSmall("...", ctx,
                    cx + COL_W - 17, ry + (ROW_H - 7) / 2,
                    btnHov ? HEAD_COL : BTN_COL);

                ry += ROW_H;

                // genişletilmiş ayarlar
                if (expanded.get(cat).contains(mod)) {
                    ctx.fill(cx + 1, ry - 1, cx + COL_W - 1, ry, HEAD_SEP);
                    for (Setting<?> s : mod.getSettings()) {
                        if (!s.isVisible()) continue;
                        if (s instanceof bind b && b.isModuleKey()) continue;
                        if (!(s instanceof bool) && !(s instanceof number) && !(s instanceof mode<?>) && !(s instanceof bind))
                            continue;

                        ctx.fill(cx + 1, ry, cx + COL_W - 1, ry + SET_H, SET_BG);

                        if (s instanceof bool bv) {
                            boolean val = bv.getValue();
                            TextRenderer.drawTiny(bv.getName(), ctx,
                                cx + PAD + 4, ry + (SET_H - 6) / 2,
                                val ? accent() : NUM_NAME);
                            String tag = val ? "ON" : "OFF";
                            TextRenderer.drawTiny(tag, ctx,
                                cx + COL_W - 4 - TextRenderer.tinyWidth(tag),
                                ry + (SET_H - 6) / 2,
                                val ? accent() : BOOL_OFF);

                        } else if (s instanceof number nv) {
                            double frac = (nv.getValue() - nv.getMin()) /
                                          Math.max(1e-9, nv.getMax() - nv.getMin());
                            ctx.fill(cx + PAD + 4, ry + SET_H - 2,
                                     cx + PAD + 4 + (int)((COL_W - PAD*2 - 8) * frac),
                                     ry + SET_H, Theme.fade(accent(), 0.75f));
                            TextRenderer.drawTiny(nv.getName(), ctx,
                                cx + PAD + 4, ry + (SET_H - 6) / 2, NUM_NAME);
                            String tv = numFmt(nv);
                            TextRenderer.drawTiny(tv, ctx,
                                cx + COL_W - 4 - TextRenderer.tinyWidth(tv),
                                ry + (SET_H - 6) / 2, accent());

                        } else if (s instanceof mode<?> mv) {
                            TextRenderer.drawTiny(mv.getName(), ctx,
                                cx + PAD + 4, ry + (SET_H - 6) / 2, NUM_NAME);
                            String tv = mv.getMode().toString().replace('_', ' ');
                            TextRenderer.drawTiny(tv, ctx,
                                cx + COL_W - 4 - TextRenderer.tinyWidth(tv),
                                ry + (SET_H - 6) / 2, accent());

                        } else if (s instanceof bind bnd) {
                            TextRenderer.drawTiny(bnd.getName(), ctx,
                                cx + PAD + 4, ry + (SET_H - 6) / 2, NUM_NAME);
                            String tv = listening == bnd ? "[...]"
                                : "[" + bnd.getKeyName() + "]";
                            TextRenderer.drawTiny(tv, ctx,
                                cx + COL_W - 4 - TextRenderer.tinyWidth(tv),
                                ry + (SET_H - 6) / 2,
                                listening == bnd ? accent() : BOOL_OFF);
                        }

                        ry += SET_H;
                    }
                    ctx.fill(cx + 1, ry, cx + COL_W - 1, ry + 1, HEAD_SEP);
                    ry += 1;
                }
            }

            ctx.disableScissor();
        }

        // ── arama kutusu ─────────────────────────────────────────────────────
        int sbW = SEARCH_W;
        int sbX = sw / 2 - sbW / 2;
        int sbY = topY + cH + 8;

        ctx.fill(sbX, sbY, sbX + sbW, sbY + BOTTOM_GAP - 12, SRCH_BG);
        ctx.fill(sbX,       sbY,                   sbX + sbW, sbY + 1,              SRCH_BOR);
        ctx.fill(sbX,       sbY + BOTTOM_GAP - 13, sbX + sbW, sbY + BOTTOM_GAP - 12, SRCH_BOR);
        ctx.fill(sbX,       sbY + 1,               sbX + 1,   sbY + BOTTOM_GAP - 13, SRCH_BOR);
        ctx.fill(sbX+sbW-1, sbY + 1,               sbX + sbW, sbY + BOTTOM_GAP - 13, SRCH_BOR);

        int textY = sbY + (BOTTOM_GAP - 12) / 2 - 3;
        if (search.isEmpty() && !searching) {
            TextRenderer.drawTiny("Поиск", ctx, sbX + 8, textY, SRCH_PH); // Поиск = arama (fotoğraftaki yazı)
        } else {
            String caret = searching && (System.currentTimeMillis() / 500) % 2 == 0 ? "|" : "";
            TextRenderer.drawTiny(search + caret, ctx, sbX + 8, textY, SRCH_TXT);
        }

        // ── kredi ────────────────────────────────────────────────────────────
        TextRenderer.drawTiny("@alexnumberone & @TersUga", ctx, 6, sh - 10, 0xFFFFFFFF);

        m.popMatrix();
    }

    // ── mouse click ──────────────────────────────────────────────────────────

    @Override
    public boolean mouseClicked(Click click, boolean doubled) {
        float rawX = (float) click.x();
        float rawY = (float) click.y();
        int   btn  = click.button();
        int   mx   = (int)(rawX / scale);
        int   my   = (int)(rawY / scale);

        // tuş dinleme iptal
        if (listening != null) {
            if (btn != GLFW.GLFW_MOUSE_BUTTON_LEFT) listening.setKey(btn);
            listening.setListening(false);
            listening    = null;
            listeningMod = null;
            return true;
        }

        int sw   = (int)(width  / scale);
        int sh   = (int)(height / scale);
        int cH   = colH(sh);
        int topY = topY(sh);

        String filter = search.trim().toLowerCase();

        // arama kutusu tıklama
        int sbW = SEARCH_W;
        int sbX = sw / 2 - sbW / 2;
        int sbY = topY + cH + 8;
        if (mx >= sbX && mx <= sbX + sbW && my >= sbY && my <= sbY + BOTTOM_GAP - 12) {
            searching = true;
            return true;
        }
        searching = false;

        // kolon tıklamaları
        for (int ci = 0; ci < COLS.length; ci++) {
            Category cat = COLS[ci];
            int cx = colX(sw, ci);

            if (mx < cx || mx >= cx + COL_W) continue;
            if (my < topY || my >= topY + cH) continue;

            int scroll = scrolls.get(cat);
            int ry     = topY + HEAD_H - scroll;

            for (Module mod : mods.get(cat)) {
                if (!filter.isEmpty() && !mod.getName().toString().toLowerCase().contains(filter)) continue;

                // "..." butonu
                if (mx >= cx + COL_W - 20 && mx < cx + COL_W - 2
                    && my >= ry && my < ry + ROW_H) {
                    Set<Module> exp = expanded.get(cat);
                    if (!exp.remove(mod)) exp.add(mod);
                    Sounds.play(Sounds.COLLAPSE);
                    return true;
                }

                // modül satırı
                if (my >= ry && my < ry + ROW_H) {
                    if (btn == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
                        mod.toggle();
                        Sounds.play(Sounds.TOGGLE, 1f, mod.isEnabled() ? 1.1f : 0.9f);
                    } else if (btn == GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
                        Set<Module> exp = expanded.get(cat);
                        if (!exp.remove(mod)) exp.add(mod);
                        Sounds.play(Sounds.COLLAPSE);
                    } else if (btn == GLFW.GLFW_MOUSE_BUTTON_MIDDLE) {
                        for (Setting<?> s : mod.getSettings())
                            if (s instanceof bind b && b.isModuleKey()) {
                                listening    = b;
                                listeningMod = mod;
                                b.setListening(true);
                            }
                    }
                    return true;
                }

                ry += ROW_H;

                // genişletilmiş ayar satırları
                if (expanded.get(cat).contains(mod)) {
                    for (Setting<?> s : mod.getSettings()) {
                        if (!s.isVisible()) continue;
                        if (s instanceof bind b && b.isModuleKey()) continue;
                        if (!(s instanceof bool) && !(s instanceof number) && !(s instanceof mode<?>) && !(s instanceof bind))
                            continue;

                        if (my >= ry && my < ry + SET_H) {
                            if (s instanceof bool bv) {
                                bv.toggle();
                                Sounds.play(Sounds.CLICK);
                            } else if (s instanceof mode<?> mv) {
                                mv.cycle();
                                Sounds.play(Sounds.CLICK);
                            } else if (s instanceof bind bnd) {
                                listening    = bnd;
                                listeningMod = null;
                                bnd.setListening(true);
                            } else if (s instanceof number nv && btn == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
                                draggingNum = nv;
                                numBarX     = (cx + PAD + 4) * scale;
                                numBarW     = (COL_W - PAD * 2 - 8) * scale;
                                setNum(rawX);
                            }
                            return true;
                        }
                        ry += SET_H;
                    }
                    ry += 1; // separator
                }
            }
        }
        return true;
    }

    @Override
    public boolean mouseDragged(Click click, double dX, double dY) {
        if (draggingNum != null) {
            setNum((float) click.x());
            return true;
        }
        return super.mouseDragged(click, dX, dY);
    }

    private void setNum(float rawX) {
        if (draggingNum == null) return;
        float frac = Math.clamp((rawX - numBarX) / numBarW, 0f, 1f);
        draggingNum.setValue(draggingNum.getMin() + frac * (draggingNum.getMax() - draggingNum.getMin()));
    }

    @Override
    public boolean mouseReleased(Click click) {
        draggingNum = null;
        return super.mouseReleased(click);
    }

    @Override
    public boolean mouseScrolled(double rawMX, double rawMY, double hAmt, double vAmt) {
        int sw   = (int)(width  / scale);
        int sh   = (int)(height / scale);
        int mx   = (int)(rawMX  / scale);
        int my   = (int)(rawMY  / scale);
        int cH   = colH(sh);
        int topY = topY(sh);

        String filter = search.trim().toLowerCase();

        for (int ci = 0; ci < COLS.length; ci++) {
            Category cat = COLS[ci];
            int cx = colX(sw, ci);
            if (mx < cx || mx >= cx + COL_W) continue;
            if (my < topY || my >= topY + cH) continue;

            int maxScroll = Math.max(0, contentH(cat, filter) - cH);
            int cur       = scrolls.get(cat);
            scrolls.put(cat, (int) Math.clamp(cur - vAmt * 18, 0, maxScroll));
            return true;
        }
        return true;
    }

    // ── keyboard ─────────────────────────────────────────────────────────────

    @Override
    public boolean keyPressed(KeyInput input) {
        int key = input.key();

        if (listening != null) {
            if (key == GLFW.GLFW_KEY_ESCAPE || key == GLFW.GLFW_KEY_DELETE || key == GLFW.GLFW_KEY_BACKSPACE)
                listening.setKey(GLFW.GLFW_KEY_UNKNOWN);
            else
                listening.setKey(key);
            listening.setListening(false);
            listening    = null;
            listeningMod = null;
            return true;
        }

        if (key == GLFW.GLFW_KEY_ESCAPE) {
            if (searching) { searching = false; return true; }
            close();
            return true;
        }

        if (searching && key == GLFW.GLFW_KEY_BACKSPACE && !search.isEmpty()) {
            search = search.substring(0, search.length() - 1);
            return true;
        }

        return super.keyPressed(input);
    }

    @Override
    public boolean charTyped(CharInput input) {
        if (!input.isValidChar()) return false;
        String typed = input.asString();
        if (listening != null) return false;
        // her harf direkt arama başlatır
        if (search.length() < 32) {
            search    += typed;
            searching  = true;
        }
        return true;
    }

    @Override
    public void removed() {
        if (listening != null) listening.setListening(false);
        listening    = null;
        listeningMod = null;
        draggingNum  = null;
    }

    @Override
    public void close() {
        Cl1ent.INSTANCE.getModuleManager().getModule(ClickGUI.class).setEnabledStatus(false);
        ClickGUI.uiSound(Sounds.CLOSE);
        listening    = null;
        listeningMod = null;
        mc.setScreen(Cl1ent.INSTANCE.previousScreen instanceof TitleScreen
            ? null : Cl1ent.INSTANCE.previousScreen);
        Cl1ent.INSTANCE.previousScreen = null;
    }
}
