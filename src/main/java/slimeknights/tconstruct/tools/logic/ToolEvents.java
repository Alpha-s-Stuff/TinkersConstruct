package slimeknights.tconstruct.tools.logic;

import com.google.common.collect.Multiset;
import io.github.fabricators_of_create.porting_lib.core.event.BaseEvent.Result;
import io.github.fabricators_of_create.porting_lib.entity.events.EntityEvents;
import io.github.fabricators_of_create.porting_lib.entity.events.LivingEntityEvents.LivingTickEvent;
import io.github.fabricators_of_create.porting_lib.entity.events.LivingEntityEvents.LivingVisibilityEvent;
import io.github.fabricators_of_create.porting_lib.entity.events.PlayerEvents;
import io.github.fabricators_of_create.porting_lib.entity.events.ProjectileImpactEvent;
import io.github.fabricators_of_create.porting_lib.entity.events.living.LivingHurtEvent;
import io.github.fabricators_of_create.porting_lib.event.common.GrindstoneEvents;
import io.github.fabricators_of_create.porting_lib.util.EntityHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BeehiveBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.CarvedPumpkinBlock;
import net.minecraft.world.level.block.entity.BeehiveBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import slimeknights.tconstruct.common.TinkerTags;
import slimeknights.tconstruct.common.config.Config;
import slimeknights.tconstruct.library.events.TinkerToolEvent.ToolHarvestEvent;
import slimeknights.tconstruct.library.modifiers.Modifier;
import slimeknights.tconstruct.library.modifiers.ModifierEntry;
import slimeknights.tconstruct.library.modifiers.TinkerHooks;
import slimeknights.tconstruct.library.modifiers.data.ModifierMaxLevel;
import slimeknights.tconstruct.library.modifiers.dynamic.MobDisguiseModifier;
import slimeknights.tconstruct.library.tools.capability.EntityModifierCapability;
import slimeknights.tconstruct.library.tools.capability.PersistentDataCapability;
import slimeknights.tconstruct.library.tools.capability.TinkerDataCapability;
import slimeknights.tconstruct.library.tools.capability.TinkerDataKeys;
import slimeknights.tconstruct.library.tools.context.EquipmentContext;
import slimeknights.tconstruct.library.tools.definition.ModifiableArmorMaterial;
import slimeknights.tconstruct.library.tools.helper.ArmorUtil;
import slimeknights.tconstruct.library.tools.helper.ModifierUtil;
import slimeknights.tconstruct.library.tools.helper.ToolAttackUtil;
import slimeknights.tconstruct.library.tools.helper.ToolDamageUtil;
import slimeknights.tconstruct.library.tools.nbt.IToolStackView;
import slimeknights.tconstruct.library.tools.nbt.ModifierNBT;
import slimeknights.tconstruct.library.tools.nbt.NamespacedNBT;
import slimeknights.tconstruct.library.tools.nbt.ToolStack;
import slimeknights.tconstruct.library.utils.BlockSideHitListener;
import slimeknights.tconstruct.library.utils.Util;
import slimeknights.tconstruct.tools.TinkerModifiers;
import slimeknights.tconstruct.tools.modifiers.defense.ProjectileProtectionModifier;
import slimeknights.tconstruct.tools.modifiers.upgrades.armor.HasteModifier;

import java.util.List;
import java.util.Objects;

/**
 * Event subscriber for tool events
 */
@SuppressWarnings("unused")
public class ToolEvents {

  public static void init() {
    PlayerEvents.BREAK_SPEED.register(ToolEvents::onBreakSpeed);
    ToolHarvestEvent.EVENT.register(ToolEvents::onHarvest);
    LivingHurtEvent.HURT.register(ToolEvents::enderDragonDamage);
    LivingHurtEvent.HURT.register(ToolEvents::livingAttack);
    LivingHurtEvent.HURT.register(ToolEvents::livingHurt);
    LivingTickEvent.TICK.register(ToolEvents::livingWalk);
    LivingVisibilityEvent.VISIBILITY.register(ToolEvents::livingVisibility);
    EntityEvents.PROJECTILE_IMPACT.register(ToolEvents::projectileHit);
    GrindstoneEvents.ON_PLACE_ITEM.register(ToolEvents::onGrindstoneChange);
  }

