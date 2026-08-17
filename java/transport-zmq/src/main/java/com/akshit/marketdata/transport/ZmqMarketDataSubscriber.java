package com.akshit.marketdata.transport;

import com.akshit.marketdata.proto.MarketDataEnvelope;
import com.google.protobuf.InvalidProtocolBufferException;
import org.zeromq.SocketType;
import org.zeromq.ZContext;
import org.zeromq.ZMQ;

import java.time.Duration;
import java.util.Optional;

public final class ZmqMarketDataSubscriber implements AutoCloseable {
    private final ZContext context;
    private final ZMQ.Socket socket;

    public ZmqMarketDataSubscriber(String connectAddress, String topic, Duration receiveTimeout) {
        this.context = new ZContext();
        this.socket = context.createSocket(SocketType.SUB);
        this.socket.connect(connectAddress);
        this.socket.subscribe(topic.getBytes(ZMQ.CHARSET));
        this.socket.setReceiveTimeOut((int) receiveTimeout.toMillis());
    }

    public Optional<MarketDataEnvelope> receive() {
        String topic = socket.recvStr();
        if (topic == null) {
            return Optional.empty();
        }
        byte[] payload = socket.recv();
        if (payload == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(MarketDataEnvelope.parseFrom(payload));
        } catch (InvalidProtocolBufferException e) {
            throw new IllegalArgumentException("Received invalid protobuf payload on topic " + topic, e);
        }
    }

    @Override
    public void close() {
        socket.close();
        context.close();
    }
}
