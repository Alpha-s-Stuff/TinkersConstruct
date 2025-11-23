package slimeknights.tconstruct.plugin.rei;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import dev.architectury.fluid.FluidStack;
import io.github.fabricators_of_create.porting_lib.mixin.common.accessor.RecipeManagerAccessor;
import me.shedaniel.rei.api.client.plugins.REIClientPlugin;
import me.shedaniel.rei.api.client.registry.category.CategoryRegistry;
import me.shedaniel.rei.api.client.registry.display.DisplayRegistry;
import me.shedaniel.rei.api.client.registry.entry.EntryRegistry;
import me.shedaniel.rei.api.client.registry.transfer.TransferHandlerRegistry;
import me.shedaniel.rei.api.common.category.CategoryIdentifier;
import me.shedaniel.rei.api.common.entry.EntryStack;
import me.shedaniel.rei.api.common.entry.type.EntryTypeRegistry;
import me.shedaniel.rei.api.common.util.EntryStacks;
import me.shedaniel.rei.plugin.common.BuiltinPlugin;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidConstants;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.Container;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.material.Fluid;
import slimeknights.mantle.recipe.helper.RecipeHelper;
import slimeknights.tconstruct.common.TinkerTags;
import slimeknights.tconstruct.common.config.Config;
import slimeknights.tconstruct.common.registration.CastItemObject;
import slimeknights.tconstruct.fluids.TinkerFluids;
import slimeknights.tconstruct.library.modifiers.Modifier;
import slimeknights.tconstruct.library.modifiers.ModifierEntry;
import slimeknights.tconstruct.library.recipe.TinkerRecipeTypes;
import slimeknights.tconstruct.library.recipe.alloying.AlloyRecipe;
import slimeknights.tconstruct.library.recipe.casting.IDisplayableCastingRecipe;
import slimeknights.tconstruct.library.recipe.entitymelting.EntityMeltingRecipe;
import slimeknights.tconstruct.library.recipe.fuel.MeltingFuel;
import slimeknights.tconstruct.library.recipe.material.MaterialRecipe;
import slimeknights.tconstruct.library.recipe.melting.MeltingRecipe;
import slimeknights.tconstruct.library.recipe.modifiers.adding.IDisplayModifierRecipe;
import slimeknights.tconstruct.library.recipe.modifiers.severing.SeveringRecipe;
import slimeknights.tconstruct.library.recipe.molding.MoldingRecipe;
import slimeknights.tconstruct.library.recipe.partbuilder.IDisplayPartBuilderRecipe;
import slimeknights.tconstruct.library.tools.SlotType;
import slimeknights.tconstruct.library.tools.item.IModifiableDisplay;
import slimeknights.tconstruct.plugin.jei.entity.DefaultEntityMeltingRecipe;
import slimeknights.tconstruct.plugin.jei.melting.MeltingFuelHandler;
import slimeknights.tconstruct.plugin.jei.partbuilder.MaterialItemList;
import slimeknights.tconstruct.plugin.rei.casting.CastingBasinCategory;
import slimeknights.tconstruct.plugin.rei.casting.CastingDisplay;
import slimeknights.tconstruct.plugin.rei.casting.CastingTableCategory;
import slimeknights.tconstruct.plugin.rei.entity.EntityEntryDefinition;
import slimeknights.tconstruct.plugin.rei.entity.EntityMeltingRecipeCategory;
import slimeknights.tconstruct.plugin.rei.entity.EntityMeltingRecipeDisplay;
import slimeknights.tconstruct.plugin.rei.entity.SeveringCategory;
import slimeknights.tconstruct.plugin.rei.entity.SeveringDisplay;
import slimeknights.tconstruct.plugin.rei.melting.FoundryCategory;
import slimeknights.tconstruct.plugin.rei.melting.MeltingCategory;
import slimeknights.tconstruct.plugin.rei.melting.MeltingDisplay;
import slimeknights.tconstruct.plugin.rei.modifiers.ModifierEntryDefinition;
import slimeknights.tconstruct.plugin.rei.modifiers.ModifierRecipeCategory;
import slimeknights.tconstruct.plugin.rei.modifiers.ModifierRecipeDisplay;
import slimeknights.tconstruct.plugin.rei.partbuilder.PartBuilderCategory;
import slimeknights.tconstruct.plugin.rei.partbuilder.PartBuilderDisplay;
import slimeknights.tconstruct.plugin.rei.partbuilder.PatternEntryDefinition;
import slimeknights.tconstruct.shared.TinkerMaterials;
import slimeknights.tconstruct.smeltery.TinkerSmeltery;
import slimeknights.tconstruct.smeltery.data.SmelteryCompat;
import slimeknights.tconstruct.tables.TinkerTables;

