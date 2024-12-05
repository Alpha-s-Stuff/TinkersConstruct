package slimeknights.tconstruct.world;

import com.google.common.collect.Lists;
import io.github.fabricators_of_create.porting_lib.config.ConfigEvents;
import io.github.fabricators_of_create.porting_lib.config.ConfigType;
import io.github.fabricators_of_create.porting_lib.entity.events.LivingEntityEvents.LivingVisibilityEvent;
import io.github.fabricators_of_create.porting_lib.entity.events.LivingEntityEvents;
import io.github.fabricators_of_create.porting_lib.fluids.FluidStack;
import io.github.fabricators_of_create.porting_lib.tags.Tags;
import net.fabricmc.fabric.api.biome.v1.BiomeModifications;
import net.fabricmc.fabric.api.biome.v1.BiomeSelectionContext;
import net.fabricmc.fabric.api.biome.v1.BiomeSelectors;
import net.fabricmc.fabric.api.loot.v2.LootTableEvents;
import net.fabricmc.fabric.api.loot.v2.LootTableSource;
import net.fabricmc.fabric.api.tag.convention.v1.ConventionalBiomeTags;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.SkullBlock;
import net.minecraft.world.level.dimension.LevelStem;
import net.minecraft.world.level.levelgen.GenerationStep.Decoration;
import net.minecraft.world.level.storage.loot.LootDataManager;
import net.minecraft.world.level.storage.loot.LootPool;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.entries.LootItem;
import net.minecraft.world.level.storage.loot.entries.LootPoolEntryContainer;
import net.minecraft.world.level.storage.loot.functions.SetItemCountFunction;
import net.minecraft.world.level.storage.loot.providers.number.UniformGenerator;
import slimeknights.mantle.loot.function.SetFluidLootFunction;
import slimeknights.tconstruct.TConstruct;
import slimeknights.tconstruct.common.config.Config;
import slimeknights.tconstruct.fluids.TinkerFluids;
import slimeknights.tconstruct.library.json.AddToolDataFunction;
import slimeknights.tconstruct.library.json.RandomMaterial;
import slimeknights.tconstruct.library.recipe.FluidValues;
import slimeknights.tconstruct.shared.block.SlimeType;
import slimeknights.tconstruct.smeltery.TinkerSmeltery;
import slimeknights.tconstruct.tools.TinkerTools;
import slimeknights.tconstruct.tools.stats.ExtraMaterialStats;
import slimeknights.tconstruct.tools.stats.HandleMaterialStats;
import slimeknights.tconstruct.tools.stats.HeadMaterialStats;

import java.util.Arrays;
import java.util.Collection;
import java.util.function.Predicate;

@SuppressWarnings("unused")
public class WorldEvents {
  public static void init() {
    LootTableEvents.MODIFY.register(WorldEvents::onLootTableLoad);
    LivingVisibilityEvent.VISIBILITY.register(WorldEvents::livingVisibility);
    LivingEntityEvents.DROPS.register(WorldEvents::creeperKill);
    ConfigEvents.LOADING.register(config -> {
      if (config.getModId().equals(TConstruct.MOD_ID) && config.getType() == ConfigType.COMMON)
        onBiomeLoad();
    });
  }

