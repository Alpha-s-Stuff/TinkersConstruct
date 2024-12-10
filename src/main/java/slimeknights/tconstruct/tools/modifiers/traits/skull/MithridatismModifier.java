package slimeknights.tconstruct.tools.modifiers.traits.skull;

import io.github.fabricators_of_create.porting_lib.entity.events.living.MobEffectEvent;
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
    MobEffectEvent.ADDED.register(MithridatismModifier::isPotionApplicable);
  }

  /**
   * Prevents poison on the entity
   */
  private static void isPotionApplicable(MobEffectEvent.Added event) {
    LivingEntity entity = event.getEntity();
    MobEffectInstance effect = event.getEffectInstance();
    if (effect.getEffect() == MobEffects.POISON && ModifierUtil.getTotalModifierLevel(entity, MITHRIDATISM) > 0) {
      event.setCanceled(true);
    }
  }
}
