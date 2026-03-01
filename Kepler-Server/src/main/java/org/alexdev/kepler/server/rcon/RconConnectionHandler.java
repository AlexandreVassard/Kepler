package org.alexdev.kepler.server.rcon;

import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import org.alexdev.kepler.Kepler;
import org.alexdev.kepler.dao.mysql.CurrencyDao;
import org.alexdev.kepler.dao.mysql.PlayerDao;
import org.alexdev.kepler.game.player.Player;
import org.alexdev.kepler.game.catalogue.CatalogueManager;
import org.alexdev.kepler.game.room.Room;
import org.alexdev.kepler.game.room.RoomManager;
import org.alexdev.kepler.game.player.PlayerDetails;
import org.alexdev.kepler.game.player.PlayerManager;
import org.alexdev.kepler.log.Log;
import org.alexdev.kepler.messages.outgoing.catalogue.CATALOGUE_PAGES;
import org.alexdev.kepler.messages.outgoing.messenger.ROOMFORWARD;
import org.alexdev.kepler.messages.outgoing.rooms.badges.AVAILABLE_BADGES;
import org.alexdev.kepler.messages.outgoing.rooms.badges.USER_BADGE;
import org.alexdev.kepler.messages.outgoing.user.ALERT;
import org.alexdev.kepler.messages.outgoing.rooms.user.FIGURE_CHANGE;
import org.alexdev.kepler.messages.outgoing.user.USER_OBJECT;
import org.alexdev.kepler.messages.outgoing.rooms.user.HOTEL_VIEW;
import org.alexdev.kepler.messages.outgoing.user.currencies.CREDIT_BALANCE;
import org.alexdev.kepler.server.netty.NettyPlayerNetwork;
import org.alexdev.kepler.server.rcon.messages.RconMessage;
import org.alexdev.kepler.server.rcon.messages.RconResponse;
import org.alexdev.kepler.util.DateUtil;
import org.alexdev.kepler.util.config.GameConfiguration;
import org.alexdev.kepler.util.config.writer.GameConfigWriter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.time.Duration;

public class RconConnectionHandler extends ChannelInboundHandlerAdapter {
    final private static Logger log = LoggerFactory.getLogger(RconConnectionHandler.class);

    private final RconServer server;

    public RconConnectionHandler(RconServer rconServer) {
        this.server = rconServer;
    }

    @Override
    public void channelRegistered(ChannelHandlerContext ctx) {
        if (!this.server.getChannels().add(ctx.channel()) || Kepler.isShuttingdown()) {
            Log.getErrorLogger().error("Could not accept RCON connection from {}", ctx.channel().remoteAddress().toString().replace("/", "").split(":")[0]);
            ctx.close();
        }

        log.info("[RCON] Connection from {}", ctx.channel().remoteAddress().toString().replace("/", "").split(":")[0]);
    }

    @Override
    public void channelUnregistered(ChannelHandlerContext ctx) {
        this.server.getChannels().remove(ctx.channel());
        log.info("[RCON] Disconnection from {}", ctx.channel().remoteAddress().toString().replace("/", "").split(":")[0]);
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) {
        if (!(msg instanceof RconMessage message)) return;
        log.info("[RCON] Message received: " + message.getHeader());

        RconResponse response;
        try {
            response = handleMessage(message);
        } catch (Exception ex) {
            Log.getErrorLogger().error("[RCON] Error occurred when handling RCON message: ", ex);
            response = RconResponse.error(ex.getMessage() != null ? ex.getMessage() : ex.getClass().getSimpleName());
        }

        ctx.writeAndFlush(response).addListener(ChannelFutureListener.CLOSE);
    }

