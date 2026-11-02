package com.gregtechceu.gtceu.api.recipe.gui;

import com.gregtechceu.gtceu.GTCEu;
import com.gregtechceu.gtceu.api.GTValues;
import com.gregtechceu.gtceu.api.capability.recipe.IO;
import com.gregtechceu.gtceu.api.capability.recipe.RecipeCapability;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gregtechceu.gtceu.api.recipe.GTRecipeType;
import com.gregtechceu.gtceu.api.recipe.OverclockingLogic;
import com.gregtechceu.gtceu.api.recipe.RecipeHelper;
import com.gregtechceu.gtceu.api.recipe.content.Content;
import com.gregtechceu.gtceu.api.recipe.ingredient.EnergyStack;
import com.gregtechceu.gtceu.common.data.GTRecipeTypes;
import com.gregtechceu.gtceu.common.machine.multiblock.electric.FusionReactorMachine;
import com.gregtechceu.gtceu.utils.FormattingUtil;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraftforge.fml.loading.FMLLoader;

import brachy.modularui.api.drawable.IDrawable;
import brachy.modularui.api.drawable.Text;
import brachy.modularui.api.widget.IWidget;
import brachy.modularui.utils.Alignment;
import brachy.modularui.utils.MouseData;
import brachy.modularui.value.DoubleValue;
import brachy.modularui.widget.ParentWidget;
import brachy.modularui.widget.WidgetTree;
import brachy.modularui.widgets.ButtonWidget;
import brachy.modularui.widgets.layout.Flow;
import org.lwjgl.glfw.GLFW;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;

import static com.gregtechceu.gtceu.api.GTValues.ULV;
import static com.gregtechceu.gtceu.api.GTValues.V;

public class GTRecipeViewerWidget extends ParentWidget<GTRecipeViewerWidget> {

    private final GTRecipe baseRecipe;

    private GTRecipe modifiedRecipe;

    private final GTRecipeType recipeType;
    private final GTRecipeTypeUILayout uiLayout;

    public final Flow textComponents = Flow.col()
            .widthRel(1f)
            .coverChildrenHeight()
            .childPadding(1)
            .crossAxisAlignment(Alignment.CrossAxis.START)
            .collapseDisabledChildren();

    public final Flow inputColumn = Flow.col().coverChildren().crossAxisAlignment(Alignment.CrossAxis.START);
    public final Flow outputColumn = Flow.col().coverChildren().crossAxisAlignment(Alignment.CrossAxis.START);

    public final Flow recipeContentRow;
    public final ParentWidget<?> additionalRecipeContent = new ParentWidget<>()
            .coverChildrenHeight().widthRel(1f);

    private final int minTier;
    private int tier;

