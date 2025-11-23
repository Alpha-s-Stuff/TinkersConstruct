package slimeknights.tconstruct;

import net.fabricmc.api.EnvType;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.fabricmc.fabric.api.lookup.v1.item.ItemApiLookup;
import net.fabricmc.fabric.api.transfer.v1.context.ContainerItemContext;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.TextComponent;
import slimeknights.mantle.transfer.item.IItemHandler;
import slimeknights.tconstruct.fluids.FluidEvents;
import slimeknights.tconstruct.library.tools.capability.ToolInventoryCapability;
import slimeknights.tconstruct.library.tools.nbt.IToolStackView;
import slimeknights.tconstruct.shared.AchievementEvents;
import slimeknights.tconstruct.shared.CommonsEvents;
import slimeknights.tconstruct.tools.logic.InteractionHandler;
import slimeknights.tconstruct.tools.logic.ToolEvents;

public class FabricEvents {

  public static final ItemApiLookup<IItemHandler, IToolStackView> ITEM_STORAGE = ItemApiLookup.get(TConstruct.getResource("item_storage"), IItemHandler.class, IToolStackView.class);

  public static void init() {
    FluidEvents.onFurnaceFuel();
    ToolEvents.init();
    CommonsEvents.init();
    AchievementEvents.init();
    InteractionHandler.init();

    ITEM_STORAGE.registerFallback((itemStack, tool) -> {
      if (tool.getVolatileData().getInt(ToolInventoryCapability.TOTAL_SLOTS) > 0) {
        return new ToolInventoryCapability(() -> tool);
      }
      return null;
    });
    
    if (FabricLoader.getInstance().getEnvironmentType() == EnvType.CLIENT && FabricLoader.getInstance().isDevelopmentEnvironment()) {
      ItemTooltipCallback.EVENT.register((stack, context, lines) -> {
        stack.getTags().forEach(tagKey ->
          lines.add(new TextComponent("#" + tagKey.location().toString()).withStyle(ChatFormatting.GRAY))
        );
      });
    }
  }
  
//  private static class Client {
//    @Environment(EnvType.CLIENT)
//    private static void init() {
//      WorldClientEvents.clientSetup();
//    }
//  }
}
