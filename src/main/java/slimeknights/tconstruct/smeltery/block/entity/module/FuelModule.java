package slimeknights.tconstruct.smeltery.block.entity.module;

import io.github.fabricators_of_create.porting_lib.fluids.FluidStack;
import io.github.fabricators_of_create.porting_lib.transfer.TransferUtil;
import io.github.fabricators_of_create.porting_lib.util.LazyOptional;
import io.github.fabricators_of_create.porting_lib.common.util.NonNullConsumer;
import io.github.fabricators_of_create.porting_lib.common.util.NonNullFunction;
import io.github.fabricators_of_create.porting_lib.util.StorageProvider;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import net.fabricmc.fabric.api.registry.FuelRegistry;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidStorage;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidVariant;
import net.fabricmc.fabric.api.transfer.v1.item.ItemStorage;
import net.fabricmc.fabric.api.transfer.v1.item.ItemVariant;
import net.fabricmc.fabric.api.transfer.v1.storage.SlottedStorage;
import net.fabricmc.fabric.api.transfer.v1.storage.Storage;
import net.fabricmc.fabric.api.transfer.v1.storage.StorageView;
import net.fabricmc.fabric.api.transfer.v1.transaction.Transaction;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.material.Fluid;
import org.jetbrains.annotations.Nullable;
import slimeknights.mantle.block.entity.MantleBlockEntity;
import slimeknights.mantle.util.WeakConsumerWrapper;
import slimeknights.tconstruct.TConstruct;
import slimeknights.tconstruct.library.recipe.fuel.MeltingFuel;
import slimeknights.tconstruct.library.recipe.fuel.MeltingFuelLookup;
import slimeknights.tconstruct.library.utils.TagUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * Module handling fuel consumption for the melter and smeltery
 */
@RequiredArgsConstructor
public class FuelModule implements ContainerData {
  /** Block position that will never be valid in world, used for sync */
  private static final BlockPos NULL_POS = new BlockPos(0, Short.MIN_VALUE, 0);
  /** Temperature used for solid fuels, hot enough to melt iron */
  public static final int SOLID_TEMPERATURE = 800;

  /** Listener to attach to stored capability */
  private final NonNullConsumer<SlottedStorage<FluidVariant>> fluidListener = new WeakConsumerWrapper<>(this, (self, cap) -> self.reset());
  private final NonNullConsumer<SlottedStorage<ItemVariant>> itemListener = new WeakConsumerWrapper<>(this, (self, cap) -> self.reset());

  /** Parent TE */
  private final MantleBlockEntity parent;
  /** Supplier for the list of valid tank positions */
  private final Supplier<List<BlockPos>> tankSupplier;

  /** Last fuel recipe used */
  @Nullable
  private MeltingFuel lastRecipe;
  /** Last fluid handler where fluid was extracted */
  @Nullable
  private StorageProvider<FluidVariant> fluidHandler;
  /** Last item handler where items were extracted */
  @Nullable
  private StorageProvider<ItemVariant> itemHandler;
  /** Position of the last fluid handler */
  private BlockPos lastPos = NULL_POS;


  /** Client fuel display */
  private List<BlockPos> tankDisplayHandlers;
  /** Listener to attach to display capabilities */
  private final NonNullConsumer<SlottedStorage<FluidVariant>> displayListener = new WeakConsumerWrapper<>(this, (self, cap) -> {
    if (self.tankDisplayHandlers != null) {
      self.tankDisplayHandlers.remove(cap);
    }
  });

  /** Current amount of fluid in the TE */
  @Getter
  private int fuel = 0;
  /** Amount of fuel produced by the last source */
  @Getter
  private int fuelQuality = 0;
  /** Temperature of the current fuel */
  @Getter
  private int temperature = 0;


  /*
   * Helpers
   */

  private void reset() {
    this.fluidHandler = null;
    this.itemHandler = null;
    this.tankDisplayHandlers = null;
    this.lastPos = NULL_POS;
  }

  /** Gets a nonnull world instance from the parent */
  private Level getLevel() {
    return Objects.requireNonNull(parent.getLevel(), "Parent tile entity has null world");
  }

