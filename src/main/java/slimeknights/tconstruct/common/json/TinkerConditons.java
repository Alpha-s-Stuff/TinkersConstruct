package slimeknights.tconstruct.common.json;

import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;
import net.fabricmc.fabric.api.resource.conditions.v1.ConditionJsonProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;

import java.util.Locale;

import static slimeknights.tconstruct.common.json.ConfigEnabledCondition.PROPS;

public class TinkerConditons {
  public static ConditionJsonProvider configEnabled(ConfigEnabledCondition value) {
    return new ConditionJsonProvider() {
      @Override
      public ResourceLocation getConditionId() {
        return ConfigEnabledCondition.ID;
      }

      @Override
      public void writeParameters(JsonObject json) {
        json.addProperty("prop", value.getConfigName());
      }
    };
  }

  public static boolean isConfigEnabled(JsonObject json) {
    String prop = GsonHelper.getAsString(json, "prop");
    ConfigEnabledCondition config = PROPS.get(prop.toLowerCase(Locale.ROOT));
    if (config == null) {
      throw new JsonSyntaxException("Invalid property name '" + prop + "'");
    }
    return config.test();
  }
}
