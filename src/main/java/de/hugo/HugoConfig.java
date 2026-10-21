package de.hugo;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.client.util.InputUtil;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class HugoMod implements ClientModInitializer {
    public static HugoConfig config;

    private static KeyBinding openConfigKey;

    private static final EquipmentSlot[] SLOTS = {
            EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND,
            EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET
    };

    /** Ablauf der Reparatur-Sequenz. Jede Phase wartet 'wait' Ticks und fuehrt dann den naechsten Schritt aus. */
    private enum Phase {
        IDLE,
        SET_SENT,      // sethome gesendet -> als Naechstes: Repair-Command
        REPAIR_SENT,   // Repair gesendet  -> als Naechstes: zurueck zum Home
        RETURN_SENT    // home gesendet    -> als Naechstes: delhome
    }

    private Phase phase = Phase.IDLE;
    private int wait = 0;
    private String pendingRepair = "";
    /** Slot und Item, die repariert werden; die Sequenz wartet, bis dieses Item voll ist. */
    private EquipmentSlot pendingSlot = EquipmentSlot.MAINHAND;
    private Item pendingItem = null;
    private int repairElapsed = 0;

    /** Slots, fuer die der Command schon ausgeloest wurde (bis das Item wieder ueber der Schwelle ist). */
    private final Set<EquipmentSlot> triggered = EnumSet.noneOf(EquipmentSlot.class);
    private final Map<EquipmentSlot, Item> lastItem = new EnumMap<>(EquipmentSlot.class);
    private final List<String> hudLines = new ArrayList<>();

    private long lastRun = 0;
    private int tickCounter = 0;

    @Override
    public void onInitializeClient() {
        config = HugoConfig.load();

        KeyBinding.Category category = KeyBinding.Category.create(Identifier.of("hugo", "main"));
        openConfigKey = KeyBindingHelper.registerKeyBinding(
                new KeyBinding("key.hugo.config", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_H, category));

        ClientTickEvents.END_CLIENT_TICK.register(this::onTick);
        HudRenderCallback.EVENT.register(this::renderHud);
    }

    private void onTick(MinecraftClient client) {
        while (openConfigKey.wasPressed()) {
            client.setScreen(new HugoConfigScreen(client.currentScreen));
        }

        ClientPlayerEntity player = client.player;
        if (player == null || client.world == null) {
            triggered.clear();
            lastItem.clear();
            hudLines.clear();
            phase = Phase.IDLE;
            return;
        }

        // Sequenz laeuft jeden Tick
        runSequence(player);

        // alle 5 Ticks (0,25 s) pruefen reicht voellig
        if (++tickCounter % 5 != 0) {
            return;
        }
        check(player);
    }

    private static void send(ClientPlayerEntity player, String command) {
        if (command != null && !command.isEmpty()) {
            player.networkHandler.sendChatCommand(command);
        }
    }

    private int stepTicks() {
        return Math.max(1, (int) Math.round(config.stepDelaySeconds * 20));
    }

    private void startRepair(ClientPlayerEntity player, String command, EquipmentSlot slot) {
        if (!config.returnHome) {
            send(player, command);
            return;
        }
        // 1) temporaeres Home an der aktuellen Position setzen
        pendingRepair = command;
        pendingSlot = slot;
        pendingItem = player.getEquippedStack(slot).getItem();
        repairElapsed = 0;
        send(player, config.withName(config.setHomeCommand));
        phase = Phase.SET_SENT;
        wait = stepTicks();
    }

    private void runSequence(ClientPlayerEntity player) {
        if (phase == Phase.IDLE) {
            return;
        }
        if (wait-- > 0) {
            return;
        }
        switch (phase) {
            case SET_SENT -> {
                // 2) Repair-Command (teleportiert z.B. zum Reparatur-Home)
                send(player, pendingRepair);
                phase = Phase.REPAIR_SENT;
                wait = stepTicks();
            }
            case REPAIR_SENT -> {
                // 3) warten, bis das Item voll repariert ist (oder Timeout), dann zurueck zur alten Position
                repairElapsed++;
                ItemStack stack = player.getEquippedStack(pendingSlot);
                boolean full = stack.isEmpty()
                        || stack.getItem() != pendingItem
                        || stack.getDamage() == 0;
                boolean timeout = repairElapsed >= Math.max(1, config.repairWaitSeconds) * 20;
                if (full || timeout) {
                    send(player, config.withName(config.tpHomeCommand));
                    phase = Phase.RETURN_SENT;
                    wait = stepTicks() * 3; // etwas laenger warten, bis der Teleport durch ist
                } else {
                    wait = 0; // naechsten Tick wieder pruefen
                }
            }
            case RETURN_SENT -> {
                // 4) temporaeres Home wieder loeschen
                send(player, config.withName(config.delHomeCommand));
                phase = Phase.IDLE;
            }
            default -> phase = Phase.IDLE;
        }
    }

    private boolean slotEnabled(EquipmentSlot slot) {
        return switch (slot) {
            case MAINHAND -> config.checkMainHand;
            case OFFHAND -> config.checkOffHand;
            default -> config.checkArmor;
        };
    }

    private void check(ClientPlayerEntity player) {
        hudLines.clear();
        if (!config.enabled) {
            return;
        }

        String command = config.cleanCommand();
        long now = System.currentTimeMillis();

        for (EquipmentSlot slot : SLOTS) {
            if (!slotEnabled(slot)) {
                triggered.remove(slot);
                continue;
            }

            ItemStack stack = player.getEquippedStack(slot);

            // anderes Item im Slot -> wieder scharf schalten
            Item item = stack.isEmpty() ? null : stack.getItem();
            if (lastItem.get(slot) != item) {
                triggered.remove(slot);
                if (item == null) {
                    lastItem.remove(slot);
                } else {
                    lastItem.put(slot, item);
                }
            }

            if (stack.isEmpty() || !stack.isDamageable()) {
                continue;
            }

            int max = stack.getMaxDamage();
            int left = max - stack.getDamage();
            boolean low = config.percentMode
                    ? (left * 100.0 / max) <= config.threshold
                    : left <= config.threshold;

            if (!low) {
                triggered.remove(slot);
                continue;
            }

            hudLines.add(stack.getName().getString() + ": " + left + "/" + max);

            if (phase == Phase.IDLE
                    && !triggered.contains(slot)
                    && !command.isEmpty()
                    && now - lastRun >= config.cooldownSeconds * 1000L) {
                startRepair(player, command, slot);
                triggered.add(slot);
                lastRun = now;
            }
        }
    }

    private void renderHud(DrawContext context, RenderTickCounter tickCounter) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (!config.enabled || !config.hudEnabled || client.player == null || client.options.hudHidden) {
            return;
        }

        int x = config.hudX;
        int y = config.hudY;
        String cmd = config.cleanCommand();
        String limit = config.percentMode ? config.threshold + "%" : config.threshold + " HP";

        context.drawTextWithShadow(client.textRenderer, "Hugo: /" + cmd + " @ " + limit, x, y, 0xFFFFFFFF);
        if (phase != Phase.IDLE) {
            y += 10;
            context.drawTextWithShadow(client.textRenderer, "Reparatur laeuft...", x, y, 0xFFFFFF55);
        }
        for (String line : hudLines) {
            y += 10;
            context.drawTextWithShadow(client.textRenderer, "! " + line, x, y, 0xFFFF5555);
        }
    }
}