    public GTRecipeViewerWidget(GTRecipe recipe) {
        this.baseRecipe = recipe;
        this.recipeType = recipe.getType();
        this.modifiedRecipe = recipe;
        this.uiLayout = Objects.requireNonNull(recipe.getType().getUiLayout(),
                "No recipe type UI declared, add one to your recipe type definition.");
        this.minTier = RecipeHelper.getRecipeEUtTier(recipe);
        this.tier = minTier;

        // === DEEPSPACE DEBUG PATCH (ctor) START ===
        if (recipeType.registryName != null && recipeType.registryName.getPath().contains("sifter")) {
            GTCEu.LOGGER.error(
                    "[DeepSpace/CTOR DEBUG]\nSIFTER RECIPE WIDGET CONSTRUCTED:\nrecipe = {}\nrecipeType = {}\ninputs.keySet = {}\noutputs.keySet = {}\ntickInputs.keySet = {}\ntickOutputs.keySet = {}\ninputs.item.size = {}\noutputs.item.size = {}",
                    recipe.id, recipeType.registryName,
                    recipe.inputs.keySet(), recipe.outputs.keySet(),
                    recipe.tickInputs.keySet(), recipe.tickOutputs.keySet(),
                    recipe.getInputContents(com.gregtechceu.gtceu.api.capability.recipe.ItemRecipeCapability.CAP)
                            .size(),
                    recipe.getOutputContents(com.gregtechceu.gtceu.api.capability.recipe.ItemRecipeCapability.CAP)
                            .size());
        }
        // === DEEPSPACE DEBUG PATCH (ctor) END ===

        Flow mainColumn = Flow.col().widthRel(1f).coverChildrenHeight();

        child(mainColumn);
        padding(3);
        coverChildrenWidth(150);
        coverChildrenHeight(60);

        // Attach duration here so it is always the first text row
        textComponents.child(Text
                .dynamic(() -> Component.translatable("gtceu.recipe.duration",
                        FormattingUtil.formatNumbers((double) modifiedRecipe.duration / 20)))
                .asWidget()
                .setEnabledIf(v -> !modifiedRecipe.data.getBoolean("hide_duration")));

        recipeContentRow = uiLayout.getCustomUIBuilder() == null ? buildDefaultLayout() :
                uiLayout.getCustomUIBuilder().apply(recipe);

        mainColumn.child(recipeContentRow.coverChildrenWidth().marginTop(5).marginBottom(3)
                .paddingLeft(2).paddingRight(2));
        mainColumn.child(additionalRecipeContent.child(textComponents));

        loadContentIntoSlots();

        buildAdditionalRecipeContent();

        attachOverclockButton();

        attachDebugRecipeIDButton();
    }

    private Flow buildDefaultLayout() {
        var row = Flow.row()
                .horizontalCenter()
                .coverChildrenHeight()
                .widthRel(1.f)
                .childPadding((recipeType.getUiLayout().getProgressBar().progressSize() / 2) + 2)
                .child(inputColumn)
                .child(uiLayout.getProgressWidgetSupplier()
                        .get(uiLayout, DoubleValue.simulateProgress(2000), null))
                .child(outputColumn);
        for (var entry : recipeType.maxInputs.object2IntEntrySet()) {
            var layoutFunc = uiLayout.capabilityInfo(entry.getKey()).recipeViewerLayoutBuilder;
            if (layoutFunc == null || entry.getIntValue() == 0) continue;
            layoutFunc.createCapabilityUILayout(uiLayout, this, IO.IN);
        }

        for (var entry : recipeType.maxOutputs.object2IntEntrySet()) {
            var layoutFunc = uiLayout.capabilityInfo(entry.getKey()).recipeViewerLayoutBuilder;
            if (layoutFunc == null || entry.getIntValue() == 0) continue;
            layoutFunc.createCapabilityUILayout(uiLayout, this, IO.OUT);
        }
        return row;
    }

    public static String capabilityWidgetName(RecipeCapability<?> cap, IO io, int index) {
        return "%s_%s_%s".formatted(cap.id, io.toString().toLowerCase(), index);
    }

    private void loadContentIntoSlots() {
        var allInputCaps = new HashSet<>(modifiedRecipe.inputs.keySet());
        allInputCaps.addAll(modifiedRecipe.tickInputs.keySet());
        var allOutputCaps = new HashSet<>(modifiedRecipe.outputs.keySet());
        allOutputCaps.addAll(modifiedRecipe.tickOutputs.keySet());

        // === DEEPSPACE DEBUG PATCH (ctor) START ===
        if (recipeType.registryName != null && recipeType.registryName.getPath().contains("sifter")) {
            GTCEu.LOGGER.error(
                    "[DeepSpace/CTOR DEBUG]\nloadContentIntoSlots CAP SETS:\nrecipe = {}\nallInputCaps = {}\nallOutputCaps = {}",
                    baseRecipe.id, allInputCaps, allOutputCaps);
        }
        // === DEEPSPACE DEBUG PATCH (ctor) END ===

        for (var cap : allInputCaps) {
            loadCapContent(cap, IO.IN);
        }

        for (var cap : allOutputCaps) {
            loadCapContent(cap, IO.OUT);
        }
    }