  static void onBreakSpeed(PlayerEvents.BreakSpeed event) {
    Player player = event.getPlayer();

    // if we are underwater, have the aqua affinity modifier, and are not under the effects of vanilla aqua affinity, cancel the underwater modifier
    if (player.isEyeInFluid(FluidTags.WATER) && ModifierUtil.getTotalModifierLevel(player, TinkerDataKeys.AQUA_AFFINITY) > 0 && !EnchantmentHelper.hasAquaAffinity(player)) {
      event.setNewSpeed(event.getNewSpeed() * 5);
    }

    // tool break speed hook
    ItemStack stack = player.getMainHandItem();
    if (stack.is(TinkerTags.Items.HARVEST)) {
      ToolStack tool = ToolStack.from(stack);
      if (!tool.isBroken()) {
        List<ModifierEntry> modifiers = tool.getModifierList();
        if (!modifiers.isEmpty()) {
          // modifiers using additive boosts may want info on the original boosts provided
          float miningSpeedModifier = Modifier.getMiningModifier(player);
          boolean isEffective = stack.isCorrectToolForDrops(event.getState());
          Direction direction = BlockSideHitListener.getSideHit(player);
          for (ModifierEntry entry : tool.getModifierList()) {
            entry.getHook(TinkerHooks.BREAK_SPEED).onBreakSpeed(tool, entry, event, direction, isEffective, miningSpeedModifier);
            // if any modifier cancels mining, stop right here
            if (event.isCanceled()) {
              return;
            }
          }
        }
      }
    }

    // next, add in armor haste
    float armorHaste = ModifierUtil.getTotalModifierFloat(player, HasteModifier.HASTE);
    if (armorHaste > 0) {
      // adds in 10% per level
      event.setNewSpeed(event.getNewSpeed() * (1 + 0.1f * armorHaste));
    }
  }

  static void onHarvest(ToolHarvestEvent event) {
    // prevent processing if already processed
    if (event.getResult() != Result.DEFAULT) {
      return;
    }
    BlockState state = event.getState();
    Block block = state.getBlock();
    Level world = event.getWorld();
    BlockPos pos = event.getPos();

    // carve pumpkins
    if (block == Blocks.PUMPKIN) {
      Direction facing = event.getContext().getClickedFace();
      if (facing.getAxis() == Direction.Axis.Y) {
        facing = event.getContext().getHorizontalDirection().getOpposite();
      }
      // carve block
      world.playSound(null, pos, SoundEvents.PUMPKIN_CARVE, SoundSource.BLOCKS, 1.0F, 1.0F);
      world.setBlock(pos, Blocks.CARVED_PUMPKIN.defaultBlockState().setValue(CarvedPumpkinBlock.FACING, facing), 11);
      // spawn seeds
      ItemEntity itemEntity = new ItemEntity(
        world,
        pos.getX() + 0.5D + facing.getStepX() * 0.65D,
        pos.getY() + 0.1D,
        pos.getZ() + 0.5D + facing.getStepZ() * 0.65D,
        new ItemStack(Items.PUMPKIN_SEEDS, 4));
      itemEntity.setDeltaMovement(
        0.05D * facing.getStepX() + world.random.nextDouble() * 0.02D,
        0.05D,
        0.05D * facing.getStepZ() + world.random.nextDouble() * 0.02D);
      world.addFreshEntity(itemEntity);
      event.setResult(Result.ALLOW);
    }

    // hives: get the honey
    if (block instanceof BeehiveBlock beehive) {
      int level = state.getValue(BeehiveBlock.HONEY_LEVEL);
      if (level >= 5) {
        // first, spawn the honey
        world.playSound(null, pos, SoundEvents.BEEHIVE_SHEAR, SoundSource.NEUTRAL, 1.0F, 1.0F);
        Block.popResource(world, pos, new ItemStack(Items.HONEYCOMB, 3));

        // if not smoking, make the bees angry
        if (!CampfireBlock.isSmokeyPos(world, pos)) {
          if (beehive.hiveContainsBees(world, pos)) {
            beehive.angerNearbyBees(world, pos);
          }
          beehive.releaseBeesAndResetHoneyLevel(world, state, pos, event.getPlayer(), BeehiveBlockEntity.BeeReleaseStatus.EMERGENCY);
        } else {
          beehive.resetHoneyLevel(world, state, pos);
        }
        event.setResult(Result.ALLOW);
      } else {
        event.setResult(Result.DENY);
      }
    }
  }