  static void onBiomeLoad() {
    // setup for biome checks
    // nether - any biome is fine
    if (Config.COMMON.generateCobalt.get()) {
      BiomeModifications.addFeature(BiomeSelectors.foundInTheNether(), Decoration.UNDERGROUND_DECORATION, TinkerWorld.placedSmallCobaltOreKey);
      BiomeModifications.addFeature(BiomeSelectors.foundInTheNether(), Decoration.UNDERGROUND_DECORATION, TinkerWorld.placedLargeCobaltOreKey);
    }
    // ichor can be anywhere
    if (Config.COMMON.ichorGeodes.get()) {
      BiomeModifications.addFeature(BiomeSelectors.foundInTheNether(), Decoration.LOCAL_MODIFICATIONS, TinkerWorld.placedIchorGeodeKey);
    }
    // end, mostly do stuff in the outer islands
    // slime spawns anywhere, uses the grass
    BiomeModifications.addSpawn(BiomeSelectors.foundInTheEnd(), MobCategory.MONSTER, TinkerWorld.enderSlimeEntity.get(), 10, 2, 4);
    // geodes only on outer islands
    if (Config.COMMON.enderGeodes.get()/* && key != null && !Biomes.THE_END.equals(key)*/) {
      BiomeModifications.addFeature(context -> context.canGenerateIn(LevelStem.END) && context.getBiomeKey() != Biomes.THE_END,Decoration.LOCAL_MODIFICATIONS, TinkerWorld.placedEnderGeodeKey);
    }
    // overworld gets tricky
    // slime spawns anywhere, uses the grass
    BiomeModifications.addSpawn(BiomeSelectors.foundInOverworld(), MobCategory.MONSTER, TinkerWorld.earthSlimeEntity.get(), 100, 2, 4);
    BiomeModifications.addSpawn(BiomeSelectors.foundInOverworld(), MobCategory.MONSTER, TinkerWorld.skySlimeEntity.get(), 100, 2, 4);

    // earth spawns anywhere, sky does not spawn in ocean (looks weird)
    if (Config.COMMON.earthGeodes.get()) {
      BiomeModifications.addFeature(BiomeSelectors.foundInOverworld(), Decoration.LOCAL_MODIFICATIONS, TinkerWorld.placedEarthGeodeKey);
    }
    // sky spawn in non-oceans, they look funny in the ocean as they spawn so high
    if (Config.COMMON.skyGeodes.get()) {
      Predicate<BiomeSelectionContext> context = biomeSelectionContext -> {
        if (!biomeSelectionContext.canGenerateIn(LevelStem.OVERWORLD))
          return false;
        boolean add;
        Holder<Biome> biomeHolder = biomeSelectionContext.getBiomeRegistryEntry();
        ResourceKey<Biome> key = biomeSelectionContext.getBiomeKey();
        boolean hasNoTypes = key == null;
        if (hasNoTypes) {
          add = !biomeHolder.is(ConventionalBiomeTags.OCEAN) && !biomeHolder.is(ConventionalBiomeTags.BEACH) && !biomeHolder.is(ConventionalBiomeTags.RIVER);
        } else {
          add = !biomeHolder.is(Tags.Biomes.IS_WATER) && !biomeHolder.is(ConventionalBiomeTags.BEACH);
        }
        return add;
      };
      BiomeModifications.addFeature(context, Decoration.LOCAL_MODIFICATIONS, TinkerWorld.placedSkyGeodeKey);
    }
  }


  /* Loot injection */

  /**
   * Injects an entry into a loot pool
   * @param lootTable      Loot table event
   * @param poolName   Pool name
   * @param entries    Entry to inject
   */
  private static void injectInto(LootTable lootTable, String poolName, LootPoolEntryContainer... entries) {
    LootPool pool = getPool(lootTable, poolName);
    //noinspection ConstantConditions method is annotated wrongly
    if (pool != null) {
      int oldLength = pool.entries.length;
      pool.entries = Arrays.copyOf(pool.entries, oldLength + entries.length);
      System.arraycopy(entries, 0, pool.entries, oldLength, entries.length);
    }
  }

  public static LootPool getPool(LootTable table, String name) {
    return Lists.newArrayList(table.pools).stream().filter(e -> name.equals(e.getName())).findFirst().orElse(null);
  }

  /** Makes a seed injection loot entry */
  private static LootPoolEntryContainer makeSeed(SlimeType type, int weight) {
    return LootItem.lootTableItem(TinkerWorld.slimeGrassSeeds.get(type)).setWeight(weight)
                    .apply(SetItemCountFunction.setCount(UniformGenerator.between(2, 4))).build();
  }

  /** Makes a sapling injection loot entry */
  private static LootPoolEntryContainer makeSapling(SlimeType type, int weight) {
    return LootItem.lootTableItem(TinkerWorld.slimeSapling.get(type)).setWeight(weight).build();
  }

