package org.alexdev.kepler.server.rcon.codec;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToByteEncoder;
import org.alexdev.kepler.server.rcon.messages.RconResponse;
import org.alexdev.kepler.util.StringUtil;

@ChannelHandler.Sharable
public class RconNetworkEncoder extends MessageToByteEncoder<RconResponse> {
    @Override
    protected void encode(ChannelHandlerContext ctx, RconResponse msg, ByteBuf out) {
        byte[] body = msg.getMessage().getBytes(StringUtil.getCharset());
        out.writeInt(msg.getCode());
        out.writeInt(body.length);
        out.writeBytes(body);
    }
}
