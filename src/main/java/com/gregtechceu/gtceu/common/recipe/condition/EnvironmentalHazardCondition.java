package com.gregtechceu.gtceu.common.recipe.condition;

import com.gregtechceu.gtceu.api.data.medicalcondition.MedicalCondition;
import com.gregtechceu.gtceu.api.machine.trait.recipe.RecipeLogic;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gregtechceu.gtceu.api.recipe.RecipeCondition;
import com.gregtechceu.gtceu.api.recipe.condition.RecipeConditionType;
import com.gregtechceu.gtceu.common.capability.EnvironmentalHazardSavedData;
import com.gregtechceu.gtceu.common.data.GTMedicalConditions;
import com.gregtechceu.gtceu.common.data.GTRecipeConditions;
import com.gregtechceu.gtceu.config.ConfigHolder;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.jetbrains.annotations.NotNull;

@NoArgsConstructor
@AllArgsConstructor
public class EnvironmentalHazardCondition extends RecipeCondition<EnvironmentalHazardCondition> {

    // spotless:off
    public static final Codec<EnvironmentalHazardCondition> CODEC = RecordCodecBuilder.create(instance -> RecipeCondition.isReverse(instance).and(
            MedicalCondition.CODEC.fieldOf("condition").forGetter(EnvironmentalHazardCondition::getCondition)
    ).apply(instance, EnvironmentalHazardCondition::new));
    // spotless:on

    @Getter
    private MedicalCondition condition = GTMedicalConditions.CARBON_MONOXIDE_POISONING;

    public EnvironmentalHazardCondition(boolean isReverse, MedicalCondition condition) {
        super(isReverse);
        this.condition = condition;
    }

    @Override
    public RecipeConditionType<EnvironmentalHazardCondition> getType() {
        return GTRecipeConditions.ENVIRONMENTAL_HAZARD;
    }

    @Override
    public Component getTooltips() {
        return isReverse ?
                Component.translatable("gtceu.recipe.environmental_hazard.reverse", condition.getTranslatableName()) :
                Component.translatable("gtceu.recipe.environmental_hazard", condition.getTranslatableName());
    }

    @Override
    public boolean testCondition(@NotNull GTRecipe recipe, @NotNull RecipeLogic recipeLogic) {
        // NOTE: this must be gated on `environmentalHazards` (the pollution/radiation zone system), not
        // `hazardsEnabled` (the unrelated "hazardous materials directly hurt players" toggle). This recipe
        // condition exists purely to check whether there's actual chunk pollution for the Air Scrubber to
        // consume, so it must keep doing that real check even when `hazardsEnabled` is false - otherwise
        // servers that disable direct material hazards (a very common config choice) but keep chunk
        // pollution/scrubbers enabled would have this condition unconditionally return true, letting Air
        // Scrubbers "run" without ever actually finding pollution to remove.
        if (!ConfigHolder.INSTANCE.gameplay.environmentalHazards) return false;
        if (!(recipeLogic.getMachine().getLevel() instanceof ServerLevel serverLevel)) {
            return false;
        }
        EnvironmentalHazardSavedData savedData = EnvironmentalHazardSavedData.getOrCreate(serverLevel);
        var zone = savedData.getZoneByContainedPos(recipeLogic.getMachine().getBlockPos());
        return zone != null && zone.strength() > 0;
    }

    @Override
    public EnvironmentalHazardCondition createTemplate() {
        return new EnvironmentalHazardCondition();
    }
}