    private void loadCapContent(RecipeCapability<?> cap, IO io) {
        List<Content> contents = io == IO.IN ? modifiedRecipe.getInputContents(cap) :
                modifiedRecipe.getOutputContents(cap);
        List<Content> tickContents = io == IO.IN ? modifiedRecipe.getTickInputContents(cap) :
                modifiedRecipe.getTickOutputContents(cap);

        var widgetBuilder = uiLayout.capabilityInfo(cap).capabilityWidgetBuilder;

        // === DEEPSPACE DEBUG PATCH (widget) START ===
        if (recipeType.registryName != null && recipeType.registryName.getPath().contains("sifter")) {
            GTCEu.LOGGER.error(
                    "[DeepSpace/CTOR DEBUG]\nloadCapContent CALLED:\nrecipe = {}\ncap = {}\nio = {}\ncontents.size = {}\ntickContents.size = {}\nwidgetBuilder = {}",
                    baseRecipe.id, cap.id, io, contents.size(), tickContents.size(),
                    widgetBuilder == null ? "NULL" : "present");
        }
        // === DEEPSPACE DEBUG PATCH (widget) END ===

        if (widgetBuilder == null) {
            // === DEEPSPACE DEBUG PATCH (widget) START ===
            if (!contents.isEmpty() || !tickContents.isEmpty()) {
                GTCEu.LOGGER.error(
                        "[DeepSpace/WIDGET DEBUG]\nNULL WIDGET BUILDER (nothing will render for this capability):\nrecipe = {}\nrecipeType = {}\ncap = {}\nio = {}\ncontents.size = {}\ntickContents.size = {}",
                        baseRecipe.id, recipeType.registryName, cap.id, io, contents.size(), tickContents.size());
            }
            // === DEEPSPACE DEBUG PATCH (widget) END ===
            return;
        }

        int currentContentIndex = 0;

        for (var content : contents) {
            IWidget widget = WidgetTree.findFirstWithNameNullable(this,
                    capabilityWidgetName(cap, io, currentContentIndex));
            if (widget == null) {
                // === DEEPSPACE DEBUG PATCH (widget) START ===
                GTCEu.LOGGER.error(
                        "[DeepSpace/WIDGET DEBUG]\nNO SLOT WIDGET FOUND FOR CONTENT (slot count vs recipeType max mismatch?):\nrecipe = {}\nrecipeType = {}\ncap = {}\nio = {}\nslotIndex = {}\nwidgetName = {}\ncontent = {}\ncontents.size = {}\nrecipeType.maxInputs = {}\nrecipeType.maxOutputs = {}",
                        baseRecipe.id, recipeType.registryName, cap.id, io, currentContentIndex,
                        capabilityWidgetName(cap, io, currentContentIndex), content.content(), contents.size(),
                        recipeType.maxInputs, recipeType.maxOutputs);
                // === DEEPSPACE DEBUG PATCH (widget) END ===
                continue;
            }
            widgetBuilder.buildWidgetContent(widget, content, io, false, recipeType, modifiedRecipe, tier, tier);
            currentContentIndex++;
        }

        for (var tickContent : tickContents) {
            IWidget widget = WidgetTree.findFirstWithNameNullable(this,
                    capabilityWidgetName(cap, io, currentContentIndex));
            if (widget == null) {
                // === DEEPSPACE DEBUG PATCH (widget) START ===
                GTCEu.LOGGER.error(
                        "[DeepSpace/WIDGET DEBUG]\nNO SLOT WIDGET FOUND FOR TICK CONTENT:\nrecipe = {}\nrecipeType = {}\ncap = {}\nio = {}\nslotIndex = {}\nwidgetName = {}\ntickContent = {}\ntickContents.size = {}",
                        baseRecipe.id, recipeType.registryName, cap.id, io, currentContentIndex,
                        capabilityWidgetName(cap, io, currentContentIndex), tickContent.content(),
                        tickContents.size());
                // === DEEPSPACE DEBUG PATCH (widget) END ===
                continue;
            }
            widgetBuilder.buildWidgetContent(widget, tickContent, io, true, recipeType, modifiedRecipe, tier, tier);
            currentContentIndex++;
        }
    }

