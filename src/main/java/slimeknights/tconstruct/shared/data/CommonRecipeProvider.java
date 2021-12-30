package slimeknights.tconstruct.shared.data;

import net.minecraft.data.DataGenerator;
import net.minecraft.data.recipes.FinishedRecipe;
import net.minecraft.data.recipes.ShapedRecipeBuilder;
import net.minecraft.data.recipes.ShapelessRecipeBuilder;
import net.minecraft.data.recipes.SimpleCookingRecipeBuilder;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.common.Tags;
import slimeknights.mantle.recipe.data.ConsumerWrapperBuilder;
import slimeknights.tconstruct.common.TinkerTags;
import slimeknights.tconstruct.common.data.BaseRecipeProvider;
import slimeknights.tconstruct.common.json.ConfigEnabledCondition;
import slimeknights.tconstruct.fluids.TinkerFluids;
import slimeknights.tconstruct.library.data.recipe.ICommonRecipeHelper;
import slimeknights.tconstruct.library.recipe.FluidValues;
import slimeknights.tconstruct.library.recipe.casting.ItemCastingRecipeBuilder;
import slimeknights.tconstruct.shared.TinkerCommons;
import slimeknights.tconstruct.shared.TinkerMaterials;
import slimeknights.tconstruct.shared.block.ClearStainedGlassBlock.GlassColor;
import slimeknights.tconstruct.shared.block.SlimeType;
import slimeknights.tconstruct.smeltery.TinkerSmeltery;
import slimeknights.tconstruct.tables.TinkerTables;
import slimeknights.tconstruct.world.TinkerWorld;

import java.util.function.Consumer;

public class CommonRecipeProvider extends BaseRecipeProvider implements ICommonRecipeHelper {
  public CommonRecipeProvider(DataGenerator generator) {
    super(generator);
  }

  @Override
  public String getName() {
    return "Tinkers' Construct Common Recipes";
  }

  @Override
  protected void buildCraftingRecipes(Consumer<FinishedRecipe> consumer) {
    this.addCommonRecipes(consumer);
    this.addMaterialRecipes(consumer);
  }