  static void enderDragonDamage(LivingHurtEvent event) {
    DamageSource source = event.getSource();
    LivingEntity entity = event.getEntity();
    float amount = event.getAmount();
    if (!Config.COMMON.dropDragonScales.get()) {
      return;
    }
    // dragon being damaged
    if (entity.getType() == EntityType.ENDER_DRAGON && amount > 0 && !entity.level().isClientSide) {
      // player caused explosion, end crystals and TNT are examples
      if (source.is(DamageTypes.PLAYER_EXPLOSION) && source.getEntity() != null && source.getEntity().getType() == EntityType.PLAYER) {
        // drops 1 - 8 scales
        ModifierUtil.dropItem(entity, new ItemStack(TinkerModifiers.dragonScale, 1 + entity.level().random.nextInt(8)));
      }
    }
  }

  static float livingAttack(LivingHurtEvent event) {
    DamageSource source = event.getSource();
    LivingEntity entity = event.getEntity();
    float amount = event.getAmount();
    // client side always returns false, so this should be fine?
    if (entity.level().isClientSide() || entity.isDeadOrDying()) {
      return amount;
    }
    // I cannot think of a reason to run when invulnerable
//    DamageSource source = event.getSource();
    if (entity.isInvulnerableTo(source)) {
      return amount;
    }

    // a lot of counterattack hooks want to detect direct attacks, so save time by calculating once
    boolean isDirectDamage = source.getEntity() != null && !source.is(DamageTypes.THORNS);

    // determine if there is any modifiable armor, handles the target wearing modifiable armor
    EquipmentContext context = new EquipmentContext(entity);
//    float amount = event.getAmount();
    if (context.hasModifiableArmor()) {
      // first we need to determine if any of the four slots want to cancel the event, then we need to determine if any want to respond assuming its not canceled
      for (EquipmentSlot slotType : EquipmentSlot.values()) {
        if (ModifierUtil.validArmorSlot(entity, slotType)) {
          IToolStackView toolStack = context.getToolInSlot(slotType);
          if (toolStack != null && !toolStack.isBroken()) {
            for (ModifierEntry entry : toolStack.getModifierList()) {
              if (entry.getHook(TinkerHooks.DAMAGE_BLOCK).isDamageBlocked(toolStack, entry, context, slotType, source, amount)) {
                return 0;
              }
            }
          }
        }
      }

      // next, give modifiers a chance to respond to the entity being attacked, for counterattack hooks mainly
      // first we need to determine if any of the four slots want to cancel the event, then we need to determine if any want to respond assuming its not canceled
      for (EquipmentSlot slotType : ModifiableArmorMaterial.ARMOR_SLOTS) {
        IToolStackView toolStack = context.getToolInSlot(slotType);
        if (toolStack != null && !toolStack.isBroken()) {
          for (ModifierEntry entry : toolStack.getModifierList()) {
            entry.getHook(TinkerHooks.DAMAGE_TAKEN).onDamageTaken(toolStack, entry, context, slotType, source, amount, isDirectDamage);
          }
        }
      }
      // shields only run this hook when blocking
      // TODO: what if the slot in charge is not the blocking slot, can that happen?
      if (entity.isBlocking()) {
        EquipmentSlot slot = Util.getSlotType(entity.getUsedItemHand());
        IToolStackView shield = context.getToolInSlot(slot);
        if (shield != null && !shield.isBroken()) {
          for (ModifierEntry entry : shield.getModifierList()) {
            entry.getHook(TinkerHooks.DAMAGE_TAKEN).onDamageTaken(shield, entry, context, slot, source, amount, isDirectDamage);
          }
        }
      }
    }

    // next, consider the attacker is wearing modifiable armor
    Entity attacker = source.getEntity();
    if (attacker instanceof LivingEntity livingAttacker) {
      context = new EquipmentContext(livingAttacker);
      if (context.hasModifiableArmor()) {
        for (EquipmentSlot slotType : ModifiableArmorMaterial.ARMOR_SLOTS) {
          IToolStackView toolStack = context.getToolInSlot(slotType);
          if (toolStack != null && !toolStack.isBroken()) {
            for (ModifierEntry entry : toolStack.getModifierList()) {
              entry.getHook(TinkerHooks.DAMAGE_DEALT).onDamageDealt(toolStack, entry, context, slotType, entity, source, amount, isDirectDamage);
            }
          }
        }
      }
    }
    return amount;
  }

  /**
   * Determines how much to damage armor based on the given damage to the player
   *
   * @param damage Amount to damage the player
   * @return Amount to damage the armor
   */
  private static int getArmorDamage(float damage) {
    damage /= 4;
    if (damage < 1) {
      return 1;
    }
    return (int) damage;
  }

