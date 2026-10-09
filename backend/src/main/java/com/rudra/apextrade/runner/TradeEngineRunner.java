package com.rudra.apextrade.runner;

import com.rudra.apextrade.bot.MarketMakerBot;
import com.rudra.apextrade.core.book.OrderBook;
import com.rudra.apextrade.core.engine.MatchingEngine;
import com.rudra.apextrade.core.order.PriceLevel;
import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "apextrade.runner.enabled", havingValue = "true", matchIfMissing = true)
public class TradeEngineRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(TradeEngineRunner.class);

    private final MarketMakerBot marketMakerBot;
    private final OrderBook orderBook;
    private final MatchingEngine matchingEngine;

    public TradeEngineRunner(MarketMakerBot marketMakerBot, OrderBook orderBook, MatchingEngine matchingEngine) {
        this.marketMakerBot = marketMakerBot;
        this.orderBook = orderBook;
        this.matchingEngine = matchingEngine;
    }

    @Override
    public void run(@NonNull ApplicationArguments args) throws Exception {
        log.info("========================================================================");
        log.info(" [ApexTrade] Ultra-Low-Latency In-Memory Matching Engine Bootstrapping");
        log.info(" Core 0: matching-core-thread active | Disruptor RingBuffer: 65,536 slots");
        log.info(" Anchor Seed Price: 50,000 | Seeding Initial Market Maker Liquidity...");
        log.info("========================================================================");

        marketMakerBot.seedAtBoot();

        Thread.sleep(50);

        PriceLevel bestBid = orderBook.getBestBid();
        PriceLevel bestAsk = orderBook.getBestAsk();

        log.info(">>> Initial Market Depth Successfully Formed! <<<");
        log.info("Best Bid : {} (Volume: {}, Orders: {})",
                bestBid != null ? bestBid.getPrice() : "N/A",
                bestBid != null ? bestBid.getTotalVolume() : 0,
                bestBid != null ? bestBid.getOrderCount() : 0);
        log.info("Best Ask : {} (Volume: {}, Orders: {})",
                bestAsk != null ? bestAsk.getPrice() : "N/A",
                bestAsk != null ? bestAsk.getTotalVolume() : 0,
                bestAsk != null ? bestAsk.getOrderCount() : 0);
        log.info("Spread   : {} ticks",
                (bestBid != null && bestAsk != null) ? (bestAsk.getPrice() - bestBid.getPrice()) : "N/A");
        log.info("LTP      : {}", matchingEngine.getLastTradedPrice());
        log.info("========================================================================");
    }
}
