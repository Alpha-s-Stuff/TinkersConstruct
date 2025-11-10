package slimeknights.tconstruct.plugin.jei.fabric;

import io.github.fabricators_of_create.porting_lib.fluids.FluidStack;
import mezz.jei.api.fabric.ingredients.fluids.IJeiFluidIngredient;
import mezz.jei.api.helpers.IPlatformFluidHelper;
import mezz.jei.api.ingredients.IIngredientTypeWithSubtypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.material.Fluid;

import java.util.List;
import java.util.Optional;

public class JEITypes {
  public static FluidStack toFluidStack(IJeiFluidIngredient ingredient) {
    return new FluidStack(ingredient.getFluid(), ingredient.getAmount(), ingredient.getTag().orElse(null));
  }

  public static IJeiFluidIngredient toJEI(FluidStack stack) {
    return new JEIFluidStack(stack);
  }

  public static List<IJeiFluidIngredient> toJEI(List<FluidStack> stacks) {
    return stacks.stream().map(stack -> (IJeiFluidIngredient) new JEIFluidStack(stack)).toList();
  }

  public record JEIFluidStack(FluidStack stack) implements IJeiFluidIngredient {
    @Override
    public Fluid getFluid() {
      return stack.getFluid();
    }

    @Override
    public long getAmount() {
      return stack.getAmount();
    }

    @Override
    public Optional<CompoundTag> getTag() {
      return Optional.ofNullable(stack.getTag());
    }
  }
}
