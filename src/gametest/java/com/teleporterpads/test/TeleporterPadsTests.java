package com.teleporterpads.test;

import com.teleporterpads.PadNetwork;
import com.teleporterpads.PadTeleporter;
import com.teleporterpads.TeleporterPadBlock;
import com.teleporterpads.TeleporterPads;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

@SuppressWarnings("removal")
public class TeleporterPadsTests {
	/** Long enough to charge a pad, with some slack. */
	private static final int WAIT = PadTeleporter.CHARGE_TICKS + 10;

	/** Puts a pad of {@code color} owned by {@code owner} straight into {@code level}, as if that player had placed it. */
	private static GlobalPos pad(final ServerLevel level, final BlockPos pos, final DyeColor color, final UUID owner) {
		level.setBlockAndUpdate(pos.above(), Blocks.AIR.defaultBlockState());
		level.setBlockAndUpdate(pos.above(2), Blocks.AIR.defaultBlockState());
		level.setBlockAndUpdate(pos, TeleporterPads.pad(color).defaultBlockState());
		GlobalPos globalPos = GlobalPos.of(level.dimension(), pos.immutable());
		PadNetwork.get(level.getServer()).put(globalPos, owner, color);
		return globalPos;
	}

	private static void standOn(final ServerPlayer player, final ServerLevel level, final BlockPos pos) {
		player.teleportTo(level, pos.getX() + 0.5, pos.getY() + TeleporterPadBlock.HEIGHT, pos.getZ() + 0.5, java.util.Set.of(), 0.0F, 0.0F, true);
	}

	private static void assertStandingOn(final GameTestHelper helper, final ServerPlayer player, final GlobalPos pad, final String why) {
		Vec3 expected = Vec3.atBottomCenterOf(pad.pos()).add(0.0, TeleporterPadBlock.HEIGHT, 0.0);
		helper.assertTrue(
			player.level().dimension() == pad.dimension() && player.position().distanceTo(expected) < 0.01,
			why + ": expected " + pad + " but player is at " + player.position() + " in " + player.level().dimension()
		);
	}

	private static void finish(final GameTestHelper helper, final ServerPlayer player) {
		helper.getLevel().getServer().getPlayerList().remove(player);
		helper.succeed();
	}

	@GameTest
	public void placingAPadLinksItToThePlacer(final GameTestHelper helper) {
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(TeleporterPads.padItem(DyeColor.LIME)));
		helper.placeAt(player, player.getMainHandItem(), new BlockPos(2, 0, 2), Direction.UP);

		helper.assertBlockPresent(TeleporterPads.pad(DyeColor.LIME), new BlockPos(2, 1, 2));
		GlobalPos pos = GlobalPos.of(helper.getLevel().dimension(), helper.absolutePos(new BlockPos(2, 1, 2)));
		PadNetwork.Pad pad = PadNetwork.get(helper.getLevel().getServer()).at(pos);
		helper.assertTrue(pad != null && pad.color() == DyeColor.LIME, "placed pad should be registered as lime, was " + pad);
		helper.assertTrue(player.getUUID().equals(pad.owner().orElse(null)), "placed pad should belong to the placer");

