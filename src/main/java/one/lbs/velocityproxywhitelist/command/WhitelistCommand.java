package one.lbs.velocityproxywhitelist.command;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import com.mojang.brigadier.tree.LiteralCommandNode;
import com.velocitypowered.api.command.BrigadierCommand;
import com.velocitypowered.api.command.CommandSource;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import one.lbs.velocityproxywhitelist.VelocityProxyWhitelist;
import one.lbs.velocityproxywhitelist.utils.UUIDUtils;
import one.lbs.velocityproxywhitelist.utils.TextUtil;

import javax.annotation.Nullable;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@Singleton
public class WhitelistCommand {
    @Inject
    VelocityProxyWhitelist pluginInst;
    public BrigadierCommand createBrigadierCommand() {
        LiteralCommandNode<CommandSource> llsSeenNode = LiteralArgumentBuilder
                .<CommandSource>literal("wlist")
                .requires(commandSource -> commandSource.hasPermission("vproxywhitelist.command.wlist"))
                .executes(pluginInst.injector.getInstance(StatusUtils.class)::whiteListStatus)
                .then(pluginInst.injector.getInstance(AddCommand.class).createSubCommand())
                .then(pluginInst.injector.getInstance(AddCommand.class).createOnlineCommand())
                .then(pluginInst.injector.getInstance(AddCommand.class).createOfflineCommand())
                .then(pluginInst.injector.getInstance(AddCommand.class).createBothCommand())
                .then(pluginInst.injector.getInstance(RemoveCommand.class).createSubCommand())
                .then(pluginInst.injector.getInstance(HelpCommand.class).createSubCommand())
                .then(
                        LiteralArgumentBuilder.<CommandSource>literal("enable")
                                .executes(pluginInst.injector.getInstance(StatusUtils.class)::enableWhitelist)
                )
                .then(
                        LiteralArgumentBuilder.<CommandSource>literal("disable")
                                .executes(pluginInst.injector.getInstance(StatusUtils.class)::disableWhitelist)
                ).then(pluginInst.injector.getInstance(QueryCommand.class).createSubCommand())
                .build();
        return new BrigadierCommand(llsSeenNode);
    }

    @Singleton
    private static class StatusUtils {

        @Inject
        VelocityProxyWhitelist pluginInst;

        public int enableWhitelist(CommandContext<CommandSource> commandContext) {
            CommandSource source = commandContext.getSource();
            if (pluginInst.config.isWhiteListEnabled()) {
                source.sendMessage(Component.translatable("velocityproxywhitelist.command.wlist.enable.already"));
                return 0;
            }
            boolean value = pluginInst.config.setWhitelistStatus(true);
            if (!value) {
                source.sendMessage(Component.translatable("velocityproxywhitelist.command.wlist.enable.failure"));
                return 0;
            }
            source.sendMessage(Component.translatable("velocityproxywhitelist.command.wlist.enable.success"));
            return 1;
        }

        public int disableWhitelist(CommandContext<CommandSource> commandContext) {
            CommandSource source = commandContext.getSource();
            if (!pluginInst.config.isWhiteListEnabled()) {
                source.sendMessage(Component.translatable("velocityproxywhitelist.command.wlist.disable.already"));
                return 0;
            }
            boolean value = pluginInst.config.setWhitelistStatus(false);
            if (!value) {
                source.sendMessage(Component.translatable("velocityproxywhitelist.command.wlist.disable.failure"));
                return 0;
            }
            source.sendMessage(Component.translatable("velocityproxywhitelist.command.wlist.disable.success"));
            return 1;
        }

        public int whiteListStatus(CommandContext<CommandSource> commandContext) {
            CommandSource source = commandContext.getSource();
            NamedTextColor color;
            boolean enabledFlag = pluginInst.config.isWhiteListEnabled();
            if (enabledFlag) {
                color = NamedTextColor.GREEN;
            } else {
                color = NamedTextColor.RED;
            }
            source.sendMessage(
                    Component.translatable("velocityproxywhitelist.command.wlist.status").args(
                            Component.text(String.valueOf(enabledFlag))
                                    .color(color)
                    )
            );
            return 1;
        }
    }

