package slimeknights.tconstruct.tools.modifiers.defense;

import io.github.fabricators_of_create.porting_lib.event.common.ExplosionEvents;
import io.github.fabricators_of_create.porting_lib.entity.events.LivingEntityEvents.LivingTickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import slimeknights.tconstruct.TConstruct;
import slimeknights.tconstruct.library.modifiers.data.ModifierMaxLevel;
import slimeknights.tconstruct.library.tools.capability.TinkerDataCapability;
import slimeknights.tconstruct.library.tools.capability.TinkerDataCapability.TinkerDataKey;
import slimeknights.tconstruct.library.tools.context.EquipmentContext;
import slimeknights.tconstruct.library.tools.nbt.IToolStackView;
import slimeknights.tconstruct.library.utils.TooltipKey;
import slimeknights.tconstruct.tools.modifiers.defense.BlastProtectionModifier.BlastData;

import javax.annotation.Nullable;
import java.util.List;

public class BlastProtectionModifier extends AbstractProtectionModifier<BlastData> {
  /** Entity data key for the data associated with this modifier */
  private static final TinkerDataKey<BlastData> BLAST_DATA = TConstruct.createKey("blast_protection");
  public BlastProtectionModifier() {
    super(BLAST_DATA);
    ExplosionEvents.DETONATE.register(BlastProtectionModifier::onExplosionDetonate);
    LivingTickEvent.TICK.register(BlastProtectionModifier::livingTick);
  }

  @Override
  public float getProtectionModifier(IToolStackView tool, int level, EquipmentContext context, EquipmentSlot slotType, DamageSource source, float modifierValue) {
    if (!source.is(DamageTypeTags.BYPASSES_EFFECTS) && !source.is(DamageTypeTags.BYPASSES_INVULNERABILITY) && source.is(DamageTypeTags.IS_EXPLOSION)) {
      modifierValue += getScaledLevel(tool, level) * 2.5f;
    }
    return modifierValue;
  }

  @Override
  public void addInformation(IToolStackView tool, int level, @Nullable Player player, List<Component> tooltip, TooltipKey tooltipKey, TooltipFlag tooltipFlag) {
    AbstractProtectionModifier.addResistanceTooltip(this, tool, level, 2.5f, tooltip);
  }

  @Override
  protected BlastData createData() {
    return new BlastData();
  }

  @Override
  protected void reset(BlastData data) {
    data.wasKnockback = false;
  }

  /** On explosion, checks if any blast protected entity is involved, if so marks them for knockback update next tick */
  private static void onExplosionDetonate(Level world, Explosion explosion, List<Entity> list, double d) {
    Vec3 center = new Vec3(explosion.x, explosion.y, explosion.z);
    float diameter = explosion.radius * 2;
    // search the entities for someone protection by blast protection
    for (Entity entity : list) {
      if (!entity.ignoreExplosion()) {
        TinkerDataCapability.CAPABILITY.maybeGet(entity).ifPresent(data -> {
          // if the entity has blast protection and the blast protection level is bigger than vanilla, time to process
          BlastData blastData = data.get(BLAST_DATA);
          if (blastData != null && blastData.getMax() > 0) {
            // explosion is valid as long as the entity's eye is not directly on the explosion
            double x = entity.getX() - center.x;
            double z = entity.getZ() - center.z;
            if (x != 0 || z != 0 || (entity.getEyeY() - center.y) != 0) {
              // we need two numbers to calculate the knockback: distance to explosion and block density
              double y = entity.getY() - center.y;
              double distance = Mth.sqrt((float)(x * x + y * y + z * z)) / diameter;
              if (distance <= 1) {
                blastData.wasKnockback = true;
              }
            }
          }
        });
      }
    }
  }

  /** If the entity is marked for knockback update, adjust velocity */
  private static void livingTick(LivingTickEvent event) {
    LivingEntity living = event.getEntity();
    if (!living.level().isClientSide && !living.isSpectator()) {
      TinkerDataCapability.CAPABILITY.maybeGet(living).ifPresent(data -> {
        BlastData blastData = data.get(BLAST_DATA);
        if (blastData != null && blastData.wasKnockback) {
          blastData.wasKnockback = false;
          float max = blastData.getMax();
          if (max > 0) {
            // due to MC-198809, vanilla does not actually reduce the knockback except on levels higher than obtainable in survival (blast prot VII)
            // thus, we only care about our own level for reducing
            double scale = 1 - (blastData.getMax() * 0.15f);
            if (scale <= 0) {
              living.setDeltaMovement(Vec3.ZERO);
            } else {
              living.setDeltaMovement(living.getDeltaMovement().multiply(scale, scale, scale));
            }
            living.hurtMarked = true;
          }
        }
      });
    }
  }

  /** Data object for the modifier */
  protected static class BlastData extends ModifierMaxLevel {
    /** If true, the entity was knocked back and needs their velocity adjusted */
    boolean wasKnockback = false;
  }
}
