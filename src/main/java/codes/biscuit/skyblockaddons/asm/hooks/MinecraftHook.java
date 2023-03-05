package codes.biscuit.skyblockaddons.asm.hooks;

import codes.biscuit.skyblockaddons.SkyblockAddons;
import codes.biscuit.skyblockaddons.asm.utils.ReturnValue;
import codes.biscuit.skyblockaddons.core.Feature;
import codes.biscuit.skyblockaddons.core.Location;
import codes.biscuit.skyblockaddons.core.Translations;
import codes.biscuit.skyblockaddons.features.cooldowns.CooldownManager;
import codes.biscuit.skyblockaddons.utils.InventoryUtils;
import codes.biscuit.skyblockaddons.utils.ItemUtils;
import lombok.Getter;
import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.entity.Entity;
import net.minecraft.entity.item.EntityItemFrame;
import net.minecraft.init.Blocks;
import net.minecraft.inventory.ContainerPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.BlockPos;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.Vec3;

import java.util.*;

public class MinecraftHook {

    @Getter private static long lastLockedSlotItemChange = -1;

    private static final Set<Location> DEEP_CAVERNS_LOCATIONS = EnumSet.of(Location.DEEP_CAVERNS, Location.GUNPOWDER_MINES,
            Location.LAPIS_QUARRY, Location.PIGMAN_DEN, Location.SLIMEHILL, Location.DIAMOND_RESERVE, Location.OBSIDIAN_SANCTUARY);

    private static final Set<Location> DWARVEN_MINES_LOCATIONS = EnumSet.of(Location.DWARVEN_MINES, Location.THE_LIFT, Location.DWARVEN_VILLAGE,
            Location.GATES_TO_THE_MINES, Location.THE_FORGE, Location.FORGE_BASIN, Location.LAVA_SPRINGS, Location.PALACE_BRIDGE,
            Location.ROYAL_PALACE, Location.ARISTOCRAT_PASSAGE, Location.HANGING_TERRACE, Location.CLIFFSIDE_VEINS,
            Location.RAMPARTS_QUARRY, Location.DIVANS_GATEWAY, Location.FAR_RESERVE, Location.GOBLIN_BURROWS, Location.UPPER_MINES,
            Location.MINERS_GUILD, Location.GREAT_ICE_WALL, Location.THE_MIST, Location.CC_MINECARTS_CO, Location.GRAND_LIBRARY,
            Location.HANGING_COURT, Location.ROYAL_MINES);

    // The room with the puzzle is made of wood that you have to mine
    private static final AxisAlignedBB DWARVEN_PUZZLE_ROOM = new AxisAlignedBB(171, 195, 125, 192, 196, 146);

    private static final Set<Block> DEEP_CAVERNS_MINEABLE_BLOCKS = new HashSet<>(Arrays.asList(Blocks.coal_ore, Blocks.iron_ore, Blocks.gold_ore, Blocks.redstone_ore, Blocks.emerald_ore,
            Blocks.diamond_ore, Blocks.diamond_block, Blocks.obsidian, Blocks.lapis_ore, Blocks.lit_redstone_ore));

    private static final Set<Block> NETHER_MINEABLE_BLOCKS = new HashSet<>(Arrays.asList(Blocks.glowstone, Blocks.quartz_ore, Blocks.nether_wart));

    // TODO: Make this less computationally expensive
    // More specifically, should be cyan hardened clay, grey/light blue wool, dark prismarine, prismarine brick, prismarine, polished diorite
    private static final Set<String> DWARVEN_MINEABLE_BLOCKS = new HashSet<>(Arrays.asList("minecraft:prismarine0",
            "minecraft:prismarine1", "minecraft:prismarine2", "minecraft:stone4", "minecraft:wool3", "minecraft:wool7",
            "minecraft:stained_hardened_clay9"));

    private static final Set<Location> PARK_LOCATIONS = EnumSet.of(Location.BIRCH_PARK, Location.SPRUCE_WOODS, Location.SAVANNA_WOODLAND, Location.DARK_THICKET,
            Location.JUNGLE_ISLAND, Location.MELODYS_PLATEAU);

    private static final Set<Block> LOGS = new HashSet<>(Arrays.asList(Blocks.log, Blocks.log2));

    private static long lastStemMessage = -1;
    private static long lastUnmineableMessage = -1;
    public static BlockPos prevClickBlock = new BlockPos(-1, -1, -1);
    public static long startMineTime = Long.MAX_VALUE;

    public static LinkedHashMap<BlockPos, Long> recentlyClickedBlocks = new LinkedHashMap<>();