    @Singleton
    private static class AddCommand implements Command {

        @Inject
        VelocityProxyWhitelist pluginInst;

        @Override
        public LiteralArgumentBuilder<CommandSource> createSubCommand() {
            return LiteralArgumentBuilder.<CommandSource>literal("add")
                    .then(RequiredArgumentBuilder.<CommandSource, String>argument("username/uuid", StringArgumentType.string())
                            .executes(this)
                    );
        }

        public LiteralArgumentBuilder<CommandSource> createOnlineCommand() {
            return LiteralArgumentBuilder.<CommandSource>literal("onlineAdd")
                    .then(RequiredArgumentBuilder.<CommandSource, String>argument("username", StringArgumentType.string())
                            .executes(this::onlineAdd)
                    );
        }

        public LiteralArgumentBuilder<CommandSource> createOfflineCommand() {
            return LiteralArgumentBuilder.<CommandSource>literal("offlineAdd")
                    .then(RequiredArgumentBuilder.<CommandSource, String>argument("username", StringArgumentType.string())
                            .executes(this::offlineAdd)
                    );
        }

        public LiteralArgumentBuilder<CommandSource> createBothCommand() {
            return LiteralArgumentBuilder.<CommandSource>literal("bothAdd")
                    .then(RequiredArgumentBuilder.<CommandSource, String>argument("username", StringArgumentType.string())
                            .executes(this::bothAdd)
                    );
        }

        private int errorOccurred(CommandSource source, String username) {
            source.sendMessage(Component.translatable("velocityproxywhitelist.command.wlist.add.failure")
                    .color(NamedTextColor.RED)
                    .args(TextUtil.getUsernameComponent(username))
            );
            return 0;
        }

        @Override
        public int run(CommandContext<CommandSource> commandContext) {
            String username = commandContext.getArgument("username/uuid", String.class);
            CommandSource source = commandContext.getSource();
            @Nullable UUID playerUUID = UUIDUtils.getUUID(pluginInst, username);
            if (playerUUID == null) {
                return errorOccurred(source, username);
            }

            if (pluginInst.config.isPlayerWhitelisted(playerUUID)) {
                source.sendMessage(Component.translatable("velocityproxywhitelist.command.wlist.add.already_in_whitelist")
                        .color(NamedTextColor.RED)
                        .args(TextUtil.getUsernameComponent(username).color(NamedTextColor.YELLOW),
                                Component.text(playerUUID.toString()).color(NamedTextColor.GOLD)));
                return 0;
            } else {
                boolean saved = pluginInst.config.whitelistAdd(playerUUID);
                if (!saved) {
                    return errorOccurred(source, username);
                } else {
                    source.sendMessage(Component.translatable("velocityproxywhitelist.command.wlist.add.success")
                            .color(NamedTextColor.GREEN)
                            .args(
                                    TextUtil.getUsernameComponent(username).color(NamedTextColor.YELLOW),
                                    Component.text(playerUUID.toString()).color(NamedTextColor.GOLD)
                            ));
                    return 1;
                }
            }
        }

