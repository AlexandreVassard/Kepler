package org.alexdev.kepler.server.rcon;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import org.alexdev.kepler.Kepler;
import org.alexdev.kepler.dao.mysql.CurrencyDao;
import org.alexdev.kepler.dao.mysql.PlayerDao;
import org.alexdev.kepler.game.player.Player;
import org.alexdev.kepler.game.room.Room;
import org.alexdev.kepler.game.room.RoomManager;
import org.alexdev.kepler.game.player.PlayerDetails;
import org.alexdev.kepler.game.player.PlayerManager;
import org.alexdev.kepler.log.Log;
import org.alexdev.kepler.messages.outgoing.user.ALERT;
import org.alexdev.kepler.messages.outgoing.rooms.user.FIGURE_CHANGE;
import org.alexdev.kepler.messages.outgoing.user.USER_OBJECT;
import org.alexdev.kepler.messages.outgoing.user.currencies.CREDIT_BALANCE;
import org.alexdev.kepler.server.rcon.messages.RconMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

public class RconConnectionHandler extends ChannelInboundHandlerAdapter {
    final private static Logger log = LoggerFactory.getLogger(RconConnectionHandler.class);

    private final RconServer server;

    public RconConnectionHandler(RconServer rconServer) {
        this.server = rconServer;
    }

    @Override
    public void channelRegistered(ChannelHandlerContext ctx) {
        if (!this.server.getChannels().add(ctx.channel()) || Kepler.isShuttingdown()) {
            //Log.getErrorLogger().error("Could not accept RCON connection from {}", ctx.channel().remoteAddress().toString().replace("/", "").split(":")[0]);
            ctx.close();
        }

        //log.info("[RCON] Connection from {}", ctx.channel().remoteAddress().toString().replace("/", "").split(":")[0]);
    }

    @Override
    public void channelUnregistered(ChannelHandlerContext ctx) {
        this.server.getChannels().remove(ctx.channel());
        //log.info("[RCON] Disconnection from {}", ctx.channel().remoteAddress().toString().replace("/", "").split(":")[0]);
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) {
        if (!(msg instanceof RconMessage)) {
            return;
        }

        RconMessage message = (RconMessage) msg;
        //log.info("[RCON] Message received: " + message.getHeader());

        try {
            switch (message.getHeader()) {
                case REFRESH_LOOKS:
                    Player online = PlayerManager.getInstance().getPlayerById(Integer.parseInt(message.getValues().get("userId")));

                    if (online != null) {
                        online.getRoomUser().refreshAppearance();
                    }

                    break;
                case HOTEL_ALERT:
                    String hotelAlert = message.getValues().get("message");

                    StringBuilder alert = new StringBuilder();
                    alert.append(hotelAlert);

                    if (message.getValues().containsKey("sender")) {
                    String messageSender = message.getValues().get("sender");
                    alert.append("<br>");
                    alert.append("<br>");
                    alert.append("- ").append(messageSender);
}
                    for (Player player : PlayerManager.getInstance().getPlayers()) {
                        player.send(new ALERT(alert.toString()));
                    }
                    break;
                case REFRESH_CLUB:
                    online = PlayerManager.getInstance().getPlayerById(Integer.parseInt(message.getValues().get("userId")));

                    if (online != null) {
                        PlayerDetails playerDetails = PlayerDao.getDetails(online.getDetails().getId());

                        online.getDetails().setCredits(playerDetails.getCredits());
                        online.getDetails().setClubExpiration(playerDetails.getClubExpiration());
                        online.getDetails().setFirstClubSubscription(playerDetails.getFirstClubSubscription());

                        online.send(new CREDIT_BALANCE(online.getDetails()));
                        online.refreshFuserights();
                        online.refreshClub();
                    }

                    break;
                case REFRESH_HAND:
                    online = PlayerManager.getInstance().getPlayerById(Integer.parseInt(message.getValues().get("userId")));

                    if (online != null) {
                        online.getInventory().reload();

                        if (online.getRoomUser().getRoom() != null)
                            online.getInventory().getView("new");
                    }

                    break;
                case REFRESH_CREDITS:
                    online = PlayerManager.getInstance().getPlayerById(Integer.parseInt(message.getValues().get("userId")));

                    if (online != null) {
                        online.getDetails().setCredits(CurrencyDao.getCredits(online.getDetails().getId()));
                        online.send(new CREDIT_BALANCE(online.getDetails()));
                    }

                    break;
                case REFRESH_MOTTO:
                    online = PlayerManager.getInstance().getPlayerById(Integer.parseInt(message.getValues().get("userId")));

                    if (online != null) {
                        PlayerDetails newDetails = PlayerDao.getDetails(online.getDetails().getId());
                        online.getDetails().setMotto(newDetails.getMotto());

                        online.send(new USER_OBJECT(online.getDetails()));

                        if (online.getRoomUser().getRoom() != null) {
                            online.getRoomUser().getRoom().send(new FIGURE_CHANGE(online.getRoomUser().getInstanceId(), online.getDetails()));
                        }
                    }

                    break;
                case USER_ALERT:
                    online = PlayerManager.getInstance().getPlayerById(Integer.parseInt(message.getValues().get("userId")));

                    if (online != null) {
                        StringBuilder userAlert = new StringBuilder();
                        userAlert.append(message.getValues().get("message"));

                        if (message.getValues().containsKey("sender")) {
                            String messageSender = message.getValues().get("sender");
                            userAlert.append("<br>");
                            userAlert.append("<br>");
                            userAlert.append("- ").append(messageSender);
                        }

                        online.send(new ALERT(userAlert.toString()));
                    }

                    break;
                case DISCONNECT_USER:
                    online = PlayerManager.getInstance().getPlayerById(Integer.parseInt(message.getValues().get("userId")));

                    if (online != null) {
                        online.kickFromServer();
                    }

                    break;
                case ROOM_ALERT:
                    Room room = RoomManager.getInstance().getRoomById(Integer.parseInt(message.getValues().get("roomId")));

                    if (room != null && room.isActive()) {
                        StringBuilder roomAlert = new StringBuilder();
                        roomAlert.append(message.getValues().get("message"));

                        if (message.getValues().containsKey("sender")) {
                            String messageSender = message.getValues().get("sender");
                            roomAlert.append("<br>");
                            roomAlert.append("<br>");
                            roomAlert.append("- ").append(messageSender);
                        }

                        room.send(new ALERT(roomAlert.toString()));
                    }

                    break;
            }
        } catch (Exception ex) {
            Log.getErrorLogger().error("[RCON] Error occurred when handling RCON message: ", ex);
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        if (cause instanceof Exception) {
            if (!(cause instanceof IOException)) {
                Log.getErrorLogger().error("[RCON] Error occurred: ", cause);
            }
        }

        ctx.close();
    }
}