    public static void rightClickMouse(ReturnValue<?> returnValue) {
        SkyblockAddons main = SkyblockAddons.getInstance();
        if (main.getUtils().isOnSkyblock()) {
            Minecraft mc = Minecraft.getMinecraft();
            if (mc.objectMouseOver != null && mc.objectMouseOver.typeOfHit == MovingObjectPosition.MovingObjectType.ENTITY) {
                Entity entityIn = mc.objectMouseOver.entityHit;

                if (main.getConfigValues().isEnabled(Feature.LOCK_SLOTS) && entityIn instanceof EntityItemFrame && ((EntityItemFrame)entityIn).getDisplayedItem() == null) {
                    int slot = mc.thePlayer.inventory.currentItem + 36;
                    if (main.getConfigValues().getLockedSlots().contains(slot) && slot >= 9) {
                        main.getUtils().playLoudSound("note.bass", 0.5);
                        main.getUtils().sendMessage(main.getConfigValues().getRestrictedColor(Feature.DROP_CONFIRMATION) + Translations.getMessage("messages.slotLocked"));
                        returnValue.cancel();
                    }
                }
            }
        }
    }

    public static void updatedCurrentItem() {
        Minecraft mc = Minecraft.getMinecraft();
        SkyblockAddons main = SkyblockAddons.getInstance();

        if (main.getConfigValues().isEnabled(Feature.LOCK_SLOTS) && (main.getUtils().isOnSkyblock() || main.getPlayerListener().aboutToJoinSkyblockServer())) {
            int slot = mc.thePlayer.inventory.currentItem + 36;
            if (main.getConfigValues().isEnabled(Feature.LOCK_SLOTS) && main.getConfigValues().getLockedSlots().contains(slot)
                    && (slot >= 9 || mc.thePlayer.openContainer instanceof ContainerPlayer && slot >= 5)) {

                MinecraftHook.lastLockedSlotItemChange = System.currentTimeMillis();
            }

            ItemStack heldItemStack = mc.thePlayer.getHeldItem();
            if (heldItemStack != null && main.getConfigValues().isEnabled(Feature.STOP_DROPPING_SELLING_RARE_ITEMS) && !main.getUtils().isInDungeon()
                    && !main.getUtils().getItemDropChecker().canDropItem(heldItemStack, true, false)) {

                MinecraftHook.lastLockedSlotItemChange = System.currentTimeMillis();
            }
        }
    }