  static void onLootTableLoad(ResourceManager resourceManager, LootDataManager manager, ResourceLocation name, LootTable.Builder tableBuilder, LootTableSource source) {
    if ("minecraft".equals(name.getNamespace())) {
      switch (name.getPath()) {
        // sky
        case "chests/simple_dungeon":
          if (Config.COMMON.slimyLootChests.get()) {
            injectInto(manager.getLootTable(name), "pool1", makeSeed(SlimeType.EARTH, 3), makeSeed(SlimeType.SKY, 7));
            injectInto(manager.getLootTable(name), "main", makeSapling(SlimeType.EARTH, 3), makeSapling(SlimeType.SKY, 7));
          }
          break;
        // ichor
        case "chests/nether_bridge":
          if (Config.COMMON.slimyLootChests.get()) {
            injectInto(manager.getLootTable(name), "main", makeSeed(SlimeType.BLOOD, 5));
          }
          break;
        case "chests/bastion_bridge":
          if (Config.COMMON.slimyLootChests.get()) {
            injectInto(manager.getLootTable(name), "pool2", makeSapling(SlimeType.BLOOD, 1));
          }
          break;
        // ender
        case "chests/end_city_treasure":
          if (Config.COMMON.slimyLootChests.get()) {
            injectInto(manager.getLootTable(name), "main", makeSeed(SlimeType.ENDER, 5), makeSapling(SlimeType.ENDER, 3));
          }
          break;

        // barter for molten blaze lanterns
        case "gameplay/piglin_bartering": {
          int weight = Config.COMMON.barterBlazingBlood.get();
          if (weight > 0) {
            injectInto(manager.getLootTable(name), "main", LootItem.lootTableItem(TinkerSmeltery.scorchedLantern).setWeight(weight)
                                              .apply(SetFluidLootFunction.builder(new FluidStack(TinkerFluids.blazingBlood.get(), FluidValues.SCORCHED_LANTERN_CAPACITY)))
                                              .apply(SetItemCountFunction.setCount(UniformGenerator.between(1, 4)))
                                              .build());
          }
          break;
        }

          // randomly swap vanilla tool for a tinkers tool
        case "chests/spawn_bonus_chest": {
          int weight = Config.COMMON.tinkerToolBonusChest.get();
          if (weight > 0) {
            RandomMaterial randomHead = RandomMaterial.random(HeadMaterialStats.ID).tier(1).build();
            RandomMaterial firstHandle = RandomMaterial.firstWithStat(HandleMaterialStats.ID); // should be wood
            RandomMaterial randomBinding = RandomMaterial.random(ExtraMaterialStats.ID).tier(1).build();
            injectInto(manager.getLootTable(name), "main", LootItem.lootTableItem(TinkerTools.handAxe.get())
                                              .setWeight(weight)
                                              .apply(AddToolDataFunction.builder()
                                                               .addMaterial(randomHead)
                                                               .addMaterial(firstHandle)
                                                               .addMaterial(randomBinding))
                                              .build());
            injectInto(manager.getLootTable(name), "pool1", LootItem.lootTableItem(TinkerTools.pickaxe.get())
                                               .setWeight(weight)
                                               .apply(AddToolDataFunction.builder()
                                                               .addMaterial(randomHead)
                                                               .addMaterial(firstHandle)
                                                               .addMaterial(randomBinding))
                                               .build());
          }
          break;
        }
      }
    }
  }


  /* Heads */
  static void livingVisibility(LivingVisibilityEvent event) {
    Entity lookingEntity = event.getLookingEntity();
    if (lookingEntity == null) {
      return;
    }
    LivingEntity entity = event.getEntity();
    ItemStack helmet = entity.getItemBySlot(EquipmentSlot.HEAD);
    Item item = helmet.getItem();
    if (item != Items.AIR && TinkerWorld.headItems.contains(item)) {
      if (lookingEntity.getType() == ((TinkerHeadType)((SkullBlock)((BlockItem)item).getBlock()).getType()).getType()) {
        event.modifyVisibility(0.5f);
      }
    }
  }

  static boolean creeperKill(LivingEntity target, DamageSource source, Collection<ItemEntity> drops, int lootingLevel, boolean recentlyHit) {
    if (source != null) {
      Entity entity = source.getEntity();
      if (entity instanceof Creeper creeper) {
        if (creeper.canDropMobsSkull()) {
          LivingEntity dying = target;
          TinkerHeadType headType = TinkerHeadType.fromEntityType(dying.getType());
          if (headType != null && Config.COMMON.headDrops.get(headType).get()) {
            creeper.increaseDroppedSkulls();
            drops.add(dying.spawnAtLocation(TinkerWorld.heads.get(headType)));
          }
        }
      }
    }
    return false;
  }
}