  private void addCommonRecipes(Consumer<FinishedRecipe> consumer) {
    // firewood and lavawood
    String folder = "common/firewood/";
    slabStairsCrafting(consumer, TinkerCommons.blazewood, folder, false);
    slabStairsCrafting(consumer, TinkerCommons.lavawood, folder, false);

    // nahuatl
    slabStairsCrafting(consumer, TinkerMaterials.nahuatl, folder, false);
    ShapedRecipeBuilder.shaped(TinkerMaterials.nahuatl.getFence(), 6)
                       .pattern("WWW").pattern("WWW")
                       .define('W', TinkerMaterials.nahuatl)
                       .unlockedBy("has_planks", has(TinkerMaterials.nahuatl))
                       .save(consumer, modResource(folder + "nahuatl_fence"));

    // mud bricks
    slabStairsCrafting(consumer, TinkerCommons.mudBricks, "common/", false);

    // book
    ShapelessRecipeBuilder.shapeless(TinkerCommons.materialsAndYou)
                          .requires(Items.BOOK)
                          .requires(TinkerTables.pattern)
                          .unlockedBy("has_item", has(TinkerTables.pattern))
                          .save(consumer, prefix(TinkerCommons.materialsAndYou, "common/"));
    ShapelessRecipeBuilder.shapeless(TinkerCommons.tinkersGadgetry)
                          .requires(Items.BOOK)
                          .requires(SlimeType.SKY.getSlimeballTag())
                          .unlockedBy("has_item", has(SlimeType.SKY.getSlimeballTag()))
                          .save(consumer, prefix(TinkerCommons.tinkersGadgetry, "common/"));
    ShapelessRecipeBuilder.shapeless(TinkerCommons.punySmelting)
                          .requires(Items.BOOK)
                          .requires(TinkerSmeltery.grout)
                          .unlockedBy("has_item", has(TinkerSmeltery.grout))
                          .save(consumer, prefix(TinkerCommons.punySmelting, "common/"));
    ItemCastingRecipeBuilder.tableRecipe(TinkerCommons.mightySmelting)
                            .setFluidAndTime(TinkerFluids.searedStone, false, FluidValues.INGOT)
                            .setCast(Items.BOOK, true)
                            .save(consumer, prefix(TinkerCommons.mightySmelting, "common/"));
    ShapelessRecipeBuilder.shapeless(TinkerCommons.fantasticFoundry)
                          .requires(Items.BOOK)
                          .requires(TinkerSmeltery.netherGrout)
                          .unlockedBy("has_item", has(TinkerSmeltery.netherGrout))
                          .save(consumer, prefix(TinkerCommons.fantasticFoundry, "common/"));
    ShapelessRecipeBuilder.shapeless(TinkerCommons.encyclopedia)
                          .requires(TinkerCommons.materialsAndYou)
                          .requires(TinkerCommons.punySmelting)
                          .requires(TinkerCommons.mightySmelting)
                          .requires(TinkerCommons.fantasticFoundry)
                          .unlockedBy("has_item", has(TinkerCommons.fantasticFoundry))
                          .save(consumer, prefix(TinkerCommons.encyclopedia, "common/"));

    // glass
    folder = "common/glass/";
    ShapedRecipeBuilder.shaped(TinkerCommons.clearGlassPane, 16)
                       .define('#', TinkerCommons.clearGlass)
                       .pattern("###")
                       .pattern("###")
                       .unlockedBy("has_block", has(TinkerCommons.clearGlass))
                       .save(consumer, prefix(TinkerCommons.clearGlassPane, folder));
    for (GlassColor color : GlassColor.values()) {
      Block block = TinkerCommons.clearStainedGlass.get(color);
      ShapedRecipeBuilder.shaped(block, 8)
                         .define('#', TinkerCommons.clearGlass)
                         .define('X', color.getDye().getTag())
                         .pattern("###")
                         .pattern("#X#")
                         .pattern("###")
                         .group(modPrefix("stained_clear_glass"))
                         .unlockedBy("has_clear_glass", has(TinkerCommons.clearGlass))
                         .save(consumer, prefix(block, folder));
      Block pane = TinkerCommons.clearStainedGlassPane.get(color);
      ShapedRecipeBuilder.shaped(pane, 16)
                         .define('#', block)
                         .pattern("###")
                         .pattern("###")
                         .group(modPrefix("stained_clear_glass_pane"))
                         .unlockedBy("has_block", has(block))
                         .save(consumer, prefix(pane, folder));
      ShapedRecipeBuilder.shaped(pane, 8)
                         .define('#', TinkerCommons.clearGlassPane)
                         .define('X', color.getDye().getTag())
                         .pattern("###")
                         .pattern("#X#")
                         .pattern("###")
                         .group(modPrefix("stained_clear_glass_pane"))
                         .unlockedBy("has_clear_glass", has(TinkerCommons.clearGlassPane))
                         .save(consumer, wrap(pane, folder, "_from_panes"));
    }
    // fix vanilla recipes not using tinkers glass
    String glassVanillaFolder = folder + "vanilla/";
    Consumer<FinishedRecipe> vanillaGlassConsumer = withCondition(consumer, ConfigEnabledCondition.GLASS_RECIPE_FIX);
    ShapedRecipeBuilder.shaped(Blocks.BEACON)
                       .define('S', Items.NETHER_STAR)
                       .define('G', Tags.Items.GLASS_COLORLESS)
                       .define('O', Blocks.OBSIDIAN)
                       .pattern("GGG")
                       .pattern("GSG")
                       .pattern("OOO")
                       .unlockedBy("has_nether_star", has(Items.NETHER_STAR))
                       .save(vanillaGlassConsumer, prefix(Blocks.BEACON, glassVanillaFolder));
    ShapedRecipeBuilder.shaped(Blocks.DAYLIGHT_DETECTOR)
                       .define('Q', Items.QUARTZ)
                       .define('G', Tags.Items.GLASS_COLORLESS)
                       .define('W', ItemTags.WOODEN_SLABS)
                       .pattern("GGG")
                       .pattern("QQQ")
                       .pattern("WWW")
                       .unlockedBy("has_quartz", has(Items.QUARTZ))
                       .save(vanillaGlassConsumer, prefix(Blocks.DAYLIGHT_DETECTOR, glassVanillaFolder));
    ShapedRecipeBuilder.shaped(Items.END_CRYSTAL)
                       .define('T', Items.GHAST_TEAR)
                       .define('E', Items.ENDER_EYE)
                       .define('G', Tags.Items.GLASS_COLORLESS)
                       .pattern("GGG")
                       .pattern("GEG")
                       .pattern("GTG")
                       .unlockedBy("has_ender_eye", has(Items.ENDER_EYE))
                       .save(vanillaGlassConsumer, prefix(Items.END_CRYSTAL, glassVanillaFolder));
    ShapedRecipeBuilder.shaped(Items.GLASS_BOTTLE, 3)
                       .define('#', Tags.Items.GLASS_COLORLESS)
                       .pattern("# #")
                       .pattern(" # ")
                       .unlockedBy("has_glass", has(Tags.Items.GLASS_COLORLESS))
                       .save(vanillaGlassConsumer, prefix(Items.GLASS_BOTTLE, glassVanillaFolder));


    // vanilla recipes
    ShapelessRecipeBuilder.shapeless(Items.FLINT)
                          .requires(Blocks.GRAVEL)
                          .requires(Blocks.GRAVEL)
                          .requires(Blocks.GRAVEL)
                          .unlockedBy("has_item", has(Blocks.GRAVEL))
                          .save(
                            ConsumerWrapperBuilder.wrap()
                                                  .addCondition(ConfigEnabledCondition.GRAVEL_TO_FLINT)
                                                  .build(consumer),
                            modResource("common/flint"));
  }

