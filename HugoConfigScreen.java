package de.hugo;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.SliderWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.screen.ScreenTexts;
import net.minecraft.text.Text;

import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

public class HugoConfigScreen extends Screen {
    private final Screen parent;
    private final HugoConfig cfg = HugoMod.config;

    public HugoConfigScreen(Screen parent) {
        super(Text.translatable("hugo.config.title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        int left = width / 2 - 155;
        int right = width / 2 + 5;
        int w = 150;
        int y = 40;
        int step = 24;

        // Zeile 1: Mod an/aus | HUD an/aus
        toggle(left, y, w, () -> onOff("hugo.config.enabled", cfg.enabled), () -> cfg.enabled = !cfg.enabled);
        toggle(right, y, w, () -> onOff("hugo.config.hud", cfg.hudEnabled), () -> cfg.hudEnabled = !cfg.hudEnabled);

        // Zeile 2: Command
        y += step + 12;
        TextFieldWidget field = new TextFieldWidget(textRenderer, left, y, 310, 20, Text.translatable("hugo.config.command"));
        field.setMaxLength(100);
        field.setText(cfg.command);
        field.setChangedListener(s -> cfg.command = s);
        addDrawableChild(field);

        // Zeile 3: Modus | Schwelle
        y += step;
        toggle(left, y, w, () -> Text.translatable(cfg.percentMode ? "hugo.config.mode.percent" : "hugo.config.mode.absolute"),
                () -> cfg.percentMode = !cfg.percentMode);
        addDrawableChild(new IntSlider(right, y, w, 1, 100, cfg.threshold,
                v -> cfg.threshold = v,
                v -> Text.translatable("hugo.config.threshold", v + (cfg.percentMode ? "%" : ""))));

        // Zeile 4: Haende
        y += step;
        toggle(left, y, w, () -> onOff("hugo.config.mainhand", cfg.checkMainHand), () -> cfg.checkMainHand = !cfg.checkMainHand);
        toggle(right, y, w, () -> onOff("hugo.config.offhand", cfg.checkOffHand), () -> cfg.checkOffHand = !cfg.checkOffHand);

        // Zeile 5: Ruestung | Cooldown
        y += step;
        toggle(left, y, w, () -> onOff("hugo.config.armor", cfg.checkArmor), () -> cfg.checkArmor = !cfg.checkArmor);
        addDrawableChild(new IntSlider(right, y, w, 1, 60, cfg.cooldownSeconds,
                v -> cfg.cooldownSeconds = v,
                v -> Text.translatable("hugo.config.cooldown", v)));

        // Zeile 6: HUD-Position
        y += step;
        addDrawableChild(new IntSlider(left, y, w, 0, Math.max(1, width - 20), Math.min(cfg.hudX, Math.max(1, width - 20)),
                v -> cfg.hudX = v,
                v -> Text.translatable("hugo.config.hudx", v)));
        addDrawableChild(new IntSlider(right, y, w, 0, Math.max(1, height - 20), Math.min(cfg.hudY, Math.max(1, height - 20)),
                v -> cfg.hudY = v,
                v -> Text.translatable("hugo.config.hudy", v)));

        // Fertig
        addDrawableChild(ButtonWidget.builder(ScreenTexts.DONE, b -> close())
                .dimensions(width / 2 - 100, height - 30, 200, 20).build());
    }

    private static Text onOff(String key, boolean value) {
        return Text.translatable(key).append(": ").append(value ? ScreenTexts.ON : ScreenTexts.OFF);
    }

    private void toggle(int x, int y, int w, Supplier<Text> label, Runnable action) {
        addDrawableChild(ButtonWidget.builder(label.get(), b -> {
            action.run();
            b.setMessage(label.get());
        }).dimensions(x, y, w, 20).build());
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);
        context.drawCenteredTextWithShadow(textRenderer, title, width / 2, 15, 0xFFFFFFFF);
        context.drawTextWithShadow(textRenderer, Text.translatable("hugo.config.command"), width / 2 - 155, 67, 0xFFA0A0A0);
    }

    @Override
    public void close() {
        cfg.save();
        client.setScreen(parent);
    }

    /** Slider fuer ganze Zahlen. */
    private static class IntSlider extends SliderWidget {
        private final int min;
        private final int max;
        private final Consumer<Integer> onChange;
        private final Function<Integer, Text> format;

        IntSlider(int x, int y, int w, int min, int max, int value,
                  Consumer<Integer> onChange, Function<Integer, Text> format) {
            super(x, y, w, 20, Text.empty(), (value - min) / (double) (max - min));
            this.min = min;
            this.max = max;
            this.onChange = onChange;
            this.format = format;
            updateMessage();
        }

        private int current() {
            return min + (int) Math.round(value * (max - min));
        }

        @Override
        protected void updateMessage() {
            if (format != null) {
                setMessage(format.apply(current()));
            }
        }

        @Override
        protected void applyValue() {
            onChange.accept(current());
        }
    }
}
