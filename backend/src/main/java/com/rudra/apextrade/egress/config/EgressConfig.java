package com.rudra.apextrade.egress.config;

import com.rudra.apextrade.egress.buffer.SpscTradeRingBuffer;
import com.rudra.apextrade.egress.handler.TradeEgressHandler;
import com.rudra.apextrade.egress.publisher.TradeEgressPublisher;
import com.rudra.apextrade.gateway.websocket.TradeWebSocketHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class EgressConfig {

    private static final int EGRESS_BUFFER_SIZE = 65536;

    @Bean
    public SpscTradeRingBuffer spscTradeRingBuffer() {
        return new SpscTradeRingBuffer(EGRESS_BUFFER_SIZE);
    }

    @Bean
    public TradeEgressPublisher tradeEgressPublisher(SpscTradeRingBuffer spscTradeRingBuffer) {
        return new TradeEgressPublisher(spscTradeRingBuffer);
    }

    @Bean
    public TradeEgressHandler tradeEgressHandler(SpscTradeRingBuffer spscTradeRingBuffer,
                                                 TradeWebSocketHandler tradeWebSocketHandler) {
        return new TradeEgressHandler(spscTradeRingBuffer, tradeWebSocketHandler);
    }
}