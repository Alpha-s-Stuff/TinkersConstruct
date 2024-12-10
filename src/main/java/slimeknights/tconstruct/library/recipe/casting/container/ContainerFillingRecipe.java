package slimeknights.tconstruct.library.recipe.casting.container;

import io.github.fabricators_of_create.porting_lib.fluids.FluidStack;
import io.github.fabricators_of_create.porting_lib.transfer.TransferUtil;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import net.fabricmc.fabric.api.transfer.v1.context.ContainerItemContext;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidStorage;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidVariant;
import net.fabricmc.fabric.api.transfer.v1.storage.Storage;
import net.fabricmc.fabric.api.transfer.v1.storage.StorageUtil;
import net.fabricmc.fabric.api.transfer.v1.transaction.Transaction;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.Container;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.Fluid;
import slimeknights.mantle.recipe.IMultiRecipe;
import slimeknights.tconstruct.library.recipe.TinkerRecipeTypes;
import slimeknights.tconstruct.library.recipe.casting.DisplayCastingRecipe;
import slimeknights.tconstruct.library.recipe.casting.ICastingContainer;
import slimeknights.tconstruct.library.recipe.casting.ICastingRecipe;
import slimeknights.tconstruct.smeltery.TinkerSmeltery;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * Casting recipe that takes an arbitrary fluid for a given amount and fills a container
 */
@RequiredArgsConstructor
public abstract class ContainerFillingRecipe implements ICastingRecipe, IMultiRecipe<DisplayCastingRecipe> {
  @Getter
  protected final RecipeType<?> type;
  @Getter
  protected final ResourceLocation id;
  @Getter
  protected final String group;
  @Getter
  protected final int fluidAmount;
  @Getter
  protected final Item container;

  private static Optional<Storage<FluidVariant>> getFluidHandlerItem(ItemStack stack) {
    return Optional.ofNullable(FluidStorage.ITEM.find(stack, ContainerItemContext.withConstant(stack)));
  }

  @Override
  public long getFluidAmount(ICastingContainer inv) {
    FluidVariant fluid = FluidVariant.of(inv.getFluid());
    try (Transaction tx = TransferUtil.getTransaction()) {
      return getFluidHandlerItem(inv.getStack())
        .map(handler -> StorageUtil.simulateInsert(handler, fluid, this.fluidAmount, tx))
        .orElse(0L);
    }
  }

  @Override
  public boolean isConsumed() {
    return true;
  }

  @Override
  public boolean switchSlots() {
    return false;
  }

  @Override
  public int getCoolingTime(ICastingContainer inv) {
    return 5;
  }

  @Override
  public boolean matches(ICastingContainer inv, Level worldIn) {
    ItemStack stack = inv.getStack();
    FluidVariant fluid = FluidVariant.of(inv.getFluid());
    try (Transaction tx = TransferUtil.getTransaction()) {
      return stack.getItem() == this.container.asItem()
           && getFluidHandlerItem(stack)
        .filter(handler -> StorageUtil.simulateInsert(handler, fluid, this.fluidAmount, tx) > 0)
        .isPresent();
    }
  }

  /** @deprecated use {@link ICastingRecipe#assemble(Container, RegistryAccess)} */
  @Override
  @Deprecated
  public ItemStack getResultItem(RegistryAccess registryAccess) {
    return new ItemStack(this.container);
  }

  @Override
  public ItemStack assemble(ICastingContainer inv, RegistryAccess registryAccess) {
    ItemStack stack = inv.getStack().copy();
    ContainerItemContext context = ContainerItemContext.withConstant(stack);
    return Optional.ofNullable(FluidStorage.ITEM.find(stack, context)).map(handler -> {
      try (Transaction tx = TransferUtil.getTransaction()) {
        handler.insert(FluidVariant.of(inv.getFluid(), inv.getFluidTag()), this.fluidAmount, tx);
        tx.commit();
      }
      return context.getItemVariant().toStack((int) context.getAmount());
    }).orElse(stack);
  }

  /* Display */
  /** Cache of items to display for this container */
  private List<DisplayCastingRecipe> displayRecipes = null;

  @Override
  public List<DisplayCastingRecipe> getRecipes() {
    if (displayRecipes == null) {
      List<ItemStack> casts = Collections.singletonList(new ItemStack(container));
      displayRecipes = BuiltInRegistries.FLUID.stream()
                                             .filter(fluid -> fluid.getBucket() != Items.AIR && fluid.isSource(fluid.defaultFluidState()))
                                             .map(fluid -> {
                                               FluidStack fluidStack = new FluidStack(fluid, fluidAmount);
                                               ItemStack stack = new ItemStack(container);
                                               ContainerItemContext context = ContainerItemContext.withConstant(stack);
                                               stack = Optional.ofNullable(FluidStorage.ITEM.find(stack, context)).map(handler -> {
                                                 try (Transaction tx = TransferUtil.getTransaction()) {
                                                   handler.insert(fluidStack.getType(), fluidStack.getAmount(), tx);
                                                   tx.commit();
                                                 }
                                                 return context.getItemVariant().toStack((int) context.getAmount());
                                               }).orElse(stack);
                                               return new DisplayCastingRecipe(getType(), casts, Collections.singletonList(fluidStack), stack, 5, true);
                                             })
                                             .toList();
    }
    return displayRecipes;
  }

  /** Basin implementation */
  public static class Basin extends ContainerFillingRecipe {
    public Basin(ResourceLocation idIn, String groupIn, int fluidAmount, Item containerIn) {
      super(TinkerRecipeTypes.CASTING_BASIN.get(), idIn, groupIn, fluidAmount, containerIn);
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
      return TinkerSmeltery.basinFillingRecipeSerializer.get();
    }
  }

  /** Table implementation */
  public static class Table extends ContainerFillingRecipe {

    public Table(ResourceLocation idIn, String groupIn, int fluidAmount, Item containerIn) {
      super(TinkerRecipeTypes.CASTING_TABLE.get(), idIn, groupIn, fluidAmount, containerIn);
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
      return TinkerSmeltery.tableFillingRecipeSerializer.get();
    }
  }
}