  // low priority to minimize conflict as we apply reduction as if we are the final change to damage before vanilla
  static void livingHurt(LivingHurtEvent event) {
    DamageSource source = event.getSource();
    LivingEntity entity = event.getEntity();
    float amount = event.getAmount();

    // determine if there is any modifiable armor, if not nothing to do
    EquipmentContext context = new EquipmentContext(entity);
    int vanillaModifier = 0;
    float modifierValue = 0;

    // for our own armor, we have boosts from modifiers to consider
    if (context.hasModifiableArmor()) {
      // first, fetch vanilla enchant level, assuming its not bypassed in vanilla
      if (!source.is(DamageTypeTags.BYPASSES_EFFECTS)) {
        modifierValue = vanillaModifier = EnchantmentHelper.getDamageProtection(entity.getArmorSlots(), source);
      }

      // next, determine how much tinkers armor wants to change it
      // note that armor modifiers can choose to block "absolute damage" if they wish, currently just starving damage I think
      for (EquipmentSlot slotType : EquipmentSlot.values()) {
        if (ModifierUtil.validArmorSlot(entity, slotType)) {
          IToolStackView tool = context.getToolInSlot(slotType);
          if (tool != null && !tool.isBroken()) {
            for (ModifierEntry entry : tool.getModifierList()) {
              modifierValue = entry.getHook(TinkerHooks.PROTECTION).getProtectionModifier(tool, entry, context, slotType, source, modifierValue);
            }
          }
        }
      }

      // give slimes a 4x armor boost
      if (entity.getType().is(TinkerTags.EntityTypes.SMALL_ARMOR)) {
        modifierValue *= 4;
      }
    } else if (!source.is(DamageTypeTags.BYPASSES_EFFECTS) && entity.getType().is(TinkerTags.EntityTypes.SMALL_ARMOR)) {
      vanillaModifier = EnchantmentHelper.getDamageProtection(entity.getArmorSlots(), source);
      modifierValue = vanillaModifier * 4;
    }

    // TODO: consider hook for modifiers to change damage directly
    // if we changed anything, run our logic. Changing the cap has 2 problematic cases where same value will work:
    // * increased cap and vanilla is over the vanilla cap
    // * decreased cap and vanilla is now under the cap
    // that said, don't actually care about cap unless we have some protection, can use vanilla to simplify logic
    float cap = 20f;
    if (modifierValue > 0) {
      cap = Math.min(20 + context.getTinkerData().resolve().map(data -> data.get(TinkerDataKeys.PROTECTION_CAP)).orElse(0f), 25 * 0.95f);
    }
    if (vanillaModifier != modifierValue || (cap > 20 && vanillaModifier > 20) || (cap < 20 && vanillaModifier > cap)) {
      // fetch armor and toughness if blockable, passing in 0 to the logic will skip the armor calculations
      float armor = 0, toughness = 0;
      if (!source.is(DamageTypeTags.BYPASSES_ARMOR)) {
        armor = entity.getArmorValue();
        toughness = (float) entity.getAttributeValue(Attributes.ARMOR_TOUGHNESS);
      }

      // set the final dealt damage
      float finalDamage = ArmorUtil.getDamageForEvent(amount, armor, toughness, vanillaModifier, modifierValue, cap);
//      event.setAmount(finalDamage);

      // armor is damaged less as a result of our math, so damage the armor based on the difference if there is one
      if (!source.is(DamageTypeTags.BYPASSES_ARMOR)) {
        int damageMissed = getArmorDamage(amount) - getArmorDamage(finalDamage);
        // TODO: is this check sufficient for whether the armor should be damaged? I partly wonder if I need to use reflection to call damageArmor
        if (damageMissed > 0 && entity instanceof Player) {
          for (EquipmentSlot slotType : ModifiableArmorMaterial.ARMOR_SLOTS) {
            // for our own armor, saves effort to damage directly with our utility
            IToolStackView tool = context.getToolInSlot(slotType);
            if (tool != null && (!source.is(DamageTypeTags.IS_FIRE) || !tool.getItem().isFireResistant())) {
              ToolDamageUtil.damageAnimated(tool, damageMissed, entity, slotType);
            } else {
              // if not our armor, damage using vanilla like logic
              ItemStack armorStack = entity.getItemBySlot(slotType);
              if (!armorStack.isEmpty() && (!source.is(DamageTypeTags.IS_FIRE) || !armorStack.getItem().isFireResistant()) && armorStack.getItem() instanceof ArmorItem) {
                armorStack.hurtAndBreak(damageMissed, entity, e -> e.broadcastBreakEvent(slotType));
              }
            }
          }
        }
      }
      event.setAmount(finalDamage);
    }
  }

