package one.lbs.velocityproxywhitelist.command;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.tree.LiteralCommandNode;
import com.velocitypowered.api.command.BrigadierCommand;
import com.velocitypowered.api.command.CommandSource;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import one.lbs.velocityproxywhitelist.VelocityProxyWhitelist;
import one.lbs.velocityproxywhitelist.utils.TextUtil;
import one.lbs.velocityproxywhitelist.utils.UUIDUtils;

import javax.annotation.Nullable;
import java.util.UUID;

@Singleton
public class BlacklistCommand {

    @Inject
    VelocityProxyWhitelist pluginInst;

    public BrigadierCommand createBrigadierCommand() {
        LiteralCommandNode<CommandSource> llsSeenNode = LiteralArgumentBuilder
                .<CommandSource>literal("blist")
                .requires(commandSource -> commandSource.hasPermission("vproxywhitelist.command.blist"))
                .executes(pluginInst.injector.getInstance(StatusUtils.class)::blacklistStatus)
                .then(pluginInst.injector.getInstance(AddCommand.class).createSubCommand())
                .then(pluginInst.injector.getInstance(RemoveCommand.class).createSubCommand())
                .then(pluginInst.injector.getInstance(HelpCommand.class).createSubCommand())
                .then(
                        LiteralArgumentBuilder.<CommandSource>literal("enable")
                                .executes(pluginInst.injector.getInstance(StatusUtils.class)::enableBlacklist)
                )
                .then(
                        LiteralArgumentBuilder.<CommandSource>literal("disable")
                                .executes(pluginInst.injector.getInstance(StatusUtils.class)::disableBlacklist)
                ).then(pluginInst.injector.getInstance(QueryCommand.class).createSubCommand())
                .build();
        return new BrigadierCommand(llsSeenNode);
    }

    @Singleton
    private static class StatusUtils {

        @Inject
        VelocityProxyWhitelist pluginInst;

        public int enableBlacklist(CommandContext<CommandSource> commandContext) {
            CommandSource source = commandContext.getSource();
            if (pluginInst.config.isBlackListEnabled()) {
                source.sendMessage(Component.translatable("velocityproxywhitelist.command.blist.enable.already"));
                return 0;
            }
            boolean value = pluginInst.config.setBlacklistStatus(true);
            if (!value) {
                source.sendMessage(Component.translatable("velocityproxywhitelist.command.blist.enable.failure"));
                return 0;
            }
            source.sendMessage(Component.translatable("velocityproxywhitelist.command.blist.enable.success"));
            return 1;
        }

        public int disableBlacklist(CommandContext<CommandSource> commandContext) {
            CommandSource source = commandContext.getSource();
            if (!pluginInst.config.isBlackListEnabled()) {
                source.sendMessage(Component.translatable("velocityproxywhitelist.command.blist.disable.already"));
                return 0;
            }
            boolean value = pluginInst.config.setBlacklistStatus(false);
            if (!value) {
                source.sendMessage(Component.translatable("velocityproxywhitelist.command.blist.disable.failure"));
                return 0;
            }
            source.sendMessage(Component.translatable("velocityproxywhitelist.command.blist.disable.success"));
            return 1;
        }

