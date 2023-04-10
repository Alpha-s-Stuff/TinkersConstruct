package slimeknights.tconstruct.shared.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import slimeknights.tconstruct.TConstruct;
import slimeknights.tconstruct.shared.command.argument.MaterialArgument;
import slimeknights.tconstruct.shared.command.argument.ModifierArgument;
import slimeknights.tconstruct.shared.command.argument.SlotTypeArgument;
import slimeknights.tconstruct.shared.command.argument.ToolStatArgument;
import slimeknights.tconstruct.shared.command.subcommand.GeneratePartTexturesCommand;
import slimeknights.tconstruct.shared.command.subcommand.ModifierUsageCommand;
import slimeknights.tconstruct.shared.command.subcommand.ModifiersCommand;
import slimeknights.tconstruct.shared.command.subcommand.SlotsCommand;
import slimeknights.tconstruct.shared.command.subcommand.StatsCommand;

import java.util.function.Consumer;

public class TConstructCommand {

  /** Registers all TConstruct command related content */
  public static void init() {
    ArgumentTypes.register(TConstruct.resourceString("slot_type"), SlotTypeArgument.class, new EmptyArgumentSerializer<>(SlotTypeArgument::slotType));
    ArgumentTypes.register(TConstruct.resourceString("tool_stat"), ToolStatArgument.class, new EmptyArgumentSerializer<>(ToolStatArgument::stat));
    ArgumentTypes.register(TConstruct.resourceString("modifier"), ModifierArgument.class, new EmptyArgumentSerializer<>(ModifierArgument::modifier));
    ArgumentTypes.register(TConstruct.resourceString("material"), MaterialArgument.class, new EmptyArgumentSerializer<>(MaterialArgument::material));

    // add command listener
    CommandRegistrationCallback.EVENT.register(TConstructCommand::registerCommand);
  }

  /** Registers a sub command for the root Mantle command */
  private static void register(LiteralArgumentBuilder<CommandSourceStack> root, String name, Consumer<LiteralArgumentBuilder<CommandSourceStack>> consumer) {
    LiteralArgumentBuilder<CommandSourceStack> subCommand = Commands.literal(name);
    consumer.accept(subCommand);
    root.then(subCommand);
  }

  /** Event listener to register the Mantle command */
  private static void registerCommand(CommandDispatcher<CommandSourceStack> dispatcher, CommandBuildContext registryAccess, Commands.CommandSelection environment) {
    LiteralArgumentBuilder<CommandSourceStack> builder = Commands.literal(TConstruct.MOD_ID);

    // sub commands
    register(builder, "modifiers", ModifiersCommand::register);
    register(builder, "tool_stats", StatsCommand::register);
    register(builder, "slots", SlotsCommand::register);
    register(builder, "modifier_usage", ModifierUsageCommand::register);
    register(builder, "generate_part_textures", GeneratePartTexturesCommand::register);

    // register final command
    dispatcher.register(builder);
  }
}
