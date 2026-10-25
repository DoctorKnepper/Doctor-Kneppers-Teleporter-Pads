package com.teleporterpads;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Teleports players who stand on a pad. Standing still on a pad for {@link #CHARGE_TICKS} sends you
 * to the next linked pad. After arriving you have to step off the pad before it will send you on again,
 * so you don't bounce straight back. Sneaking on a pad keeps you there.
 */
public final class PadTeleporter {
	public static final int CHARGE_TICKS = 30;

	private static final class Standing {
		final GlobalPos pad;
		int ticks;
		/** Just arrived here (or nothing to go to): wait until the player steps off. */
		boolean waitForStepOff;

		Standing(final GlobalPos pad, final boolean waitForStepOff) {
			this.pad = pad;
			this.waitForStepOff = waitForStepOff;
		}
	}

	private static final Map<UUID, Standing> STANDING = new HashMap<>();

	private PadTeleporter() {
	}

	public static void forget(final ServerPlayer player) {
		STANDING.remove(player.getUUID());
	}

	public static void tick(final ServerPlayer player) {
		ServerLevel level = player.level();
		BlockPos feet = player.blockPosition();
		BlockState state = level.getBlockState(feet);
		if (!(state.getBlock() instanceof TeleporterPadBlock block) || player.isSpectator() || !player.isAlive() || player.isPassenger()) {
			STANDING.remove(player.getUUID());
			return;
		}

		GlobalPos here = GlobalPos.of(level.dimension(), feet.immutable());
		Standing standing = STANDING.get(player.getUUID());
		if (standing == null || !standing.pad.equals(here)) {
			standing = new Standing(here, false);
			STANDING.put(player.getUUID(), standing);
		}

		if (standing.waitForStepOff || player.isShiftKeyDown()) {
			standing.ticks = 0;
			return;
		}

		DyeColor color = block.getColor();
		standing.ticks++;
		chargeEffects(level, player, color, standing.ticks);
		if (standing.ticks < CHARGE_TICKS) {
			return;
		}

		standing.ticks = 0;
		standing.waitForStepOff = true;
		PadNetwork network = PadNetwork.get(level.getServer());
		PadNetwork.Pad from = network.at(here);
		if (from == null || from.color() != color) {
			// Placed without a player (a command, or another mod): it joins the unowned pads of its color.
			from = network.put(here, from != null ? from.owner().orElse(null) : null, color);
		}

		List<PadNetwork.Pad> loop = network.loopOf(from);
		boolean blocked = false;
		for (PadNetwork.Pad to : network.destinationsFrom(from)) {
			ServerLevel targetLevel = level.getServer().getLevel(to.pos().dimension());
			if (targetLevel == null || !isPadStill(targetLevel, to)) {
				network.remove(to.pos());
				continue;
			}

			Vec3 arrival = Vec3.atBottomCenterOf(to.pos().pos()).add(0.0, TeleporterPadBlock.HEIGHT, 0.0);
			AABB box = player.getBoundingBox().move(arrival.subtract(player.position())).deflate(1.0E-3);
			if (!targetLevel.noCollision(player, box)) {
				blocked = true;
				continue;
			}

			teleport(player, level, targetLevel, arrival, color);
			int number = network.loopOf(to).indexOf(to) + 1;
			player.sendOverlayMessage(Component.translatable(
				"message.teleporterpads.arrived", colorName(color), number, network.loopOf(to).size()
			).withColor(textColor(color)));
			STANDING.put(player.getUUID(), new Standing(to.pos(), true));
			return;
		}

		player.sendOverlayMessage(
			(blocked
				? Component.translatable("message.teleporterpads.blocked", colorName(color))
				: Component.translatable(loop.size() <= 1 ? "message.teleporterpads.no_link" : "message.teleporterpads.all_gone", colorName(color)))
				.withStyle(ChatFormatting.GRAY)
		);
	}

	private static boolean isPadStill(final ServerLevel level, final PadNetwork.Pad pad) {
		BlockPos pos = pad.pos().pos();
		// Load the destination (it may be far away or in another dimension).
		level.getChunk(pos.getX() >> 4, pos.getZ() >> 4, ChunkStatus.FULL, true);
		return level.getBlockState(pos).getBlock() instanceof TeleporterPadBlock block && block.getColor() == pad.color();
	}

	private static void teleport(final ServerPlayer player, final ServerLevel fromLevel, final ServerLevel toLevel, final Vec3 arrival, final DyeColor color) {
		Vec3 departure = player.position();
		fromLevel.playSound(null, departure.x, departure.y, departure.z, SoundEvents.PLAYER_TELEPORT, SoundSource.PLAYERS, 1.0F, 1.0F);
		fromLevel.sendParticles(ParticleTypes.REVERSE_PORTAL, departure.x, departure.y + 1.0, departure.z, 40, 0.3, 0.8, 0.3, 0.05);
		player.teleportTo(toLevel, arrival.x, arrival.y, arrival.z, Set.of(), player.getYRot(), player.getXRot(), true);
		player.resetFallDistance();
		toLevel.playSound(null, arrival.x, arrival.y, arrival.z, SoundEvents.PLAYER_TELEPORT, SoundSource.PLAYERS, 1.0F, 1.2F);
		toLevel.sendParticles(dust(color, 1.2F), arrival.x, arrival.y + 1.0, arrival.z, 40, 0.3, 0.8, 0.3, 0.0);
	}

	/** A swirl of the pad's color that grows while you wait. */
	private static void chargeEffects(final ServerLevel level, final ServerPlayer player, final DyeColor color, final int ticks) {
		if (ticks == 1) {
			level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BEACON_ACTIVATE, SoundSource.BLOCKS, 0.4F, 1.6F);
		}

		double progress = (double)ticks / CHARGE_TICKS;
		double angle = ticks * 0.6;
		for (int i = 0; i < 2; i++) {
			double a = angle + i * Math.PI;
			level.sendParticles(
				dust(color, 1.0F),
				player.getX() + Math.cos(a) * 0.6,
				player.getY() + progress * 2.0,
				player.getZ() + Math.sin(a) * 0.6,
				1, 0.0, 0.0, 0.0, 0.0
			);
		}

		if (ticks % 4 == 0) {
			level.sendParticles(ParticleTypes.ELECTRIC_SPARK, player.getX(), player.getY() + 0.2, player.getZ(), 3, 0.35, 0.05, 0.35, 0.1);
		}
	}

	private static DustParticleOptions dust(final DyeColor color, final float scale) {
		return new DustParticleOptions(color.getTextureDiffuseColor(), scale);
	}

	/** The dye's text color, except that black text would be unreadable over the game. */
	private static int textColor(final DyeColor color) {
		return color == DyeColor.BLACK ? 0x8A8A8A : color.getFireworkColor();
	}

	private static Component colorName(final DyeColor color) {
		return Component.translatable("color.minecraft." + color.getName());
	}
}
