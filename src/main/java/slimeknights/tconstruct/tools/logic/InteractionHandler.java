package slimeknights.tconstruct.tools.logic;

import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.minecraft.advancements.CriteriaTriggers;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.network.protocol.game.ServerboundUseItemOnPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.pattern.BlockInWorld;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import org.jetbrains.annotations.Nullable;
import slimeknights.mantle.client.TooltipKey;
import slimeknights.tconstruct.TConstruct;
import slimeknights.tconstruct.common.TinkerTags;
import slimeknights.tconstruct.library.modifiers.ModifierEntry;
import slimeknights.tconstruct.library.modifiers.hooks.IArmorInteractModifier;
import slimeknights.tconstruct.library.tools.helper.ToolAttackUtil;
import slimeknights.tconstruct.library.tools.nbt.IToolStackView;
import slimeknights.tconstruct.library.tools.nbt.ToolStack;
import slimeknights.tconstruct.library.utils.Util;
import slimeknights.tconstruct.tools.TinkerModifiers;

import java.util.function.Function;

/**
 * This class handles interaction based event hooks
 */
public class InteractionHandler {
  /** Implements {@link slimeknights.tconstruct.library.modifiers.Modifier#beforeEntityUse(IToolStackView, int, Player, Entity, InteractionHand, EquipmentSlot)} */
  static InteractionResult beforeEntityInteract(Player player, Level world, InteractionHand hand, Entity target, @Nullable EntityHitResult hitResult) {
    ItemStack stack = player.getItemInHand(hand);
    EquipmentSlot slotType = Util.getSlotType(hand);
    if (!stack.is(TinkerTags.Items.HELD)) {
      // if the hand is empty, allow performing chestplate interaction (assuming a modifiable chestplate)
      if (stack.isEmpty()) {
        stack = player.getItemBySlot(EquipmentSlot.CHEST);
        if (stack.is(TinkerTags.Items.CHESTPLATES)) {
          slotType = EquipmentSlot.CHEST;
        } else {
          return InteractionResult.PASS;
        }
      } else {
        return InteractionResult.PASS;
      }
    }
    // actual interaction hook
    ToolStack tool = ToolStack.from(stack);
    for (ModifierEntry entry : tool.getModifierList()) {
      // exit on first successful result
      InteractionResult result = entry.getModifier().beforeEntityUse(tool, entry.getLevel(), player, target, hand, slotType);
      if (result.consumesAction()) {
        return result;
      }
    }
    return InteractionResult.PASS;
  }

  /** Implements {@link slimeknights.tconstruct.library.modifiers.Modifier#afterEntityUse(IToolStackView, int, Player, LivingEntity, InteractionHand, EquipmentSlot)} for chestplates */
  static InteractionResult afterEntityInteract(Player player, Level world, InteractionHand hand, Entity target, @Nullable EntityHitResult hitResult) {
    if (player.getItemInHand(hand).isEmpty() && !player.isSpectator()) {
      ItemStack chestplate = player.getItemBySlot(EquipmentSlot.CHEST);
      if (chestplate.is(TinkerTags.Items.CHESTPLATES)) {
        // from this point on, we are taking over interaction logic, to ensure chestplate hooks run in the right order
//        event.setCanceled(true);

        ToolStack tool = ToolStack.from(chestplate);

        // initial entity interaction
        InteractionResult result = target.interact(player, hand);
        if (result.consumesAction()) {
          return InteractionResult.PASS;
        }

        // after entity use for chestplates
        if (target instanceof LivingEntity livingTarget) {
          for (ModifierEntry entry : tool.getModifierList()) {
            // exit on first successful result
            result = entry.getModifier().afterEntityUse(tool, entry.getLevel(), player, livingTarget, hand, EquipmentSlot.CHEST);
            if (result.consumesAction()) {
              return result;
            }
          }
        }

        // did not interact with an entity? try direct interaction
        // needs to be run here as the interact empty hook does not fire when targeting entities
        result = onChestplateUse(player, chestplate, hand);
        return InteractionResult.PASS;
      }
    }
    return InteractionResult.PASS;
  }