  /**
   * Finds a recipe for the given fluid
   * @param fluid  Fluid
   * @return  Recipe
   */
  @Nullable
  private MeltingFuel findRecipe(Fluid fluid) {
    if (lastRecipe != null && lastRecipe.matches(fluid)) {
      return lastRecipe;
    }
    MeltingFuel recipe = MeltingFuelLookup.findFuel(fluid);
    if (recipe != null) {
      lastRecipe = recipe;
    }
    return recipe;
  }


  /* Fuel attributes */

  /**
   * Checks if we have fuel
   * @return  True if we have fuel
   */
  public boolean hasFuel() {
    return fuel > 0;
  }

  /**
   * Consumes fuel from the module
   * @param amount  Amount of fuel to consume
   */
  public void decreaseFuel(int amount) {
    fuel = Math.max(0, fuel - amount);
    parent.setChangedFast();
  }


  /* Fuel updating */

  /* Cache of objects, since they are otherwise created possibly several times */
  private final NonNullFunction<Storage<ItemVariant>,Integer> trySolidFuelConsume = handler -> trySolidFuel(handler, true);
  private final NonNullFunction<Storage<ItemVariant>,Integer> trySolidFuelNoConsume = handler -> trySolidFuel(handler, false);
  private final NonNullFunction<Storage<FluidVariant>,Integer> tryLiquidFuelConsume = handler -> tryLiquidFuel(handler, true);
  private final NonNullFunction<Storage<FluidVariant>,Integer> tryLiquidFuelNoConsume = handler -> tryLiquidFuel(handler, false);

  /**
   * Tries to consume fuel from the given fluid handler
   * @param handler  Handler to consume fuel from
   * @return   Temperature of the consumed fuel, 0 if none found
   */
  private int trySolidFuel(Storage<ItemVariant> handler, boolean consume) {
    for (StorageView<ItemVariant> view : handler.nonEmptyViews()) {
      ItemStack stack = view.getResource().toStack((int) view.getAmount());
      int time = 0;
      if (FuelRegistry.INSTANCE.get(view.getResource().getItem()) != null)
        time = FuelRegistry.INSTANCE.get(view.getResource().getItem()/*, TinkerRecipeTypes.FUEL.get()*/) / 4;
      if (time > 0) {
        if (consume) {
          try (Transaction tx = TransferUtil.getTransaction()) {
            view.extract(view.getResource(), 1, tx);
            if (view.getResource().matches(stack) && !stack.isEmpty()) {
              fuel += time;
              fuelQuality = time;
              temperature = SOLID_TEMPERATURE;
              parent.setChangedFast();
              // return the container
              ItemStack container = view.getResource().toStack((int) view.getAmount()).getRecipeRemainder();
              if (!container.isEmpty()) {
                // if we cannot insert the container back, spit it on the ground
                long inserted = TransferUtil.insertItem(handler, container);
                if (inserted == 0) {
                  Level world = getLevel();
                  double x = (world.random.nextFloat() * 0.5F) + 0.25D;
                  double y = (world.random.nextFloat() * 0.5F) + 0.25D;
                  double z = (world.random.nextFloat() * 0.5F) + 0.25D;
                  BlockPos pos = lastPos == NULL_POS ? parent.getBlockPos() : lastPos;
                  ItemEntity itementity = new ItemEntity(world, pos.getX() + x, pos.getY() + y, pos.getZ() + z, container);
                  itementity.setDefaultPickUpDelay();
                  world.addFreshEntity(itementity);
                }
              }
            } else {
              TConstruct.LOG.error("Invalid item removed from solid fuel handler");
            }
            tx.commit();
          }
        }
        return SOLID_TEMPERATURE;
      }
    }
    return 0;
  }

  /**
   * Gets the mapper function for solid fuel
   * @param consume  If true, fuel is consumed
   * @return Mapper function for solid fuel
   */
  private NonNullFunction<Storage<ItemVariant>,Integer> trySolidFuel(boolean consume) {
    return consume ? trySolidFuelConsume : trySolidFuelNoConsume;
  }

