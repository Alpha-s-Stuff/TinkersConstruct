package slimeknights.tconstruct.smeltery.block.entity.component;

import io.github.fabricators_of_create.porting_lib.common.util.NonNullConsumer;
import io.github.fabricators_of_create.porting_lib.util.LazyOptional;
import lombok.Getter;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import slimeknights.mantle.block.entity.IRetexturedBlockEntity;
import slimeknights.mantle.client.model.data.IModelData;
import slimeknights.mantle.client.model.data.ModelDataMap;
import slimeknights.mantle.client.model.data.SinglePropertyData;
import slimeknights.mantle.inventory.EmptyItemHandler;
import slimeknights.mantle.transfer.TransferUtil;
import slimeknights.mantle.transfer.fluid.EmptyFluidHandler;
import slimeknights.mantle.transfer.fluid.FluidTransferable;
import slimeknights.mantle.transfer.fluid.IFluidHandler;
import slimeknights.mantle.transfer.item.IItemHandler;
import slimeknights.mantle.transfer.item.ItemTransferable;
import slimeknights.mantle.util.RetexturedHelper;
import slimeknights.mantle.util.WeakConsumerWrapper;
import slimeknights.tconstruct.smeltery.TinkerSmeltery;
import slimeknights.tconstruct.smeltery.block.entity.tank.IDisplayFluidListener;
import slimeknights.tconstruct.smeltery.block.entity.tank.ISmelteryTankHandler;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Objects;

import static slimeknights.mantle.util.RetexturedHelper.TAG_TEXTURE;

/**
 * Shared logic between drains and ducts
 */
public abstract class SmelteryInputOutputBlockEntity<T> extends SmelteryComponentBlockEntity implements IRetexturedBlockEntity {
  /** Capability this TE watches */
  private final Class<T> capability;
  /** Empty capability for in case the valid capability becomes invalid without invalidating */
  protected final T emptyInstance;
  /** Listener to attach to consumed capabilities */
  protected final NonNullConsumer<LazyOptional<T>> listener = new WeakConsumerWrapper<>(this, (te, cap) -> te.clearHandler());
  @Nullable
  private LazyOptional<T> capabilityHolder = null;

  /* Retexturing */
  @Getter
  private final IModelData modelData = getRetexturedModelData();
  @Nonnull
  @Getter
  private Block texture = Blocks.AIR;

  protected SmelteryInputOutputBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state, Class<T> capability, T emptyInstance) {
    super(type, pos, state);
    this.capability = capability;
    this.emptyInstance = emptyInstance;
  }

  /** Clears all cached capabilities */
  private void clearHandler() {
    if (capabilityHolder != null) {
      capabilityHolder.invalidate();
      capabilityHolder = null;
    }
  }

  @Override
  public void invalidateCaps() {
    super.invalidateCaps();
    clearHandler();
  }

  @Override
  protected void setMaster(@Nullable BlockPos master, @Nullable Block block) {
    assert level != null;

    // if we have a new master, invalidate handlers
    boolean masterChanged = false;
    if (!Objects.equals(getMasterPos(), master)) {
      clearHandler();
      masterChanged = true;
    }
    super.setMaster(master, block);
    // notify neighbors of the change (state change skips the notify flag)
    if (masterChanged) {
      level.blockUpdated(worldPosition, getBlockState().getBlock());
    }
  }

  /**
   * Gets the capability to store in this IO block. Capability parent should have the proper listeners attached
   * @param parent  Parent tile entity
   * @return  Capability from parent, or empty if absent
   */
  protected LazyOptional<T> getCapability(BlockEntity parent) {
    LazyOptional<T> handler = (LazyOptional<T>) TransferUtil.getHandler(parent, null, capability); // TODO: PORT this shouldnt need to be casted?
    if (handler.isPresent()) {
      handler.addListener(listener);

      return LazyOptional.of(() -> handler.orElse(emptyInstance));
    }
    return LazyOptional.empty();
  }

  /**
   * Fetches the capability handlers if missing
   */
  protected LazyOptional<T> getCachedCapability() {
    if (capabilityHolder == null) {
      if (validateMaster()) {
        BlockPos master = getMasterPos();
        if (master != null && this.level != null) {
          BlockEntity te = level.getBlockEntity(master);
          if (te != null) {
            capabilityHolder = getCapability(te);
            return capabilityHolder;
          }
        }
      }
      capabilityHolder = LazyOptional.empty();
    }
    return capabilityHolder;
  }

