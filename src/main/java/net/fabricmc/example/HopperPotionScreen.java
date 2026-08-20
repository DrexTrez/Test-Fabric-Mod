package net.fabricmc.example;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.PotionItem;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.HopperBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.network.protocol.game.ServerboundInteractPacket;

import java.util.ArrayList;
import java.util.List;

public class HopperPotionScreen extends Screen {

    private final Player player;
    private HopperBlockEntity targetHopper;
    private final List<PotionEntry> potionEntries = new ArrayList<>();

    // Potion recipes — item yang dibutuhkan per potion
    record PotionEntry(String name, List<ItemStack> ingredients) {}

    public HopperPotionScreen(Player player) {
        super(Component.literal("Hopper AutoFill — Potion List"));
        this.player = player;
        buildPotionList();
        findNearestHopper();
    }

    private void buildPotionList() {
        potionEntries.add(new PotionEntry("Awkward Potion", List.of(
            new ItemStack(Items.NETHER_WART, 1),
            new ItemStack(Items.WATER_BOTTLE, 1)
        )));
        potionEntries.add(new PotionEntry("Healing Potion", List.of(
            new ItemStack(Items.NETHER_WART, 1),
            new ItemStack(Items.GLISTERING_MELON_SLICE, 1),
            new ItemStack(Items.WATER_BOTTLE, 1)
        )));
        potionEntries.add(new PotionEntry("Strength Potion", List.of(
            new ItemStack(Items.NETHER_WART, 1),
            new ItemStack(Items.BLAZE_POWDER, 1),
            new ItemStack(Items.WATER_BOTTLE, 1)
        )));
        potionEntries.add(new PotionEntry("Speed Potion", List.of(
            new ItemStack(Items.NETHER_WART, 1),
            new ItemStack(Items.SUGAR, 1),
            new ItemStack(Items.WATER_BOTTLE, 1)
        )));
        potionEntries.add(new PotionEntry("Fire Resistance Potion", List.of(
            new ItemStack(Items.NETHER_WART, 1),
            new ItemStack(Items.MAGMA_CREAM, 1),
            new ItemStack(Items.WATER_BOTTLE, 1)
        )));
        potionEntries.add(new PotionEntry("Night Vision Potion", List.of(
            new ItemStack(Items.NETHER_WART, 1),
            new ItemStack(Items.GOLDEN_CARROT, 1),
            new ItemStack(Items.WATER_BOTTLE, 1)
        )));
        potionEntries.add(new PotionEntry("Invisibility Potion", List.of(
            new ItemStack(Items.NETHER_WART, 1),
            new ItemStack(Items.GOLDEN_CARROT, 1),
            new ItemStack(Items.FERMENTED_SPIDER_EYE, 1),
            new ItemStack(Items.WATER_BOTTLE, 1)
        )));
        potionEntries.add(new PotionEntry("Poison Potion", List.of(
            new ItemStack(Items.NETHER_WART, 1),
            new ItemStack(Items.SPIDER_EYE, 1),
            new ItemStack(Items.WATER_BOTTLE, 1)
        )));
    }

    private void findNearestHopper() {
        // Cari hopper dalam radius 5 blok dari player
        BlockPos playerPos = player.blockPosition();
        for (int dx = -5; dx <= 5; dx++) {
            for (int dy = -3; dy <= 3; dy++) {
                for (int dz = -5; dz <= 5; dz++) {
                    BlockPos pos = playerPos.offset(dx, dy, dz);
                    BlockEntity be = player.level().getBlockEntity(pos);
                    if (be instanceof HopperBlockEntity hopper) {
                        targetHopper = hopper;
                        return;
                    }
                }
            }
        }
    }

    @Override
    protected void init() {
        int startY = 40;
        int buttonHeight = 20;
        int gap = 4;

        for (int i = 0; i < potionEntries.size(); i++) {
            PotionEntry entry = potionEntries.get(i);
            int y = startY + i * (buttonHeight + gap);
            boolean canCraft = hasIngredients(entry.ingredients());

            addRenderableWidget(Button.builder(
                Component.literal((canCraft ? "§a" : "§c") + entry.name()),
                btn -> onPotionSelected(entry)
            ).bounds(this.width / 2 - 100, y, 200, buttonHeight).build());
        }

        // Close button
        addRenderableWidget(Button.builder(
            Component.literal("Tutup"),
            btn -> this.onClose()
        ).bounds(this.width / 2 - 40, this.height - 30, 80, 20).build());
    }

    private boolean hasIngredients(List<ItemStack> ingredients) {
        for (ItemStack required : ingredients) {
            int totalFound = 0;
            for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
                ItemStack inSlot = player.getInventory().getItem(slot);
                if (ItemStack.isSameItem(inSlot, required)) {
                    totalFound += inSlot.getCount();
                }
            }
            if (totalFound < required.getCount()) return false;
        }
        return true;
    }

    private void onPotionSelected(PotionEntry entry) {
        if (targetHopper == null) {
            // Tidak ada hopper terdekat
            player.sendSystemMessage(Component.literal("§cTidak ada hopper dalam jangkauan 5 blok."));
            return;
        }

        if (!hasIngredients(entry.ingredients())) {
            player.sendSystemMessage(Component.literal("§cItem tidak cukup di inventori."));
            return;
        }

        // Pindahkan setiap ingredient dari inventori player ke hopper
        for (ItemStack required : entry.ingredients()) {
            int remaining = required.getCount();
            for (int slot = 0; slot < player.getInventory().getContainerSize() && remaining > 0; slot++) {
                ItemStack inSlot = player.getInventory().getItem(slot);
                if (ItemStack.isSameItem(inSlot, required)) {
                    int take = Math.min(inSlot.getCount(), remaining);

                    // Cari slot kosong atau stackable di hopper
                    boolean inserted = insertToHopper(new ItemStack(required.getItem(), take));
                    if (inserted) {
                        inSlot.shrink(take);
                        remaining -= take;
                        if (inSlot.isEmpty()) {
                            player.getInventory().setItem(slot, ItemStack.EMPTY);
                        }
                    }
                }
            }
        }

        player.sendSystemMessage(Component.literal(
            "§aIngredients untuk " + entry.name() + " dipindahkan ke hopper."
        ));
        this.onClose();
    }

    private boolean insertToHopper(ItemStack stack) {
        for (int slot = 0; slot < targetHopper.getContainerSize(); slot++) {
            ItemStack hopperSlot = targetHopper.getItem(slot);
            if (hopperSlot.isEmpty()) {
                targetHopper.setItem(slot, stack.copy());
                return true;
            } else if (ItemStack.isSameItem(hopperSlot, stack)) {
                int canAdd = Math.min(hopperSlot.getMaxStackSize() - hopperSlot.getCount(), stack.getCount());
                if (canAdd > 0) {
                    hopperSlot.grow(canAdd);
                    return true;
                }
            }
        }
        return false; // hopper penuh
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
        this.renderBackground(graphics, mouseX, mouseY, delta);
        graphics.drawCenteredString(this.font, this.title, this.width / 2, 15, 0xFFFFFF);

        String hopperStatus = targetHopper != null
            ? "§aHopper ditemukan: " + targetHopper.getBlockPos().toShortString()
            : "§cTidak ada hopper terdekat";
        graphics.drawString(this.font, hopperStatus, 10, 28, 0xFFFFFF);

        super.render(graphics, mouseX, mouseY, delta);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