import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

public class REIPlugin implements REIClientPlugin {

  @Override
  public void registerCategories(CategoryRegistry registry) {
    // casting
    registry.add(new CastingBasinCategory());
    registry.add(new CastingTableCategory());
    registry.add(new MoldingRecipeCategory());
    // melting and casting
    registry.add(new MeltingCategory());
    registry.add(new AlloyRecipeCategory());
    registry.add(new EntityMeltingRecipeCategory());
    registry.add(new FoundryCategory());
    // tinker station
    registry.add(new ModifierRecipeCategory());
    registry.add(new SeveringCategory());
    // part builder
    registry.add(new PartBuilderCategory());

    // tables
    registry.addWorkstations(TConstructREIConstants.PART_BUILDER, EntryStacks.of(TinkerTables.partBuilder));
    registry.addWorkstations(TConstructREIConstants.MODIFIERS, EntryStacks.of(TinkerTables.tinkerStation));
    registry.addWorkstations(TConstructREIConstants.MODIFIERS, EntryStacks.of(TinkerTables.tinkersAnvil));
    registry.addWorkstations(TConstructREIConstants.MODIFIERS, EntryStacks.of(TinkerTables.scorchedAnvil));

    // smeltery
    registry.addWorkstations(TConstructREIConstants.MELTING, EntryStacks.of(TinkerSmeltery.searedMelter));
    registry.addWorkstations(BuiltinPlugin.FUEL, EntryStacks.of(TinkerSmeltery.searedHeater));
    addCastingCatalyst(registry, TinkerSmeltery.searedTable, TConstructREIConstants.CASTING_TABLE, TinkerRecipeTypes.MOLDING_TABLE.get());
    addCastingCatalyst(registry, TinkerSmeltery.searedBasin, TConstructREIConstants.CASTING_BASIN, TinkerRecipeTypes.MOLDING_BASIN.get());
    addCatalysts(registry, EntryStacks.of(TinkerSmeltery.smelteryController), TConstructREIConstants.MELTING, TConstructREIConstants.ALLOY, TConstructREIConstants.ENTITY_MELTING);

    // foundry
    registry.addWorkstations(TConstructREIConstants.ALLOY, EntryStacks.of(TinkerSmeltery.scorchedAlloyer));
    addCastingCatalyst(registry, TinkerSmeltery.scorchedTable, TConstructREIConstants.CASTING_TABLE, TinkerRecipeTypes.MOLDING_TABLE.get());
    addCastingCatalyst(registry, TinkerSmeltery.scorchedBasin, TConstructREIConstants.CASTING_BASIN, TinkerRecipeTypes.MOLDING_BASIN.get());
    registry.addWorkstations(TConstructREIConstants.FOUNDRY, EntryStacks.of(TinkerSmeltery.foundryController));

    // modifiers
    for (Holder<Item> item : Objects.requireNonNull(Registry.ITEM.getTagOrEmpty(TinkerTags.Items.MELEE))) {
      registry.addWorkstations(TConstructREIConstants.SEVERING, EntryStacks.of(IModifiableDisplay.getDisplayStack(item.value())));
    }
  }

  @Override
  public void registerEntryTypes(EntryTypeRegistry registry) {
    registry.register(TConstructREIConstants.ENTITY_TYPE, new EntityEntryDefinition());
    registry.register(TConstructREIConstants.MODIFIER_TYPE, new ModifierEntryDefinition());
    registry.register(TConstructREIConstants.PATTERN_TYPE, new PatternEntryDefinition());
  }

  @Override
  public void registerEntries(EntryRegistry registry) {
    RecipeManager manager = Minecraft.getInstance().level.getRecipeManager();
    List<ModifierEntry> modifiers = Collections.emptyList();
    if (Config.CLIENT.showModifiersInJEI.get()) {
      modifiers = RecipeHelper.getJEIRecipes(manager, TinkerRecipeTypes.TINKER_STATION.get(), IDisplayModifierRecipe.class)
        .stream()
        .map(recipe -> recipe.getDisplayResult().getModifier())
        .distinct()
        .sorted(Comparator.comparing(Modifier::getId))
        .map(mod -> new ModifierEntry(mod, 1))
        .collect(Collectors.toList());
    }

    modifiers.forEach(entry -> registry.addEntry(EntryStack.of(TConstructREIConstants.MODIFIER_TYPE, entry)));

    // hide knightslime and slimesteel until implemented
    removeFluid(registry, TinkerFluids.moltenSoulsteel.get(), TinkerFluids.moltenSoulsteel.asItem());
    removeFluid(registry, TinkerFluids.moltenKnightslime.get(), TinkerFluids.moltenKnightslime.asItem());
    // hide compat that is not present
    for (SmelteryCompat compat : SmelteryCompat.values()) {
      Iterable<Holder<Item>> ingot = getTag(new ResourceLocation("c", "ingots/" + compat.getName()));
      if (Iterables.isEmpty(ingot)) {
        removeFluid(registry, compat.getFluid().get(), compat.getBucket());
      }
    }
    if (!FabricLoader.getInstance().isModLoaded("ceramics")) {
      removeFluid(registry, TinkerFluids.moltenPorcelain.get(), TinkerFluids.moltenPorcelain.asItem());
    }
    optionalCast(registry, TinkerSmeltery.plateCast);
    optionalCast(registry, TinkerSmeltery.gearCast);
    optionalCast(registry, TinkerSmeltery.coinCast);
    optionalCast(registry, TinkerSmeltery.wireCast);
    optionalItem(registry, TinkerMaterials.necroniumBone, "ingots/uranium");
  }

