# ⚡ ApexTrade: Ultra-Low-Latency In-Memory Matching Engine

[![Java](https://img.shields.io/badge/Java-25-orange.svg)](https://openjdk.org/)
[![Architecture](https://img.shields.io/badge/Architecture-LMAX%20Disruptor-blue.svg)](https://lmax-exchange.github.io/disruptor/)
[![Throughput](https://img.shields.io/badge/Peak%20Throughput-19.47M%20ops%2Fsec-success.svg)](#benchmark-results)
[![Latency](https://img.shields.io/badge/Per--Order%20Latency-51.36%20ns-brightgreen.svg)](#benchmark-results)
[![Deployment](https://img.shields.io/badge/Cloud-Azure%20Bare--Metal-0089D6.svg)](#cloud-deployment-specifications)

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
