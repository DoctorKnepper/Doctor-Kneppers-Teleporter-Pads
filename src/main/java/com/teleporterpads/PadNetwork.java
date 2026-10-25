package com.teleporterpads;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import org.jspecify.annotations.Nullable;

/**
 * Every Teleporter Pad in the world, in the order they were placed. Pads link up when they have
 * the same color and the same owner (the player who placed them); pads placed some other way,
 * such as by a command, have no owner and link with the other unowned pads of their color.
 */
public class PadNetwork extends SavedData {
	public record Pad(GlobalPos pos, Optional<UUID> owner, DyeColor color) {
		static final Codec<Pad> CODEC = RecordCodecBuilder.create(i -> i.group(
				GlobalPos.CODEC.fieldOf("pos").forGetter(Pad::pos),
				UUIDUtil.CODEC.optionalFieldOf("owner").forGetter(Pad::owner),
				DyeColor.CODEC.fieldOf("color").forGetter(Pad::color)
			)
			.apply(i, Pad::new));

		boolean linksWith(final Pad other) {
			return this.color == other.color && this.owner.equals(other.owner);
		}
	}

	public static final Codec<PadNetwork> CODEC = RecordCodecBuilder.create(i -> i.group(
			Pad.CODEC.listOf().optionalFieldOf("pads", List.of()).forGetter(PadNetwork::pads)
		)
		.apply(i, PadNetwork::new));
	public static final SavedDataType<PadNetwork> TYPE = new SavedDataType<>(
		TeleporterPads.id("pads"), PadNetwork::new, CODEC, DataFixTypes.SAVED_DATA_WANDERING_TRADER
	);

	private final Map<GlobalPos, Pad> pads = new LinkedHashMap<>();

	public PadNetwork() {
	}

	private PadNetwork(final List<Pad> pads) {
		for (Pad pad : pads) {
			this.pads.put(pad.pos(), pad);
		}
	}

	public static PadNetwork get(final MinecraftServer server) {
		return server.getDataStorage().computeIfAbsent(TYPE);
	}

	private List<Pad> pads() {
		return new ArrayList<>(this.pads.values());
	}

	public @Nullable Pad at(final GlobalPos pos) {
		return this.pads.get(pos);
	}

	/** Adds a pad, or updates its color and owner. A changed pad moves to the end of its new loop. */
	public Pad put(final GlobalPos pos, final @Nullable UUID owner, final DyeColor color) {
		Pad pad = new Pad(pos, Optional.ofNullable(owner), color);
		if (!pad.equals(this.pads.get(pos))) {
			this.pads.remove(pos);
			this.pads.put(pos, pad);
			this.setDirty();
		}

		return pad;
	}

	public void remove(final GlobalPos pos) {
		if (this.pads.remove(pos) != null) {
			this.setDirty();
		}
	}

	/** All pads linked with {@code pad} (itself included), in placement order. */
	public List<Pad> loopOf(final Pad pad) {
		List<Pad> loop = new ArrayList<>();
		for (Pad other : this.pads.values()) {
			if (other.linksWith(pad)) {
				loop.add(other);
			}
		}

		return loop;
	}

	/** The pads after {@code pad} in its loop, wrapping around, not including {@code pad} itself. */
	public List<Pad> destinationsFrom(final Pad pad) {
		List<Pad> loop = this.loopOf(pad);
		int index = loop.indexOf(pad);
		List<Pad> next = new ArrayList<>(loop.size());
		for (int k = 1; k < loop.size(); k++) {
			next.add(loop.get((index + k) % loop.size()));
		}

		return next;
	}

	public @Nullable UUID ownerOf(final GlobalPos pos) {
		Pad pad = this.pads.get(pos);
		return pad == null ? null : pad.owner().orElse(null);
	}
}
