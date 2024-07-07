package one.lbs.velocityproxywhitelist.utils;

import com.google.inject.Inject;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer;
import one.lbs.velocityproxywhitelist.VelocityProxyWhitelist;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.Constructor;
import org.yaml.snakeyaml.error.YAMLException;
import org.yaml.snakeyaml.nodes.Tag;
import org.yaml.snakeyaml.representer.Representer;

import javax.annotation.Nullable;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.concurrent.CopyOnWriteArrayList;

// From Lazy-Bing-Server/lls-manager com.plusls.llsmanager.data.LBSWhiteList/AbstractConfig
public class YamlConfig {
    protected final Path path;
    private ConfigData configData = new ConfigData();
    public VelocityProxyWhitelist pluginInst;


    public YamlConfig(VelocityProxyWhitelist pluginInst, Path path) {
        this.pluginInst = pluginInst;
        this.path = path;
    }

    // Configuration read & write
    public boolean save() {
        DumperOptions dumperOptions = new DumperOptions();
        dumperOptions.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
        Representer representer = new Representer(dumperOptions);
        Yaml yaml = new Yaml(representer);

        if (!Files.exists(path)) {
            try {
                Files.createFile(path);
            } catch (IOException e) {
                e.printStackTrace();
                pluginInst.logger.error("Save {} error: createFile fail.", path);
                return false;
            }
        }
        BufferedWriter bfw;
        try {
            bfw = Files.newBufferedWriter(path);
        } catch (IOException e) {
            e.printStackTrace();
            pluginInst.logger.error("Save {} error: newBufferedWriter fail.", path);
            return false;
        }

        try {
            bfw.write(yaml.dumpAs(getData(), Tag.MAP, DumperOptions.FlowStyle.BLOCK));
            bfw.close();
        } catch (IOException e) {
            e.printStackTrace();
            pluginInst.logger.error("Save {} error: bfw.write fail.", path);
            return false;
        }
        return true;
    }

    public boolean load() {
        Constructor constructor = new Constructor(ConfigData.class);
        Yaml yaml = new Yaml(constructor);

        if (!Files.exists(path)) {
            return save();
        }
        try {
            BufferedReader bfr = Files.newBufferedReader(path);
            setData(yaml.load(bfr));
            bfr.close();
        } catch (IOException e) {
            e.printStackTrace();
            pluginInst.logger.error("Load {} error: newBufferedReader fail.", path);
            return false;
        } catch (YAMLException e) {
            pluginInst.logger.error("YAML {} parser fail!!", path);
            return false;
        }
        return true;
    }

    public ConfigData getData() {
        return configData;
    }

    protected void setData(ConfigData data) {
        configData = data;
    }

    // Data Fields
    public static class ConfigData {
        public boolean enableWhiteList = false;
        public String whiteListBlockReason = "";
        public CopyOnWriteArrayList<String> whiteList = new CopyOnWriteArrayList<>();
        public boolean enableBlackList = true;
        public ConcurrentHashMap<String, String> blackList = new ConcurrentHashMap<>();
        public boolean enableSubServerPermission = true;
        public CopyOnWriteArrayList<String> defaultDisabledServers = new CopyOnWriteArrayList<>();
        public String subServerBlockedReason = "";


    }

    // Functional methods
    public boolean isWhiteListEnabled() {
        return configData.enableWhiteList;
    }

    public boolean isBlackListEnabled() {
        return configData.enableBlackList;
    }

    public boolean isPlayerWhitelisted(UUID uuid) {
        return configData.whiteList.contains(uuid.toString());
    }

    public boolean isPlayerBlacklisted(UUID uuid) {
        return configData.blackList.containsKey(uuid.toString());
    }

    public boolean isSubServerPermissionEnabled() {
        return configData.enableSubServerPermission;
    }

    public boolean isSubServerDefaultDisabled(String serverName) {
        return configData.defaultDisabledServers.contains(serverName);
    }

    public @Nullable Component getReasonComponent(String reason, @Nullable Component defaultReason) {
        if (reason.isEmpty()) {
            return defaultReason;
        }
        try {
            return GsonComponentSerializer.gson().deserialize(reason);
        } catch (Exception e) {
            return Component.text(reason);
        }
    }

    public Component getSubServerBlockedReason() {
        return getReasonComponent(
                configData.subServerBlockedReason,
                Component.translatable("multiplayer.disconnect.not_whitelisted")
        );
    }

    public Component getPlayerBannedReason(UUID uuid) {
        return getReasonComponent(
                Objects.requireNonNull(configData.blackList.get(uuid.toString())),
                Component.translatable("multiplayer.disconnect.banned")
        );
    }

    public @Nullable Component getPlayerBannedReason(UUID uuid, @Nullable Component defaultReason) {
        return getReasonComponent(
                Objects.requireNonNull(configData.blackList.get(uuid.toString())),
                defaultReason
        );
    }

    public Component getWhiteListBlockReason(){
        return getReasonComponent(
                configData.whiteListBlockReason,
                Component.translatable("multiplayer.disconnect.not_whitelisted")
        );
    }

    public boolean whitelistAdd(UUID uuid) {
        if (isPlayerWhitelisted(uuid)) {
            return false;
        } else {
            configData.whiteList.add(uuid.toString());
            return save();
        }
    }

    public boolean blacklistAdd(UUID uuid, Component reason) {
        return blacklistAdd(uuid, GsonComponentSerializer.gson().serialize(reason));
    }

    public boolean blacklistAdd(UUID uuid, String reason) {
        if (isPlayerBlacklisted(uuid)) {
            return false;
        } else {
            configData.blackList.put(uuid.toString(), reason);
            return save();
        }
    }

    public boolean whitelistRemove(UUID uuid) {
        if (!isPlayerWhitelisted(uuid)) {
            return false;
        } else {
            configData.whiteList.remove(uuid.toString());
            return save();
        }
    }

    public boolean blacklistRemove(UUID uuid) {
        if (!isPlayerBlacklisted(uuid)) {
            return false;
        } else {
            configData.blackList.remove(uuid.toString());
            return save();
        }
    }

    public boolean setWhitelistStatus(boolean status) {
        if (configData.enableWhiteList == status) {
            return false;
        } else {
            configData.enableWhiteList = status;
            return save();
        }
    }

    public boolean setBlacklistStatus(boolean status) {
        if (configData.enableBlackList == status) {
            return false;
        } else {
            configData.enableBlackList = status;
            return save();
        }
    }

    public boolean setSubServerPermissionStatus(boolean status) {
        if (configData.enableSubServerPermission == status) {
            return false;
        } else {
            configData.enableSubServerPermission = status;
            return save();
        }
    }
}