    public static void onClickMouse(ReturnValue<?> returnValue) {

        Minecraft mc = Minecraft.getMinecraft();
        if (mc.objectMouseOver == null || mc.objectMouseOver.typeOfHit != MovingObjectPosition.MovingObjectType.BLOCK) {
            return;
        }
        BlockPos blockPos = mc.objectMouseOver.getBlockPos();
        if (mc.theWorld.getBlockState(blockPos).getBlock().getMaterial() == Material.air) {
            return;
        }

        SkyblockAddons main = SkyblockAddons.getInstance();
        EntityPlayerSP p = mc.thePlayer;
        ItemStack heldItem = p.getHeldItem();
        if (heldItem != null && main.getUtils().isOnSkyblock()) {
            // TODO: tmp fix for Dwarven mines. Problem is that block doesn't contain metadata
            IBlockState state =  mc.theWorld.getBlockState(blockPos);
            Block block = state.getBlock();
            String id = "" + Block.blockRegistry.getNameForObject(block) + block.getMetaFromState(state);
            long now = System.currentTimeMillis();

            if (main.getConfigValues().isEnabled(Feature.AVOID_BREAKING_STEMS) && (block.equals(Blocks.melon_stem) || block.equals(Blocks.pumpkin_stem))) {
                if (main.getConfigValues().isEnabled(Feature.ENABLE_MESSAGE_WHEN_BREAKING_STEMS) && now - lastStemMessage > 20000) {
                    lastStemMessage = now;
                    main.getUtils().sendMessage(main.getConfigValues().getRestrictedColor(Feature.AVOID_BREAKING_STEMS) + Translations.getMessage("messages.cancelledStemBreak"));
                }
                returnValue.cancel();
            } else if (main.getConfigValues().isEnabled(Feature.AVOID_BREAKING_BOTTOM_SUGAR_CANE) && block.equals(Blocks.reeds) && main.getUtils().getLocation().equals(Location.ISLAND)
                    && !mc.theWorld.getBlockState(blockPos.down()).getBlock().equals(Blocks.reeds)) {
                if (main.getConfigValues().isEnabled(Feature.ENABLE_MESSAGE_WHEN_BREAKING_STEMS) && now - lastStemMessage > 20000) {
                    lastStemMessage = now;
                    main.getUtils().sendMessage(main.getConfigValues().getRestrictedColor(Feature.AVOID_BREAKING_STEMS) + Translations.getMessage("messages.cancelledStemBreak"));
                }
                returnValue.cancel();
            } else if (main.getConfigValues().isEnabled(Feature.ONLY_MINE_ORES_DEEP_CAVERNS) && DEEP_CAVERNS_LOCATIONS.contains(main.getUtils().getLocation())
                    && ItemUtils.isMiningTool(heldItem) && !DEEP_CAVERNS_MINEABLE_BLOCKS.contains(block)) {
                if (main.getConfigValues().isEnabled(Feature.ENABLE_MESSAGE_WHEN_MINING_DEEP_CAVERNS) && now - lastUnmineableMessage > 60000) {
                    lastUnmineableMessage = now;
                    main.getUtils().sendMessage(main.getConfigValues().getRestrictedColor(Feature.ONLY_MINE_ORES_DEEP_CAVERNS) + Translations.getMessage("messages.cancelledBlockBreak"));
                }
                returnValue.cancel();
            } else if (main.getConfigValues().isEnabled(Feature.ONLY_MINE_VALUABLES_NETHER) && Location.BLAZING_FORTRESS.equals(main.getUtils().getLocation()) &&
                    ItemUtils.isMiningTool(heldItem) && !NETHER_MINEABLE_BLOCKS.contains(block)) {
                if (main.getConfigValues().isEnabled(Feature.ENABLE_MESSAGE_WHEN_MINING_NETHER) && now - lastUnmineableMessage > 60000) {
                    lastUnmineableMessage = now;
                    main.getUtils().sendMessage(main.getConfigValues().getRestrictedColor(Feature.ONLY_MINE_VALUABLES_NETHER) + Translations.getMessage("messages.cancelledBlockBreak"));
                }
                returnValue.cancel();
            } else if (main.getConfigValues().isEnabled(Feature.ONLY_BREAK_LOGS_PARK) && PARK_LOCATIONS.contains(main.getUtils().getLocation())
                    && main.getUtils().isAxe(heldItem.getItem()) && !LOGS.contains(block)) {
                if (main.getConfigValues().isEnabled(Feature.ENABLE_MESSAGE_WHEN_BREAKING_PARK) && now - lastUnmineableMessage > 60000) {
                    lastUnmineableMessage = now;
                    main.getUtils().sendMessage(main.getConfigValues().getRestrictedColor(Feature.ONLY_BREAK_LOGS_PARK) + Translations.getMessage("messages.cancelledBlockBreak"));
                }
                returnValue.cancel();
            } else if (main.getConfigValues().isEnabled(Feature.ONLY_MINE_ORES_DWARVEN_MINES) && DWARVEN_MINES_LOCATIONS.contains(main.getUtils().getLocation())
                    && ItemUtils.isMiningTool(heldItem) && (!DWARVEN_MINEABLE_BLOCKS.contains(id) && !DEEP_CAVERNS_MINEABLE_BLOCKS.contains(block)) && !block.equals(Blocks.dragon_egg) &&
                    !(DWARVEN_PUZZLE_ROOM.isVecInside(new Vec3(blockPos.getX() + .5, blockPos.getY() + .5, blockPos.getZ() + .5)))) {
                if (main.getConfigValues().isEnabled(Feature.ENABLE_MESSAGE_WHEN_BREAKING_PARK) && now - lastUnmineableMessage > 60000) {
                    lastUnmineableMessage = now;
                    main.getUtils().sendMessage(main.getConfigValues().getRestrictedColor(Feature.ONLY_MINE_ORES_DWARVEN_MINES) + Translations.getMessage("messages.cancelledBlockBreak"));
                }
                returnValue.cancel();
            } else if (main.getConfigValues().isEnabled(Feature.JUNGLE_AXE_COOLDOWN)) {
                if ((block.equals(Blocks.log) || block.equals(Blocks.log2))) {
                    String itemId = ItemUtils.getSkyblockItemID(p.getHeldItem());
                    if (itemId != null) {
                        final boolean holdingJungleAxeOnCooldown = itemId.equals(InventoryUtils.JUNGLE_AXE_ID) && CooldownManager.isOnCooldown(itemId);
                        final boolean holdingTreecapitatorOnCooldown = itemId.equals(InventoryUtils.TREECAPITATOR_ID) && CooldownManager.isOnCooldown(itemId);

                        if (holdingJungleAxeOnCooldown || holdingTreecapitatorOnCooldown) {
                            returnValue.cancel();
                        }
                    }
                }
            }
        }

        if (!returnValue.isCancelled() && !prevClickBlock.equals(blockPos)) {
            startMineTime = System.currentTimeMillis();
        }
        prevClickBlock = blockPos;
        if (!returnValue.isCancelled()) {
            recentlyClickedBlocks.put(blockPos, System.currentTimeMillis());
        }
    }

    public static void onSendClickBlockToController(boolean leftClick, ReturnValue<?> returnValue) {
        // If we aren't trying to break anything, don't change vanilla behavior (was causing false positive chat messages)
        if (!leftClick) {
            return;
        }
        onClickMouse(returnValue);
        // Canceling this is tricky. Not only do we have to reset block removing, but also reset the position we are breaking
        // This is because we want playerController.onClick to be called when they go back to that block
        // It's also important to resetBlockRemoving before changing current block, since then we'd be sending the server inaccurate info that could trigger wdr
        // This mirrors PlayerControllerMP.clickBlock(), which sends an ABORT_DESTROY message, before calling onPlayerDestroyBlock, which changes "currentBlock"
        if (returnValue.isCancelled()) {
            Minecraft.getMinecraft().playerController.resetBlockRemoving();
            Minecraft.getMinecraft().playerController.currentBlock = new BlockPos(-1, -1, -1);
        }
    }

}
