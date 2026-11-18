package com.rudra.apextrade.bot;

/**
 * ApexTrade Automated Liquidity & Market Maker Engine
 * Spawns concurrent virtual threads for Maker (pyramid quoting) and Taker (spread crosser).
 */
public class MarketMakerApp {

    private static final String DEFAULT_GATEWAY_URL = "http://152.67.4.206:8080";
    private static final double DEFAULT_CENTER_PRICE = 50000.0; // $50,000

    public static void main(String[] args) {
        printBanner();

        String targetUrl = DEFAULT_GATEWAY_URL;
        String mode = "all";
        double centerPrice = DEFAULT_CENTER_PRICE;

        for (int i = 0; i < args.length; i++) {
            if ("--target".equalsIgnoreCase(args[i]) && i + 1 < args.length) {
                targetUrl = args[++i];
            } else if ("--mode".equalsIgnoreCase(args[i]) && i + 1 < args.length) {
                mode = args[++i].toLowerCase();
            } else if ("--center".equalsIgnoreCase(args[i]) && i + 1 < args.length) {
                centerPrice = Double.parseDouble(args[++i]);
            }
        }

        System.out.println("  Target Exchange : " + targetUrl);
        System.out.println("  Operating Mode  : " + mode.toUpperCase());
        System.out.println("  Center Price    : $" + String.format("%.2f", centerPrice));
        System.out.println("=================================================");

        ExchangeHttpClient client = new ExchangeHttpClient(targetUrl);

        // Center price in cents for the XORShift64 generator
        long centerPriceCents = Math.round(centerPrice * 100);
        long maxDeviationCents = 150_000; // ±$1,500 corridor
        long tickStepCents = 25_00;       // $25 steps

        FastFairPriceGenerator priceGen = new FastFairPriceGenerator(
                centerPriceCents,
                maxDeviationCents,
                tickStepCents,
                System.nanoTime()
        );

        try {
            MarketMakerBot makerBot = null;
            ArbitrageBot takerBot = null;

            if ("all".equals(mode) || "maker".equals(mode)) {
                System.out.println("[Auth] Authenticating Maker Bot (bot_maker@apextrade.com)...");
                ExchangeHttpClient.AuthSession makerSession = client.authenticate("bot_maker@apextrade.com", "BotPassword123!");
                System.out.printf("[Auth] Maker Bot authenticated! Trader #%d%n", makerSession.userId());

                makerBot = new MarketMakerBot(
                        client,
                        makerSession,
                        priceGen,
                        5,      // 5 depth levels
                        10.0,   // $10 half-spread ($20 full spread)
                        25.0,   // $25 level step
                        0.25,   // 0.25 BTC per level
                        2500    // refresh every 2.5s
                );

                Thread.ofVirtual().name("bot-maker-thread").start(makerBot);
            }

            if ("all".equals(mode) || "taker".equals(mode)) {
                System.out.println("[Auth] Authenticating Taker Bot (bot_taker@apextrade.com)...");
                ExchangeHttpClient.AuthSession takerSession = client.authenticate("bot_taker@apextrade.com", "BotPassword123!");
                System.out.printf("[Auth] Taker Bot authenticated! Trader #%d%n", takerSession.userId());

                takerBot = new ArbitrageBot(
                        client,
                        takerSession,
                        priceGen,
                        0.10,   // 0.10 BTC per sweep
                        3000    // cross spread every 3s
                );

                Thread.ofVirtual().name("bot-taker-thread").start(takerBot);
            }

            final MarketMakerBot finalMaker = makerBot;
            final ArbitrageBot finalTaker = takerBot;

            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                System.out.println("\n[Shutdown] Stopping market-making threads...");
                if (finalMaker != null) finalMaker.stop();
                if (finalTaker != null) finalTaker.stop();
            }));

            System.out.println("[ApexTrade Bot] Liquidity harness live. Press Ctrl+C to stop.");
            Thread.currentThread().join();

        } catch (Exception e) {
            System.err.println("[Fatal Bot Error] " + e.getMessage());
            e.printStackTrace();
        }
    }

    private static void printBanner() {
        System.out.println("""
            =================================================
               ApexTrade Automated Liquidity & MM Engine    
               High-Frequency Virtual-Thread Market Maker   
            =================================================""");
    }
}
