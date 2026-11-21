package com.rudra.apextrade.bot;

public class LiquidityHarnessApp {

    private static final String GATEWAY_URL = "http://localhost:8080";
    private static final double CENTER_PRICE = 50000.0;

    static void main() {
        System.out.printf("""
                =================================================
                    ApexTrade Automated Liquidity Harness Engine
                    High-Frequency Virtual-Thread Market Driver
                =================================================
                    Target Exchange : %s
                    Center Price    : $%.2f
                =================================================%n""", GATEWAY_URL, CENTER_PRICE);

        ExchangeHttpClient client = new ExchangeHttpClient(GATEWAY_URL);

        long centerPriceCents = Math.round(CENTER_PRICE * 100);
        long maxDeviationCents = 2_000_000;
        long tickStepCents = 25;

        FastFairPriceGenerator priceGen = new FastFairPriceGenerator(
                centerPriceCents,
                maxDeviationCents,
                tickStepCents,
                System.nanoTime()
        );

        try {
            ExchangeHttpClient.AuthSession makerSession = authenticateWithRetry(client, "bot_maker@apextrade.com", "BotPassword123!");
            System.out.printf("[Auth] Maker Bot authenticated! Trader #%d%n", makerSession.userId());

            MarketMakerBot makerBot = new MarketMakerBot(
                    client,
                    makerSession,
                    priceGen,
                    50,     // 50 depth levels
                    2,      // 2 slices per level (100 bids + 100 asks = 200 orders total)
                    0.50,   // $0.50 half-spread ($1.00 full spread)
                    0.25,   // $0.25 level step
                    0.05,   // 0.05 BTC total volume per level (0.03 + 0.02 BTC)
                    1000    // refresh every 1.0s
            );

            ExchangeHttpClient.AuthSession takerSession = authenticateWithRetry(client, "bot_taker@apextrade.com", "BotPassword123!");
            System.out.printf("[Auth] Taker Bot authenticated! Trader #%d%n", takerSession.userId());

            ArbitrageBot takerBot = new ArbitrageBot(
                    client,
                    takerSession,
                    priceGen,
                    0.075,
                    150
            );

            Thread.ofVirtual().name("bot-maker-thread").start(makerBot);
            Thread.ofVirtual().name("bot-taker-thread").start(takerBot);

            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                System.out.println("\n[Shutdown] Stopping liquidity harness threads...");
                makerBot.stop();
                takerBot.stop();
            }));

            System.out.println("[ApexTrade] Liquidity harness live. Press Ctrl+C to stop.");
            Thread.currentThread().join();

        } catch (Exception e) {
            System.err.println("[Fatal Bot Error] " + e.getMessage());
            e.printStackTrace();
        }
    }

    private static ExchangeHttpClient.AuthSession authenticateWithRetry(ExchangeHttpClient client, String email, String password) throws InterruptedException {
        while (true) {
            try {
                System.out.printf("[Auth] Authenticating %s...%n", email);
                return client.authenticate(email, password);
            } catch (Exception e) {
                System.err.printf("[Auth Retry] Unable to authenticate %s (%s). Retrying in 2 seconds...%n", email, e.getMessage());
                Thread.sleep(2000);
            }
        }
    }
}