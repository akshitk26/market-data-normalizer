package com.akshit.marketdata.transport;

import com.akshit.marketdata.proto.MarketDataEnvelope;
import org.zeromq.SocketType;
import org.zeromq.ZContext;
import org.zeromq.ZMQ;

public final class ZmqMarketDataPublisher implements AutoCloseable {
    private final ZContext context;
    private final ZMQ.Socket socket;

    public ZmqMarketDataPublisher(String bindAddress) {
        this.context = new ZContext();
        this.socket = context.createSocket(SocketType.PUB);
        this.socket.bind(bindAddress);
    }

    public void publish(String topic, MarketDataEnvelope envelope) {
        socket.sendMore(topic);
        socket.send(envelope.toByteArray());
    }

    @Override
    public void close() {
        socket.close();
        context.close();
    }
}