  private void addMaterialRecipes(Consumer<FinishedRecipe> consumer) {
    String folder = "common/materials/";

    // ores
    metalCrafting(consumer, TinkerMaterials.cobalt, folder);
    // tier 3
    metalCrafting(consumer, TinkerMaterials.slimesteel, folder);
    metalCrafting(consumer, TinkerMaterials.tinkersBronze, folder);
    metalCrafting(consumer, TinkerMaterials.roseGold, folder);
    metalCrafting(consumer, TinkerMaterials.pigIron, folder);
    // tier 4
    metalCrafting(consumer, TinkerMaterials.queensSlime, folder);
    metalCrafting(consumer, TinkerMaterials.manyullyn, folder);
    metalCrafting(consumer, TinkerMaterials.hepatizon, folder);
    //registerMineralRecipes(consumer, TinkerMaterials.soulsteel,   folder);
    packingRecipe(consumer, "ingot", Items.COPPER_INGOT,    "nugget", TinkerMaterials.copperNugget,    TinkerTags.Items.NUGGETS_COPPER,    folder);
    packingRecipe(consumer, "ingot", Items.NETHERITE_INGOT, "nugget", TinkerMaterials.netheriteNugget, TinkerTags.Items.NUGGETS_NETHERITE, folder);
    // tier 5
    //registerMineralRecipes(consumer, TinkerMaterials.knightslime, folder);

    // smelt ore into ingots, must use a blast furnace for nether ores
    Item cobaltIngot = TinkerMaterials.cobalt.getIngot();
    SimpleCookingRecipeBuilder.blasting(Ingredient.of(TinkerWorld.cobaltOre), cobaltIngot, 1.5f, 200)
                              .unlockedBy("has_item", has(TinkerWorld.cobaltOre))
                              .save(consumer, wrap(cobaltIngot, folder, "_smelting"));
  }
}
