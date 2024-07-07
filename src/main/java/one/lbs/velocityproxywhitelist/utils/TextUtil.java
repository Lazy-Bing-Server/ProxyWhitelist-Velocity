package one.lbs.velocityproxywhitelist.utils;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;

public class TextUtil {

    @Deprecated
    public static Component getServerAutoComponent(String name) {
        return getServerNameComponent(name);
    }

    public static Component getServerNameComponent(String serverName) {
        HoverEvent<Component> hoverEvent = HoverEvent.showText(Component.translatable("velocityproxywhitelist.text.server.hover_event")
                .args(getServerNameComponentWithoutEvent(serverName)));
        return getServerNameComponentWithoutEvent(serverName).hoverEvent(hoverEvent)
                .clickEvent(ClickEvent.suggestCommand("/server " + serverName));
    }

    public static Component getServerNameComponentWithoutEvent(String serverName) {
        return Component.text(serverName).color(NamedTextColor.GOLD).decoration(TextDecoration.BOLD, true);
    }

    public static Component getUsernameComponent(String username) {
        HoverEvent<Component> hoverEvent = HoverEvent.showText(Component.translatable("velocityproxywhitelist.text.user.hover_event")
                .args(getUsernameComponentWithoutEvent(username)));
        return getUsernameComponentWithoutEvent(username).hoverEvent(hoverEvent)
                .clickEvent(ClickEvent.runCommand("/!!seen " + username));
    }

    public static Component getUsernameComponentWithoutEvent(String username) {
        return Component.text(username).color(NamedTextColor.GREEN);
    }

}
