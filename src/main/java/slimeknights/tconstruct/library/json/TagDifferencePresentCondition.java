package slimeknights.tconstruct.library.json;

import com.google.common.collect.Iterables;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.fabricmc.fabric.api.resource.conditions.v1.ConditionJsonProvider;
import net.fabricmc.fabric.impl.resource.conditions.ResourceConditionsImpl;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import org.jetbrains.annotations.Nullable;
import slimeknights.mantle.util.JsonHelper;
import slimeknights.tconstruct.TConstruct;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Condition requiring that items exist in the intersection of all required item tags */
public class TagDifferencePresentCondition<T> implements ConditionJsonProvider {
  public static final ResourceLocation NAME = TConstruct.getResource("tag_difference_present");

  private final TagKey<T> base;
  private final List<TagKey<T>> subtracted;

  public TagDifferencePresentCondition(TagKey<T> base, List<TagKey<T>> subtracted) {
    if (subtracted.isEmpty()) {
      throw new IllegalArgumentException("Cannot create a condition with no subtracted");
    }
    this.base = base;
    this.subtracted = subtracted;
  }

  /** Creates a condition from a set of keys */
  @SafeVarargs
  public static <T> TagDifferencePresentCondition<T> ofKeys(TagKey<T> base, TagKey<T>... subtracted) {
    return new TagDifferencePresentCondition<>(base, Arrays.asList(subtracted));
  }

  /** Creates a condition from a registry and a set of names */
  public static <T> TagDifferencePresentCondition<T> ofNames(ResourceKey<? extends Registry<T>> registry, ResourceLocation base, ResourceLocation... subtracted) {
    TagKey<T> baseKey = TagKey.create(registry, base);
    return new TagDifferencePresentCondition<>(baseKey, Arrays.stream(subtracted).map(name -> TagKey.create(registry, name)).toList());
  }

  @Override
  public ResourceLocation getConditionId() {
    return NAME;
  }

  public boolean test() {
    // get the base tag
    Collection<Holder<T>> base = getTag(this.base);
    if (base == null || base.isEmpty()) {
      return false;
    }

    // get subtracted tags
    //List<Tag<Item>> subtracted = this.subtracted.stream().map(itemTags::getTag).filter(tag -> tag == null || tag.getValues().isEmpty()).toList();
    // none of the subtracted tags had anything? done
    if (subtracted.isEmpty()) {
      return true;
    }
    // all tags have something, so find the first item that is in all tags
    itemLoop:
    for (Holder<T> entry : base) {
      // find the first item contained in no subtracted tags
      for (TagKey<T> tag : subtracted) {
        // TODO: will this work?
        if (getTag(tag).contains(entry)) {
          continue itemLoop;
        }
      }
      // no subtracted contains the item? success
      return true;
    }
    // no item not in any subtracted
    return false;
  }

  private Collection<Holder<T>> getTag(TagKey<T> tag) {
    // cursed fabric internals
    @Nullable
    Map<ResourceKey<?>, Map<ResourceLocation, Collection<Holder<?>>>> allTags = ResourceConditionsImpl.LOADED_TAGS.get();

    if (allTags == null) {
      ResourceConditionsImpl.LOGGER.warn("Can't retrieve deserialized tags. Failing tconstruct:tag_difference_present resource condition check.");
      return Set.of();
    }
    Map<ResourceLocation, Collection<Holder<T>>> tags = (Map) allTags.get(this.base.registry());

    if (tags == null) {
      return Set.of();
    }

    return tags.getOrDefault(tag.location(), Set.of());
  }

  @Override
  public void writeParameters(JsonObject json) {
    json.addProperty("registry", this.base.registry().location().toString());
    json.addProperty("base", this.base.location().toString());
    JsonArray names = new JsonArray();
    for (TagKey<?> name : this.subtracted) {
      names.add(name.location().toString());
    }
    json.add("subtracted", names);
  }

  public static <T> TagDifferencePresentCondition<T> readGeneric(JsonObject json) {
    ResourceKey<Registry<T>> registry = ResourceKey.createRegistryKey(JsonHelper.getResourceLocation(json, "registry"));
    return new TagDifferencePresentCondition<>(
      TagKey.create(registry, JsonHelper.getResourceLocation(json, "base")),
      JsonHelper.parseList(json, "subtracted", (e, s) -> TagKey.create(registry, JsonHelper.convertToResourceLocation(e, s))));
  }
}
