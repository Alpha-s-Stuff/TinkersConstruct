package slimeknights.tconstruct.tools.data;

import io.github.fabricators_of_create.porting_lib.util.FluidUnit;
import me.alphamode.forgetags.Tags;
import net.fabricmc.fabric.api.resource.conditions.v1.DefaultResourceConditions;
import net.minecraft.core.Registry;
import net.minecraft.data.DataGenerator;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.MobType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import slimeknights.mantle.recipe.data.FluidNameIngredient;
import slimeknights.tconstruct.TConstruct;
import slimeknights.tconstruct.common.TinkerTags;
import slimeknights.tconstruct.fluids.TinkerFluids;
import slimeknights.tconstruct.library.data.tinkering.AbstractSpillingFluidProvider;
import slimeknights.tconstruct.library.json.predicate.entity.LivingEntityPredicate;
import slimeknights.tconstruct.library.json.predicate.entity.MobTypePredicate;
import slimeknights.tconstruct.library.modifiers.spilling.effects.AddBreathSpillingEffect;
import slimeknights.tconstruct.library.modifiers.spilling.effects.AddInsomniaSpillingEffect;
import slimeknights.tconstruct.library.modifiers.spilling.effects.CureEffectsSpillingEffect;
import slimeknights.tconstruct.library.modifiers.spilling.effects.DamageSpillingEffect;
import slimeknights.tconstruct.library.modifiers.spilling.effects.DamageSpillingEffect.DamageType;
import slimeknights.tconstruct.library.modifiers.spilling.effects.EffectSpillingEffect;
import slimeknights.tconstruct.library.modifiers.spilling.effects.ExtinguishSpillingEffect;
import slimeknights.tconstruct.library.modifiers.spilling.effects.PotionFluidEffect;
import slimeknights.tconstruct.library.modifiers.spilling.effects.RemoveEffectSpillingEffect;
import slimeknights.tconstruct.library.modifiers.spilling.effects.RestoreHungerSpillingEffect;
import slimeknights.tconstruct.library.modifiers.spilling.effects.SetFireSpillingEffect;
import slimeknights.tconstruct.library.modifiers.spilling.effects.TeleportSpillingEffect;
import slimeknights.tconstruct.library.recipe.FluidValues;
import slimeknights.tconstruct.library.recipe.TagPredicate;
import slimeknights.tconstruct.tools.TinkerModifiers;
import slimeknights.tconstruct.tools.modifiers.traits.skull.StrongBonesModifier;

import java.util.function.Function;

public class SpillingFluidProvider extends AbstractSpillingFluidProvider {
  public SpillingFluidProvider(DataGenerator generator) {
    super(generator, TConstruct.MOD_ID);
  }

