package com.gregtechceu.gtceu.api.machine.mui;

import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gregtechceu.gtceu.common.mui.GTGuiTextures;
import com.gregtechceu.gtceu.common.mui.GTMuiWidgets;

import brachy.modularui.api.drawable.IDrawable;
import brachy.modularui.drawable.UITexture;
import brachy.modularui.screen.ModularPanel;
import brachy.modularui.screen.UISettings;
import brachy.modularui.theme.ThemeAPI;
import brachy.modularui.utils.Alignment;
import brachy.modularui.widget.ParentWidget;
import brachy.modularui.widgets.SlotGroupWidget;
import brachy.modularui.widgets.layout.Flow;
import lombok.Getter;

public class MachineUIPanel extends ModularPanel<MachineUIPanel> {

    public static final int DEFAULT_CONTENT_WIDTH = 169;
    public static final int DEFAULT_CONTENT_HEIGHT = 77;

    @Getter
    protected final Flow leftConfiguratorPanel, rightConfiguratorPanel;
    @Getter
    protected final ParentWidget<?> mainContents;

    public MachineUIPanel(MetaMachine machine, UISettings settings, boolean attachPlayerInventory,
                          boolean addTitleBar, boolean drawGTLogo, UITexture gtLogoTexture) {
        super(machine.getDefinition().getId().getPath());

        UITexture themeBackground = null;
        if (!machine.getDefinition().getThemeId().equals(ThemeAPI.DEFAULT_ID)) {
            String themeIdStr = machine.getDefinition().getThemeId();
            // ModularUI's theme-baking pipeline can leave a JSON-defined theme's
            // panel background unresolved as IDrawable.NONE rather than a real
            // UITexture (observed reliably for GTCEu's bronze/steel/primitive
            // themes, whose "panel" widget-theme JSON never defines a "panel:hover"
            // variant). Guard the cast instead of trusting it blindly, and fall
            // back to the theme's own known-correct Java texture constant so the
            // GUI still opens correctly even when that bake goes wrong.
            IDrawable rawBackground = ThemeAPI.INSTANCE.getTheme(themeIdStr)
                    .getPanelTheme()
                    .theme().getBackground();
            themeBackground = rawBackground instanceof UITexture texture ? texture :
                    fallbackBackgroundForTheme(themeIdStr);
        }
        if (themeBackground == null) {
            String defaultThemeIdStr = settings.getTheme();
            IDrawable defaultBackground = ThemeAPI.INSTANCE.getTheme(defaultThemeIdStr).getPanelTheme()
                    .theme().getBackground();
            if (defaultBackground instanceof UITexture texture) {
                themeBackground = texture;
            } else {
                // Mirrors the guard above: settings.getTheme() hits the exact same
                // ModularUI theme-baking pipeline, so it can fail to resolve to a
                // real UITexture for the same reason. Previously this branch had
                // no fallback and silently dropped to the generic background below,
                // which is the most likely explanation for steam (bronze/steel)
                // machines still showing no themed background after the fix above -
                // if a steam machine's theme is resolved via panel settings rather
                // than machine.getDefinition().getThemeId(), it would hit this
                // branch and get no safety net.
                com.gregtechceu.gtceu.GTCEu.LOGGER.warn(
                        "[GTDEBUG-PANEL-SETTINGS-THEME] settings.getTheme()={} resolved to non-UITexture background: {} - falling back to known texture",
                        defaultThemeIdStr,
                        defaultBackground == null ? "null" : defaultBackground.getClass().getName());
                themeBackground = fallbackBackgroundForTheme(defaultThemeIdStr);
            }
        }

        if (themeBackground == null) themeBackground = GTGuiTextures.BACKGROUND;
        background(themeBackground);
        leftConfiguratorPanel = Flow.col()
                .coverChildren()
                .rightRel(1.0f)
                .reverseLayout(true)
                .padding(4, 2, 4, 4)
                .bottom(16)
                .crossAxisAlignment(Alignment.CrossAxis.CENTER)
                .childPadding(2)
                .excludeAreaInRecipeViewer()
                .background(themeBackground.getSubArea(0f, 0f, 0.75f, 1.0f))
                .setEnabledIf(f -> !f.getChildren().isEmpty())
                .decoration();

        rightConfiguratorPanel = Flow.col()
                .coverChildren()
                .leftRel(1.0f)
                .reverseLayout(true)
                .padding(2, 4, 4, 4)
                .bottom(16)
                .crossAxisAlignment(Alignment.CrossAxis.CENTER)
                .childPadding(2)
                .excludeAreaInRecipeViewer()
                .background(themeBackground.getSubArea(0.25f, 0f, 1.0f, 1.0f))
                .setEnabledIf(f -> !f.getChildren().isEmpty())
                .decoration();

        Flow panelContents = Flow.col().coverChildren();
        panelContents.margin(4);
        mainContents = new ParentWidget<>().coverChildren(DEFAULT_CONTENT_WIDTH, DEFAULT_CONTENT_HEIGHT);

        panelContents.child(mainContents);

        if (attachPlayerInventory) {
            panelContents.childPadding(2);
            var inventory = SlotGroupWidget.playerInventory((index, slot) -> slot)
                    .marginTop(1)
                    .marginBottom(3);
            panelContents.child(inventory);
        }

        if (addTitleBar) {
            child(GTMuiWidgets.createTitleBar(machine.getDefinition(), 172).decoration());
        }

        if (drawGTLogo) {
            child(new IDrawable.DrawableWidget(gtLogoTexture)
                    .right(7).bottom(7 + (attachPlayerInventory ? 78 : 0)).decoration());
        }

        child(leftConfiguratorPanel);
        child(rightConfiguratorPanel);
        child(panelContents);
        coverChildren();
    }

    /**
     * Known-correct fallback panel background texture for a given GTCEu theme id,
     * used when the theme's baked ModularUI panel background failed to resolve
     * to a real UITexture. See the guard in the constructor above for context.
     */
    public static UITexture fallbackBackgroundForTheme(String themeId) {
        UITexture base = switch (themeId) {
            case "gtceu:bronze" -> GTGuiTextures.BACKGROUND_BRONZE;
            case "gtceu:steel" -> GTGuiTextures.BACKGROUND_STEEL;
            case "gtceu:primitive" -> GTGuiTextures.BACKGROUND_PRIMITIVE;
            case "gtceu:cover" -> GTGuiTextures.BACKGROUND_POPUP;
            default -> GTGuiTextures.BACKGROUND;
        };
        // The texture asset itself is fine (verified against the active resource
        // pack's PNG directly). What was actually invisible is the GL tint: these
        // textures use ColorType.DEFAULT, so at draw time they get multiplied by
        // whatever color the (already-corrupted) baked "panel" widget theme
        // resolves to - which bakes out wrong/empty for bronze/steel/primitive
        // alongside the "background":"none" corruption. withColorOverride forces
        // an opaque white (no-op) tint so the real texture colors always show,
        // independent of that broken theme color.
        return base.withColorOverride(0xFFFFFFFF);
    }
}
