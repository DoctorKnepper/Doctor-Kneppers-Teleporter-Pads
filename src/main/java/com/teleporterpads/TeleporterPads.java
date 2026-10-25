package com.teleporterpads;

import java.util.EnumMap;
import java.util.Map;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.PushReaction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Registers the sixteen Teleporter Pads (one per dye color) and the per-tick teleport check. */
public class TeleporterPads implements ModInitializer {
	public static final String MOD_ID = "teleporterpads";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	private static final Map<DyeColor, TeleporterPadBlock> PADS = new EnumMap<>(DyeColor.class);
	private static final Map<DyeColor, Item> PAD_ITEMS = new EnumMap<>(DyeColor.class);

	static {
		for (DyeColor color : DyeColor.values()) {
			String name = color.getName() + "_teleporter_pad";
			ResourceKey<Block> blockKey = ResourceKey.create(Registries.BLOCK, id(name));
			TeleporterPadBlock block = Registry.register(
				BuiltInRegistries.BLOCK,
				blockKey,
				new TeleporterPadBlock(
					color,
					BlockBehaviour.Properties.of()
						.setId(blockKey)
						.mapColor(color)
						.strength(1.5F, 6.0F)
						.sound(SoundType.METAL)
						.lightLevel(state -> 6)
						.noOcclusion()
						.pushReaction(PushReaction.IMMOVEABLE)
				)
			);
			ResourceKey<Item> itemKey = ResourceKey.create(Registries.ITEM, id(name));
			BlockItem item = new BlockItem(block, new Item.Properties().setId(itemKey).useBlockDescriptionPrefix());
			item.registerBlocks(Item.BY_BLOCK, item);
			PADS.put(color, block);
			PAD_ITEMS.put(color, Registry.register(BuiltInRegistries.ITEM, itemKey, item));
		}
	}

	public static Identifier id(final String path) {
		return Identifier.fromNamespaceAndPath(MOD_ID, path);
	}

	public static TeleporterPadBlock pad(final DyeColor color) {
		return PADS.get(color);
	}

	public static Item padItem(final DyeColor color) {
		return PAD_ITEMS.get(color);
	}

	@Override
	public void onInitialize() {
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.FUNCTIONAL_BLOCKS).register(output -> PAD_ITEMS.values().forEach(output::accept));
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.COLORED_BLOCKS).register(output -> PAD_ITEMS.values().forEach(output::accept));
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			for (ServerPlayer player : server.getPlayerList().getPlayers()) {
				PadTeleporter.tick(player);
			}
		});
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> PadTeleporter.forget(handler.getPlayer()));
		LOGGER.info("Teleporter Pads loaded");
	}
}