  @Override
  protected void addFluids() {
    // vanilla
    addFluid(Fluids.WATER, FluidUnit.DROPLETS.getOneBucketAmount() * 50L)
      .addEffect(LivingEntityPredicate.WATER_SENSITIVE, new DamageSpillingEffect(DamageType.PIERCING, 2f))
      .addEffect(ExtinguishSpillingEffect.INSTANCE);
    addFluid(Fluids.LAVA, FluidUnit.DROPLETS.getOneBucketAmount() * 50L)
      .addEffect(LivingEntityPredicate.FIRE_IMMUNE.inverted(), new DamageSpillingEffect(DamageType.FIRE, 2f))
      .addEffect(new SetFireSpillingEffect(10));
    addFluid(Tags.Fluids.MILK, FluidUnit.DROPLETS.getOneBucketAmount() * 100L)
      .addEffect(new CureEffectsSpillingEffect(new ItemStack(Items.MILK_BUCKET)))
      .addEffect(StrongBonesModifier.SPILLING_EFFECT);

    // blaze - more damage, less fire
    burningFluid("blazing_blood", TinkerFluids.blazingBlood.getLocalTag(), FluidUnit.DROPLETS.getOneBucketAmount() * 50L, 3f, 5);

    // slime
    int slimeballPiece = FluidValues.SLIMEBALL / 5;
    // earth - lucky
    addFluid(TinkerFluids.earthSlime.getForgeTag(), slimeballPiece)
      .addEffect(new EffectSpillingEffect(MobEffects.LUCK, 15, 1))
      .addEffect(new EffectSpillingEffect(MobEffects.MOVEMENT_SLOWDOWN, 15, 1));
    // sky - jump boost
    addFluid(TinkerFluids.skySlime.getLocalTag(), slimeballPiece)
      .addEffect(new EffectSpillingEffect(MobEffects.JUMP, 20, 1))
      .addEffect(new EffectSpillingEffect(MobEffects.MOVEMENT_SLOWDOWN, 15, 1));
    // ender - levitation
    addFluid(TinkerFluids.enderSlime.getLocalTag(), slimeballPiece)
      .addEffect(new EffectSpillingEffect(MobEffects.LEVITATION, 5, 1))
      .addEffect(new EffectSpillingEffect(MobEffects.MOVEMENT_SLOWDOWN, 15, 1));
    // slimelike
    // blood - food
    addFluid(TinkerFluids.blood.getLocalTag(), slimeballPiece)
      .addEffect(new RestoreHungerSpillingEffect(1, 0.2f))
      .addEffect(new EffectSpillingEffect(MobEffects.DIG_SLOWDOWN, 10, 1));
    // venom - poison & strength
    addFluid(TinkerFluids.venom.getLocalTag(), slimeballPiece)
      .addEffect(new EffectSpillingEffect(MobEffects.POISON, 5, 1))
      .addEffect(new EffectSpillingEffect(MobEffects.DAMAGE_BOOST, 10, 1));
    // magma - fire resistance
    addFluid(TinkerFluids.magma.getForgeTag(), slimeballPiece)
      .addEffect(new EffectSpillingEffect(MobEffects.FIRE_RESISTANCE, 25, 1));
    // soul - slowness and blindness
    addFluid(TinkerFluids.liquidSoul.getLocalTag(), FluidUnit.DROPLETS.getOneBucketAmount() * 50L)
      .addEffect(new EffectSpillingEffect(MobEffects.MOVEMENT_SLOWDOWN, 25, 2))
      .addEffect(new EffectSpillingEffect(MobEffects.BLINDNESS, 5, 1));
    // ender - teleporting
    addFluid(TinkerFluids.moltenEnder.getForgeTag(), FluidUnit.DROPLETS.getOneBucketAmount() * 50L)
      .addEffect(new DamageSpillingEffect(DamageType.MAGIC, 1f))
      .addEffect(TeleportSpillingEffect.INSTANCE);

    // foods
    addFluid(TinkerFluids.honey.getForgeTag(), slimeballPiece)
      .addEffect(new RestoreHungerSpillingEffect(1, 0.02f))
      .addEffect(new RemoveEffectSpillingEffect(MobEffects.POISON));
    addFluid(TinkerFluids.beetrootSoup.getForgeTag(), slimeballPiece)
      .addEffect(new RestoreHungerSpillingEffect(1, 0.15f));
    addFluid(TinkerFluids.mushroomStew.getForgeTag(), slimeballPiece)
      .addEffect(new RestoreHungerSpillingEffect(1, 0.15f));
    addFluid(TinkerFluids.rabbitStew.getForgeTag(), slimeballPiece)
      .addEffect(new RestoreHungerSpillingEffect(2, 0.10f));
    // pig iron fills you up food, but still hurts
    addFluid(TinkerFluids.moltenPigIron.getLocalTag(), FluidValues.NUGGET)
      .addEffect(new RestoreHungerSpillingEffect(2, 0.3f))
      .addEffect(new SetFireSpillingEffect(2));

    // metals, lose reference to mistborn (though a true fan would probably get angry at how much I stray from the source)
    metalborn(TinkerFluids.moltenIron.getForgeTag(), 2f).addEffect(new EffectSpillingEffect(TinkerModifiers.magneticEffect.get(), 4, 2));
    metalborn(TinkerFluids.moltenSteel.getForgeTag(), 2f).addEffect(new EffectSpillingEffect(TinkerModifiers.replusiveEffect.get(), 4, 2));
    metalborn(TinkerFluids.moltenCopper.getForgeTag(), 1.5f).addEffect(new AddBreathSpillingEffect(80));
    metalborn(TinkerFluids.moltenBronze.getForgeTag(), 2f).addEffect(new AddInsomniaSpillingEffect(-2000));
    metalborn(TinkerFluids.moltenAmethystBronze.getLocalTag(), 1.5f).addEffect(new AddInsomniaSpillingEffect(2000));
    metalborn(TinkerFluids.moltenZinc.getForgeTag(), 1.5f).addEffect(new EffectSpillingEffect(MobEffects.MOVEMENT_SPEED, 10, 1));
    metalborn(TinkerFluids.moltenBrass.getForgeTag(), 2f).addEffect(new EffectSpillingEffect(MobEffects.FIRE_RESISTANCE, 8, 1));
    metalborn(TinkerFluids.moltenTin.getForgeTag(), 1.5f).addEffect(new EffectSpillingEffect(MobEffects.NIGHT_VISION, 8, 1));
    metalborn(TinkerFluids.moltenPewter.getForgeTag(), 2f).addEffect(new EffectSpillingEffect(MobEffects.DAMAGE_BOOST, 7, 1));
    addFluid(TinkerFluids.moltenGold.getForgeTag(), FluidValues.NUGGET)
      .addEffect(new MobTypePredicate(MobType.UNDEAD), new DamageSpillingEffect(DamageType.MAGIC, 2f))
      .addEffect(new EffectSpillingEffect(MobEffects.REGENERATION, 6, 1));
    addFluid(TinkerFluids.moltenElectrum.getForgeTag(), FluidValues.NUGGET)
      .addEffect(new MobTypePredicate(MobType.UNDEAD), new DamageSpillingEffect(DamageType.MAGIC, 2f))
      .addEffect(new EffectSpillingEffect(MobEffects.DIG_SPEED, 8, 1));
    addFluid(TinkerFluids.moltenRoseGold.getForgeTag(), FluidValues.NUGGET)
      .addEffect(new MobTypePredicate(MobType.UNDEAD), new DamageSpillingEffect(DamageType.MAGIC, 2f))
      .addEffect(new EffectSpillingEffect(MobEffects.HEALTH_BOOST, 15, 1));
    metalborn(TinkerFluids.moltenAluminum.getForgeTag(), 1f).addEffect(new CureEffectsSpillingEffect(new ItemStack(Items.MILK_BUCKET)));
    addFluid(TinkerFluids.moltenSilver.getForgeTag(), FluidValues.NUGGET)
      .addEffect(new MobTypePredicate(MobType.UNDEAD), new DamageSpillingEffect(DamageType.MAGIC, 2f))
      .addEffect(new RemoveEffectSpillingEffect(MobEffects.WITHER));

    metalborn(TinkerFluids.moltenLead.getForgeTag(), 1.5f).addEffect(new EffectSpillingEffect(MobEffects.MOVEMENT_SLOWDOWN, 6, 1));
    metalborn(TinkerFluids.moltenNickel.getForgeTag(), 1.5f).addEffect(new EffectSpillingEffect(MobEffects.WEAKNESS, 7, 1));
    metalborn(TinkerFluids.moltenInvar.getForgeTag(), 2f).addEffect(new EffectSpillingEffect(MobEffects.HUNGER, 10, 1));
    metalborn(TinkerFluids.moltenConstantan.getForgeTag(), 2f).addEffect(new EffectSpillingEffect(MobEffects.HUNGER, 10, 1));
    burningFluid(TinkerFluids.moltenUranium.getForgeTag(), 1.5f, 3).addEffect(new EffectSpillingEffect(MobEffects.POISON, 10, 1));

    metalborn(TinkerFluids.moltenCobalt.getForgeTag(), 1f)
      .addEffect(new EffectSpillingEffect(MobEffects.DIG_SPEED, 7, 1))
      .addEffect(new EffectSpillingEffect(MobEffects.MOVEMENT_SPEED, 7, 1));
    metalborn(TinkerFluids.moltenManyullyn.getForgeTag(), 3f).addEffect(new EffectSpillingEffect(MobEffects.DAMAGE_RESISTANCE, 15, 1));
    metalborn(TinkerFluids.moltenHepatizon.getForgeTag(), 2.5f).addEffect(new EffectSpillingEffect(MobEffects.DAMAGE_RESISTANCE, 10, 1));
    burningFluid(TinkerFluids.moltenNetherite.getForgeTag(), 3.5f, 4).addEffect(new EffectSpillingEffect(MobEffects.BLINDNESS, 15, 1));

    metalborn(TinkerFluids.moltenSlimesteel.getLocalTag(), 1f).addEffect(new EffectSpillingEffect(MobEffects.SLOW_FALLING, 5, 1));
    metalborn(TinkerFluids.moltenQueensSlime.getLocalTag(), 1f).addEffect(new EffectSpillingEffect(MobEffects.LEVITATION, 5, 1));

    // multi-recipes
    burningFluid("glass",           TinkerTags.Fluids.GLASS_SPILLING,           FluidUnit.DROPLETS.getOneBucketAmount() * 100L, 1f,   3);
    burningFluid("clay",            TinkerTags.Fluids.CLAY_SPILLING,            FluidValues.BRICK / 5,              1.5f, 3);
    burningFluid("metal_cheap",     TinkerTags.Fluids.CHEAP_METAL_SPILLING,     FluidValues.NUGGET,                 1.5f, 7);
    burningFluid("metal_average",   TinkerTags.Fluids.AVERAGE_METAL_SPILLING,   FluidValues.NUGGET,                 2f,   7);
    burningFluid("metal_expensive", TinkerTags.Fluids.EXPENSIVE_METAL_SPILLING, FluidValues.NUGGET,                 3f,   7);

    // potion fluid compat
    // standard potion is 250 mb, but we want a smaller number. For the effects, we really want to divide into 4 pieces
    addFluid("potion_fluid", TinkerTags.Fluids.POTION, FluidValues.BOTTLE / 2).addEffect(new PotionFluidEffect(0.5f, TagPredicate.ANY));

    // create has three types of bottles stored on their fluid, react to it to boost
    Function<String,TagPredicate> createBottle = value -> {
      CompoundTag compound = new CompoundTag();
      compound.putString("Bottle", value);
      return new TagPredicate(compound);
    };
    String create = "create";
    addFluid("potion_create", FluidNameIngredient.of(new ResourceLocation(create, "potion"), FluidUnit.DROPLETS.getOneBucketAmount() * 125L))
      .condition(DefaultResourceConditions.allModsLoaded(create))
      .addEffect(new PotionFluidEffect(0.25f, createBottle.apply("REGULAR")))
      .addEffect(new PotionFluidEffect(0.5f, createBottle.apply("SPLASH")))
      .addEffect(new PotionFluidEffect(1f, createBottle.apply("LINGERING")));

  }

  /** Builder for an effect based metal */
  private Builder metalborn(TagKey<Fluid> tag, float damage) {
    return burningFluid(tag.location().getPath(), tag, FluidValues.NUGGET, damage, 0);
  }

  @Override
  public String getName() {
    return "Tinkers' Construct Spilling Fluid Provider";
  }
}
