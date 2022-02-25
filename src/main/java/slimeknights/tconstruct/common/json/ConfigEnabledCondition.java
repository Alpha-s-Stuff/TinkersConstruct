package slimeknights.tconstruct.common.json;

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonObject;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSyntaxException;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import net.fabricmc.fabric.api.resource.conditions.v1.ConditionJsonProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.Serializer;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import net.minecraft.world.level.storage.loot.predicates.LootItemConditionType;
import net.minecraftforge.common.ForgeConfigSpec.BooleanValue;
import slimeknights.tconstruct.TConstruct;
import slimeknights.tconstruct.common.config.Config;
import slimeknights.tconstruct.shared.TinkerCommons;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.function.BooleanSupplier;

@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class ConfigEnabledCondition implements ConditionJsonProvider, LootItemCondition {
  public static final ResourceLocation ID = TConstruct.getResource("config");
  public static final ConfigSerializer SERIALIZER = new ConfigSerializer();
  /* Map of config names to condition cache */
  public static final Map<String,ConfigEnabledCondition> PROPS = new HashMap<>();

  @Getter
  private final String configName;
  private final BooleanSupplier supplier;


//  @Override
//  public boolean test() {
//    return supplier.getAsBoolean();
//  }

  @Override
  public boolean test(LootContext lootContext) {
    return supplier.getAsBoolean();
  }

  @Override
  public LootItemConditionType getType() {
    return TinkerCommons.lootConfig;
  }

  @Override
  public ResourceLocation getConditionId() {
    return ID;
  }

  @Override
  public void writeParameters(JsonObject object) {
    object.addProperty("value", configName);
  }

  private static class ConfigSerializer implements Serializer<ConfigEnabledCondition> {
//    @Override
//    public ResourceLocation getID() {
//      return ID;
//    }


    public void write(JsonObject json, ConfigEnabledCondition value) {
      json.addProperty("prop", value.configName);
    }


    public ConfigEnabledCondition read(JsonObject json) {
      String prop = GsonHelper.getAsString(json, "prop");
      ConfigEnabledCondition config = PROPS.get(prop.toLowerCase(Locale.ROOT));
      if (config == null) {
        throw new JsonSyntaxException("Invalid property name '" + prop + "'");
      }
      return config;
    }

    @Override
    public void serialize(JsonObject json, ConfigEnabledCondition condition, JsonSerializationContext context) {
      write(json, condition);
    }

    @Override
    public ConfigEnabledCondition deserialize(JsonObject json, JsonDeserializationContext context) {
      return read(json);
    }
  }

  /**
   * Adds a condition
   * @param prop     Property name
   * @param supplier Boolean supplier
   * @return Added condition
   */
  private static ConfigEnabledCondition add(String prop, BooleanSupplier supplier) {
    ConfigEnabledCondition conf = new ConfigEnabledCondition(prop, supplier);
    PROPS.put(prop.toLowerCase(Locale.ROOT), conf);
    return conf;
  }

  /**
   * Adds a condition
   * @param prop     Property name
   * @param supplier Config value
   * @return Added condition
   */
  private static ConfigEnabledCondition add(String prop, BooleanValue supplier) {
    return add(prop, supplier::get);
  }

  @Override
  public String toString() {
    return "config_setting_enabled(\"" + this.configName + "\")";
  }

  /* Properties */
  public static final ConditionJsonProvider SPAWN_WITH_BOOK = add("spawn_with_book", Config.COMMON.shouldSpawnWithTinkersBook);
  public static final ConditionJsonProvider GRAVEL_TO_FLINT = add("gravel_to_flint", Config.COMMON.addGravelToFlintRecipe);
  public static final ConditionJsonProvider CHEAPER_NETHERITE_ALLOY = add("cheaper_netherite_alloy", Config.COMMON.cheaperNetheriteAlloy);
  public static final ConditionJsonProvider WITHER_BONE_DROP = add("wither_bone_drop", Config.COMMON.witherBoneDrop);
  public static final ConditionJsonProvider WITHER_BONE_CONVERSION = add("wither_bone_conversion", Config.COMMON.witherBoneConversion);
  public static final ConditionJsonProvider SLIME_RECIPE_FIX = add("slime_recipe_fix", Config.COMMON.glassRecipeFix);
  public static final ConditionJsonProvider GLASS_RECIPE_FIX = add("glass_recipe_fix", Config.COMMON.glassRecipeFix);
  public static final ConditionJsonProvider FORCE_INTEGRATION_MATERIALS = add("force_integration_materials", Config.COMMON.forceIntegrationMaterials);
  public static final ConditionJsonProvider ICHOR_GEODES = add("ichor_geodes", Config.COMMON.ichorGeodes);
}
