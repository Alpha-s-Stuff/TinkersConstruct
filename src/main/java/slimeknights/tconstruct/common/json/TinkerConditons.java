package slimeknights.tconstruct.common.json;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;
import net.fabricmc.fabric.api.resource.conditions.v1.ConditionJsonProvider;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.SerializationTags;
import net.minecraft.tags.Tag;
import net.minecraft.tags.TagCollection;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.storage.loot.LootContext;
import slimeknights.tconstruct.library.json.TagDifferencePresentCondition;

import java.util.List;
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

  public static ConditionJsonProvider tagDiffrence(TagDifferencePresentCondition value){
    return new ConditionJsonProvider() {
      @Override
      public ResourceLocation getConditionId() {
        return TagDifferencePresentCondition.NAME;
      }

      @Override
      public void writeParameters(JsonObject json) {
        json.addProperty("base", value.getBase().toString());
        JsonArray names = new JsonArray();
        for (ResourceLocation name : value.getSubtracted()) {
          names.add(name.toString());
        }
        json.add("subtracted", names);
      }
    };
  }

  public static boolean isConfigEnabled(JsonObject json) {
    String prop = GsonHelper.getAsString(json, "prop");
    ConfigEnabledCondition config = PROPS.get(prop.toLowerCase(Locale.ROOT));
    if (config == null) {
      throw new JsonSyntaxException("Invalid property name '" + prop + "'");
    }
    return config.test(null);
  }

  public static boolean areTagsDiffrent(JsonObject json){
    ResourceLocation location = new ResourceLocation(json.get("base").getAsString());
    TagDifferencePresentCondition condition = TagDifferencePresentCondition.TAGS.get(location);
    return condition.test();
  }


}