        public int onlineAdd(CommandContext<CommandSource> commandContext) {
            String username = commandContext.getArgument("username", String.class);
            CommandSource source = commandContext.getSource();
            @Nullable UUID playerUUID = UUIDUtils.getOnlineUUIDFromUserName(username);
            if (playerUUID == null) {
                return errorOccurred(source, username);
            }
            if (pluginInst.config.isPlayerWhitelisted(playerUUID)) {
                source.sendMessage(Component.translatable("velocityproxywhitelist.command.wlist.add.already_in_whitelist")
                        .color(NamedTextColor.RED)
                        .args(TextUtil.getUsernameComponent(username).color(NamedTextColor.YELLOW),
                                Component.text(playerUUID.toString()).color(NamedTextColor.GOLD)));
                return 0;
            }
            boolean saved = pluginInst.config.whitelistAdd(playerUUID);
            if (!saved) {
                return errorOccurred(source, username);
            } else {
                source.sendMessage(Component.translatable("velocityproxywhitelist.command.wlist.add.success")
                        .color(NamedTextColor.GREEN)
                        .args(
                                TextUtil.getUsernameComponent(username).color(NamedTextColor.YELLOW),
                                Component.text(playerUUID.toString()).color(NamedTextColor.GOLD)
                        ));
                return 1;
            }
        }

        public int offlineAdd(CommandContext<CommandSource> commandContext) {
            String username = commandContext.getArgument("username", String.class);
            CommandSource source = commandContext.getSource();
            @Nullable UUID playerUUID = UUIDUtils.getOfflineUUIDFromUserName(username);
            if (playerUUID == null) {
                return errorOccurred(source, username);
            }
            if (pluginInst.config.isPlayerWhitelisted(playerUUID)) {
                source.sendMessage(Component.translatable("velocityproxywhitelist.command.wlist.add.already_in_whitelist")
                        .color(NamedTextColor.RED)
                        .args(TextUtil.getUsernameComponent(username).color(NamedTextColor.YELLOW),
                                Component.text(playerUUID.toString()).color(NamedTextColor.GOLD)));
                return 0;
            }
            boolean saved = pluginInst.config.whitelistAdd(playerUUID);
            if (!saved) {
                return errorOccurred(source, username);
            } else {
                source.sendMessage(Component.translatable("velocityproxywhitelist.command.wlist.add.success")
                        .color(NamedTextColor.GREEN)
                        .args(
                                TextUtil.getUsernameComponent(username).color(NamedTextColor.YELLOW),
                                Component.text(playerUUID.toString()).color(NamedTextColor.GOLD)
                        ));
                return 1;
            }
        }

        public int bothAdd(CommandContext<CommandSource> commandContext) {
            int online = onlineAdd(commandContext);
            int offline = offlineAdd(commandContext);
            int both = online + offline;
            if (both > 1) {
                return  1;
            }
            return both;
        }
    }

    @Singleton
    private static class RemoveCommand implements Command {

        @Inject
        VelocityProxyWhitelist pluginInst;

        @Override
        public LiteralArgumentBuilder<CommandSource> createSubCommand() {
            return LiteralArgumentBuilder.<CommandSource>literal("remove")
                    .then(RequiredArgumentBuilder.<CommandSource, String>argument("username/uuid", StringArgumentType.string())
                            .executes(this));
        }

        private int errorOccurred(CommandSource source, String username) {
            source.sendMessage(Component.translatable("velocityproxywhitelist.command.wlist.remove.failure")
                    .color(NamedTextColor.RED)
                    .args(TextUtil.getUsernameComponent(username))
            );
            return 0;
        }

        @Override
        public int run(CommandContext<CommandSource> commandContext) {
            String username = commandContext.getArgument("username/uuid", String.class);
            CommandSource source = commandContext.getSource();
            UUID playerUUID = UUIDUtils.getUUID(pluginInst, username);
            if (playerUUID == null) {
                return errorOccurred(source, username);
            }

            if (!pluginInst.config.isPlayerWhitelisted(playerUUID)) {
                source.sendMessage(Component.translatable("velocityproxywhitelist.command.wlist.remove.not_in_whitelist")
                        .color(NamedTextColor.RED)
                        .args(TextUtil.getUsernameComponent(username).color(NamedTextColor.YELLOW),
                                Component.text(playerUUID.toString()).color(NamedTextColor.GOLD)));
                return 0;
            } else {
                boolean saved = pluginInst.config.whitelistRemove(playerUUID);
                if (!saved) {
                    return errorOccurred(source, username);
                } else {
                    source.sendMessage(Component.translatable("velocityproxywhitelist.command.wlist.remove.success")
                            .color(NamedTextColor.GREEN)
                            .args(TextUtil.getUsernameComponent(username).color(NamedTextColor.YELLOW),
                                    Component.text(playerUUID.toString()).color(NamedTextColor.GOLD)));
                    return 1;
                }
            }
        }
    }

