package org.alexdev.kepler.server.rcon.messages;

public class RconResponse {
    private final int code;
    private final String message;

    public RconResponse(int code, String message) {
        this.code = code;
        this.message = message;
    }

    public int getCode() { return code; }
    public String getMessage() { return message; }

    public static RconResponse ok() { return new RconResponse(200, "OK"); }
    public static RconResponse notFound(String what) { return new RconResponse(404, what + " not found"); }
    public static RconResponse error(String reason) { return new RconResponse(500, reason); }
}
