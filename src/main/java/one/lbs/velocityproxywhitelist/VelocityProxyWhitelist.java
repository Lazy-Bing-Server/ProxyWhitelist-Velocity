package one.lbs.velocityproxywhitelist;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.inject.Inject;
import com.google.inject.Injector;
import com.velocitypowered.api.event.PostOrder;
import com.velocitypowered.api.event.ResultedEvent;
import com.velocitypowered.api.event.connection.LoginEvent;
import com.velocitypowered.api.event.player.ServerPreConnectEvent;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.proxy.ProxyReloadEvent;
import com.velocitypowered.api.permission.Tristate;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.plugin.annotation.DataDirectory;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.util.GameProfile;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.translation.GlobalTranslator;
import net.kyori.adventure.translation.TranslationRegistry;
import net.kyori.adventure.util.UTF8ResourceBundleControl;
import one.lbs.velocityproxywhitelist.command.BlacklistCommand;
import one.lbs.velocityproxywhitelist.command.WhitelistCommand;
import one.lbs.velocityproxywhitelist.utils.YamlConfig;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.*;
import java.util.*;

@Plugin(
        id = BuildConstants.ID,
        name = BuildConstants.NAME,
        version = BuildConstants.VERSION,
        url = "http://github.com/Lazy-Bing-Server/ProxyWhitelist-Velocity",
        description = "A whitelist plugin for Velocity",
        authors = {"Ra1ny_Yuki"}
)
public class VelocityProxyWhitelist {

    @Inject
    public Logger logger;
    @Inject
    public ProxyServer server;
    @Inject
    public Injector injector;
    @Inject
    @DataDirectory
    public Path dataFolderPath;

    public static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    @Nullable
    private static VelocityProxyWhitelist instance;
    public YamlConfig config;

    @NotNull
    public static VelocityProxyWhitelist getInstance() {
        return Objects.requireNonNull(instance);
    }

    private void loadConfig() {
        config = new YamlConfig(this, dataFolderPath.resolve(Paths.get("config.yml")));
        if (!config.load()) {
            logger.error("{} load config fail!", BuildConstants.NAME);
            throw new IllegalStateException(String.format("%s init fail.", BuildConstants.NAME));
        }
        config.save();
        logger.info("Config loaded!");
    }

    @Subscribe
    public void proxyReloadEventHandler(ProxyReloadEvent event) {
        loadConfig();
    }

    @Subscribe
    public void onProxyInitialization(ProxyInitializeEvent event) {
        instance = this;
        if (!Files.exists(dataFolderPath)) {
            try {
                Files.createDirectories(dataFolderPath);
            } catch (IOException e) {
                e.printStackTrace();
                logger.error("VelocityProxyWhitelist load fail! createDirectories {} fail!!", dataFolderPath);
            }
        }
        registerTranslations();
        loadConfig();


        /* config.whitelistAdd(UUID.fromString("02ba4ee5-3ccb-4fed-98f6-51101fc831b4"));
        for (String uuid : config.getData().whiteList) {
            logger.info("Player whitelisted: {}", uuid);
        } */
        server.getCommandManager().register(injector.getInstance(WhitelistCommand.class).createBrigadierCommand());
        server.getCommandManager().register(injector.getInstance(BlacklistCommand.class).createBrigadierCommand());
    }

    private static Path getL10nPath() {
        Path l10nPath;
        URL knownResource = VelocityProxyWhitelist.class.getClassLoader().getResource("l10n/messages.properties");

        if (knownResource == null) {
            throw new IllegalStateException("messages.properties does not exist, don't know where we are");
        }
        if (knownResource.getProtocol().equals("jar")) {
            // Running from a JAR
            String jarPathRaw = knownResource.toString().split("!")[0];
            URI path = URI.create(jarPathRaw + "!/");

            FileSystem fileSystem;
            try {
                fileSystem = FileSystems.newFileSystem(path, Map.of("create", "true"));
            } catch (IOException e) {
                e.printStackTrace();
                throw new IllegalStateException(e);
            }
            l10nPath = fileSystem.getPath("l10n");
            if (!Files.exists(l10nPath)) {
                throw new IllegalStateException("l10n does not exist, don't know where we are");
            }
        } else {
            // Running from the file system
            URI uri;
            try {
                URL url = VelocityProxyWhitelist.class.getClassLoader().getResource("l10n");
                if (url == null) {
                    throw new IllegalStateException("l10n does not exist, don't know where we are");
                }
                uri = url.toURI();
            } catch (URISyntaxException e) {
                e.printStackTrace();
                throw new IllegalStateException(e);
            }
            l10nPath = Paths.get(uri);
        }
        return l10nPath;
    }

    private void registerTranslations() {
        logger.info("Loading localizations...");
        final TranslationRegistry translationRegistry = TranslationRegistry
                .create(Key.key(BuildConstants.ID, "translations"));
        translationRegistry.defaultLocale(Locale.US);

        // get l10nPath
        Path l10nPath = getL10nPath();

        try {
            Files.walk(l10nPath).forEach(file -> {
                if (!Files.isRegularFile(file)) {
                    return;
                }
                String filename = com.google.common.io.Files
                        .getNameWithoutExtension(file.getFileName().toString());
                String localeName = filename.replace("messages_", "")
                        .replace("messages", "")
                        .replace('_', '-');
                Locale locale;
                if (localeName.isEmpty()) {
                    locale = Locale.US;
                } else {
                    locale = Locale.forLanguageTag(localeName);
                }
                translationRegistry.registerAll(locale,
                        ResourceBundle.getBundle("l10n/messages",
                                locale, UTF8ResourceBundleControl.get()), false);
            });
        } catch (IOException e) {
            e.printStackTrace();
            throw new IllegalStateException(e);
        }
        GlobalTranslator.translator().addSource(translationRegistry);
    }

    @Subscribe(order = PostOrder.EARLY)
    public void onLogin(LoginEvent event) {
        if (!event.getResult().isAllowed()) {
            return;
        }
        GameProfile profile = event.getPlayer().getGameProfile();
        UUID uuid = profile.getId();
        if (config.isBlackListEnabled() && config.isPlayerBlacklisted(uuid)) {
            event.setResult(ResultedEvent.ComponentResult.denied(config.getPlayerBannedReason(uuid)));
            return;
        }
        if (config.isWhiteListEnabled() && !config.isPlayerWhitelisted(uuid)) {
            event.setResult(ResultedEvent.ComponentResult.denied(config.getWhiteListBlockReason()));
        }
    }

    @Subscribe(order = PostOrder.EARLY)
    public void onServerPreConnect(ServerPreConnectEvent event) {
        if (event.getResult().getServer().isEmpty() || config.isSubServerPermissionEnabled()) {
            return;
        }
        String serverName = event.getResult().getServer().get().getServerInfo().getName();
        String permissionNode = String.format("vproxywhitelist.servers.%s", serverName);
        Player player = event.getPlayer();
        Tristate permissionValue = player.getPermissionValue(permissionNode);

        if (permissionValue == Tristate.FALSE || (permissionValue == Tristate.UNDEFINED && config.isSubServerDefaultDisabled(serverName))) {
            event.setResult(ServerPreConnectEvent.ServerResult.denied());
            player.sendMessage(config.getSubServerBlockedReason());
        }
    }
}