//  @Nonnull
//  @Override
//  public <C> LazyOptional<C> getCapability(Capability<C> capability, @Nullable Direction facing) {
//    if (capability == this.capability) {
//      return getCachedCapability().cast();
//    }
//    return super.getCapability(capability, facing);
//  }


  /* Retexturing */

  @Override
  public IModelData getRetexturedModelData() {
    return new SinglePropertyData<>(RetexturedHelper.BLOCK_PROPERTY);
  }

  @Override
  public String getTextureName() {
    return RetexturedHelper.getTextureName(texture);
  }

  @Override
  public void updateTexture(String name) {
    Block oldTexture = texture;
    texture = RetexturedHelper.getBlock(name);
    if (oldTexture != texture) {
      setChangedFast();
      RetexturedHelper.onTextureUpdated(this);
    }
  }


  /* NBT */

  @Override
  protected boolean shouldSyncOnUpdate() {
    return true;
  }

  @Override
  protected void saveSynced(CompoundTag tags) {
    super.saveSynced(tags);
    if (texture != Blocks.AIR) {
      tags.putString(TAG_TEXTURE, getTextureName());
    }
  }

  @Override
  public void load(CompoundTag tags) {
    super.load(tags);
    if (tags.contains(TAG_TEXTURE, Tag.TAG_STRING)) {
      texture = RetexturedHelper.getBlock(tags.getString(TAG_TEXTURE));
      RetexturedHelper.onTextureUpdated(this);
    }
  }

  @Override
  public IModelData getRenderAttachmentData() {
    return getRetexturedModelData();
  }

  /** Fluid implementation of smeltery IO */
  public static abstract class SmelteryFluidIO extends SmelteryInputOutputBlockEntity<IFluidHandler> implements FluidTransferable {
    protected SmelteryFluidIO(BlockEntityType<?> type, BlockPos pos, BlockState state) {
      super(type, pos, state, IFluidHandler.class, EmptyFluidHandler.INSTANCE);
    }

    /** Wraps the given capability */
    protected LazyOptional<IFluidHandler> makeWrapper(LazyOptional<IFluidHandler> capability) {
      return LazyOptional.of(() -> capability.orElse(emptyInstance));
    }

    @Override
    protected LazyOptional<IFluidHandler> getCapability(BlockEntity parent) {
      // fluid capability is not exposed directly in the smeltery
      if (parent instanceof ISmelteryTankHandler) {
        LazyOptional<IFluidHandler> capability = ((ISmelteryTankHandler) parent).getFluidCapability();
        if (capability.isPresent()) {
          capability.addListener(listener);
          return makeWrapper(capability);
        }
      }
      return LazyOptional.empty();
    }

    @Override
    public IModelData getRenderAttachmentData() {
      return getModelData();
    }

    @Override
    public IModelData getRetexturedModelData() {
      return new ModelDataMap.Builder().withProperty(RetexturedHelper.BLOCK_PROPERTY).withProperty(IDisplayFluidListener.PROPERTY).build();
    }

    @Nullable
    @Override
    public LazyOptional<IFluidHandler> getFluidHandler(@Nullable Direction direction) {
      return getCachedCapability();
    }
  }

  /** Item implementation of smeltery IO */
  public static class ChuteBlockEntity extends SmelteryInputOutputBlockEntity<IItemHandler> implements ItemTransferable {
    public ChuteBlockEntity(BlockPos pos, BlockState state) {
      this(TinkerSmeltery.chute.get(), pos, state);
    }

    protected ChuteBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
      super(type, pos, state, IItemHandler.class, EmptyItemHandler.INSTANCE);
    }

    @Nullable
    @Override
    public LazyOptional<IItemHandler> getItemHandler(@Nullable Direction direction) {
      return getCachedCapability();
    }
  }

}
