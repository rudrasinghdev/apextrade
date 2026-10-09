package com.rudra.apextrade.runner;

import com.rudra.apextrade.core.book.OrderBook;
import com.rudra.apextrade.core.engine.MatchingEngine;
import com.rudra.apextrade.core.order.OrderPool;
import com.rudra.apextrade.core.order.PriceLevel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@EnableScheduling
@ConditionalOnProperty(name = "apextrade.monitor.enabled", havingValue = "true", matchIfMissing = true)
public class LiveTerminalMonitor {

    private static final Logger log = LoggerFactory.getLogger(LiveTerminalMonitor.class);

    private final MatchingEngine matchingEngine;
    private final OrderBook orderBook;
    private final OrderPool orderPool;

    public LiveTerminalMonitor(MatchingEngine matchingEngine, OrderBook orderBook, OrderPool orderPool) {
        this.matchingEngine = matchingEngine;
        this.orderBook = orderBook;
        this.orderPool = orderPool;
    }

    @Scheduled(fixedRate = 2000) // Reports every 2 seconds
    public void printLiveStatus() {
        PriceLevel bestBid = orderBook.getBestBid();
        PriceLevel bestAsk = orderBook.getBestAsk();

        long ltp = matchingEngine.getLastTradedPrice();
        String bidStr = bestBid != null ? String.valueOf(bestBid.getPrice()) : "EMPTY";
        String askStr = bestAsk != null ? String.valueOf(bestAsk.getPrice()) : "EMPTY";
        String spreadStr = (bestBid != null && bestAsk != null)
                ? (bestAsk.getPrice() - bestBid.getPrice()) + " ticks"
                : "N/A";

        log.info("[CORE MONITOR] LTP: {} USDT | Best Bid: {} | Best Ask: {} | Spread: {} | Pool Avail: {}/{}",
                ltp, bidStr, askStr, spreadStr, orderPool.getAvailableCount(), orderPool.getCapacity());
    }
}
