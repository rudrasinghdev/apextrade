# ⚡ ApexTrade: Distributed Ultra-Low-Latency Matching Core & Edge Gateway

[![Java](https://img.shields.io/badge/Java-25%20LTS-orange.svg)](https://openjdk.org/)
[![Concurrency](https://img.shields.io/badge/Concurrency-LMAX%20Disruptor%204.0.0-blue.svg)](https://lmax-exchange.github.io/disruptor/)
[![Virtual Threads](https://img.shields.io/badge/Project%20Loom-Virtual%20Threads-blueviolet.svg)](https://openjdk.org/projects/loom/)
[![Peak Throughput](https://img.shields.io/badge/Peak%20Throughput-19.47M%20ops%2Fsec-success.svg)](#-verified-in-memory-benchmarks)
[![Service Time](https://img.shields.io/badge/Mean%20Service%20Time-51.36%20ns%2Forder-brightgreen.svg)](#-verified-in-memory-benchmarks)
[![Deployment](https://img.shields.io/badge/Cloud-Oracle%20Cloud%20Dual--VM-red.svg)](#-distributed-dual-vm-cloud-architecture)

ApexTrade is an institutional-grade, multi-module electronic trading core engineered with **Mechanical Sympathy** for Linux and modern x86_64 multi-core architectures.

Structured as a high-performance **Maven Multi-Module Reactor**, ApexTrade strictly decouples the high-concurrency client network edge from the deterministic matching core across independent physical/virtual host machines. The matching engine operates as an isolated single-writer state machine free of runtime heap allocations, lock contention, and framework overhead—clocking a verified peak throughput of **19.47 Million orders/second** at a **mean service time of 51.36 nanoseconds per order**, streaming binary wire frames over a dedicated private network fabric.

---

## 🏛️ Distributed Dual-VM Cloud Architecture

ApexTrade runs in production on **Oracle Cloud Infrastructure (OCI Mumbai `ap-mumbai-1`)** across two isolated virtual machines connected via an internal VCN private subnet, ensuring zero public exposure for the core matching engine:

```text
                                CLIENTS / FRONTEND TERMINAL
                                             │
                       HTTP / REST (Port 8080) │ WebSocket (/ws/trades, /topic/orders)
                                             ▼
       ┌────────────────────────────────────────────────────────────────────────┐
       │             VM 1: apextrade-gateway (Edge Gateway / S1)                │
       │             Public IP: 152.67.4.206  •  Private IP: 10.0.0.220         │
       │                                                                        │
       │  • Spring Boot 4.1.1 + Tomcat 11.0.24 (Java 25.0.5)                   │
       │  • Project Loom Virtual Threads (spring.threads.virtual.enabled=true)  │
       │  • JWT Auth & Neon PostgreSQL Cloud Database Connection Pooling        │
       │  • In-Memory Atomic Margin Ledger (Pre-Trade Risk & Balance Locking)   │
       │  • Ingress Disruptor (OrderCommandPublisher -> OrderNetworkWriter)     │
       │  • Egress Disruptor (EngineConnectionManager -> ExecutionDispatch)     │
       │  • Non-blocking STOMP WebSocket Broadcaster (/topic/trades)           │
       └───────────────────────────────────┬────────────────────────────────────┘
                                           │
                         Dedicated Private Network Fabric
                  Zero-Allocation Variable Binary Protocol over TCP
                              (Private Subnet 10.0.1.0/24)
                                           │
       ┌───────────────────────────────────┴────────────────────────────────────┐
       │             VM 2: apextrade-engine (Deterministic Core / S2)           │
       │            Air-Gapped Private IP: 10.0.1.111  •  Port: 9000            │
       │                       (NO PUBLIC IP ASSIGNED)                          │
       │                                                                        │
       │  ┌─────────────────────────┐         ┌──────────────────────────────┐  │
       │  │ LMAX Disruptor Ingress  │────────▶│     matching-core-thread     │  │
       │  │ RingBuffer (64K Slots)  │         │  (Pinned Single-Writer Core) │  │
       │  │ YieldingWaitStrategy    │         └──────────────┬───────────────┘  │
       │  └─────────────────────────┘                        │                  │
       │                                                     ▼                  │
       │  ┌─────────────────────────┐         ┌──────────────────────────────┐  │
       │  │ Lock-Free SPSC Egress   │◀────────│     OrderBook & Engine       │  │
       │  │ RingBuffer (64K Slots)  │         │  • Zero-GC OrderPool (100k)  │  │
       │  │ (Non-blocking Outbound) │         │  • B-Tree / PriceLevel Lists │  │
       │  └─────────────────────────┘         └──────────────────────────────┘  │
       │                                                                        │
       │  • Standalone Native Daemon (Zero Framework / Zero Spring Overhead)    │
       │  • Java NIO ServerSocketChannel & Direct ByteBuffer Slicing            │
       └────────────────────────────────────────────────────────────────────────┘
```

---

## 📦 Multi-Module Reactor Overview

```text
apextrade/
├── pom.xml                                   # Parent Reactor POM (Packaging: pom)
├── backend/
│   ├── apextrade-common/                     # Shared domain primitives & binary wire codecs
│   │   └── src/main/java/com/rudra/apextrade/common/
│   │       ├── domain/                       # Side, OrderType, OrderStatus, CommandType, ExecutionType
│   │       ├── event/                        # Disruptor Events & RingBuffer Factories
│   │       └── wire/
│   │           ├── OrderCommandCodec.java    # 40B/20B Ingress Variable Binary Protocol (0x4150)
│   │           └── ExecutionWireCodec.java   # 72B/48B/28B Egress Execution Binary Protocol (0x4558)
│   ├── apextrade-engine/                     # Pure Java Ultra-Low-Latency Deterministic Core
│   │   └── src/main/java/com/rudra/apextrade/
│   │       ├── core/
│   │       │   ├── book/OrderBook.java       # Cache-friendly PriceLevel B-Tree ladder
│   │       │   ├── engine/MatchingEngine.java# Price-Time Priority matching engine
│   │       │   ├── engine/EngineMain.java    # Standalone daemon launcher & NIO TCP listener
│   │       │   ├── order/Order.java          # Object-recycled order model
│   │       │   ├── order/OrderPool.java      # 100,000 pre-warmed order memory pool
│   │       │   └── order/PriceLevel.java     # Doubly linked FIFO queue per tick
│   │       ├── ingress/                      # LMAX Disruptor ring buffer ingress pipeline
│   │       ├── egress/                       # Lock-free SPSC outbound trade buffer pipeline
│   │       └── transport/                    # Java NIO SocketChannel listener & frame dispatch
│   └── apextrade-gateway/                    # Edge Gateway (Spring Boot & Cloud Edge)
│       └── src/main/java/com/rudra/apextrade/
│           ├── GatewayApplication.java       # Spring Boot 4.1.1 entry point (Java 25)
│           ├── account/                      # In-memory atomic margin ledger & pre-trade risk
│           ├── auth/                         # JWT authentication & BCrypt password hashing
│           ├── order/                        # Order validation, routing, and controller endpoints
│           ├── transport/                    # EngineConnectionManager, socket writers, execution handlers
│           └── websocket/                    # Non-blocking trade and execution STOMP broadcaster
```

---

## ⚡ Mechanical Sympathy & Engineering Highlights

### 1. Zero-Allocation Variable-Framing Binary Wire Protocol

Inter-service communication between Gateway and Engine uses custom compact binary wire protocols. All integer and long fields are aligned to their natural byte boundaries (e.g., 8-byte `long` fields placed at offsets divisible by 8) to avoid CPU unaligned memory access penalties.

#### Ingress Frames: Gateway ➔ Engine (`OrderCommandCodec`, Magic: `0x4150` / `'AP'`)

* **`NEW_ORDER` Frame (40 Bytes Total):**
```text
 0                   1                   2                   3
 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1
+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
|       MAGIC (0x4150)          | MSG_TYPE (0x01)| PADDING (1B) |
+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
|   SIDE (1B)   | ORDER_TYPE(1B)|       PADDING (2 Bytes)       |
+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
|                       USER_ID (8 Bytes)                       |
|                                                               |
+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
|                       ORDER_ID (8 Bytes)                      |
|                                                               |
+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
|                        PRICE (8 Bytes)                        |
|                                                               |
+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
|                       QUANTITY (8 Bytes)                      |
|                                                               |
+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
```

* **`CANCEL_ORDER` Frame (20 Bytes Total):**
```text
 0                   1                   2                   3
 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1
+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
|       MAGIC (0x4150)          | MSG_TYPE (0x02)| PADDING (1B) |
+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
|                       USER_ID (8 Bytes)                       |
|                                                               |
+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
|                       ORDER_ID (8 Bytes)                      |
|                                                               |
+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
```

#### Egress Frames: Engine ➔ Gateway (`ExecutionWireCodec`, Magic: `0x4558` / `'EX'`)

* **`TRADE` Fill Frame (72 Bytes Total):**
  - Header: Magic (`0x4558`, 2B), `MsgType=1` (1B), Padding (1B), Alignment Padding (4B).
  - Body: `TradeId` (8B), `BuyerUserId` (8B), `BuyerOrderId` (8B), `SellerUserId` (8B), `SellerOrderId` (8B), `Price` (8B), `Quantity` (8B), `Timestamp` (8B).
* **`CANCEL_CONFIRMED` Frame (48 Bytes Total):**
  - Header: Magic (`0x4558`, 2B), `MsgType=2` (1B), Padding (1B), `Side` (1B), Alignment Padding (3B).
  - Body: `OrderId` (8B), `UserId` (8B), `Price` (8B), `UnfilledQuantity` (8B), `Timestamp` (8B).
* **`CANCEL_REJECTED` Frame (28 Bytes Total):**
  - Header: Magic (`0x4558`, 2B), `MsgType=3` (1B), Padding (1B).
  - Body: `OrderId` (8B), `UserId` (8B), `Timestamp` (8B).

---

### 2. Lock-Free Single-Writer State Machine

All book modifications, price level traversals, and execution matches execute deterministically on a single thread (`matching-core-thread`).
* **Zero Locking:** Eliminates `synchronized`, `ReentrantLock`, and OS mutexes (`pthread_mutex`).
* **Cache-Line Padding:** Sequence counters and ring buffer boundaries are padded with 64-byte empty caches (`@Contended` / padding fields) to eliminate **false sharing** across CPU L1/L2 cache lines.

### 3. In-Memory Atomic Margin & Risk Ledger

The Gateway edge features a nanosecond pre-trade risk engine backed by `AtomicLong` CAS (Compare-And-Swap) balances:
* **Pre-Trade Margin Check:**
  - `BUY` orders atomically lock `price * quantity` USD before order acceptance.
  - `SELL` orders atomically lock `quantity` base asset (e.g., BTC Satoshi) before order acceptance.
  - If available balance is insufficient, rejection occurs in nanoseconds without transmitting wire frames to the engine.
* **Post-Trade Settlement:**
  - Maker and taker balances settle atomically upon receiving `TRADE` execution frames.
  - Locked margin is cleared, acquired currency is credited, and fee deductions execute in-memory.
* **Debounced Cancel Unlocking:**
  - Cancel commands trigger state transitions that prevent double-unwinding. Unfilled margin is unlocked immediately upon receiving the engine's `CANCEL_CONFIRMED` execution frame.

### 4. Zero-GC Memory Management (`OrderPool`)

* Pre-allocates and warms **100,000 `Order` instances** into contiguous memory during engine initialization.
* Active orders borrow from the pool; cancellations and fills return instances immediately.
* Completely eliminates runtime heap allocation pauses (`0.00 ms GC pause`) on the matching hot path.

### 5. Java 25 Project Loom Virtual Threads

The gateway edge enables `spring.threads.virtual.enabled=true`. Web and socket connection handlers execute on lightweight, user-mode Virtual Threads, preventing thread starvation and context-switch thrashing under heavy concurrent client traffic.

---

## 🚀 Verified In-Memory Benchmarks

Benchmarked on **Microsoft Azure** (`Standard_D4as_v4`, 4 vCPUs AMD EPYC 7452 @ 2.35–3.35 GHz) running Linux Ubuntu 24.04 LTS:

### Benchmark Workload:
* **Dataset:** 1,000,000 unthrottled orders.
* **Order Profile:** Aggressive crossing `BUY` limit orders sweeping resting `SELL` asks across a 5-level price ladder (top-of-book execution).
* **Measurement Barrier:** High-resolution timer records from order #1 submission until the consumer thread clears the 1,000,000th gating sequence (`ringBuffer.getMinimumGatingSequence() >= targetSequence`).

| Execution Stage | Total Orders | Duration | Throughput (TPS) | Mean Service Time (1/TPS) | CPU Cycles / Order |
| :--- | :---: | :---: | :---: | :---: | :---: |
| **Cold Run (JIT Tier 1–3 Profiling)** | 1,000,000 | 239.95 ms | 4,167,481 ops/sec | 239.95 ns | ~720 cycles |
| **Warm Run (C2 Tier-4 Optimization)** | 1,000,000 | 77.85 ms | 12,845,785 ops/sec | 77.85 ns | ~233 cycles |
| **Saturated Peak (BTB / Loop Unrolled)** | **1,000,000** | **51.36 ms** | **19,470,243 ops/sec** | **51.36 ns** | **~154 cycles** |

```text
=======================================================
>>> STARTING IN-MEMORY BENCHMARK: 1,000,000 ORDERS <<<
=======================================================
[1/3] Pre-allocating and warming OrderPool (100,000 slots)... DONE.
[2/3] Seeding resting ask depth across 5 price levels... DONE.
[3/3] Blasting 1,000,000 unthrottled orders through RingBuffer...
[BENCHMARK COMPLETED] 1,000,000 orders in 51.36 ms (19,470,243.87 ops/sec)
=======================================================
```

---

## 🛠️ Build, Test & Run Guide

### Prerequisites
* **Java Development Kit (JDK):** Java 25 or later.
* **Build Tool:** Apache Maven 3.9+.
* **Database:** PostgreSQL instance (e.g., Neon Cloud or local PostgreSQL).

### 1. Compile & Package the Reactor
From the project root:
```bash
# Run all unit and integration tests across all modules
mvn clean test

# Package both runnable artifacts
mvn clean package -DskipTests
```
This produces:
* `backend/apextrade-engine/target/apextrade-engine-0.0.1-SNAPSHOT.jar` (Lean standalone daemon, ~2.1 MB)
* `backend/apextrade-gateway/target/apextrade-gateway-0.0.1-SNAPSHOT.jar` (Edge Web & WebSocket service, ~60 MB)

### 2. Distributed Cloud Deployment (OCI Dual-VM)

#### On VM 2 (`apextrade-engine`, Private IP: `10.0.1.111`):
```bash
# Run standalone matching engine daemon in the background
nohup java -Xms128m -Xmx350m -jar apextrade-engine-0.0.1-SNAPSHOT.jar > engine.log 2>&1 &

# Inspect live engine logs
tail -f engine.log
```

#### On VM 1 (`apextrade-gateway`, Public IP: `152.67.4.206`):
```bash
# Verify TCP connectivity to engine on port 9000
nc -zv 10.0.1.111 9000

# Run Spring Boot Gateway in the background
nohup java -Xms128m -Xmx450m -jar apextrade-gateway-0.0.1-SNAPSHOT.jar > gateway.log 2>&1 &

# Inspect live gateway logs
tail -f gateway.log
```

---

## 🗺️ Engineering Roadmap

- [x] **Phase 1: Multi-Module Reactor Core**
  - Modularized into `apextrade-common`, `apextrade-engine`, and `apextrade-gateway`.
  - Pure Java matching engine core decoupled from Spring framework dependencies.
  - Zero-GC 100,000 `OrderPool` with B-Tree price level indexing.
  - Verified peak 19.47M ops/sec throughput.
- [x] **Phase 2: High-Performance Inter-Process TCP Network Engine**
  - Non-blocking Java NIO `ServerSocketChannel` (`EngineTcpServer`) on port `9000`.
  - Zero-allocation variable binary protocol (`OrderCommandCodec` & `ExecutionWireCodec`).
  - Dual-Disruptor asynchronous network socket reader and writer pipelines.
- [x] **Phase 3: In-Memory Atomic Margin Ledger & Pre-Trade Risk**
  - Lock-free `AtomicLong` CAS margin reservation engine in Gateway.
  - Real-time post-trade two-phase settlement for maker and taker.
  - Distributed dual-VM cloud deployment on Oracle Cloud Infrastructure (OCI).
- [ ] **Phase 4: Memory-Mapped Write-Ahead Log (WAL) & Deterministic Recovery**
  - Off-heap zero-copy write-ahead journal using `FileChannel.map()` (< 500 ns persistence).
  - Deterministic state machine replay upon cold engine restart.
- [ ] **Phase 5: Automated Market Making & JMH Microbenchmark Suite**
  - Continuous liquidity provisioning bot quoting dynamic spreads around mid-market.
  - JMH microbenchmark suite measuring tick-to-trade latency percentiles (p50, p90, p99, p99.9).
- [ ] **Phase 6: High-Availability State Machine Replication (Raft)**
  - Raft consensus layer replicating deterministic command frames across matching engine replicas.
  - Sub-millisecond leader election and failover with zero state divergence.

---

## 📜 Technology Matrix

| Component | Technology | Rationale |
| :--- | :--- | :--- |
| **Language Runtime** | Java 25 (LTS) | C2 JIT compilation, Project Loom Virtual Threads, Foreign Function & Memory API ready. |
| **Ingress Concurrency** | LMAX Disruptor 4.0.0 | Mechanical sympathy, lock-free ring buffers, cache-line padded sequencing (`YieldingWaitStrategy`). |
| **Egress Concurrency** | SPSC Ring Buffer | Non-blocking trade publication isolating matching core from client network lag. |
| **Wire Protocol** | Custom Binary Frame | Variable-length zero-allocation frames with magic validation (`0x4150` Ingress, `0x4558` Egress). |
| **Pre-Trade Risk Engine** | In-Memory Atomic CAS | Sub-microsecond balance validation and margin locking using `AtomicLong` primitives. |
| **Web & Edge Framework** | Spring Boot 4.1.1 | REST entry point, JWT authentication, non-blocking STOMP WebSocket broadcast. |
| **Database** | Neon PostgreSQL | Serverless connection pooling for user identity, authentication, and cold storage audit trail. |
| **Cloud Infrastructure** | Oracle Cloud Infrastructure | Multi-tier VCN architecture with air-gapped private subnet for the matching engine. |
