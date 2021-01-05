package slimeknights.tconstruct.library.modifiers.aspects;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.CompoundNBT;
import slimeknights.tconstruct.library.modifiers.IModifier;
import slimeknights.tconstruct.library.modifiers.nbt.ModifierAndExtraPair;
import slimeknights.tconstruct.library.modifiers.nbt.ModifierNBT;

public class LevelAspect extends ModifierAspect {

  private final int maxLevel;

  public LevelAspect(IModifier parent, int maxLevel) {
    super(parent);
    this.maxLevel = maxLevel;
  }

  @Override
  public boolean canApply(ItemStack stack, ItemStack original) {
    return true;
  }

  @Override
  public ModifierAndExtraPair editNbt(ModifierNBT modifierNBT, CompoundNBT extraNBT) {
    return super.editNbt(modifierNBT.withLevel(modifierNBT.level + 1), extraNBT);
  }
}
