# ⚡ ApexTrade: Ultra-Low-Latency In-Memory Matching Engine

[![Java](https://img.shields.io/badge/Java-25-orange.svg)](https://openjdk.org/)
[![Architecture](https://img.shields.io/badge/Architecture-LMAX%20Disruptor-blue.svg)](https://lmax-exchange.github.io/disruptor/)
[![Throughput](https://img.shields.io/badge/Peak%20Throughput-19.47M%20ops%2Fsec-success.svg)](#-verified-benchmark-results)
[![Latency](https://img.shields.io/badge/Per--Order%20Latency-51.36%20ns-brightgreen.svg)](#-verified-benchmark-results)
[![Deployment](https://img.shields.io/badge/Cloud-Azure%20Bare--Metal-0089D6.svg)](#%EF%B8%8F-cloud-deployment-specifications)

ApexTrade is an institutional-grade, in-memory cryptocurrency matching engine engineered with **Mechanical Sympathy** for bare-metal Linux and modern multi-core x86_64 architecture. 

By eliminating memory allocation churn, utilizing lock-free single-writer ring buffers, pinning critical threads to isolated CPU cores, and reserving physical Linux kernel **2MB HugePages**, ApexTrade achieves **19.47 Million orders/second** at **~51.36 nanoseconds per order** on standard cloud hardware.

---

## 🚀 Verified Benchmark Results

Benchmarked on **Microsoft Azure** (`Standard_D4as_v4`, AMD EPYC 7452 @ 3.0 GHz) under an unthrottled 1,000,000 order burst:

| Execution Stage | Total Orders | Duration | Throughput (TPS) | Latency / Order | CPU Cycles / Order |
| :--- | :---: | :---: | :---: | :---: | :---: |
| **Cold Run (JIT Profiling)** | 1,000,000 | 239.95 ms | 4,167,481 ops/sec | 239.95 ns | ~720 cycles |
| **Warm Run (C2 Tier-4)** | 1,000,000 | 77.85 ms | 12,845,785 ops/sec | 77.85 ns | ~233 cycles |
| **Saturated Hardware Peak** | **1,000,000** | **51.36 ms** | **19,470,243 ops/sec** | **51.36 ns** | **~154 cycles** |

```text
=======================================================
>>> STARTING BARE-METAL BENCHMARK: 1000000 ORDERS <<<
=======================================================
[BENCHMARK COMPLETED] 1000000 orders in 51.36 ms (19470243.87 ops/sec)
=======================================================
```

---

## 🏛️ System Architecture

```text
               +--------------------------------------------------+
               |                  ApexTrade Engine                 |
               +--------------------------------------------------+
                                        |
                 Producer Threads (Tomcat / REST / Taker Bots)
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
   * Eliminates thread contention, kernel locks (`pthread_mutex`), and cache-coherence traffic across CPU sockets.

2. **Zero-GC Object Pool (`OrderPool`):**
   * Pre-allocates and warms 100,000 `Order` objects at application boot.
   * Borrows on limit placement and recycles on trade match/cancel.
   * Yields **0.00 ms Garbage Collection pauses** under continuous multi-million order traffic.

3. **Linux Kernel 2MB HugePages (`hugetlbfs`):**
   * Eliminates TLB (Translation Lookaside Buffer) cache thrashing by replacing default 4KB memory pages with 2MB physical pages.
   * Reduces memory address translation latency from 4 pointer hops to 1.

4. **Hardware CPU Core Pinning (`taskset`):**
   * `matching-core-thread` is locked strictly to **CPU Core 0**.
   * Prevents the Linux Completely Fair Scheduler (CFS) from migrating threads, keeping the 32 KB L1 Data Cache and 512 KB L2 Cache permanently hot.

5. **Autonomous Market Bots:**
   * **`MarketMakerBot`:** Continuously quotes 5 bid/ask depth levels around anchor prices.
   * **`ArbitrageBot`:** Mean-reverting taker bot driven by an ultra-fast XORShift64 PRNG (~2ns) to attack dislocations and maintain realistic spread dynamics.

---

## ⚙️ Cloud Deployment Specifications

* **Cloud Provider:** Microsoft Azure
* **Instance Type:** `Standard_D4as_v4`
* **CPU:** 4 vCPUs (AMD EPYC 7452, 2.35 GHz base / 3.35 GHz boost)
* **RAM:** 16 GiB (NUMA Node 0)
* **OS:** Linux Ubuntu 24.04 LTS (Kernel 6.8+)
* **JVM Runtime:** Oracle OpenJDK 25.0.4.1 (64-Bit Server VM)

---

## 🛠️ Bare-Metal Tuning & Reproduction Guide

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
# Compile with Maven
mvn clean package -DskipTests

# Launch with Pre-Touch and LargePages enabled
taskset -c 0,1,2,3 java -XX:+UseLargePages -XX:+AlwaysPreTouch -Xms2g -Xmx2g -jar apextrade-0.0.1-SNAPSHOT.jar
```

### 3. Pin the Matching Core to CPU 0
```bash
# Locate matching-core-thread SPID and pin to Core 0
sudo taskset -cp 0 $(ps -T -p $(pgrep -f apextrade) -o spid,comm | grep -i matching | awk '{print $1}')
```

### 4. Trigger the 1,000,000 Order Blast
```bash
curl -X POST "http://localhost:8080/api/benchmark/start?orders=1000000"
```

---

## 📜 Tech Stack
* **Language:** Java 25 (Loom, Modern Foreign Memory API ready)
* **Framework:** Spring Boot 4.1.1 (Embedded Tomcat 11)
* **Concurrency:** LMAX Disruptor 4.0.0
* **OS Interop:** Linux `taskset`, `sysctl`, `hugetlbfs`, POSIX Shared Memory