        public int blacklistStatus(CommandContext<CommandSource> commandContext) {
            CommandSource source = commandContext.getSource();
            NamedTextColor color;
            boolean enabledFlag = pluginInst.config.isBlackListEnabled();
            if (enabledFlag) {
                color = NamedTextColor.GREEN;
            } else {
                color = NamedTextColor.RED;
            }
            source.sendMessage(
                    Component.translatable("velocityproxywhitelist.command.blist.status").args(
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
                            .executes(this).then(
                                    RequiredArgumentBuilder.<CommandSource, String>argument("reason", StringArgumentType.greedyString()).executes(this::runWithReason))
                    );
        }

        private int errorOccurred(CommandSource source, String username) {
            source.sendMessage(Component.translatable("velocityproxywhitelist.command.blist.add.failure")
                    .color(NamedTextColor.RED)
                    .args(TextUtil.getUsernameComponent(username))
            );
            return 0;
        }



        private int execute(CommandSource source, String username) {
            return execute(source, username, "");
        }

        private int execute(CommandSource source, String username, String reason) {
            @Nullable UUID playerUUID = UUIDUtils.getUUID(pluginInst, username);
            if (playerUUID == null) {
                return errorOccurred(source, username);
            }

            if (pluginInst.config.isPlayerBlacklisted(playerUUID)) {
                source.sendMessage(Component.translatable("velocityproxywhitelist.command.blist.add.already_in_blacklist")
                        .color(NamedTextColor.RED)
                        .args(TextUtil.getUsernameComponent(username).color(NamedTextColor.YELLOW),
                                Component.text(playerUUID.toString()).color(NamedTextColor.GOLD)));
                return 0;
            } else {
                boolean saved = pluginInst.config.blacklistAdd(playerUUID, reason);
                if (!saved) {
                    return errorOccurred(source, username);
                } else {
                    source.sendMessage(Component.translatable("velocityproxywhitelist.command.blist.add.success")
                            .color(NamedTextColor.GREEN)
                            .args(TextUtil.getUsernameComponent(username).color(NamedTextColor.YELLOW),
                                    Component.text(playerUUID.toString()).color(NamedTextColor.GOLD)));

                    @Nullable Component banReasonComponent = pluginInst.config.getReasonComponent(reason, null);
                    if (banReasonComponent == null) {
                        source.sendMessage(Component.translatable("velocityproxywhitelist.command.blist.reason.not_set"));
                    } else {
                        source.sendMessage(Component.translatable("velocityproxywhitelist.command.blist.reason.set").args(banReasonComponent));
                    }
                    return 1;
                }
            }
        }

        public int runWithReason(CommandContext<CommandSource> commandContext) {
            String username = commandContext.getArgument("username/uuid", String.class);
            String reason = commandContext.getArgument("reason", String.class);
            CommandSource source = commandContext.getSource();
            return execute(source, username, reason);
        }

        @Override
        public int run(CommandContext<CommandSource> commandContext) {
            String username = commandContext.getArgument("username/uuid", String.class);
            CommandSource source = commandContext.getSource();
            return execute(source, username);
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
            source.sendMessage(Component.translatable("velocityproxywhitelist.command.blist.remove.failure")
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

            if (!pluginInst.config.isPlayerBlacklisted(playerUUID)) {
                source.sendMessage(Component.translatable("velocityproxywhitelist.command.blist.remove.not_in_blacklist")
                        .color(NamedTextColor.RED)
                        .args(TextUtil.getUsernameComponent(username).color(NamedTextColor.YELLOW),
                                Component.text(playerUUID.toString()).color(NamedTextColor.GOLD)));
                return 0;
            } else {
                boolean saved = pluginInst.config.blacklistRemove(playerUUID);
                if (!saved) {
                    return errorOccurred(source, username);
                } else {
                    source.sendMessage(Component.translatable("velocityproxywhitelist.command.blist.remove.success")
                            .color(NamedTextColor.RED)
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

        private Component notBlacklisted(String target){
            return notBlacklisted(target, NamedTextColor.YELLOW);
        }

        private Component notBlacklisted(String target, NamedTextColor color) {
            return Component.translatable("velocityproxywhitelist.command.blist.query.not").args(
                    Component.text(target).color(color)
            );
        }

        private Component getPlayerBannedReason(UUID playerUUID) {
            Component reasonComp = pluginInst.config.getPlayerBannedReason(playerUUID, null);
            if (reasonComp == null) {
                return Component.translatable("velocityproxywhitelist.command.blist.reason.not_set");
            } else {
                return Component.translatable("velocityproxywhitelist.command.blist.reason.set").args(reasonComp);
            }
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
                if (onlineUUID != null && pluginInst.config.isPlayerBlacklisted(onlineUUID)) {
                    found = true;
                    source.sendMessage(
                            Component.translatable("velocityproxywhitelist.command.blist.query.username.online_in")
                                    .args(TextUtil.getUsernameComponent(username).color(NamedTextColor.YELLOW), Component.text(onlineUUID.toString()).color(NamedTextColor.GOLD))
                    );
                    source.sendMessage(getPlayerBannedReason(onlineUUID));
                }
                if (offlineUUID != null && pluginInst.config.isPlayerBlacklisted(offlineUUID)) {
                    found = true;
                    source.sendMessage(
                            Component.translatable("velocityproxywhitelist.command.blist.query.username.offline_in")
                                    .args(TextUtil.getUsernameComponent(username).color(NamedTextColor.YELLOW), Component.text(offlineUUID.toString()).color(NamedTextColor.GOLD))
                    );
                    source.sendMessage(getPlayerBannedReason(offlineUUID));
                }
                if (!found) {
                    source.sendMessage(notBlacklisted(username));
                }
            } else {
                if (pluginInst.config.isPlayerBlacklisted(playerUUID)) {
                    source.sendMessage(
                            Component.translatable("velocityproxywhitelist.command.blist.query.uuid.in").args(
                                    Component.text(playerUUID.toString()).color(NamedTextColor.GOLD)
                            )
                    );
                    source.sendMessage(getPlayerBannedReason(playerUUID));
                } else {
                    source.sendMessage(notBlacklisted(playerUUID.toString(), NamedTextColor.GOLD));
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
                source.sendMessage(Component.translatable(String.format("velocityproxywhitelist.command.blist.hint%d", i)));
            }
            return 1;
        }
    }
}