    @Singleton
    private static class QueryCommand implements Command {

        @Inject
        VelocityProxyWhitelist pluginInst;

        @Override
        public LiteralArgumentBuilder<CommandSource> createSubCommand() {
            return LiteralArgumentBuilder.<CommandSource>literal("query")
                    .then(RequiredArgumentBuilder.<CommandSource, String>argument("username/uuid", StringArgumentType.string())
                            .executes(this)
                    );
        }

        private Component notWhitelisted(String target){
            return notWhitelisted(target, NamedTextColor.YELLOW);
        }

        private Component notWhitelisted(String target, NamedTextColor color) {
            return Component.translatable("velocityproxywhitelist.command.wlist.query.not").args(
                    Component.text(target).color(color)
            );
        }

        @Override
        public int run(CommandContext<CommandSource> commandContext) {
            String username = commandContext.getArgument("username/uuid", String.class);
            CommandSource source = commandContext.getSource();
            @Nullable UUID playerUUID = null;
            try {
                playerUUID = UUID.fromString(username);
            } catch (IllegalArgumentException ignored) {}

            if (playerUUID == null) {
                @Nullable UUID onlineUUID = UUIDUtils.getOnlineUUIDFromUserName(username);
                @Nullable UUID offlineUUID = UUIDUtils.getOfflineUUIDFromUserName(username);
                boolean found = false;
                if (onlineUUID != null && pluginInst.config.isPlayerWhitelisted(onlineUUID)) {
                    found = true;
                    source.sendMessage(
                            Component.translatable("velocityproxywhitelist.command.wlist.query.username.online_in")
                                    .args(TextUtil.getUsernameComponent(username).color(NamedTextColor.YELLOW), Component.text(onlineUUID.toString()).color(NamedTextColor.GOLD))
                    );
                }
                if (offlineUUID != null && pluginInst.config.isPlayerWhitelisted(offlineUUID)) {
                    found = true;
                    source.sendMessage(
                            Component.translatable("velocityproxywhitelist.command.wlist.query.username.offline_in")
                                    .args(TextUtil.getUsernameComponent(username).color(NamedTextColor.YELLOW), Component.text(offlineUUID.toString()).color(NamedTextColor.GOLD))
                    );
                }
                if (!found) {
                    source.sendMessage(notWhitelisted(username));
                }
            } else {
                if (pluginInst.config.isPlayerWhitelisted(playerUUID)) {
                    source.sendMessage(
                            Component.translatable("velocityproxywhitelist.command.wlist.query.uuid.in").args(
                                    Component.text(playerUUID.toString()).color(NamedTextColor.GOLD)
                            )
                    );
                } else {
                    source.sendMessage(notWhitelisted(playerUUID.toString(), NamedTextColor.GOLD));
                }
            }
            return 1;
        }
    }

    @Singleton
    private static class HelpCommand implements Command {

        @Override
        public LiteralArgumentBuilder<CommandSource> createSubCommand() {
            return LiteralArgumentBuilder.<CommandSource>literal("help").executes(this);
        }

        @Override
        public int run(CommandContext<CommandSource> commandContext) {
            CommandSource source = commandContext.getSource();
            for (int i = 0; i < 6; ++i) {
                source.sendMessage(Component.translatable(String.format("velocityproxywhitelist.command.wlist.hint%d", i)));
            }
            return 1;
        }
    }
}
