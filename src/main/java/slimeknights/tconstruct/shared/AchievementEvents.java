package slimeknights.tconstruct.shared;

import io.github.fabricators_of_create.porting_lib.event.common.ItemCraftedCallback;
import io.github.fabricators_of_create.porting_lib.entity.events.LivingEntityEvents;
import net.fabricmc.fabric.api.entity.FakePlayer;
import net.minecraft.advancements.Advancement;
import net.minecraft.advancements.AdvancementProgress;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.Container;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
//import slimeknights.tconstruct.library.utils.TagUtil;
//import slimeknights.tconstruct.tools.common.entity.EntityArrow;
//import slimeknights.tconstruct.tools.tools.Pickaxe;

// TODO: reevaluate
public final class AchievementEvents {

  public static void init() {
    LivingEntityEvents.HURT.register(AchievementEvents::onDamageEntity);
    ItemCraftedCallback.EVENT.register(AchievementEvents::onCraft);
  }

  private static final String ADVANCEMENT_STORY_ROOT = "minecraft:story/root";
  private static final String ADVANCEMENT_STONE_PICK = "minecraft:story/upgrade_tools";
  private static final String ADVANCEMENT_IRON_PICK = "minecraft:story/iron_tools";
  private static final String ADVANCEMENT_SHOOT_ARROW = "minecraft:adventure/shoot_arrow";

  public static void onCraft(Player player, ItemStack crafted, Container craftMatrix) {
    if (player == null || player instanceof FakePlayer || !(player instanceof ServerPlayer playerMP) || crafted.isEmpty()) {
      return;
    }
    Item item = crafted.getItem();
    if (item instanceof BlockItem && ((BlockItem) item).getBlock() == Blocks.CRAFTING_TABLE) {
      grantAdvancement(playerMP, ADVANCEMENT_STORY_ROOT);
    }
    // fire vanilla pickaxe crafting when crafting tinkers picks (hammers also count for completeness sake)
    /*if (item instanceof Pickaxe) {
      int harvestLevel = TagUtil.getToolStats(event.getCrafting()).harvestLevel;
      if (harvestLevel > 0) {
        grantAdvancement(playerMP, ADVANCEMENT_STONE_PICK);
      }
      if (harvestLevel > 1) {
        grantAdvancement(playerMP, ADVANCEMENT_IRON_PICK);
      }
    }*/
  }

  public static float onDamageEntity(DamageSource source, LivingEntity damaged, float amount) {
    if (source.is(DamageTypeTags.IS_PROJECTILE) && !(source.getEntity() instanceof FakePlayer) && source.getEntity() instanceof ServerPlayer) {// && source.getImmediateSource() instanceof EntityArrow) {
      grantAdvancement((ServerPlayer) source.getEntity(), ADVANCEMENT_SHOOT_ARROW);
    }
    return amount;
  }

  private static void grantAdvancement(ServerPlayer playerMP, String advancementResource) {
    MinecraftServer server = playerMP.getServer();
    if (server != null) {
      Advancement advancement = server.getAdvancements().getAdvancement(new ResourceLocation(advancementResource));
      if (advancement != null) {
        AdvancementProgress advancementProgress = playerMP.getAdvancements().getOrStartProgress(advancement);
        if (!advancementProgress.isDone()) {
          // we use playerAdvancements.grantCriterion instead of progress.grantCriterion for the visibility stuff and toasts
          advancementProgress.getRemainingCriteria().forEach(criterion -> playerMP.getAdvancements().award(advancement, criterion));
        }
      }
    }
  }

  private AchievementEvents() {}
}