  /** Runs one of the two blockUse hooks for a chestplate */
  private static InteractionResult onBlockUse(UseOnContext context, IToolStackView tool, ItemStack stack, Function<ModifierEntry, InteractionResult> callback) {
    Player player = context.getPlayer();
    Level world = context.getLevel();
    BlockInWorld info = new BlockInWorld(world, context.getClickedPos(), false);
    if (player != null && !player.getAbilities().mayBuild && !stack.hasAdventureModePlaceTagForBlock(Registry.BLOCK, info)) {
      return InteractionResult.PASS;
    }

    // run modifier hook
    for (ModifierEntry entry : tool.getModifierList()) {
      InteractionResult result = callback.apply(entry);
      if (result.consumesAction()) {
        if (player != null) {
          player.awardStat(Stats.ITEM_USED.get(stack.getItem()));
        }
        return result;
      }
    }
    return InteractionResult.PASS;
  }

  /** Implements modifier hooks for a chestplate right clicking a block with an empty hand */
  static InteractEventHack chestplateInteractWithBlock(Player player, Level world, InteractionHand hand, BlockHitResult trace) {
    // only handle chestplate interacts if the current hand is empty
    if (player.getItemInHand(hand).isEmpty() && !player.isSpectator()) {
      // item must be a chestplate
      ItemStack chestplate = player.getItemBySlot(EquipmentSlot.CHEST);
      if (chestplate.is(TinkerTags.Items.CHESTPLATES)) {
        // no turning back, from this point we are fully in charge of interaction logic (since we need to ensure order of the hooks)

        // begin interaction
        ToolStack tool = ToolStack.from(chestplate);
        UseOnContext context = new UseOnContext(player, hand, trace);

        // first, before block use (in forge, onItemUseFirst)
        /*if (event.getUseItem() != Result.DENY)*/ {
          InteractionResult result = onBlockUse(context, tool, chestplate, entry -> entry.getModifier().beforeBlockUse(tool, entry.getLevel(), context, EquipmentSlot.CHEST));
          if (result.consumesAction()) {
            return new InteractEventHack(result, true);
          }
        }

        // next, block interaction
        // empty stack automatically bypasses sneak, so no need to check the hand we interacted with, just need to check the other hand
        BlockPos pos = trace.getBlockPos();
//        Result useBlock = event.getUseBlock();
        if (/*useBlock == Result.ALLOW || (useBlock != Result.DENY
                                         && */((!player.isSecondaryUseActive()/* || player.getItemInHand(Util.getOpposite(hand)).doesSneakBypassUse(player.getLevel(), pos, player)*/))) {
          InteractionResult result = player.level.getBlockState(pos).use(player.level, player, hand, trace);
          if (result.consumesAction()) {
            if (player instanceof ServerPlayer serverPlayer) {
              CriteriaTriggers.ITEM_USED_ON_BLOCK.trigger(serverPlayer, pos, ItemStack.EMPTY);
            }
            return new InteractEventHack(result, true);
          }
        }

        // regular item interaction: must not be deny, and either be allow or not have a cooldown
//        Result useItem = event.getUseItem();
//        event.setCancellationResult(InteractionResult.PASS);
        if (/*useItem != Result.DENY && (useItem == Result.ALLOW || */(!player.getCooldowns().isOnCooldown(chestplate.getItem()))) {
          // finally, after block use (in forge, onItemUse)
          InteractionResult result = onBlockUse(context, tool, chestplate, entry -> entry.getModifier().afterBlockUse(tool, entry.getLevel(), context, EquipmentSlot.CHEST));
          if (result.consumesAction()) {
            if (player instanceof ServerPlayer serverPlayer) {
              CriteriaTriggers.ITEM_USED_ON_BLOCK.trigger(serverPlayer, pos, ItemStack.EMPTY);
            }
            return new InteractEventHack(result, true);
          }
        }

        // did not interact with an entity? try direct interaction
        // needs to be run here as the interact empty hook does not fire when targeting blocks
        return new InteractEventHack(onChestplateUse(player, chestplate, hand), true);
      }
    }
    return new InteractEventHack(InteractionResult.PASS, false);
  }