  @Override
  public void registerDisplays(DisplayRegistry registry) {
    assert Minecraft.getInstance().level != null;
    RecipeManager manager = Minecraft.getInstance().level.getRecipeManager();
    // casting
    List<IDisplayableCastingRecipe> castingBasinRecipes = RecipeHelper.getJEIRecipes(manager, TinkerRecipeTypes.CASTING_BASIN.get(), IDisplayableCastingRecipe.class);
    castingBasinRecipes.forEach(castingBasinRecipe -> registry.add(new CastingDisplay(TConstructREIConstants.CASTING_BASIN, castingBasinRecipe)));
    List<IDisplayableCastingRecipe> castingTableRecipes = RecipeHelper.getJEIRecipes(manager, TinkerRecipeTypes.CASTING_TABLE.get(), IDisplayableCastingRecipe.class);
    castingTableRecipes.forEach(castingTableRecipe -> registry.add(new CastingDisplay(TConstructREIConstants.CASTING_TABLE, castingTableRecipe)));
    // melting
    List<MeltingRecipe> meltingRecipes = RecipeHelper.getJEIRecipes(manager, TinkerRecipeTypes.MELTING.get(), MeltingRecipe.class);
    meltingRecipes.forEach(meltingRecipe -> registry.add(new MeltingDisplay(meltingRecipe, TConstructREIConstants.MELTING), meltingRecipes));
    meltingRecipes.forEach(meltingRecipe -> registry.add(new MeltingDisplay(meltingRecipe, TConstructREIConstants.FOUNDRY), meltingRecipes));
    MeltingFuelHandler.setMeltngFuels(RecipeHelper.getRecipes(manager, TinkerRecipeTypes.FUEL.get(), MeltingFuel.class));

    // entity melting
    List<EntityMeltingRecipe> entityMeltingRecipes = RecipeHelper.getJEIRecipes(manager, TinkerRecipeTypes.ENTITY_MELTING.get(), EntityMeltingRecipe.class);
    // generate a "default" recipe for all other entity types
    entityMeltingRecipes.add(new DefaultEntityMeltingRecipe(entityMeltingRecipes));
    entityMeltingRecipes.forEach(entityMeltingRecipe -> registry.add(new EntityMeltingRecipeDisplay(entityMeltingRecipe)));

    // alloying
    List<AlloyRecipe> alloyRecipes = RecipeHelper.getJEIRecipes(manager, TinkerRecipeTypes.ALLOYING.get(), AlloyRecipe.class);
    alloyRecipes.forEach(alloyRecipe -> registry.add(new AlloyDisplay(alloyRecipe)));

    // molding
    List<MoldingRecipe> moldingRecipes = ImmutableList.<MoldingRecipe>builder()
      .addAll(RecipeHelper.getJEIRecipes(manager, TinkerRecipeTypes.MOLDING_TABLE.get(), MoldingRecipe.class))
      .addAll(RecipeHelper.getJEIRecipes(manager, TinkerRecipeTypes.MOLDING_BASIN.get(), MoldingRecipe.class))
      .build();
    moldingRecipes.forEach(moldingRecipe -> registry.add(new MoldingRecipeDisplay(moldingRecipe)));

    // modifiers
    List<IDisplayModifierRecipe> modifierRecipes = RecipeHelper.getJEIRecipes(manager, TinkerRecipeTypes.TINKER_STATION.get(), IDisplayModifierRecipe.class)
      .stream()
      .sorted((r1, r2) -> {
        SlotType t1 = r1.getSlotType();
        SlotType t2 = r2.getSlotType();
        String n1 = t1 == null ? "zzzzzzzzzz" : t1.getName();
        String n2 = t2 == null ? "zzzzzzzzzz" : t2.getName();
        return n1.compareTo(n2);
      }).collect(Collectors.toList());
    modifierRecipes.forEach(modifierRecipe -> registry.add(new ModifierRecipeDisplay(modifierRecipe)));

    // beheading
    List<SeveringRecipe> severingRecipes = RecipeHelper.getJEIRecipes(manager, TinkerRecipeTypes.SEVERING.get(), SeveringRecipe.class);
    severingRecipes.forEach(severingRecipe -> registry.add(new SeveringDisplay(severingRecipe)));

    // part builder
    List<MaterialRecipe> materialRecipes = RecipeHelper.getRecipes(manager, TinkerRecipeTypes.MATERIAL.get(), MaterialRecipe.class);
    MaterialItemList.setRecipes(materialRecipes);
    List<IDisplayPartBuilderRecipe> partRecipes = RecipeHelper.getJEIRecipes(manager, TinkerRecipeTypes.PART_BUILDER.get(), IDisplayPartBuilderRecipe.class);
    partRecipes.forEach(partRecipe -> registry.add(new PartBuilderDisplay(partRecipe)));
  }

