package com.rudra.apextrade.bot;

import com.rudra.apextrade.core.book.OrderBook;
import com.rudra.apextrade.core.engine.MatchingEngine;
import com.rudra.apextrade.disruptor.publisher.OrderCommandPublisher;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class BotConfig {

    @Bean
    public MarketMakerBot marketMakerBot(MatchingEngine matchingEngine,
                                         OrderCommandPublisher publisher,
                                         @Value("${apextrade.bot.maker.user-id:1}") long userId,
                                         @Value("${apextrade.bot.maker.levels:5}") int levels,
                                         @Value("${apextrade.bot.maker.orders-per-level:2}") int ordersPerLevel,
                                         @Value("${apextrade.bot.maker.half-spread:10}") long halfSpread,
                                         @Value("${apextrade.bot.maker.tick-step:10}") long tickStep,
                                         @Value("${apextrade.bot.maker.order-qty:100}") long orderQuantity) {
        return new MarketMakerBot(
                matchingEngine,
                publisher,
                userId,
                levels,
                ordersPerLevel,
                halfSpread,
                tickStep,
                orderQuantity,
                tickStep
        );
    }

    @Bean
    public FastFairPriceGenerator fastFairPriceGenerator(
            @Value("${apextrade.engine.listing-price:50000}") long centerPrice,
            @Value("${apextrade.bot.taker.max-deviation:200}") long maxDeviation,
            @Value("${apextrade.bot.maker.tick-step:10}") long tickStep) {
        return new FastFairPriceGenerator(centerPrice, maxDeviation, tickStep, 42L);
    }

    @Bean
    public ArbitrageBot arbitrageBot(OrderBook orderBook,
                                     OrderCommandPublisher publisher,
                                     FastFairPriceGenerator priceGenerator,
                                     @Value("${apextrade.bot.taker.user-id:2}") long userId,
                                     @Value("${apextrade.bot.taker.sweep-qty:200}") long sweepQuantity) {
        return new ArbitrageBot(orderBook, publisher, priceGenerator, userId, sweepQuantity);
    }
}
