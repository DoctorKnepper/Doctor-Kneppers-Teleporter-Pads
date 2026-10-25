package com.teleporterpads;

import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;

/** A thin pad: stand on it to go to the next pad of the same color that you placed. Use a dye on it to change its color. */
public class TeleporterPadBlock extends Block {
	/** How high the pad's top is, in blocks. Players standing on it are at {@code pos.getY() + HEIGHT}. */
	public static final double HEIGHT = 2.0 / 16.0;
	private static final VoxelShape SHAPE = Block.box(0.0, 0.0, 0.0, 16.0, 2.0, 16.0);
	private final DyeColor color;

	public TeleporterPadBlock(final DyeColor color, final BlockBehaviour.Properties properties) {
		super(properties);
		this.color = color;
	}

	public DyeColor getColor() {
		return this.color;
	}

	@Override
	protected VoxelShape getShape(final BlockState state, final BlockGetter level, final BlockPos pos, final CollisionContext context) {
		return SHAPE;
	}

	@Override
	protected boolean useShapeForLightOcclusion(final BlockState state) {
		return true;
	}

	@Override
	public void setPlacedBy(final Level level, final BlockPos pos, final BlockState state, final @Nullable LivingEntity by, final ItemStack itemStack) {
		super.setPlacedBy(level, pos, state, by, itemStack);
		if (level instanceof ServerLevel serverLevel) {
			PadNetwork.get(serverLevel.getServer())
				.put(GlobalPos.of(level.dimension(), pos.immutable()), by instanceof Player player ? player.getUUID() : null, this.color);
		}
	}

	@Override
	protected void affectNeighborsAfterRemoval(final BlockState state, final ServerLevel level, final BlockPos pos, final boolean movedByPiston) {
		super.affectNeighborsAfterRemoval(state, level, pos, movedByPiston);
		PadNetwork.get(level.getServer()).remove(GlobalPos.of(level.dimension(), pos.immutable()));
	}

	@Override
	protected InteractionResult useItemOn(
		final ItemStack stack, final BlockState state, final Level level, final BlockPos pos, final Player player, final InteractionHand hand, final BlockHitResult hitResult
	) {
		DyeColor dye = stack.get(DataComponents.DYE);
		if (dye == null || dye == this.color) {
			return InteractionResult.TRY_WITH_EMPTY_HAND;
		}

		if (level instanceof ServerLevel serverLevel) {
			// Replacing the block forgets the pad, so remember who owns it first.
			PadNetwork network = PadNetwork.get(serverLevel.getServer());
			GlobalPos globalPos = GlobalPos.of(level.dimension(), pos.immutable());
			PadNetwork.Pad old = network.at(globalPos);
			level.setBlockAndUpdate(pos, TeleporterPads.pad(dye).defaultBlockState());
			network.put(globalPos, old != null ? old.owner().orElse(null) : player.getUUID(), dye);
			level.playSound(null, pos, SoundEvents.DYE_USE, SoundSource.BLOCKS, 1.0F, 1.0F);
			stack.consume(1, player);
		}

		return InteractionResult.SUCCESS;
	}

	@Override
	public void animateTick(final BlockState state, final Level level, final BlockPos pos, final RandomSource random) {
		if (random.nextInt(3) == 0) {
			level.addParticle(
				new DustParticleOptions(this.color.getTextureDiffuseColor(), 0.8F),
				pos.getX() + 0.15 + random.nextDouble() * 0.7,
				pos.getY() + HEIGHT + 0.05,
				pos.getZ() + 0.15 + random.nextDouble() * 0.7,
				0.0,
				0.05,
				0.0
			);
		}

		// Now and then a spark drifts up from one of the corner pylons.
		if (random.nextInt(12) == 0) {
			level.addParticle(
				ParticleTypes.END_ROD,
				pos.getX() + (random.nextBoolean() ? 0.1 : 0.9),
				pos.getY() + 0.2,
				pos.getZ() + (random.nextBoolean() ? 0.1 : 0.9),
				0.0,
				0.02 + random.nextDouble() * 0.02,
				0.0
			);
		}
	}
}