  /**
   * Trys to consume fuel from the given fluid handler
   * @param handler  Handler to consume fuel from
   * @return   Temperature of the consumed fuel, 0 if none found
   */
  private int tryLiquidFuel(Storage<FluidVariant> handler, boolean consume) {
    FluidStack fluid = TransferUtil.firstOrEmpty(handler);
    MeltingFuel recipe = findRecipe(fluid.getFluid());
    if (recipe != null) {
      long amount = recipe.getAmount(fluid.getFluid());
      if (fluid.getAmount() >= amount) {
        if (consume) {
          try (Transaction tx = TransferUtil.getTransaction()) {
            long drained = handler.extract(fluid.getType(), amount, tx);
            tx.commit();
            if (drained != amount) {
              TConstruct.LOG.error("Invalid amount of fuel drained from tank");
            }
          }
          fuel += recipe.getDuration();
          fuelQuality = recipe.getDuration();
          temperature = recipe.getTemperature();
          parent.setChangedFast();
          return temperature;
        } else {
          return recipe.getTemperature();
        }
      }
    }
    return 0;
  }

  /**
   * Gets the mapper function for liquid fuel
   * @param consume  If true, fuel is consumed
   * @return Mapper function for liquid fuel
   */
  private NonNullFunction<Storage<FluidVariant>,Integer> tryLiquidFuel(boolean consume) {
    return consume ? tryLiquidFuelConsume : tryLiquidFuelNoConsume;
  }

  /**
   * Tries to consume fuel from the given position
   * @param pos  Position
   * @return   Temperature of the consumed fuel, 0 if none found
   */
  private int tryFindFuel(BlockPos pos, boolean consume) {
    // if we find a valid cap, try to consume fuel from it
    Storage<FluidVariant> storage = FluidStorage.SIDED.find(getLevel(), pos, null);
    Optional<Integer> temperature = Optional.ofNullable(storage).map(tryLiquidFuel(consume));
    if (temperature.isPresent()) {
      itemHandler = null;
      fluidHandler = StorageProvider.createForFluids(getLevel(), pos);
      tankDisplayHandlers = null;
      lastPos = pos;
      return temperature.get();
    } else {
      // if we find a valid item cap, consume fuel from that
      Storage<ItemVariant> itemCap = ItemStorage.SIDED.find(getLevel(), pos, null);
      temperature = Optional.ofNullable(itemCap).map(trySolidFuel(consume));
      if (temperature.isPresent()) {
        fluidHandler = null;
        tankDisplayHandlers = null;
        itemHandler = StorageProvider.createForItems(getLevel(), pos);
        lastPos = pos;
        return temperature.get();
      }
    }

    return 0;
  }

  /**
   * Attempts to consume fuel from one of the tanks
   * @return  temperature of the found fluid, 0 if none
   */
  public int findFuel(boolean consume) {
    // if we have a handler, try to use that if possible
    Optional<Integer> handlerTemp = Optional.empty();
    if (fluidHandler != null) {
      handlerTemp = getFluidStorage().map(tryLiquidFuel(consume));
    } else if (itemHandler != null) {
      handlerTemp = getItemStorage().map(trySolidFuel(consume));
    // if no handler, try to find one at the last position
    } else if (lastPos != NULL_POS) {
      int posTemp = tryFindFuel(lastPos, consume);
      if (posTemp > 0) {
        return posTemp;
      }
    }

    // if either handler was present, return the temperature
    if (handlerTemp.orElse(0) > 0) {
      return handlerTemp.get();
    }

    // find a new handler among our tanks
    for (BlockPos pos : tankSupplier.get()) {
      // already checked the last position above, no reason to try again
      if (!pos.equals(lastPos)) {
        int posTemp = tryFindFuel(pos, consume);
        if (posTemp > 0) {
          return posTemp;
        }
      }
    }

    // no handler found, tell client of the lack of fuel
    if (consume) {
      temperature = 0;
    }
    return 0;
  }

  public Optional<Storage<FluidVariant>> getFluidStorage() {
    Optional<Storage<FluidVariant>> optional = Optional.ofNullable(fluidHandler).map(x -> x.get(null));
    if (optional.isEmpty()) reset();
    return optional;
  }

  public Optional<Storage<ItemVariant>> getItemStorage() {
    Optional<Storage<ItemVariant>> optional = Optional.ofNullable(itemHandler).map(x -> x.get(null));
    if (optional.isEmpty()) reset();
    return optional;
  }