  @Override
  public void registerTransferHandlers(TransferHandlerRegistry registry) {
//    registry.register();
  }

//  @Override
//  public void registerRecipeTransferHandlers(IRecipeTransferRegistration registration) {
//    registration.addRecipeTransferHandler(new CraftingStationTransferInfo());
//    registration.addRecipeTransferHandler(new TinkerStationTransferInfo());
//  }

  /**
   * Removes a fluid from JEI
   * @param manager  Manager
   * @param fluid    Fluid to remove
   * @param bucket   Fluid bucket to remove
   */
  private static void removeFluid(EntryRegistry manager, Fluid fluid, Item bucket) {
    manager.removeEntry(EntryStacks.of(FluidStack.create(fluid, FluidConstants.BUCKET)));
    manager.removeEntry(EntryStacks.of(bucket));
  }

  /** Helper to get an item tag */
  private static Iterable<Holder<Item>> getTag(ResourceLocation name) {
    return getTag(TagKey.create(Registry.ITEM_REGISTRY, name));
  }

  /** Helper to get an item tag */
  private static Iterable<Holder<Item>> getTag(TagKey<Item> name) {
    return Objects.requireNonNull(Registry.ITEM.getTagOrEmpty(name));
  }

  /**
   * Hides an item if the related tag is empty
   * @param manager  Ingredient manager
   * @param item     Cast instance
   * @param tagName  Tag to check
   */
  @SuppressWarnings("SameParameterValue")
  private static void optionalItem(EntryRegistry manager, ItemLike item, String tagName) {
    Iterable<Holder<Item>> tag = getTag(new ResourceLocation("c", tagName));
    if (Iterables.isEmpty(tag)) {
      manager.removeEntry(EntryStacks.of(item));
    }
  }

  /**
   * Hides casts if the related tag is empty
   * @param manager  Ingredient manager
   * @param cast     Cast instance
   */
  private static void optionalCast(EntryRegistry manager, CastItemObject cast) {
    Iterable<Holder<Item>> tag = getTag(new ResourceLocation("c", cast.getName().getPath() + "s"));
    if (Iterables.isEmpty(tag)) {
      manager.addEntries(cast.values().stream().map(EntryStacks::of).collect(Collectors.toList()));
    }
  }

  /**
   * Adds an item as a casting catalyst, and as a molding catalyst if it has molding recipes
   * @param registry     Catalyst regisry
   * @param item         Item to add
   * @param ownCategory  Category to always add
   * @param type         Molding recipe type
   */
  private static <T extends Recipe<C>, C extends Container> void addCastingCatalyst(CategoryRegistry registry, ItemLike item, CategoryIdentifier<?> ownCategory, RecipeType<MoldingRecipe> type) {
    EntryStack<ItemStack> stack = EntryStacks.of(item);
    registry.addWorkstations(ownCategory, stack);
    assert Minecraft.getInstance().level != null;
    if (!((RecipeManagerAccessor)Minecraft.getInstance().level.getRecipeManager()).port_lib$byType(type).isEmpty()) {
      registry.addWorkstations(TConstructREIConstants.MOLDING, stack);
    }
  }

  private static void addCatalysts(CategoryRegistry registry, EntryStack<ItemStack> entryStack, CategoryIdentifier<?> ...categoryIdentifiers) {
    for (CategoryIdentifier<?> categoryIdentifier : categoryIdentifiers)
      registry.addWorkstations(categoryIdentifier, entryStack);
  }
}