  /**
   * Called the modifier hook when an entity's position changes
   */
  static void livingWalk(LivingTickEvent event) {
    LivingEntity living = event.getEntity();
    // this event runs before vanilla updates prevBlockPos
    BlockPos pos = living.blockPosition();
    BlockPos lastPos = EntityHelper.getLastPos(living);
    if (!living.isSpectator() && !living.level().isClientSide() && living.isAlive() && !Objects.equals(lastPos, pos)) {
      ItemStack boots = living.getItemBySlot(EquipmentSlot.FEET);
      if (!boots.isEmpty() && boots.is(TinkerTags.Items.BOOTS)) {
        ToolStack tool = ToolStack.from(boots);
        for (ModifierEntry entry : tool.getModifierList()) {
          entry.getHook(TinkerHooks.BOOT_WALK).onWalk(tool, entry, living, lastPos, pos);
        }
      }
    }
  }

  /**
   * Handles visibility effects of mob disguise and projectile protection
   */
  static void livingVisibility(LivingVisibilityEvent event) {
    // always nonnull in vanilla, not sure when it would be nullable but I dont see a need for either modifier
    Entity lookingEntity = event.getLookingEntity();
    if (lookingEntity == null) {
      return;
    }
    LivingEntity living = event.getEntity();
    TinkerDataCapability.CAPABILITY.maybeGet(living).ifPresent(data -> {
      // mob disguise
      Multiset<EntityType<?>> disguises = data.get(MobDisguiseModifier.DISGUISES);
      if (disguises != null && disguises.contains(lookingEntity.getType())) {
        // not as good as a real head
        event.modifyVisibility(0.65f);
      }

      // projectile protection
      ModifierMaxLevel projData = data.get(ProjectileProtectionModifier.PROJECTILE_DATA);
      if (projData != null) {
        float max = projData.getMax();
        if (max > 0) {
          // reduces visibility by 5% per level
          event.modifyVisibility(Math.max(0, 1 - (max * 0.05)));
        }
      }
    });
  }

  /**
   * Implements projectile hit hook
   */
  static void projectileHit(ProjectileImpactEvent event) {
    Projectile projectile = event.getProjectile();
    ModifierNBT modifiers = EntityModifierCapability.getOrEmpty(projectile);
    if (!modifiers.isEmpty()) {
      NamespacedNBT nbt = PersistentDataCapability.getOrWarn(projectile);
      HitResult hit = event.getRayTraceResult();
      HitResult.Type type = hit.getType();
      // extract a firing entity as that is a common need
      LivingEntity attacker = projectile.getOwner() instanceof LivingEntity l ? l : null;
      switch (type) {
        case ENTITY -> {
          EntityHitResult entityHit = (EntityHitResult) hit;
          // cancel all effects on endermen unless we have enderference, endermen like to teleport away
          // yes, hardcoded to enderference, if you need your own enderference for whatever reason, talk to us
          if (entityHit.getEntity().getType() != EntityType.ENDERMAN || modifiers.getLevel(TinkerModifiers.enderference.getId()) > 0) {
            // extract a living target as that is the most common need
            LivingEntity target = ToolAttackUtil.getLivingEntity(entityHit.getEntity());
            for (ModifierEntry entry : modifiers.getModifiers()) {
              if (entry.getHook(TinkerHooks.PROJECTILE_HIT).onProjectileHitEntity(modifiers, nbt, entry, projectile, entityHit, attacker, target)) {
                event.setCanceled(true);
              }
            }
          }
        }
        case BLOCK -> {
          BlockHitResult blockHit = (BlockHitResult) hit;
          for (ModifierEntry entry : modifiers.getModifiers()) {
            if (entry.getHook(TinkerHooks.PROJECTILE_HIT).onProjectileHitBlock(modifiers, nbt, entry, projectile, blockHit, attacker)) {
              event.setCanceled(true);
            }
          }
        }
      }
    }
  }

  static void onGrindstoneChange(GrindstoneEvents.OnplaceItem event) {
    // no removing enchantments from tools, you must use the modifier to remove them
    if (event.getTopItem().is(TinkerTags.Items.MODIFIABLE) || event.getBottomItem().is(TinkerTags.Items.MODIFIABLE)) {
      event.setCanceled(true);
    }
  }
}
