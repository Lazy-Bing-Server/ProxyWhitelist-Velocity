package one.lbs.velocityproxywhitelist.utils;

import one.lbs.velocityproxywhitelist.VelocityProxyWhitelist;

import javax.annotation.Nullable;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

public class UUIDUtils {
    public static VelocityProxyWhitelist pluginInst;
    public static @Nullable UUID getUUID(VelocityProxyWhitelist inst, String username_uuid) {
        pluginInst = inst;
        inst.server.getConfiguration().isOnlineMode();
        try {
            return UUID.fromString(username_uuid);
        } catch (IllegalArgumentException ignored) {
        }

        try {
            if (inst.server.getConfiguration().isOnlineMode()){
                return getOnlineUUIDFromUserName(username_uuid);
            } else {
                return getOfflineUUIDFromUserName(username_uuid);
            }
        } catch (Exception e) {
            pluginInst.logger.error("UUID get failed", e);
            return null;
        }
    }

    public static @Nullable UUID getOnlineUUIDFromUserName(String userName) {
        VelocityProxyWhitelist pluginInst = VelocityProxyWhitelist.getInstance();
        HttpURLConnection conn;
        try {
            URL url = new URL(String.format("https://api.mojang.com/users/profiles/minecraft/%s", userName));
            conn = (HttpURLConnection) url.openConnection();
        } catch (IOException e) {
            pluginInst.logger.error("Error occurred: ", e);
            return null;
        }
        try {
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(15000);
            conn.setReadTimeout(15000);
            conn.connect();
            if (conn.getResponseCode() == 200) {
                InputStream is = conn.getInputStream();
                if (null != is) {
                    BufferedReader br = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8));
                    String inputLine;
                    StringBuilder buffer = new StringBuilder();
                    while ((inputLine = br.readLine()) != null) {
                        buffer.append(inputLine);
                    }
                    br.close();
                    String requestedString = buffer.toString();
                    MinecraftProfile profile = VelocityProxyWhitelist.GSON.fromJson(requestedString, MinecraftProfile.class);
                    return profile.getUUID();
                }
            }
        } catch (Exception e) {
            pluginInst.logger.error("Error occurred while querying online UUID: ", e);
            return null;
        } finally {
            conn.disconnect();
        }
        return null;
    }

    public static @Nullable UUID getOfflineUUIDFromUserName(String userName) {
        VelocityProxyWhitelist pluginInst = VelocityProxyWhitelist.getInstance();
        try {
            return UUID.nameUUIDFromBytes(("OfflinePlayer:" + userName).getBytes());
        } catch (IllegalArgumentException e) {
            pluginInst.logger.error("Error while getting offline UUID", e);
        }
        return null;
    }

    public static class MinecraftProfile {
        public String name;
        public String id;

        public UUID getUUID() {
            return UUID.fromString(id.replaceAll(
                    "(\\w{8})(\\w{4})(\\w{4})(\\w{4})(\\w{12})",
                    "$1-$2-$3-$4-$5"));
        }
    }
}