  /* Tag */
  private static final String TAG_FUEL = "fuel";
  private static final String TAG_TEMPERATURE = "temperature";
  private static final String TAG_LAST_FUEL = "last_fuel_tank";

  /**
   * Reads the fuel from NBT
   * @param nbt  Tag to read from
   */
  public void readFromTag(CompoundTag nbt) {
    if (nbt.contains(TAG_FUEL, Tag.TAG_ANY_NUMERIC)) {
      fuel = nbt.getInt(TAG_FUEL);
    }
    if (nbt.contains(TAG_TEMPERATURE, Tag.TAG_ANY_NUMERIC)) {
      temperature = nbt.getInt(TAG_TEMPERATURE);
    }
    if (nbt.contains(TAG_LAST_FUEL, Tag.TAG_ANY_NUMERIC)) {
      lastPos = TagUtil.readPos(nbt, TAG_LAST_FUEL);
    }
  }

  /**
   * Writes the fuel to NBT
   * @param nbt  Tag to write to
   * @return  Tag written to
   */
  public CompoundTag writeToTag(CompoundTag nbt) {
    nbt.putInt(TAG_FUEL, fuel);
    nbt.putInt(TAG_TEMPERATURE, temperature);
    // technically unneeded for melters, but does not hurt to add
    if (lastPos != NULL_POS) {
      nbt.put(TAG_LAST_FUEL, TagUtil.writePos(lastPos));
    }
    return nbt;
  }


  /* UI syncing */
  private static final int FUEL = 0;
  private static final int FUEL_QUALITY = 1;
  private static final int TEMPERATURE = 2;
  private static final int LAST_X = 3;
  private static final int LAST_Y = 4;
  private static final int LAST_Z = 5;

  @Override
  public int getCount() {
    return 6;
  }

  @Override
  public int get(int index) {
    return switch (index) {
      case FUEL         -> fuel;
      case FUEL_QUALITY -> fuelQuality;
      case TEMPERATURE  -> temperature;
      case LAST_X -> lastPos.getX();
      case LAST_Y -> lastPos.getY();
      case LAST_Z -> lastPos.getZ();
      default -> 0;
    };
  }

  @Override
  public void set(int index, int value) {
    switch (index) {
      case FUEL         -> fuel = value;
      case FUEL_QUALITY -> fuelQuality = value;
      case TEMPERATURE  -> temperature = value;

      // position sync takes three parts
      case LAST_X, LAST_Y, LAST_Z -> {
        // position sync
        switch (index) {
          case LAST_X -> lastPos = new BlockPos(value, lastPos.getY(), lastPos.getZ());
          case LAST_Y -> lastPos = new BlockPos(lastPos.getX(), value, lastPos.getZ());
          case LAST_Z -> lastPos = new BlockPos(lastPos.getX(), lastPos.getY(), value);
        }
        fluidHandler = null;
        itemHandler = null;
        tankDisplayHandlers = null;
      }
    }
  }

  /**
   * Called on client structure update to clear the cached display listeners
   */
  public void clearCachedDisplayListeners() {
    this.tankDisplayHandlers = null;
  }