    private void buildAdditionalRecipeContent() {
        for (var condition : baseRecipe.conditions) {
            condition.modifyUI().buildRecipeUI(baseRecipe, this);
        }

        uiLayout.getRecipeUIModifiers().forEach(v -> v.buildRecipeUI(baseRecipe, this));
    }

    private void attachDebugRecipeIDButton() {
        childIf(!FMLLoader.isProduction(), () -> new ButtonWidget<>()
                .overlay(Text.str("ID"))
                .decoration()
                .bottom(3).right(3)
                .size(15, 15)
                .tooltip(r -> r.addLine("Click to copy recipe ID: " + baseRecipe.id))
                .onMousePressed((ctx, b) -> {
                    Minecraft.getInstance().keyboardHandler.setClipboard(baseRecipe.id.toString());
                    return true;
                }));
    }

    private void attachOverclockButton() {
        textComponents.child(new ButtonWidget<>().background(IDrawable.NONE)
                .hoverBackground(IDrawable.NONE)
                .size(22, 12)
                .rightRel(0.0f)
                .overlay(Text.dynamic(() -> Component.literal(GTValues.VNF[tier])))
                .tooltipBuilder(tooltip -> {
                    tooltip.addLine(Text.lang("gtceu.oc.tooltip.0", GTValues.VNF[minTier]));
                    tooltip.addLine(Text.lang("gtceu.oc.tooltip.1"));
                    tooltip.addLine(Text.lang("gtceu.oc.tooltip.2"));
                    tooltip.addLine(Text.lang("gtceu.oc.tooltip.3"));
                    tooltip.addLine(Text.lang("gtceu.oc.tooltip.4"));
                })
                .onMousePressed((ctx, b) -> {
                    var mouse = MouseData.create(b);

                    if (b == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
                        if (tier == GTValues.MAX) return true;
                        tier++;
                    } else if (b == GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
                        if (tier == minTier) return true;
                        tier--;
                    } else if (b == GLFW.GLFW_MOUSE_BUTTON_MIDDLE) {
                        tier = minTier;
                    }

                    updateOverclock(mouse);
                    return true;
                }).setEnabledIf(w -> RecipeHelper.getRealEUtWithIO(baseRecipe).isInput()));
    }

    private void updateOverclock(MouseData data) {
        OverclockingLogic oc = OverclockingLogic.NON_PERFECT_OVERCLOCK;
        if (data.shift()) oc = OverclockingLogic.PERFECT_OVERCLOCK;

        // TODO more contextual oc values based on recipe type or machine
        if (modifiedRecipe.recipeType == GTRecipeTypes.FUSION_RECIPES) {
            oc = FusionReactorMachine.FUSION_OC;
        }

        applyOverclock(oc);
    }

    private void applyOverclock(OverclockingLogic logic) {
        EnergyStack inputEUt = baseRecipe.getInputEUt();

        if (tier > minTier && !inputEUt.isEmpty()) {
            int ocs = tier - minTier;
            if (minTier == ULV) ocs--;
            var params = new OverclockingLogic.OCParams(inputEUt.voltage(), baseRecipe.duration, ocs, 1);
            var modifier = logic.runOverclockingLogic(params, V[tier]).toModifier();

            modifiedRecipe = Objects.requireNonNull(modifier.apply(baseRecipe));
        } else {
            modifiedRecipe = baseRecipe;
        }

        loadContentIntoSlots();
    }
}
