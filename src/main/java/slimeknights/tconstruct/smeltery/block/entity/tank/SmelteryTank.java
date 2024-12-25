package slimeknights.tconstruct.smeltery.block.entity.tank;

import com.google.common.collect.Lists;
import io.github.fabricators_of_create.porting_lib.fluids.FluidStack;
import lombok.AllArgsConstructor;
import lombok.Getter;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidVariant;
import net.fabricmc.fabric.api.transfer.v1.storage.SlottedStorage;
import net.fabricmc.fabric.api.transfer.v1.storage.StorageView;
import net.fabricmc.fabric.api.transfer.v1.storage.base.SingleSlotStorage;
import net.fabricmc.fabric.api.transfer.v1.transaction.TransactionContext;
import net.fabricmc.fabric.api.transfer.v1.transaction.base.SnapshotParticipant;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.Level;
import slimeknights.mantle.block.entity.MantleBlockEntity;
import slimeknights.tconstruct.common.network.TinkerNetwork;
import slimeknights.tconstruct.smeltery.block.entity.tank.ISmelteryTankHandler.FluidChange;
import slimeknights.tconstruct.smeltery.network.SmelteryTankUpdatePacket;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;

/**
 * Fluid handler implementation for the smeltery
 */
@SuppressWarnings("UnstableApiUsage")
public class SmelteryTank<T extends MantleBlockEntity & ISmelteryTankHandler> extends SnapshotParticipant<SmelteryTank.FluidSnapshot> implements SlottedStorage<FluidVariant> {
  private final T parent;
  /** Fluids actually contained in the tank */
  @Getter
  private List<FluidStack> fluids;
  /** Maximum capacity of the smeltery */
  private long capacity;
  /** Current amount of fluid in the tank */
  @Getter
  private long contained;

  public SmelteryTank(T parent) {
    fluids = Lists.newArrayList();
    capacity = 0;
    contained = 0;
    this.parent = parent;
  }

  /**
   * Called when the fluids change to sync to client
   */
  public void syncFluids() {
    Level world = parent.getLevel();
    if (world != null && !world.isClientSide) {
      BlockPos pos = parent.getBlockPos();
      TinkerNetwork.getInstance().sendToClientsAround(new SmelteryTankUpdatePacket(pos, fluids), world, pos);
    }
  }


  /* Capacity and space */

  /**
   * Updates the maximum tank capacity
   * @param maxCapacity  New max capacity
   */
  public void setCapacity(long maxCapacity) {
    this.capacity = maxCapacity;
  }

  /**
   * Gets the maximum amount of space in the smeltery tank
   * @return  Tank capacity
   */
  public long getCapacity() {
    return capacity;
  }

  /**
   * Gets the amount of empty space in the tank
   * @return  Remaining space in the tank
   */
  public long getRemainingSpace() {
    if (contained >= capacity) {
      return 0;
    }
    return capacity - contained;
  }


  /* Fluids */

  @Override
  public int getSlotCount() {
    if (contained < capacity) {
      return fluids.size() + 1;
    }
    return fluids.size();
  }

  @Override
  public SingleSlotStorage<FluidVariant> getSlot(int slot) {
    return new FluidStackSlot(getFluidInTank(slot), slot);
  }

  @Nonnull
  public FluidStack getFluidInTank(int tank) {
    if (tank < 0 || tank >= fluids.size()) {
      return FluidStack.EMPTY;
    }
    return fluids.get(tank);
  }

  public long getTankCapacity(int tank) {
    if (tank < 0) {
      return 0;
    }
    // index of the tank size means the "empty" segment
    long remaining = capacity - contained;
    if (tank == fluids.size()) {
      return remaining;
    }
    // any valid index, return the amount contained and the extra space
    return fluids.get(tank).getAmount() + remaining;
  }

  /**
   * Moves the fluid with the passed index to the beginning/bottom of the fluid tank stack
   * @param index  Index to move
   */
  public void moveFluidToBottom(int index) {
    if (index < fluids.size()) {
      FluidStack fluid = fluids.get(index);
      fluids.remove(index);
      fluids.add(0, fluid);
      parent.notifyFluidsChanged(FluidChange.CHANGED, FluidStack.EMPTY);
    }
  }


