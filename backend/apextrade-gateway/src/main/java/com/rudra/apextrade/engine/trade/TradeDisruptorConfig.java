package com.rudra.apextrade.engine.trade;

import com.lmax.disruptor.RingBuffer;
import com.lmax.disruptor.YieldingWaitStrategy;
import com.lmax.disruptor.dsl.Disruptor;
import com.lmax.disruptor.dsl.ProducerType;
import com.rudra.apextrade.account.service.AccountLedger;
import com.rudra.apextrade.order.service.OrderService;
import com.rudra.apextrade.websocket.TradeWebSocketHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.ThreadFactory;

@Configuration
public class TradeDisruptorConfig {

    private static final int BUFFER_SIZE = 65536;

    @Bean
    public TradeEventFactory tradeEventFactory() {
        return new TradeEventFactory();
    }

    @Bean
    public TradeDispatchHandler tradeDispatchHandler(
            AccountLedger ledger,
            OrderService orderService,
            TradeWebSocketHandler webSocketHandler
    ) {
        return new TradeDispatchHandler(ledger, orderService, webSocketHandler);
    }

    @Bean(destroyMethod = "shutdown")
    public Disruptor<TradeEvent> tradeDisruptor(
            TradeEventFactory factory,
            TradeDispatchHandler handler
    ) {
        ThreadFactory threadFactory = runnable -> {
            Thread thread = new Thread(runnable, "gateway-trade-disruptor");
            thread.setDaemon(true);
            return thread;
        };

        Disruptor<TradeEvent> disruptor = new Disruptor<>(
                factory,
                BUFFER_SIZE,
                threadFactory,
                ProducerType.SINGLE,
                new YieldingWaitStrategy()
        );

        disruptor.handleEventsWith(handler);
        disruptor.start();

        return disruptor;
    }

    @Bean
    public TradeEventPublisher tradeEventPublisher(Disruptor<TradeEvent> tradeDisruptor) {
        return new TradeEventPublisher(tradeDisruptor.getRingBuffer());
    }
}