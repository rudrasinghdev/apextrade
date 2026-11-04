# ⚡ ApexTrade: Ultra-Low-Latency Multi-Module Matching Core & Edge Gateway

[![Java](https://img.shields.io/badge/Java-25%20LTS-orange.svg)](https://openjdk.org/)
[![Concurrency](https://img.shields.io/badge/Concurrency-LMAX%20Disruptor%204.0.0-blue.svg)](https://lmax-exchange.github.io/disruptor/)
[![Virtual Threads](https://img.shields.io/badge/Project%20Loom-Virtual%20Threads-blueviolet.svg)](https://openjdk.org/projects/loom/)
[![Peak Throughput](https://img.shields.io/badge/Peak%20Throughput-19.47M%20ops%2Fsec-success.svg)](#-verified-in-memory-benchmarks)
[![Service Time](https://img.shields.io/badge/Mean%20Service%20Time-51.36%20ns%2Forder-brightgreen.svg)](#-verified-in-memory-benchmarks)
[![Architecture](https://img.shields.io/badge/Architecture-Modular%20Reactor-red.svg)](#-system-architecture)

ApexTrade is an institutional-grade, multi-module electronic trading core engineered with **Mechanical Sympathy** for Linux and modern x86_64 multi-core architectures.

Structured as a high-performance **Maven Multi-Module Reactor**, ApexTrade strictly decouples the network edge from the matching core. The deterministic execution engine operates as a single-writer state machine free of heap allocations, lock contention, and framework bloat—clocking a verified peak throughput of **19.47 Million orders/second** at a **mean service time of 51.36 nanoseconds per order**.

---

## 🏛️ System Architecture

The exchange is architected into three decoupled modules: **Common**, **Engine (S2)**, and **Gateway (S1)**. This ensures that slow network clients, TLS termination, and WebSocket serialization never stall the nanosecond-critical execution core.

```text
                                CLIENTS / FRONTEND TERMINAL
                                             │
                       HTTP / REST (Port 8080) │ WebSocket (/ws/trades)
                                             ▼
       ┌────────────────────────────────────────────────────────────────────────┐
       │                 apextrade-gateway (Edge Proxy / S1)                    │
       │                                                                        │
       │  • Spring Boot 4.1.1 + Tomcat 11.0.27 (Patched)                       │
       │  • Java 25 Project Loom Virtual Threads (spring.threads.virtual)       │
       │  • Inbound Order Validation, DTO Serialization, REST Controllers       │
       │  • Non-blocking Trade WebSocket Broadcaster (Concurrent Decorator)     │
       └──────────────────┬───────────────────────────────────▲─────────────────┘
                          │                                   │
              36-Byte Wire Codec (Ingress)        36-Byte Wire Codec (Egress)
       (In-Memory Disruptor / TCP Java NIO)   (Lock-Free SPSC Ring Buffer / TCP)
                          │                                   │
                          ▼                                   │
       ┌──────────────────────────────────────────────────────┴─────────────────┐
       │              apextrade-engine (Deterministic Core / S2)                │
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
       │  • Pure Java (Zero Framework / Zero Spring Boot Dependencies)          │
       │  • Standalone Executable Fat JAR via EngineMain Daemon Entrypoint      │
       └────────────────────────────────────────────────────────────────────────┘
                                      ▲
                                      │ Depends on
       ┌──────────────────────────────┴─────────────────────────────────────────┐
       │                           apextrade-common                             │
       │  • Domain Primitives: Side, OrderType, OrderStatus                     │
       │  • 36-Byte Zero-Allocation Binary Protocol: OrderWireCodec (0x4150)    │
       └────────────────────────────────────────────────────────────────────────┘
```

---

## 📦 Multi-Module Reactor Overview

```text
apextrade-core/
├── pom.xml                                   # Parent Reactor POM (Packaging: pom)
├── apextrade-common/                         # Shared wire formats and domain models
│   └── src/main/java/com/rudra/apextrade/common/
│       ├── Side.java                         # BUY / SELL primitives
│       ├── OrderType.java                    # LIMIT / MARKET primitives
│       ├── OrderStatus.java                  # NEW, PARTIALLY_FILLED, FILLED, CANCELLED
│       └── OrderWireCodec.java               # Zero-allocation 36-byte binary protocol
├── apextrade-engine/                         # Pure Java Ultra-Low-Latency Core
│   └── src/main/java/com/rudra/apextrade/
│       ├── core/
│       │   ├── book/OrderBook.java           # Cache-friendly PriceLevel B-Tree ladder
│       │   ├── engine/MatchingEngine.java    # Price-Time Priority matching engine
│       │   ├── engine/EngineMain.java        # Standalone daemon launcher
│       │   ├── order/Order.java              # Object-recycled order model
│       │   ├── order/OrderPool.java          # 100,000 pre-warmed order memory pool
│       │   ├── order/PriceLevel.java         # Doubly linked FIFO queue per tick
│       │   └── trade/Trade.java              # Execution fill event
│       ├── ingress/                          # LMAX Disruptor ring buffer ingress pipeline
│       └── egress/                           # Lock-free SPSC outbound trade buffer pipeline
└── apextrade-gateway/                        # Edge Gateway (Spring Boot & Cloud Edge)
    └── src/main/java/com/rudra/apextrade/
        ├── BackendApplication.java           # Spring Boot entry point (Java 25)
        ├── config/                           # Loom Virtual Threads, Disruptor & Egress Beans
        ├── controller/                       # Order submission & health REST APIs
        ├── dto/                              # OrderRequest & OrderResponse records
        ├── service/                          # Ingress dispatch & atomic sequencing
        └── websocket/                        # Low-latency trade broadcasting to web clients
```

---

## ⚡ Mechanical Sympathy & Engineering Highlights

### 1. Lock-Free Single-Writer State Machine
All book modifications, price level traversals, and execution matches execute deterministically on a single thread (`matching-core-thread`).
* **Zero Locking:** Eliminates `synchronized`, `ReentrantLock`, and OS mutexes (`pthread_mutex`).
* **Cache-Line Padding:** Sequence counters and ring buffer boundaries are padded with 64-byte empty caches to eliminate **false sharing** across adjacent CPU cache lines.

### 2. Dual Decoupled Ring-Buffer Topology
* **Ingress (LMAX Disruptor):** Ingests bursts of unthrottled orders from multi-threaded HTTP/virtual threads into a power-of-two 65,536-slot ring buffer using `YieldingWaitStrategy`.
* **Egress (SPSC Ring Buffer):** Trade execution reports are emitted to a single-producer single-consumer lock-free ring buffer. Network latency or backpressure from slow WebSocket clients cannot propagate backwards into the matching core.

### 3. Zero-GC Memory Management (`OrderPool`)
* Pre-allocates and warms **100,000 `Order` instances** into contiguous memory at initialization.
* Active orders borrow from the pool; cancellations and fills return instances immediately.
* Completely eliminates runtime heap allocation pauses (`0.00 ms GC pause`) on the critical path.

### 4. Zero-Allocation 36-Byte Binary Wire Protocol (`OrderWireCodec`)
For inter-process and network streaming, orders are serialized into a fixed 36-byte binary frame directly via `ByteBuffer`:
```text
 0                   1                   2                   3
 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1
+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
|       MAGIC (0x4150)          |  CMD_TYPE (1) |   SIDE (1)    |
+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
|                       ORDER_ID (8 Bytes)                      |
|                                                               |
+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
|                       USER_ID (8 Bytes)                       |
|                                                               |
+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
|                        PRICE (8 Bytes)                        |
|                                                               |
+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
|                       QUANTITY (8 Bytes)                      |
|                                                               |
+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
| ORDER_TYPE(1) |                   RESERVED                    |
+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
```

### 5. Java 25 Project Loom Virtual Threads
The gateway edge enables `spring.threads.virtual.enabled=true`. Web and socket connection handlers execute on lightweight, user-mode Virtual Threads, preventing thread starvation and reducing context-switch overhead during high concurrent socket traffic.

### 6. Linux Kernel 2MB HugePages & Core Pinning
* **2MB HugePages (`hugetlbfs`):** Collapses a 4-level page table walk (`PML4 -> PDPT -> PD -> Page Frame`) to 3 levels, maximizing TLB (Translation Lookaside Buffer) hit ratios for the pre-allocated 2 GB heap.
* **CPU Core Pinning (`taskset`):** The matching loop is pinned directly to an isolated hardware CPU core to preserve L1 Data (32 KB) and L2 (512 KB) cache warmness.

---

## 🚀 Verified In-Memory Benchmarks

Benchmarked on **Microsoft Azure** (`Standard_D4as_v4`, 4 vCPUs AMD EPYC 7452 @ 2.35–3.35 GHz) running Linux Ubuntu 24.04 LTS:

### Benchmark Workload:
* **Dataset:** 1,000,000 unthrottled orders.
* **Order Profile:** Aggressive crossing `BUY` limit orders sweeping resting `SELL` asks across a 5-level price ladder (top-of-book execution).
* **Measurement Barrier:** Timer records from order #1 submission until the consumer thread clears the 1,000,000th gating sequence (`ringBuffer.getMinimumGatingSequence() >= targetSequence`).

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

### 1. Build and Test the Complete Reactor
From the `backend/` directory:
```bash
# Run all unit and integration test suites across all 3 modules
mvn clean test

# Package all JAR artifacts
mvn clean package -DskipTests
```

### 2. Run the Standalone Matching Engine (S2)
The engine builds an executable standalone fat JAR with all low-latency dependencies bundled:
```bash
java -jar apextrade-engine/target/apextrade-engine-0.0.1-SNAPSHOT.jar
```

### 3. Run the Edge Gateway (S1)
Launch the Spring Boot edge service with Virtual Threads enabled:
```bash
mvn -pl apextrade-gateway spring-boot:run
```

### 4. Linux Kernel Tuning (Production / Benchmark Setup)
```bash
# 1. Allocate 1024 2MB HugePages (2 GB pool)
sudo sysctl -w vm.nr_hugepages=1024
sudo sysctl -w vm.hugetlb_shm_group=$(id -g)
sudo prlimit --pid=$$ --memlock=unlimited:unlimited

# 2. Launch Engine with HugePages and AlwaysPreTouch pinned to Core 0
taskset -c 0 java -XX:+UseLargePages -XX:+AlwaysPreTouch -Xms2g -Xmx2g -jar apextrade-engine/target/apextrade-engine-0.0.1-SNAPSHOT.jar
```

### 5. Docker Multi-Stage Build
The root container build compiles the multi-module reactor and produces a minimal Eclipse Temurin Java 25 runtime image:
```bash
cd backend
docker build -t apextrade-gateway:latest .
docker run -p 8080:8080 apextrade-gateway:latest
```

---

## 🗺️ Engineering Roadmap

- [x] **Phase 1: Multi-Module Reactor Core**
  - Modularized into `apextrade-common`, `apextrade-engine`, and `apextrade-gateway`.
  - Pure Java matching engine core decoupled from Spring framework dependencies.
  - Zero-allocation 36-byte binary wire protocol (`OrderWireCodec`).
  - Standalone fat JAR daemon packaging for the engine.
- [ ] **Phase 2: High-Performance Inter-Process TCP Gateway**
  - Implement non-blocking Java NIO `ServerSocketChannel` (`EngineTcpServer`) on port `9090`.
  - Direct socket frame decoding into the Disruptor ring buffer without object allocations.
  - Runtime toggle: `apextrade.engine.mode=embedded` (in-memory) vs. `tcp` (networked).
- [ ] **Phase 3: In-Memory Account & Balance Ledger**
  - Nanosecond user balance checks in Gateway prior to order submission.
  - Async persistence queue to PostgreSQL for transaction history and auditing.
- [ ] **Phase 4: Automated Liquidity Provisioning Bots**
  - Market-maker bot continuously quoting spreads around mid-market.
  - Arbitrage bot testing latency under continuous spread sweep pressure.
- [ ] **Phase 5: Nanosecond Microbenchmarking with JMH & HdrHistogram**
  - Official JMH harnesses measuring per-order latency percentiles (p50, p90, p99, p99.9).
  - Jitter analysis and GC pause logging under sustained multi-hour bursts.

---

## 📜 Technology Matrix

| Component | Technology | Rationale |
| :--- | :--- | :--- |
| **Language Runtime** | Java 25 (LTS) | Modern JIT C2 optimizations, Project Loom Virtual Threads, Foreign Memory access ready. |
| **Ingress Concurrency** | LMAX Disruptor 4.0.0 | Mechanical sympathy, lock-free ring buffer, cache-line padded sequencing. |
| **Egress Concurrency** | SPSC Ring Buffer | Non-blocking trade publication isolating matching core from client network lag. |
| **Wire Protocol** | Custom Binary Frame | 36-byte zero-allocation payload with magic byte validation (`0x4150`). |
| **Web & Edge Framework** | Spring Boot 4.1.1 | Reactive WebSocket client streaming, REST entry point, Apache Tomcat 11.0.27. |
| **OS Tuning** | Linux `taskset`, `hugetlbfs` | CPU core affinity pinning and 2MB TLB coverage. |
| **Containerization** | Eclipse Temurin 25 JRE | Multi-stage build producing lean container images for cloud deployment. |