  /* Filling and draining */

  @Override
  public long insert(FluidVariant resource, long maxAmount, TransactionContext transaction) {
    // if full or nothing being filled, do nothing
    if (contained >= capacity || maxAmount <= 0 || resource.isBlank()) {
      return 0;
    }

    // determine how much we can fill
    long usable = Math.min(capacity - contained, maxAmount);
    // could be negative if the smeltery size changes then you try filling it
    if (usable <= 0) {
      return 0;
    }

    updateSnapshots(transaction);

    // add contained fluid amount
    contained += usable;

    // check if we already have the given liquid
    for (FluidStack fluid : fluids) {
      if (fluid.isFluidEqual(resource)) {
        // yup. add it
        fluid.grow(usable);
        transaction.addOuterCloseCallback((result) -> {
          if (result.wasCommitted())
            parent.notifyFluidsChanged(FluidChange.CHANGED, fluid);
        });
        return usable;
      }
    }

    // not present yet, add it
    var fluid = new FluidStack(resource, usable);
    fluids.add(fluid);
    transaction.addOuterCloseCallback((result) -> {
      if (result.wasCommitted())
        parent.notifyFluidsChanged(FluidChange.ADDED, fluid);
    });
    return usable;
  }

  @Override
  public long extract(FluidVariant resource, long maxAmount, TransactionContext transaction) {
    // search for the resource
    ListIterator<FluidStack> iter = fluids.listIterator();
    while (iter.hasNext()) {
      FluidStack fluid = iter.next();
      if (fluid.isFluidEqual(resource)) {
        // if found, determine how much we can drain
        long drainable = Math.min(maxAmount, fluid.getAmount());

        // copy contained fluid to return for accuracy
        FluidStack ret = fluid.copy();
        ret.setAmount(drainable);

        // update tank if executing
        updateSnapshots(transaction);
        fluid.shrink(drainable);
        contained -= drainable;
        // if now empty, remove from the list
        if (fluid.getAmount() <= 0) {
          iter.remove();
          transaction.addOuterCloseCallback((result) -> {
            if (result.wasCommitted())
              parent.notifyFluidsChanged(FluidChange.REMOVED, fluid);
          });
        } else {
          transaction.addOuterCloseCallback((result) -> {
            if (result.wasCommitted())
              parent.notifyFluidsChanged(FluidChange.CHANGED, fluid);
          });
        }

        return drainable;
      }
    }

    // nothing drained
    return 0;
  }

  /* Saving and loading */

  private static final String TAG_FLUIDS = "fluids";
  private static final String TAG_CAPACITY = "capacity";

  /**
   * Updates fluids in the tank, typically from a packet
   * @param fluids  List of fluids
   */
  public void setFluids(List<FluidStack> fluids) {
    FluidStack oldFirst = getFluidInTank(0);
    this.fluids.clear();
    this.fluids.addAll(fluids);
    contained = fluids.stream().mapToLong(FluidStack::getAmount).reduce(0, Long::sum);
    FluidStack newFirst = getFluidInTank(0);
    if (!oldFirst.isFluidEqual(newFirst)) {
      parent.notifyFluidsChanged(FluidChange.ORDER_CHANGED, newFirst);
    }
  }

  /** Writes the tank to NBT */
  public CompoundTag write(CompoundTag nbt) {
    ListTag list = new ListTag();
    for (FluidStack liquid : fluids) {
      CompoundTag fluidTag = new CompoundTag();
      liquid.writeToNBT(fluidTag);
      list.add(fluidTag);
    }
    nbt.put(TAG_FLUIDS, list);
    nbt.putLong(TAG_CAPACITY, capacity);
    return nbt;
  }

  /** Reads the tank from NBT */
  public void read(CompoundTag tag) {
    ListTag list = tag.getList(TAG_FLUIDS, Tag.TAG_COMPOUND);
    fluids.clear();
    contained = 0;
    for (int i = 0; i < list.size(); i++) {
      CompoundTag fluidTag = list.getCompound(i);
      FluidStack fluid = FluidStack.loadFluidStackFromNBT(fluidTag);
      if (!fluid.isEmpty()) {
        fluids.add(fluid);
        contained += fluid.getAmount();
      }
    }
    capacity = tag.getLong(TAG_CAPACITY);
  }