    private RconResponse handleMessage(RconMessage message) throws Exception {
        if (message.getHeader() == null) {
            return RconResponse.error("Unknown command");
        }

        switch (message.getHeader()) {
            case REFRESH_LOOKS: {
                Player online = PlayerManager.getInstance().getPlayerById(Integer.parseInt(message.getValues().get("userId")));

                if (online == null) {
                    return RconResponse.notFound("User");
                }

                online.getRoomUser().refreshAppearance();
                return RconResponse.ok();
            }
            case HOTEL_ALERT: {
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

                return RconResponse.ok();
            }
            case REFRESH_CLUB: {
                Player online = PlayerManager.getInstance().getPlayerById(Integer.parseInt(message.getValues().get("userId")));

                if (online == null) {
                    return RconResponse.notFound("User");
                }

                PlayerDetails playerDetails = PlayerDao.getDetails(online.getDetails().getId());

                online.getDetails().setCredits(playerDetails.getCredits());
                online.getDetails().setClubExpiration(playerDetails.getClubExpiration());
                online.getDetails().setFirstClubSubscription(playerDetails.getFirstClubSubscription());

                online.send(new CREDIT_BALANCE(online.getDetails()));
                online.refreshFuserights();
                online.refreshClub();

                return RconResponse.ok();
            }
            case REFRESH_HAND: {
                Player online = PlayerManager.getInstance().getPlayerById(Integer.parseInt(message.getValues().get("userId")));

                if (online == null) {
                    return RconResponse.notFound("User");
                }

                online.getInventory().reload();

                if (online.getRoomUser().getRoom() != null) {
                    online.getInventory().getView("new");
                }

                return RconResponse.ok();
            }
            case REFRESH_CREDITS: {
                Player online = PlayerManager.getInstance().getPlayerById(Integer.parseInt(message.getValues().get("userId")));

                if (online == null) {
                    return RconResponse.notFound("User");
                }

                online.getDetails().setCredits(CurrencyDao.getCredits(online.getDetails().getId()));
                online.send(new CREDIT_BALANCE(online.getDetails()));

                return RconResponse.ok();
            }
            case REFRESH_MOTTO: {
                Player online = PlayerManager.getInstance().getPlayerById(Integer.parseInt(message.getValues().get("userId")));

                if (online == null) {
                    return RconResponse.notFound("User");
                }

                PlayerDetails newDetails = PlayerDao.getDetails(online.getDetails().getId());
                online.getDetails().setMotto(newDetails.getMotto());

                online.send(new USER_OBJECT(online.getDetails()));

                if (online.getRoomUser().getRoom() != null) {
                    online.getRoomUser().getRoom().send(new FIGURE_CHANGE(online.getRoomUser().getInstanceId(), online.getDetails()));
                }

                return RconResponse.ok();
            }
            case USER_ALERT: {
                Player online = PlayerManager.getInstance().getPlayerById(Integer.parseInt(message.getValues().get("userId")));

                if (online == null) {
                    return RconResponse.notFound("User");
                }

                StringBuilder userAlert = new StringBuilder();
                userAlert.append(message.getValues().get("message"));

                if (message.getValues().containsKey("sender")) {
                    String messageSender = message.getValues().get("sender");
                    userAlert.append("<br>");
                    userAlert.append("<br>");
                    userAlert.append("- ").append(messageSender);
                }

                online.send(new ALERT(userAlert.toString()));

                return RconResponse.ok();
            }
            case DISCONNECT_USER: {
                Player online = PlayerManager.getInstance().getPlayerById(Integer.parseInt(message.getValues().get("userId")));

                if (online == null) {
                    return RconResponse.notFound("User");
                }

                online.kickFromServer();

                return RconResponse.ok();
            }
            case ROOM_ALERT: {
                Room room = RoomManager.getInstance().getRoomById(Integer.parseInt(message.getValues().get("roomId")));

                if (room == null || !room.isActive()) {
                    return RconResponse.notFound("Room");
                }

                StringBuilder roomAlert = new StringBuilder();
                roomAlert.append(message.getValues().get("message"));

                if (message.getValues().containsKey("sender")) {
                    String messageSender = message.getValues().get("sender");
                    roomAlert.append("<br>");
                    roomAlert.append("<br>");
                    roomAlert.append("- ").append(messageSender);
                }

                room.send(new ALERT(roomAlert.toString()));

                return RconResponse.ok();
            }
            case KICK_USER: {
                Player online = PlayerManager.getInstance().getPlayerById(Integer.parseInt(message.getValues().get("userId")));

                if (online == null) {
                    return RconResponse.notFound("User");
                }

                if (online.getRoomUser().getRoom() != null) {
                    online.getRoomUser().kick(false);
                    online.send(new HOTEL_VIEW());
                }

                return RconResponse.ok();
            }
            case MUTE_USER: {
                Player online = PlayerManager.getInstance().getPlayerById(Integer.parseInt(message.getValues().get("userId")));

                if (online == null) {
                    return RconResponse.notFound("User");
                }

                int minutes = Integer.parseInt(message.getValues().get("minutes"));
                online.getRoomUser().setMuteTime(DateUtil.getCurrentTimeSeconds() + (minutes * 60L));

                return RconResponse.ok();
            }
            case UNMUTE_USER: {
                Player online = PlayerManager.getInstance().getPlayerById(Integer.parseInt(message.getValues().get("userId")));

                if (online == null) {
                    return RconResponse.notFound("User");
                }

                online.getRoomUser().setMuteTime(0);

                return RconResponse.ok();
            }
            case MASS_EVENT: {
                Room eventRoom = RoomManager.getInstance().getRoomById(Integer.parseInt(message.getValues().get("roomId")));

                if (eventRoom == null) {
                    return RconResponse.notFound("Room");
                }

                for (Player player : PlayerManager.getInstance().getPlayers()) {
                    eventRoom.forward(player, false);
                }

                return RconResponse.ok();
            }
            case REFRESH_BADGE: {
                Player online = PlayerManager.getInstance().getPlayerById(Integer.parseInt(message.getValues().get("userId")));

                if (online == null) {
                    return RconResponse.notFound("User");
                }

                PlayerDetails badgeDetails = PlayerDao.getDetails(online.getDetails().getId());
                online.getDetails().setCurrentBadge(badgeDetails.getCurrentBadge());
                online.getDetails().setShowBadge(badgeDetails.getShowBadge());
                online.getDetails().loadBadges();

                online.send(new AVAILABLE_BADGES(online.getDetails()));

                if (online.getRoomUser().getRoom() != null) {
                    online.getRoomUser().getRoom().send(new USER_BADGE(online.getRoomUser().getInstanceId(), online.getDetails()));
                    online.getRoomUser().getRoom().send(new FIGURE_CHANGE(online.getRoomUser().getInstanceId(), online.getDetails()));
                }

                return RconResponse.ok();
            }
            case RELOAD_SETTINGS: {
                GameConfiguration.reset(new GameConfigWriter());
                return RconResponse.ok();
            }
            case SHUTDOWN: {
                int shutdownMinutes = Integer.parseInt(message.getValues().get("minutes"));
                PlayerManager.getInstance().planMaintenance(Duration.ofMinutes(shutdownMinutes));
                return RconResponse.ok();
            }
            case SHUTDOWN_CANCEL: {
                if (!PlayerManager.getInstance().isMaintenance()) {
                    return new RconResponse(404, "No shutdown scheduled");
                }

                String cancelMessage = message.getValues().containsKey("message")
                        ? message.getValues().get("message")
                        : null;

                PlayerManager.getInstance().cancelMaintenance(cancelMessage);

                return RconResponse.ok();
            }
            case FORWARD: {
                Player online = PlayerManager.getInstance().getPlayerById(Integer.parseInt(message.getValues().get("userId")));

                if (online == null) {
                    return RconResponse.notFound("User");
                }

                boolean isPublic = message.getValues().get("type").equals("1");
                int roomId = Integer.parseInt(message.getValues().get("roomId"));
                online.send(new ROOMFORWARD(isPublic, roomId));

                return RconResponse.ok();
            }
            case REFRESH_CATALOGUE: {
                CatalogueManager.reset();

                for (Player player : PlayerManager.getInstance().getPlayers()) {
                    player.send(new CATALOGUE_PAGES(
                            CatalogueManager.getInstance().getPagesForRank(player.getDetails().getRank(), player.getDetails().hasClubSubscription())
                    ));
                }

                return RconResponse.ok();
            }
            case USER_INFO: {
                Player online = PlayerManager.getInstance().getPlayerById(Integer.parseInt(message.getValues().get("userId")));

                if (online == null) {
                    return RconResponse.notFound("User");
                }

                boolean inRoom = online.getRoomUser().getRoom() != null;
                int roomId = inRoom ? online.getRoomUser().getRoom().getId() : -1;
                long muteTime = online.getRoomUser().getMuteTime();

                String posX = inRoom ? String.valueOf(online.getRoomUser().getPosition().getX()) : "-1";
                String posY = inRoom ? String.valueOf(online.getRoomUser().getPosition().getY()) : "-1";
                String posZ = inRoom ? String.valueOf(online.getRoomUser().getPosition().getZ()) : "-1";

                boolean isDiving = online.getRoomUser().isDiving();
                boolean isWalking = online.getRoomUser().isWalking();

                String statuses = String.join(",", online.getRoomUser().getStatuses().keySet());

                Player tradePartner = online.getRoomUser().getTradePartner();
                int tradePartnerId = tradePartner != null ? tradePartner.getDetails().getId() : -1;

                String currentGameId = online.getRoomUser().getCurrentGameId() != null ? online.getRoomUser().getCurrentGameId() : "";
                int observingGameId = online.getRoomUser().getObservingGameId();

                String ip = NettyPlayerNetwork.getIpAddress(online.getNetwork().getChannel());
                String ignoredList = String.join(",", online.getIgnoredList());

                return new RconResponse(200,
                        "roomId=" + roomId +
                        ";muteTime=" + muteTime +
                        ";posX=" + posX +
                        ";posY=" + posY +
                        ";posZ=" + posZ +
                        ";isDiving=" + isDiving +
                        ";isWalking=" + isWalking +
                        ";statuses=" + statuses +
                        ";tradePartnerId=" + tradePartnerId +
                        ";currentGameId=" + currentGameId +
                        ";observingGameId=" + observingGameId +
                        ";ip=" + ip +
                        ";ignoredList=" + ignoredList
                );
            }
            default:
                return RconResponse.error("Unknown command");
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
