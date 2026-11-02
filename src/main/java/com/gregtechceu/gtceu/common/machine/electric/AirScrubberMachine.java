package com.gregtechceu.gtceu.common.machine.electric;

import com.gregtechceu.gtceu.api.blockentity.BlockEntityCreationInfo;
import com.gregtechceu.gtceu.api.data.medicalcondition.MedicalCondition;
import com.gregtechceu.gtceu.api.machine.SimpleTieredMachine;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gregtechceu.gtceu.common.data.GTRecipeTypes;
import com.gregtechceu.gtceu.common.data.machines.GTMachineUtils;
import com.gregtechceu.gtceu.common.machine.trait.hazard.EnvironmentalHazardCleanerTrait;
import com.gregtechceu.gtceu.common.recipe.condition.EnvironmentalHazardCondition;
import com.gregtechceu.gtceu.config.ConfigHolder;
import com.gregtechceu.gtceu.data.recipe.builder.GTRecipeBuilder;

import lombok.Getter;
import org.jetbrains.annotations.Nullable;

import static com.gregtechceu.gtceu.api.GTValues.LV;
import static com.gregtechceu.gtceu.api.GTValues.VHA;

public class AirScrubberMachine extends SimpleTieredMachine {

    public static final float MIN_CLEANING_PER_OPERATION = 10;

    private @Nullable MedicalCondition currentRecipeMedicalCondition;

    @Getter
    private float removedLastSecond;

    @Getter
    private final EnvironmentalHazardCleanerTrait cleanerTrait;

    public AirScrubberMachine(BlockEntityCreationInfo info, int tier) {
        super(info, tier, GTMachineUtils.largeTankSizeFunction);
        this.cleanerTrait = attachTrait(new EnvironmentalHazardCleanerTrait(tier / 2, this::validateCleaningOperation));
        recipeLogic.setRegressWhenWaiting(false);
    }

    public boolean validateCleaningOperation(MedicalCondition condition, float amount) {
        if (this.recipeLogic.isActive()) {
            return false;
        }

        currentRecipeMedicalCondition = condition;

        GTRecipeBuilder builder = GTRecipeTypes.AIR_SCRUBBER_RECIPES.recipeBuilder(condition.id.withSuffix("_autogen"))
                .duration(200).EUt(VHA[LV]);
        condition.recipeModifier.accept(builder);
        return this.recipeLogic.checkMatchedRecipeAvailable(builder.buildRawRecipe());
    }

    @Override
    public boolean isRecipeLogicAvailable() {
        // Don't run recipes if hazards are off
        return ConfigHolder.INSTANCE.gameplay.environmentalHazards;
    }

    @Override
    public boolean beforeWorking(@Nullable GTRecipe recipe) {
        if (!super.beforeWorking(recipe) || recipe == null) {
            return false;
        }

        // currentRecipeMedicalCondition is only populated by validateCleaningOperation(), which is only ever
        // invoked when a duct network routes a hazard to this scrubber (see DuctNetHandler). When this machine
        // finds its own recipe through the normal automatic recipe search (e.g. it's just sitting in a polluted
        // chunk with the right circuit inserted, with no duct pipes involved at all), that field is never set.
        // In that case, fall back to reading the condition straight off the recipe that was actually matched,
        // since every real Air Scrubber recipe carries its target condition via EnvironmentalHazardCondition.
        MedicalCondition condition = currentRecipeMedicalCondition;
        if (condition == null) {
            condition = recipe.conditions.stream()
                    .filter(EnvironmentalHazardCondition.class::isInstance)
                    .map(EnvironmentalHazardCondition.class::cast)
                    .findAny()
                    .map(EnvironmentalHazardCondition::getCondition)
                    .orElse(null);
        }

        if (condition == null) {
            return false;
        }

        // Sets the amount of hazard to clean based on the recipe tier, not the machine tier
        return cleanerTrait.beginCleaningOperation(condition, MIN_CLEANING_PER_OPERATION * (recipe.ocLevel + 1));
    }
}
