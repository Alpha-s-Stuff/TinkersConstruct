package slimeknights.tconstruct.tools.modifiers.traits.skull;

import io.github.fabricators_of_create.porting_lib.entity.events.living.MobEffectEvent;
import io.github.fabricators_of_create.porting_lib.util.PotionHelper;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import slimeknights.tconstruct.TConstruct;
import slimeknights.tconstruct.library.modifiers.impl.TotalArmorLevelModifier;
import slimeknights.tconstruct.library.tools.capability.TinkerDataCapability.TinkerDataKey;
import slimeknights.tconstruct.library.tools.context.EquipmentChangeContext;
import slimeknights.tconstruct.library.tools.helper.ModifierUtil;
import slimeknights.tconstruct.library.tools.nbt.IToolStackView;

public class BoonOfSssssModifier extends TotalArmorLevelModifier {
  private static final TinkerDataKey<Integer> POTENT_POTIONS = TConstruct.createKey("boon_of_sssss");
  public BoonOfSssssModifier() {
    super(POTENT_POTIONS, true);
    MobEffectEvent.ADDED.register(BoonOfSssssModifier::onPotionStart);
  }

  @Override
  public void onUnequip(IToolStackView tool, int level, EquipmentChangeContext context) {
    super.onUnequip(tool, level, context);
    if (context.getChangedSlot() == EquipmentSlot.HEAD) {
      IToolStackView replacement = context.getReplacementTool();
      if (replacement == null || replacement.getModifierLevel(this) == 0) {
        // cure effects using the helmet
        PotionHelper.curePotionEffects(context.getEntity(), new ItemStack(tool.getItem()));
      }
    }
  }

  /** Called when the potion effects start to apply this effect */
  private static void onPotionStart(MobEffectEvent.Added event) {
    LivingEntity living = event.getEntity();
    MobEffectInstance newEffect = event.getEffectInstance();
    if (newEffect.getEffect().isBeneficial()) {
      if (ModifierUtil.getTotalModifierLevel(living, POTENT_POTIONS) > 0) {
        newEffect.duration *= 1.25f;
        newEffect.getCurativeItems().add(new ItemStack(living.getItemBySlot(EquipmentSlot.HEAD).getItem()));
      }
    }
  }
}
