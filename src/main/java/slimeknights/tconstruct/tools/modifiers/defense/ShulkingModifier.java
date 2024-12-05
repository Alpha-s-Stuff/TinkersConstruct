package slimeknights.tconstruct.tools.modifiers.defense;

import io.github.fabricators_of_create.porting_lib.entity.events.living.LivingHurtEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.TooltipFlag;
import slimeknights.tconstruct.TConstruct;
import slimeknights.tconstruct.library.modifiers.data.ModifierMaxLevel;
import slimeknights.tconstruct.library.tools.capability.TinkerDataCapability;
import slimeknights.tconstruct.library.tools.capability.TinkerDataCapability.TinkerDataKey;
import slimeknights.tconstruct.library.tools.context.EquipmentContext;
import slimeknights.tconstruct.library.tools.nbt.IToolStackView;
import slimeknights.tconstruct.library.utils.TooltipKey;

import javax.annotation.Nullable;
import java.util.List;

public class ShulkingModifier extends AbstractProtectionModifier<ModifierMaxLevel> {
  private static final TinkerDataKey<ModifierMaxLevel> KEY = TConstruct.createKey("shulking");
  public ShulkingModifier() {
    super(KEY);
    LivingHurtEvent.HURT.register(ShulkingModifier::onAttack);
  }

  @Override
  protected ModifierMaxLevel createData() {
    return new ModifierMaxLevel();
  }

  @Override
  public float getProtectionModifier(IToolStackView tool, int level, EquipmentContext context, EquipmentSlot slotType, DamageSource source, float modifierValue) {
    if (context.getEntity().isCrouching() && !source.is(DamageTypeTags.BYPASSES_EFFECTS) && !source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
      modifierValue += getScaledLevel(tool, level) * 2.5f;
    }
    return modifierValue;
  }

  @Override
  public void addInformation(IToolStackView tool, int level, @Nullable Player player, List<Component> tooltip, TooltipKey tooltipKey, TooltipFlag tooltipFlag) {
    AbstractProtectionModifier.addResistanceTooltip(this, tool, level, 2.5f, tooltip);
  }

  private static void onAttack(LivingHurtEvent event) {
    // if the attacker is crouching, deal less damage
    Entity attacker = event.getSource().getEntity();
    if (attacker != null && attacker.isCrouching()) {
      TinkerDataCapability.CAPABILITY.maybeGet(attacker).ifPresent(data -> {
        ModifierMaxLevel max = data.get(KEY);
        if (max != null) {
          event.setAmount(event.getAmount() * (1 - (max.getMax() * 0.1f)));
        }
      });
    }
  }
}