		helper.destroyBlock(new BlockPos(2, 1, 2));
		helper.assertTrue(PadNetwork.get(helper.getLevel().getServer()).at(pos) == null, "breaking a pad should unregister it");
		finish(helper, player);
	}

	@GameTest(maxTicks = 300)
	public void sameColorPadsLinkFarApartAndDontBounceBack(final GameTestHelper helper) {
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		ServerLevel level = helper.getLevel();
		GlobalPos a = pad(level, helper.absolutePos(new BlockPos(1, 1, 1)), DyeColor.RED, player.getUUID());
		GlobalPos b = pad(level, helper.absolutePos(new BlockPos(1, 1, 1)).offset(5_000, 0, -3_000), DyeColor.RED, player.getUUID());
		// A pad of another color, and someone else's red pad, must never be picked.
		pad(level, helper.absolutePos(new BlockPos(3, 1, 1)), DyeColor.BLUE, player.getUUID());
		pad(level, helper.absolutePos(new BlockPos(3, 1, 3)), DyeColor.RED, UUID.randomUUID());

		standOn(player, level, a.pos());
		helper.runAfterDelay(WAIT, () -> {
			assertStandingOn(helper, player, b, "standing on a red pad should send you to your other red pad");
			// Staying on the arrival pad must not send you straight back.
			helper.runAfterDelay(WAIT * 2, () -> {
				assertStandingOn(helper, player, b, "arriving should not bounce you back");
				finish(helper, player);
			});
		});
	}

	@GameTest(maxTicks = 400)
	public void threePadsFormALoop(final GameTestHelper helper) {
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		ServerLevel level = helper.getLevel();
		GlobalPos a = pad(level, helper.absolutePos(new BlockPos(1, 1, 1)), DyeColor.CYAN, player.getUUID());
		GlobalPos b = pad(level, helper.absolutePos(new BlockPos(4, 1, 1)), DyeColor.CYAN, player.getUUID());
		GlobalPos c = pad(level, helper.absolutePos(new BlockPos(4, 1, 4)), DyeColor.CYAN, player.getUUID());

		standOn(player, level, a.pos());
		helper.runAfterDelay(WAIT, () -> {
			assertStandingOn(helper, player, b, "first hop");
			// Step off and back on.
			player.teleportTo(level, b.pos().getX() + 0.5, b.pos().getY() + 1.5, b.pos().getZ() - 1.5, java.util.Set.of(), 0.0F, 0.0F, true);
			helper.runAfterDelay(2, () -> {
				standOn(player, level, b.pos());
				helper.runAfterDelay(WAIT, () -> {
					assertStandingOn(helper, player, c, "second hop goes on to the third pad");
					finish(helper, player);
				});
			});
		});
	}

	@GameTest(maxTicks = 300)
	public void sneakingStaysPut(final GameTestHelper helper) {
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		ServerLevel level = helper.getLevel();
		GlobalPos a = pad(level, helper.absolutePos(new BlockPos(1, 1, 1)), DyeColor.YELLOW, player.getUUID());
		pad(level, helper.absolutePos(new BlockPos(4, 1, 4)), DyeColor.YELLOW, player.getUUID());

		standOn(player, level, a.pos());
		player.setShiftKeyDown(true);
		helper.runAfterDelay(WAIT * 2, () -> {
			assertStandingOn(helper, player, a, "sneaking on a pad should keep you there");
			finish(helper, player);
		});
	}

	@GameTest(maxTicks = 300)
	public void worksAcrossDimensions(final GameTestHelper helper) {
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		ServerLevel overworld = helper.getLevel();
		ServerLevel nether = overworld.getServer().getLevel(Level.NETHER);
		helper.assertTrue(nether != null, "test server has no nether");
		GlobalPos a = pad(overworld, helper.absolutePos(new BlockPos(1, 1, 1)), DyeColor.PURPLE, player.getUUID());
		// Derived from the test's position, which changes every run, so runs never share this spot.
		BlockPos netherPos = new BlockPos(helper.absolutePos(BlockPos.ZERO).getX() / 8, 100, helper.absolutePos(BlockPos.ZERO).getZ() / 8);
		nether.getChunk(netherPos);
		nether.setBlockAndUpdate(netherPos.below(), Blocks.OBSIDIAN.defaultBlockState());
		GlobalPos b = pad(nether, netherPos, DyeColor.PURPLE, player.getUUID());

		standOn(player, overworld, a.pos());
		helper.runAfterDelay(WAIT, () -> {
			assertStandingOn(helper, player, b, "pads should link across dimensions");
			finish(helper, player);
		});
	}

	@GameTest(maxTicks = 300)
	public void blockedAndBrokenDestinationsAreSkipped(final GameTestHelper helper) {
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		ServerLevel level = helper.getLevel();
		GlobalPos a = pad(level, helper.absolutePos(new BlockPos(1, 1, 1)), DyeColor.ORANGE, player.getUUID());
		GlobalPos broken = pad(level, helper.absolutePos(new BlockPos(3, 1, 1)), DyeColor.ORANGE, player.getUUID());
		GlobalPos blocked = pad(level, helper.absolutePos(new BlockPos(5, 1, 1)), DyeColor.ORANGE, player.getUUID());
		GlobalPos open = pad(level, helper.absolutePos(new BlockPos(5, 1, 4)), DyeColor.ORANGE, player.getUUID());
		// Removed without going through the normal break path (as a command or another mod might do).
		level.setBlock(broken.pos(), Blocks.STONE.defaultBlockState(), 2 | 16);
		PadNetwork.get(level.getServer()).put(broken, player.getUUID(), DyeColor.ORANGE);
		level.setBlockAndUpdate(blocked.pos().above(), Blocks.STONE.defaultBlockState());

		standOn(player, level, a.pos());
		helper.runAfterDelay(WAIT, () -> {
			assertStandingOn(helper, player, open, "broken and blocked pads should be skipped");
			helper.assertTrue(PadNetwork.get(level.getServer()).at(broken) == null, "a pad that is gone should be forgotten");
			finish(helper, player);
		});
	}

	@GameTest
	public void dyeChangesThePadColor(final GameTestHelper helper) {
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		ServerLevel level = helper.getLevel();
		BlockPos abs = helper.absolutePos(new BlockPos(2, 1, 2));
		GlobalPos pos = pad(level, abs, DyeColor.WHITE, player.getUUID());

		player.setGameMode(GameType.SURVIVAL);
		ItemStack dye = new ItemStack(Items.DYE.magenta(), 3);
		player.setItemInHand(InteractionHand.MAIN_HAND, dye);
		player.snapTo(abs.getX() + 0.5, abs.getY() + 1, abs.getZ() - 1.5);
		player.gameMode.useItemOn(player, level, dye, InteractionHand.MAIN_HAND, new BlockHitResult(Vec3.atCenterOf(abs), Direction.UP, abs, false));

		helper.assertBlockPresent(TeleporterPads.pad(DyeColor.MAGENTA), new BlockPos(2, 1, 2));
		PadNetwork.Pad pad = PadNetwork.get(level.getServer()).at(pos);
		helper.assertTrue(pad != null && pad.color() == DyeColor.MAGENTA, "dyed pad should be registered as magenta, was " + pad);
		helper.assertTrue(player.getUUID().equals(pad.owner().orElse(null)), "dyeing should keep the owner");
		helper.assertTrue(player.getMainHandItem().getCount() == 2, "dyeing should use up one dye");
		finish(helper, player);
	}

	@GameTest
	public void recipesExist(final GameTestHelper helper) {
		var recipes = helper.getLevel().getServer().getRecipeManager();
		for (String id : new String[] {"teleporter_pad", "red_teleporter_pad", "black_teleporter_pad"}) {
			helper.assertTrue(
				recipes.byKey(net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.RECIPE, TeleporterPads.id(id))).isPresent(),
				"missing recipe " + id
			);
		}
		helper.succeed();
	}
}
