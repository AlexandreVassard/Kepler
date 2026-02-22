package org.alexdev.kepler.server.rcon.messages;

public enum RconHeader {
    REFRESH_LOOKS("refresh_looks"),
    HOTEL_ALERT("hotel_alert"),
    REFRESH_CLUB("refresh_club"),
    REFRESH_HAND("refresh_hand"),
    REFRESH_CREDITS("refresh_credits"),
    REFRESH_MOTTO("refresh_motto"),
    USER_ALERT("user_alert"),
    DISCONNECT_USER("disconnect_user"),
    ROOM_ALERT("room_alert"),
    REFRESH_CATALOGUE("refresh_catalogue"),
    KICK_USER("kick_user"),
    MUTE_USER("mute_user"),
    UNMUTE_USER("unmute_user"),
    MASS_EVENT("mass_event"),
    REFRESH_BADGE("refresh_badge"),
    RELOAD_SETTINGS("reload_settings"),
    SHUTDOWN("shutdown"),
    SHUTDOWN_CANCEL("shutdown_cancel"),
    FORWARD("forward");

    private final String rawHeader;

    RconHeader(String rawHeader) {
        this.rawHeader = rawHeader;
    }

    public String getRawHeader() {
        return rawHeader;
    }

    public static RconHeader getByHeader(String header) {
        for (var rconHeader : values()) {
            if (rconHeader.getRawHeader().equalsIgnoreCase(header)) {
                return rconHeader;
            }
        }

        return null;
    }
}