  /**
   * Called client side to get the fuel info for the current tank
   * Note this relies on the client side fuel handlers containing fuel, which is common for our blocks as show fluid in world.
   * If a tank does not do that this won't work.
   * @return  Fuel info
   */
  public FuelInfo getFuelInfo() {
    List<BlockPos> positions = null;
    // if there is no position, means we have not yet consumed fuel. Just fetch the first tank
    // TODO: should we try to find a valid fuel tank? might be a bit confusing if they have multiple tanks in the structure before melting
    // however, a valid tank is a lot more effort to find

    // Y of -1 is how the UI syncs null
    BlockPos mainTank = lastPos;
    if (mainTank.getY() == NULL_POS.getY()) {
      // if no first, return no fuel info
      positions = tankSupplier.get();
      if (positions.isEmpty()) {
        return FuelInfo.EMPTY;
      }
      mainTank = positions.get(0);
      assert mainTank != null;
    }

    // fetch primary fuel handler
    if (fluidHandler == null && itemHandler == null) {
      Storage<FluidVariant> fluidCap = FluidStorage.SIDED.find(getLevel(), mainTank, null);
      if (fluidCap != null) {
        fluidHandler = StorageProvider.createForFluids(getLevel(), mainTank);
      } else {
        Storage<ItemVariant> itemCap = ItemStorage.SIDED.find(getLevel(), mainTank, null);
        if (itemCap != null) {
          itemHandler = StorageProvider.createForItems(getLevel(), mainTank);
        }
      }
    }
    // if its an item, stop here
    if (itemHandler != null) {
      return FuelInfo.ITEM;
    }

    // determine what fluid we have and hpw many other fluids we have
    FuelInfo info = getFluidStorage().map(handler -> {
      for (StorageView<FluidVariant> view : handler.nonEmptyViews()) {
        FluidStack fluid = new FluidStack(view);
        int temperature = 0;
        if (!fluid.isEmpty()) {
          MeltingFuel fuel = findRecipe(fluid.getFluid());
          if (fuel != null) {
            temperature = fuel.getTemperature();
          }
        }
        return FuelInfo.of(fluid, view.getCapacity(), temperature);
      }
      return FuelInfo.EMPTY;
    }).orElse(FuelInfo.EMPTY);

    // add extra fluid display
    if (!info.isEmpty()) {
      // fetch fluid handler list if missing
      Level world = getLevel();
      if (tankDisplayHandlers == null) {
        tankDisplayHandlers = new ArrayList<>();
        // only need to fetch this if either case requests
        if (positions == null) positions = tankSupplier.get();
        for (BlockPos pos : positions) {
          if (!pos.equals(mainTank)) {
            Storage<FluidVariant> handler = FluidStorage.SIDED.find(world, pos, null);
            if (handler != null) {
              tankDisplayHandlers.add(pos);
            }
          }
        }
      }

      // add display info from each handler
      FluidStack currentFuel = info.getFluid();
      for (BlockPos pos : tankDisplayHandlers) {
        Storage<FluidVariant> handler = FluidStorage.SIDED.find(world, pos, null);
        if (handler != null ) {
          // sum if empty (more capacity) or the same fluid (more amount and capacity)
          for (StorageView<FluidVariant> view : handler.nonEmptyViews()) {
            FluidStack fluid = new FluidStack(view);
            if (fluid.isEmpty()) {
              info.add(0, view.getCapacity());
            } else if (currentFuel.isFluidEqual(fluid)) {
              info.add(fluid.getAmount(), view.getCapacity());
            }
          }
        } else {
          tankDisplayHandlers.remove(pos);
        }
      }
    }

    return info;
  }

  /** Data class to hold information about the current fuel */
  @Getter
  @AllArgsConstructor(access = AccessLevel.PRIVATE)
  public static class FuelInfo {
    /** Empty fuel instance */
    public static final FuelInfo EMPTY = new FuelInfo(FluidStack.EMPTY, 0, 0, 0);
    /** Item fuel instance */
    public static final FuelInfo ITEM = new FuelInfo(FluidStack.EMPTY, 0, 0, SOLID_TEMPERATURE);

    private final FluidStack fluid;
    private long totalAmount;
    private long capacity;
    private final int temperature;

    /**
     * Gets fuel info from the given stack and capacity
     * @param fluid     Fluid
     * @param capacity  Capacity
     * @return  Fuel info
     */
    public static FuelInfo of(FluidStack fluid, long capacity, int temperature) {
      if (fluid.isEmpty()) {
        return EMPTY;
      }
      return new FuelInfo(fluid, fluid.getAmount(), Math.max(capacity, fluid.getAmount()), temperature);
    }

    /**
     * Adds an additional amount and capacity to this info
     * @param amount    Amount to add
     * @param capacity  Capacity to add
     */
    protected void add(long amount, long capacity) {
      this.totalAmount += amount;
      this.capacity += capacity;
    }

    /**
     * Checks if this fuel info is an item
     * @return  True if an item
     */
    public boolean isItem() {
      return this == ITEM;
    }

    /** Checks if this fuel info has no fluid */
    public boolean isEmpty() {
      return fluid.isEmpty() || totalAmount == 0 || capacity == 0;
    }
  }
}
