package codes.biscuit.skyblockaddons.asm.hooks;

import codes.biscuit.skyblockaddons.SkyblockAddons;
import codes.biscuit.skyblockaddons.asm.utils.ReturnValue;
import codes.biscuit.skyblockaddons.core.Feature;
import codes.biscuit.skyblockaddons.core.Location;
import codes.biscuit.skyblockaddons.core.npc.NPCUtils;
import codes.biscuit.skyblockaddons.features.JerryPresent;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityOtherPlayerMP;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.entity.Entity;
import net.minecraft.entity.item.EntityItemFrame;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

import java.util.List;


public class EntityRendererHook {

    public static void removeEntities(List<Entity> list) {
        SkyblockAddons main = SkyblockAddons.getInstance();
        if (!main.getUtils().isOnSkyblock()) {
            return;
        }

        int keyCode = Minecraft.getMinecraft().gameSettings.keyBindUseItem.getKeyCode();
        boolean isUseKeyDown = false;
        try {
            if (keyCode < 0) {
                isUseKeyDown = Mouse.isButtonDown(Minecraft.getMinecraft().gameSettings.keyBindUseItem.getKeyCode() + 100);
            } else {
                isUseKeyDown = Keyboard.isKeyDown(Minecraft.getMinecraft().gameSettings.keyBindUseItem.getKeyCode());
            }
        } catch (Throwable ex) {
            ex.printStackTrace();
            // Uhh I messed up something with the key detection... fix?
        }

        if (!GuiScreen.isCtrlKeyDown() && isUseKeyDown && main.getConfigValues().isEnabled(Feature.IGNORE_ITEM_FRAME_CLICKS)) {
            list.removeIf(listEntity -> listEntity instanceof EntityItemFrame && !main.getUtils().isInDungeon() &&
                    (((EntityItemFrame)listEntity).getDisplayedItem() != null || Minecraft.getMinecraft().thePlayer.getHeldItem() == null));
        }

        if (main.getUtils().getLocation() != Location.THE_CATACOMBS && main.getConfigValues().isEnabled(Feature.HIDE_PLAYERS_NEAR_NPCS)) {
            list.removeIf(entity -> entity instanceof EntityOtherPlayerMP && !NPCUtils.isNPC(entity) && NPCUtils.isNearNPC(entity));
        }

        if (main.getConfigValues().isEnabled(Feature.EASIER_PRESENT_OPENING)) {
            list.removeIf(entity -> {
                JerryPresent jerryPresent = JerryPresent.getJerryPresents().get(entity.getUniqueID());
                return jerryPresent != null && (!jerryPresent.isForPlayer() || !jerryPresent.getUpperDisplay().equals(entity.getUniqueID()));
            });
        }
    }

    public static void onGetNightVisionBrightness(ReturnValue<Float> returnValue) {
        SkyblockAddons main = SkyblockAddons.getInstance();
        if (main.getConfigValues().isEnabled(Feature.AVOID_BLINKING_NIGHT_VISION)) {
            returnValue.cancel(1.0F);
        }
    }

    public static void onRenderScreenPre() {
        SkyblockAddons.getInstance().getGuiManager().render();
    }
}
