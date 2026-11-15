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

package dlindustries.uk.daduncs.module.modules.mace;

import dlindustries.uk.daduncs.event.events.AttackListener;
import dlindustries.uk.daduncs.event.events.PacketSendListener;
import dlindustries.uk.daduncs.event.events.TickListener;
import dlindustries.uk.daduncs.managers.AntibanbyDaduncs;
import dlindustries.uk.daduncs.managers.SlotManager;
import dlindustries.uk.daduncs.module.Category;
import dlindustries.uk.daduncs.module.Module;
import dlindustries.uk.daduncs.module.setting.bool;
import dlindustries.uk.daduncs.module.setting.range;
import dlindustries.uk.daduncs.utils.EncryptedString;
import dlindustries.uk.daduncs.utils.InventoryUtils;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Items;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.c2s.play.ClientTickEndC2SPacket;
import net.minecraft.util.hit.EntityHitResult;

/**
 * Holds a firework rocket (elytra flight) in the main hand. On attack,
 * swaps to the sword slot for exactly the hit, then restores the firework
 * so flight input (holding right click) never drops.
 *
 * The actual attack packet is never sent here: this listener runs at the
 * HEAD of the client's doAttack() (see MinecraftClientMixin#onAttack) and
 * does not cancel the event, so doAttack()'s own body sends the real swing
 * and interact-entity packets immediately after this returns. The slot
 * swap below is what the server sees first, so damage is already
 * calculated off the sword by the time that packet lands.
 */
public final class FireworkSwap extends Module implements AttackListener, TickListener, PacketSendListener {

	private final range delay = new range(EncryptedString.of("Delay"), 0, 20, 1, 1, 1)
			.setDescription(EncryptedString.of("Ticks to wait before restoring the firework slot"));
	private final bool inputSimulation = new bool(EncryptedString.of("Input Simulation"), false)
			.setDescription(EncryptedString.of("Simulates hotbar key presses"));
	private final bool requireFirework = new bool(EncryptedString.of("Require Firework"), true)
			.setDescription(EncryptedString.of("Only swap when the main hand is holding a firework rocket"));

	private static boolean actionSentThisTick = false;
	private static FireworkSwap instance;

	public FireworkSwap() {
		super(EncryptedString.of("Firework Swap"),
				EncryptedString.of("Swaps firework to sword for the hit, then back"),
				-1,
				Category.MACE);
		addSettings(delay, inputSimulation, requireFirework);
		instance = this;
	}

	public static FireworkSwap getInstance() {
		return instance;
	}

	public static boolean isSwapActive() {
		return SlotManager.isOwnerActive(FireworkSwap.class);
	}

	@Override
	public void onEnable() {
		SlotManager.cancel(this);
		actionSentThisTick = false;

		eventManager.add(AttackListener.class, this);
		eventManager.add(TickListener.class, this);
		eventManager.add(PacketSendListener.class, this);
		super.onEnable();
	}

	@Override
	public void onDisable() {
		eventManager.remove(AttackListener.class, this);
		eventManager.remove(TickListener.class, this);
		eventManager.remove(PacketSendListener.class, this);

		SlotManager.cancel(this);
		actionSentThisTick = false;
		super.onDisable();
	}

	@Override
	public void onPacketSend(PacketSendEvent event) {
		if (mc.player == null) return;
		Packet<?> packet = event.packet;

		if (packet instanceof ClientTickEndC2SPacket) {
			actionSentThisTick = false;
		} else if (AntibanbyDaduncs.isActionPacket(packet)) {
			actionSentThisTick = true;
		}
	}

	@Override
	public void onAttack(AttackEvent event) {
		if (mc.player == null || mc.currentScreen != null) return;
		if (!isHoldingFirework()) return;
		if (SlotManager.hasActiveSwaps() && !SlotManager.isOwnerActive(this)) return;

		if (mc.crosshairTarget instanceof EntityHitResult entityHit && entityHit.getEntity() instanceof LivingEntity target) {
			if (!isEntityAllowed(target)) return;
			processSwap();
		}
	}

	private void processSwap() {
		if (actionSentThisTick || AntibanbyDaduncs.isActionSent() || AntibanbyDaduncs.isSlotPending()) {
			return;
		}

		int swordSlot = InventoryUtils.getSwordSlot();
		if (swordSlot == -1) return;

		if (SlotManager.isOwnerActive(this)) return;

		int ticks = delay.getRandomValueInt();
		SlotManager.swapTo(this, swordSlot, inputSimulation.getValue(), ticks, true, null);
	}

	@Override
	public void onTick() {
		actionSentThisTick = false;
	}

	private boolean isHoldingFirework() {
		if (!requireFirework.getValue()) return true;
		return mc.player != null && mc.player.getMainHandStack().isOf(Items.FIREWORK_ROCKET);
	}

	private boolean isEntityAllowed(LivingEntity target) {
		if (target == null || target == mc.player || !target.isAlive()) return false;
		if (target instanceof PlayerEntity player && dlindustries.uk.daduncs.Cl1ent.INSTANCE.getFriendManager().isFriend(player)) {
			return false;
		}
		return true;
	}
}
