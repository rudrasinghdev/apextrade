# ⚡ ApexTrade: Ultra-High-Throughput In-Memory Matching Core

[![Java](https://img.shields.io/badge/Java-25-orange.svg)](https://openjdk.org/)
[![Architecture](https://img.shields.io/badge/Architecture-LMAX%20Disruptor-blue.svg)](https://lmax-exchange.github.io/disruptor/)
[![Peak Throughput](https://img.shields.io/badge/Peak%20Throughput-19.47M%20ops%2Fsec-success.svg)](#-verified-benchmark-results)
[![Service Time](https://img.shields.io/badge/Mean%20Service%20Time-51.36%20ns%2Forder-brightgreen.svg)](#-verified-benchmark-results)
[![Deployment](https://img.shields.io/badge/Cloud-Azure%20VM%20(AMD%20EPYC)-0089D6.svg)](#%EF%B8%8F-cloud-deployment-specifications)

ApexTrade is an ultra-high-throughput, in-memory cryptocurrency matching core engineered with **Mechanical Sympathy** for Linux and modern multi-core x86_64 architectures.

Designed as a deterministic, single-writer state machine, it eliminates dynamic heap allocation churn via pre-warmed object pools, utilizes lock-free single-writer ring buffers, pins execution threads to dedicated CPU cores, and utilizes Linux kernel **2MB HugePages** to maximize TLB cache coverage. Under a saturated burst on a standard cloud compute instance, the core achieves a peak throughput of **19.47 Million orders/second** at a **mean service time of 51.36 nanoseconds per order**.

---

## 🚀 Verified Benchmark Results

Benchmarked on **Microsoft Azure** (`Standard_D4as_v4`, 4 vCPUs AMD EPYC 7452 @ 2.35–3.35 GHz) running Linux Ubuntu 24.04 LTS:

### Benchmark Workload
* **Dataset:** 1,000,000 unthrottled orders.
* **Order Profile:** Aggressive crossing `BUY` limit orders sweeping pre-seeded resting `SELL` asks across a 5-level price ladder (top-of-book execution).
* **Measurement Barrier:** The timer starts immediately before publishing order #1 to the ring buffer and stops only when the single-writer matching consumer thread has **fully processed and cleared** the gating sequence of the 1,000,000th order (`ringBuffer.getMinimumGatingSequence() >= targetSequence`).

| Execution Stage | Total Orders | Duration | Throughput (TPS) | Mean Service Time (1/TPS) | CPU Cycles / Order |
| :--- | :---: | :---: | :---: | :---: | :---: |
| **Cold Run (JIT Tier 1–3 Profiling)** | 1,000,000 | 239.95 ms | 4,167,481 ops/sec | 239.95 ns | ~720 cycles |
| **Warm Run (C2 Tier-4 Optimization)** | 1,000,000 | 77.85 ms | 12,845,785 ops/sec | 77.85 ns | ~233 cycles |
| **Saturated Peak (BTB / Loop Unrolled)** | **1,000,000** | **51.36 ms** | **19,470,243 ops/sec** | **51.36 ns** | **~154 cycles** |

> **Note on Service Time vs. End-to-End Latency:**  
> The 51.36 ns figure represents the **mean service time** (the inverse of throughput: $\frac{\text{elapsed time}}{\text{order count}}$) under saturated pipeline conditions. In a burst test, orders queue in the ring buffer, so individual order dwell latency (publish-to-match completion time) varies across the queue depth. True per-order percentiles (p50, p99, p99.9) will be instrumented using `HdrHistogram` in Phase 2.

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

## 🏛️ System Architecture

```text
               +--------------------------------------------------+
               |             ApexTrade Matching Engine            |
               +--------------------------------------------------+
                                         |
                 Producer Threads (REST Ingestion / Taker Bots)
                                         |
                                         v
                 +----------------------------------------------+
                 |    LMAX Disruptor RingBuffer (65,536 slots)  |
                 |     (Zero-Lock, Cache-Padded Ring Buffer)    |
                 +----------------------------------------------+
                                         |
                 Consumer Event Loop (Pinned to CPU Core 0)
                                         v
                 +----------------------------------------------+
                 |             matching-core-thread             |
                 |  - Single-Writer Order Book Traversal        |
                 |  - Bid/Ask PriceLevel Doubly Linked Lists    |
                 |  - Zero-Allocation OrderPool (100k Prewarmed)|
                 +----------------------------------------------+
```

### Key Engineering Innovations:

1. **Lock-Free Single-Writer Model (LMAX Disruptor):**
   * Replaces traditional locking queues (`ArrayBlockingQueue`) with a cache-line padded, power-of-two ring buffer (65,536 slots).
   * Eliminates thread lock contention (`pthread_mutex`) and prevents false sharing on the sequence counters via CPU cache line padding (64 bytes).

2. **Zero-GC Object Pool (`OrderPool`):**
   * Pre-allocates and warms 100,000 `Order` objects at boot into contiguous memory.
   * Borrows on limit placement and recycles on trade match/cancel.
   * Yields **0.00 ms Garbage Collection pauses** under continuous multi-million order traffic by eliminating runtime `new` allocations from the critical path.

3. **Linux Kernel 2MB HugePages (`hugetlbfs`):**
   * Replaces standard 4KB virtual memory pages with physical 2MB pages.
   * Eliminates the 4th-level page table walk (`PML4 -> PDPT -> PD -> 2MB Page Frame`), reducing page walk depth from 4 levels to 3 and drastically expanding TLB (Translation Lookaside Buffer) reach for the 2 GB pre-allocated memory pool.

4. **Thread CPU Affinity (`taskset`):**
   * Pins `matching-core-thread` directly to **vCPU Core 0**.
   * Reduces OS scheduler thread migrations, keeping the 32 KB L1 Data Cache and 512 KB L2 Cache hot for the order book's price ladder pointers.

5. **Autonomous Market Bots:**
   * **`MarketMakerBot`:** Continuously quotes 5 bid/ask depth levels around anchor prices.
   * **`ArbitrageBot`:** Mean-reverting taker bot driven by an ultra-fast XORShift64 PRNG (~2ns) to attack spread dislocations and maintain realistic market depth.

---

## ⚙️ Cloud Deployment Specifications

* **Cloud Provider:** Microsoft Azure
* **Instance Type:** `Standard_D4as_v4` (General Purpose Compute)
* **vCPUs:** 4 vCPUs (AMD EPYC 7452, 2.35 GHz base / 3.35 GHz boost)
* **RAM:** 16 GiB
* **OS:** Linux Ubuntu 24.04 LTS (Kernel 6.8+)
* **JVM Runtime:** Oracle OpenJDK 25.0.4.1 (64-Bit Server VM)

---

## 🛠️ Linux Kernel Tuning & Benchmark Reproduction Guide

### 1. Configure Linux Kernel HugePages
```bash
# Reserve 1024 HugePages of 2MB (2 GB dedicated RAM)
sudo sysctl -w vm.nr_hugepages=1024

# Grant non-root user group permissions for hugetlbfs
sudo sysctl -w vm.hugetlb_shm_group=$(id -g)

# Remove shell locked-memory quotas (mlock)
sudo prlimit --pid=$$ --memlock=unlimited:unlimited
```

### 2. Build and Launch
```bash
# Compile package
mvn clean package

# Launch with Pre-Touch and LargePages enabled
taskset -c 0,1,2,3 java -XX:+UseLargePages -XX:+AlwaysPreTouch -Xms2g -Xmx2g -jar apextrade-0.0.1-SNAPSHOT.jar
```

### 3. Pin the Matching Core to vCPU 0
```bash
# Locate matching-core-thread SPID and pin to Core 0
sudo taskset -cp 0 $(ps -T -p $(pgrep -f apextrade) -o spid,comm | grep -i matching | awk '{print $1}')
```

### 4. Trigger the 1,000,000 Order Blast
```bash
curl -X POST "http://localhost:8080/api/benchmark/start?orders=1000000"
```

---

## 🗺️ Engineering Roadmap: Towards ApexTrade v2

ApexTrade v1 proved the raw speed ceiling of a single-node, in-memory matching core. The next phase transforms it into a production-grade, distributed, fault-tolerant exchange:

* [x] **Phase 1: Deterministic In-Memory Matching Core** (19.47M ops/sec peak, zero-allocation ring buffer, 2MB HugePages).
* [ ] **Phase 2: Distributed Replicated State Machine (Raft)**:
  * 3-node cluster replicating an immutable command log before applying to the matching core.
  * Automatic leader election and failover recovery within $<300\text{ ms}$.
  * Split-brain prevention across network partitions using Quorum consensus ($W+R > N$).
* [ ] **Phase 3: Network Gateway & Micro-Benchmarking**:
  * Lightweight TCP binary order entry gateway (Java NIO / Netty).
  * True end-to-end latency percentiles (p50, p90, p99, p99.9) using `HdrHistogram`.
  * The Durability vs. Latency trade-off study: In-Memory vs. Same-Region Raft vs. Cross-Cloud Consensus.

---

## 📜 Tech Stack
* **Language:** Java 25 (Loom, Modern Foreign Memory API ready)
* **Framework:** Spring Boot 4.1.1 (Embedded Tomcat 11)
* **Concurrency:** LMAX Disruptor 4.0.0
* **OS Interop:** Linux `taskset`, `sysctl`, `hugetlbfs`, POSIX Shared Memory
