package slimeknights.tconstruct.smeltery.block.entity.inventory;

import com.google.common.collect.Iterators;
import lombok.AllArgsConstructor;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidVariant;
import net.fabricmc.fabric.api.transfer.v1.storage.SlottedStorage;
import net.fabricmc.fabric.api.transfer.v1.storage.StorageView;
import net.fabricmc.fabric.api.transfer.v1.storage.base.SingleSlotStorage;
import net.fabricmc.fabric.api.transfer.v1.transaction.TransactionContext;

import java.util.Iterator;

@AllArgsConstructor
public class DuctTankWrapper implements SlottedStorage<FluidVariant> { // Fabric has FilteringStorage but in order to not create merge conflicts we use our own class
  private final SlottedStorage<FluidVariant> parent;
  private final DuctItemHandler itemHandler;


  /* Properties */

  @Override
  public int getSlotCount() {
    return parent.getSlotCount();
  }

  @Override
  public SingleSlotStorage<FluidVariant> getSlot(int tank) {
    return parent.getSlot(tank);
  }

  @Override
  public Iterator<StorageView<FluidVariant>> iterator() {
    return Iterators.transform(parent.iterator(), FilteringStorageView::new);
  }


  /* Interactions */

  @Override
  public long insert(FluidVariant resource, long maxAmount, TransactionContext transaction) {
    if ((maxAmount <= 0 || resource.isBlank()) || !itemHandler.getFluid().isFluidEqual(resource)) {
      return 0;
    }
    return parent.insert(resource, maxAmount, transaction);
  }

  @Override
  public long extract(FluidVariant resource, long maxAmount, TransactionContext transaction) {
    if ((maxAmount <= 0 || resource.isBlank()) || !itemHandler.getFluid().isFluidEqual(resource)) {
      return 0;
    }
    return parent.extract(resource, maxAmount, transaction);
  }

  /**
   * Fabric copy of {@link net.fabricmc.fabric.api.transfer.v1.storage.base.FilteringStorage.FilteringStorageView}
   */
  private class FilteringStorageView implements StorageView<FluidVariant> {
    private final StorageView<FluidVariant> backingView;

    private FilteringStorageView(StorageView<FluidVariant> backingView) {
      this.backingView = backingView;
    }

    @Override
    public long extract(FluidVariant resource, long maxAmount, TransactionContext transaction) {
      if ((maxAmount <= 0 || resource.isBlank()) || !itemHandler.getFluid().isFluidEqual(resource)) {
        return 0;
      }
      return backingView.extract(resource, maxAmount, transaction);
    }

    @Override
    public boolean isResourceBlank() {
      return backingView.isResourceBlank();
    }

    @Override
    public FluidVariant getResource() {
      return backingView.getResource();
    }

    @Override
    public long getAmount() {
      return backingView.getAmount();
    }

    @Override
    public long getCapacity() {
      return backingView.getCapacity();
    }

    @Override
    public StorageView<FluidVariant> getUnderlyingView() {
      return backingView.getUnderlyingView();
    }
  }
}