  /** Implements {@link slimeknights.tconstruct.library.modifiers.Modifier#onToolUse(IToolStackView, int, net.minecraft.world.level.Level, Player, InteractionHand, EquipmentSlot)}, called differently on client and server */
  public static InteractionResult onChestplateUse(Player player, ItemStack chestplate, InteractionHand hand) {
    if (player.getCooldowns().isOnCooldown(chestplate.getItem())) {
      return InteractionResult.PASS;
    }

    // first, run the modifier hook
    ToolStack tool = ToolStack.from(chestplate);
    for (ModifierEntry entry : tool.getModifierList()) {
      InteractionResult result = entry.getModifier().onToolUse(tool, entry.getLevel(), player.level, player, hand, EquipmentSlot.CHEST);
      if (result.consumesAction()) {
        return result;
      }
    }
    return InteractionResult.PASS;
  }

  /** Handles attacking using the chestplate */
  static InteractionResult onChestplateAttack(Player attacker, Level world, InteractionHand hand, Entity target, @Nullable EntityHitResult hitResult) {
    if (attacker.getMainHandItem().isEmpty()) {
      ItemStack chestplate = attacker.getItemBySlot(EquipmentSlot.CHEST);
      if (chestplate.is(TinkerTags.Items.UNARMED)) {
        ToolStack tool = ToolStack.from(chestplate);
        if (!tool.isBroken() && tool.getModifierLevel(TinkerModifiers.unarmed.getId()) > 0) {
          ToolAttackUtil.attackEntity(tool, attacker, InteractionHand.MAIN_HAND, target, ToolAttackUtil.getCooldownFunction(attacker, InteractionHand.MAIN_HAND), false, EquipmentSlot.CHEST);
          return InteractionResult.SUCCESS;
        }
      }
    }
    return InteractionResult.PASS;
  }

  /**
   * Handles interaction from a helmet
   * @param player  Player instance
   * @return true if the player has a modifiable helmet
   */
  public static boolean startArmorInteract(Player player, EquipmentSlot slotType, TooltipKey modifierKey) {
    if (!player.isSpectator()) {
      ItemStack helmet = player.getItemBySlot(slotType);
      if (helmet.is(TinkerTags.Items.ARMOR)) {
        ToolStack tool = ToolStack.from(helmet);
        for (ModifierEntry entry : tool.getModifierList()) {
          IArmorInteractModifier helmetInteract = entry.getModifier().getModule(IArmorInteractModifier.class);
          if (helmetInteract != null && helmetInteract.startArmorInteract(tool, entry.getLevel(), player, slotType, modifierKey)) {
            break;
          }
        }
        return true;
      }
    }
    return false;
  }

  /**
   * Notifies modifiers the helmet keybind was released
   * @param player  Player instance
   * @return true if the player has a modifiable helmet
   */
  public static boolean stopArmorInteract(Player player, EquipmentSlot slotType) {
    if (!player.isSpectator()) {
      ItemStack helmet = player.getItemBySlot(slotType);
      if (helmet.is(TinkerTags.Items.ARMOR)) {
        ToolStack tool = ToolStack.from(helmet);
        for (ModifierEntry entry : tool.getModifierList()) {
          IArmorInteractModifier helmetInteract = entry.getModifier().getModule(IArmorInteractModifier.class);
          if (helmetInteract != null) {
            helmetInteract.stopArmorInteract(tool, entry.getLevel(), player, slotType);
          }
        }
        return true;
      }
    }
    return false;
  }

  public static void init() {
    UseEntityCallback.EVENT.register(InteractionHandler::beforeEntityInteract);
    UseEntityCallback.EVENT.register(TConstruct.getResource("event_phase"), InteractionHandler::afterEntityInteract);
    UseEntityCallback.EVENT.addPhaseOrdering(Event.DEFAULT_PHASE, TConstruct.getResource("event_phase"));
    UseBlockCallback.EVENT.register((player, world, hand, hitResult) -> { // We love poorly written fabric api events
      var result = chestplateInteractWithBlock(player, world, hand, hitResult);
      if (result.cancelled() && result.result() != InteractionResult.SUCCESS) {
        if (player.level.isClientSide) {
          ((LocalPlayer) player).connection.send(new ServerboundUseItemOnPacket(hand, hitResult));
        }
      }
      return result.result();
    });
    AttackEntityCallback.EVENT.register(InteractionHandler::onChestplateAttack);
  }

  private record InteractEventHack(InteractionResult result, boolean cancelled) {}
}
