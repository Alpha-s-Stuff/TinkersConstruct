package slimeknights.tconstruct.tools.modifiers.traits.skull;

import io.github.fabricators_of_create.porting_lib.event.common.PotionEvents;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import slimeknights.tconstruct.TConstruct;
import slimeknights.tconstruct.library.modifiers.impl.TotalArmorLevelModifier;
import slimeknights.tconstruct.library.tools.capability.TinkerDataCapability.TinkerDataKey;
import slimeknights.tconstruct.library.tools.helper.ModifierUtil;

public class MithridatismModifier extends TotalArmorLevelModifier {
  private static final TinkerDataKey<Integer> MITHRIDATISM = TConstruct.createKey("mithridatism");
  public MithridatismModifier() {
    super(MITHRIDATISM, true);
    PotionEvents.POTION_APPLICABLE.register(MithridatismModifier::isPotionApplicable);
  }

  /** Prevents poison on the entity */
  private static InteractionResult isPotionApplicable(LivingEntity entity, MobEffectInstance effect) {
    if (effect.getEffect() == MobEffects.POISON && ModifierUtil.getTotalModifierLevel(entity, MITHRIDATISM) > 0) {
      return InteractionResult.FAIL;
    }
    return InteractionResult.PASS;
  }
}