  @Override
  public Iterator<StorageView<FluidVariant>> iterator() {
    return (Iterator) getSlots().iterator();
  }

  @Override
  protected FluidSnapshot createSnapshot() {
    List<FluidStack> cachedFluids = new ArrayList<>();
    for (int i = 0; i < fluids.size(); i++) {
      cachedFluids.add(i, fluids.get(i).copy());
    }
    return new FluidSnapshot(this.contained, cachedFluids);
  }

  @Override
  protected void readSnapshot(FluidSnapshot snapshot) {
    this.fluids = snapshot.fluids();
    this.contained = snapshot.contained();
  }

  @SuppressWarnings("UnstableApiUsage")
  @AllArgsConstructor
  public class FluidStackSlot extends SnapshotParticipant<FluidSnapshot> implements SingleSlotStorage<FluidVariant> {

    private FluidStack fluid;
    private final int slot;

    @Override
    public long insert(FluidVariant resource, long maxAmount, TransactionContext transaction) {
      // if full or nothing being filled, do nothing
      if (contained >= capacity || maxAmount <= 0 || resource.isBlank()) {
        return 0;
      }

      // determine how much we can fill
      long usable = Math.min(capacity - contained, maxAmount);
      // could be negative if the smeltery size changes then you try filling it
      if (usable <= 0) {
        return 0;
      }

      updateSnapshots(transaction);

      // add contained fluid amount
      contained += usable;

      // check if we already have the given liquid
      if (fluid.isFluidEqual(resource)) {
        // yup. add it
        fluid.grow(usable);
        transaction.addOuterCloseCallback((result) -> {
          if (result.wasCommitted())
            parent.notifyFluidsChanged(FluidChange.CHANGED, fluid);
        });
        return usable;
      }

      // not present yet, add it
      var fluid = new FluidStack(resource, usable);
      fluids.add(fluid);
      transaction.addOuterCloseCallback((result) -> {
        if (result.wasCommitted())
          parent.notifyFluidsChanged(FluidChange.ADDED, fluid);
      });
      return usable;
    }

    @Override
    public long extract(FluidVariant resource, long maxAmount, TransactionContext transaction) {
      if (fluid.isFluidEqual(resource)) {
        // if found, determine how much we can drain
        long drainable = Math.min(maxAmount, fluid.getAmount());

        // copy contained fluid to return for accuracy
        FluidStack ret = fluid.copy();
        ret.setAmount(drainable);

        // update tank if executing
        updateSnapshots(transaction);
        fluid.shrink(drainable);
        contained -= drainable;
        // if now empty, remove from the list
        transaction.addOuterCloseCallback((result) -> {
          if (result.wasCommitted()) {
            if (fluid.getAmount() <= 0) {
              if (!SmelteryTank.this.fluids.isEmpty()) {
                SmelteryTank.this.fluids.remove(slot);
              }
              parent.notifyFluidsChanged(FluidChange.REMOVED, fluid);
            } else {
              parent.notifyFluidsChanged(FluidChange.CHANGED, fluid);
            }
          }
        });


        return drainable;
      }
      return 0;
    }

    @Override
    public boolean isResourceBlank() {
      return this.fluid.getType().isBlank();
    }

    @Override
    public FluidVariant getResource() {
      return this.fluid.getType();
    }

    @Override
    public long getAmount() {
      return this.fluid.getAmount();
    }

    @Override
    public long getCapacity() {
      return SmelteryTank.this.getTankCapacity(this.slot);
    }

    @Override
    protected FluidSnapshot createSnapshot() {
      return SmelteryTank.this.createSnapshot();
    }

    @Override
    protected void readSnapshot(FluidSnapshot snapshot) {
      SmelteryTank.this.readSnapshot(snapshot);
    }
  }

  public record FluidSnapshot(long contained, List<FluidStack> fluids) {}
}
