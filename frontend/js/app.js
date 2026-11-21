/**
 * ApexTrade Core - High-Frequency Trading Terminal Client
 * Architecture: Event-Driven Reactive Store, Out-of-Order Order FSM, 60fps RAF Batching
 * Mechanical Sympathy: Zero DOM thrashing, lossless fixed-point precision, no phantom states.
 */

(() => {
  'use strict';

  // ==========================================================================
  // 1. CONFIGURATION & ENVIRONMENT DETECTOR
  // ==========================================================================

  const DEFAULT_PORT = 8080;
  const ORACLE_CLOUD_HOST = '152.67.4.206:8080';

  let savedHost = localStorage.getItem('apextrade_target_host');
  if (savedHost && savedHost.includes('onrender.com')) {
    localStorage.removeItem('apextrade_target_host');
    savedHost = null;
  }

  let detectedHost;
  if (savedHost) {
    detectedHost = savedHost;
  } else if (window.location.protocol === 'http:' || window.location.protocol === 'https:') {
    detectedHost = window.location.port ? window.location.host : `${window.location.hostname}:${DEFAULT_PORT}`;
  } else {
    detectedHost = ORACLE_CLOUD_HOST;
  }

  const API_HOST = detectedHost;
  const isSecure = window.location.protocol === 'https:';
  const REST_BASE_URL = `${isSecure ? 'https:' : 'http:'}//${API_HOST}`;
  const WS_PROTOCOL = isSecure ? 'wss:' : 'ws:';
  const WS_URL = `${WS_PROTOCOL}//${API_HOST}/ws/trades`;

  const STORAGE_TOKEN_KEY = 'apextrade_jwt';
  const STORAGE_USER_KEY = 'apextrade_user';
  const STORAGE_ORDERS_KEY = 'apextrade_active_orders';

  function getOrdersStorageKey(userId) {
    return userId ? `${STORAGE_ORDERS_KEY}_${userId}` : STORAGE_ORDERS_KEY;
  }

  // ==========================================================================
  // 2. CENTRAL REACTIVE STORE & REVISION REGISTRY
  // ==========================================================================

  const state = {
    token: sessionStorage.getItem(STORAGE_TOKEN_KEY) || null,
    user: JSON.parse(sessionStorage.getItem(STORAGE_USER_KEY) || 'null'),
    balance: null,
    gatewayOnline: false,
    wsConnected: false,
    activeSide: 'BUY',
    activeOrderType: 'LIMIT',
    orders: [], // Array of local and reconciled orders
    executedOrderIds: new Set(), // Set of order IDs confirmed filled by engine WebSocket
    executedTrades: new Map(), // orderId -> trade details for fast lookup
    cancelledOrderIds: new Set(), // Set of order IDs explicitly confirmed cancelled
    auditLogs: [],
    lastTradedPrice: 50000.00,
    prevPrice: 50000.00
  };

  // Restore orders from persistent storage for active user
  function loadOrdersFromStorage() {
    if (!state.user || !state.user.userId) return [];
    try {
      const raw = localStorage.getItem(getOrdersStorageKey(state.user.userId));
      return raw ? JSON.parse(raw) : [];
    } catch {
      return [];
    }
  }

  function saveOrdersToStorage() {
    if (!state.user || !state.user.userId) return;
    try {
      localStorage.setItem(getOrdersStorageKey(state.user.userId), JSON.stringify(state.orders));
    } catch {
      // localStorage quota or private browsing safeguard
    }
  }

  state.orders = loadOrdersFromStorage();

  // Populate executedOrderIds from loaded orders
  state.orders.forEach(o => {
    if (o.status === 'FILLED') state.executedOrderIds.add(o.orderId);
    if (o.status === 'CANCELLED') state.cancelledOrderIds.add(o.orderId);
  });

  // ==========================================================================
  // 3. DOM ELEMENTS CACHE
  // ==========================================================================

  const el = {
    // Top Bar & Health
    gatewayDot: document.getElementById('gateway-dot'),
    gatewayStatusText: document.getElementById('gateway-status-text'),
    btnToggleEnv: document.getElementById('btn-toggle-env'),

    // Views
    welcomeView: document.getElementById('welcome-view'),
    terminalView: document.getElementById('terminal-view'),

    // Auth Form & Launchpad
    authContainer: document.getElementById('auth-container'),
    authAlert: document.getElementById('auth-alert'),
    tabLogin: document.getElementById('tab-login'),
    tabRegister: document.getElementById('tab-register'),
    formLogin: document.getElementById('form-login'),
    formRegister: document.getElementById('form-register'),
    loginEmail: document.getElementById('login-email'),
    loginPassword: document.getElementById('login-password'),
    regEmail: document.getElementById('reg-email'),
    regPassword: document.getElementById('reg-password'),
    btnLoginSubmit: document.getElementById('btn-login-submit'),
    btnRegSubmit: document.getElementById('btn-register-submit'),

    // Launchpad
    launchpadContainer: document.getElementById('launchpad-container'),
    lpUserEmail: document.getElementById('lp-user-email'),
    lpUserId: document.getElementById('lp-user-id'),
    lpUserRole: document.getElementById('lp-user-role'),
    lpTokenSnippet: document.getElementById('lp-token-snippet'),
    lpAvailableUsd: document.getElementById('lp-available-usd'),
    lpAvailableBtc: document.getElementById('lp-available-btc'),
    btnUpgradeTerminal: document.getElementById('btn-upgrade-terminal'),
    btnLogout: document.getElementById('btn-logout'),

    // Terminal Navbar
    terminalUserEmail: document.getElementById('terminal-user-email'),
    terminalUserId: document.getElementById('terminal-user-id'),
    terminalBalanceUsd: document.getElementById('terminal-balance-usd'),
    terminalBalanceBtc: document.getElementById('terminal-balance-btc'),
    btnBackLaunchpad: document.getElementById('btn-back-launchpad'),
    btnTerminalLogout: document.getElementById('btn-terminal-logout'),
    latencyDisplay: document.getElementById('latency-display'),
    ltpDisplay: document.getElementById('ltp-display'),
    wsDot: document.getElementById('ws-dot'),
    wsStatusText: document.getElementById('ws-status-text'),

    // DMA Form
    orderForm: document.getElementById('order-form'),
    btnSideBuy: document.getElementById('btn-side-buy'),
    btnSideSell: document.getElementById('btn-side-sell'),
    btnTypeLimit: document.getElementById('btn-type-limit'),
    btnTypeMarket: document.getElementById('btn-type-market'),
    inputPrice: document.getElementById('input-price'),
    inputQty: document.getElementById('input-qty'),
    groupPrice: document.getElementById('group-price'),
    btnSubmitOrder: document.getElementById('btn-submit-order'),
    summaryNotional: document.getElementById('summary-notional'),
    formMarginLabel: document.getElementById('form-margin-label'),
    formAvailableMargin: document.getElementById('form-available-margin'),
    statusBanner: document.getElementById('order-status-banner'),
    bannerTitle: document.getElementById('banner-title'),
    bannerDesc: document.getElementById('banner-desc'),
    btnDemoCross: document.getElementById('btn-demo-cross'),

    // Steppers
    btnPriceMinus: document.getElementById('btn-price-minus'),
    btnPricePlus: document.getElementById('btn-price-plus'),
    btnQtyMinus: document.getElementById('btn-qty-minus'),
    btnQtyPlus: document.getElementById('btn-qty-plus'),

    // Orders Table
    myOrdersTbody: document.getElementById('my-orders-tbody'),
    myOrdersCount: document.getElementById('my-orders-count'),
    btnClearOrders: document.getElementById('btn-clear-orders'),

    // Audit Log Table
    auditTbody: document.getElementById('audit-tbody'),
    btnClearAudit: document.getElementById('btn-clear-audit')
  };

  // ==========================================================================
  // 4. HTTP CLIENT (Resilient Fetch with JWT & Sub-millisecond Telemetry)
  // ==========================================================================

  async function apiRequest(endpoint, options = {}) {
    const url = `${REST_BASE_URL}${endpoint}`;
    const headers = {
      'Content-Type': 'application/json',
      ...(options.headers || {})
    };

    if (state.token && !options.skipAuth) {
      headers['Authorization'] = `Bearer ${state.token}`;
    }

    const t0 = performance.now();
    try {
      const response = await fetch(url, {
        ...options,
        headers
      });

      const elapsedMs = Math.round(performance.now() - t0);
      if (el.latencyDisplay) {
        el.latencyDisplay.textContent = `${elapsedMs} ms`;
      }

      let data = null;
      const contentType = response.headers.get('content-type');
      if (contentType && contentType.includes('application/json')) {
        data = await response.json();
      }

      if (response.status === 401 && state.token && !options.skipAuth) {
        handleLogout();
        setAuthAlert('error', 'Session expired. Please sign in again.');
      }

      return {
        ok: response.ok,
        status: response.status,
        data,
        elapsedMs
      };
    } catch (err) {
      const elapsedMs = Math.round(performance.now() - t0);
      return {
        ok: false,
        status: 0,
        error: err.message,
        elapsedMs
      };
    }
  }

  // ==========================================================================
  // 5. 60 FPS BATCH RENDER SCHEDULER (Zero DOM Freezes Under Tick Flood)
  // ==========================================================================

  let ordersDirty = false;
  let auditDirty = false;
  let rafPending = false;

  function scheduleOrdersRender() {
    ordersDirty = true;
    requestRenderFlush();
  }

  function scheduleAuditRender() {
    auditDirty = true;
    requestRenderFlush();
  }

  function requestRenderFlush() {
    if (!rafPending) {
      rafPending = true;
      requestAnimationFrame(flushRenders);
    }
  }

  function flushRenders() {
    rafPending = false;
    if (ordersDirty) {
      ordersDirty = false;
      renderOrdersDOM();
    }
    if (auditDirty) {
      auditDirty = false;
      renderAuditDOM();
    }
  }

  function renderOrdersDOM() {
    if (!el.myOrdersTbody) return;

    el.myOrdersCount.textContent = state.orders.length;

    if (state.orders.length === 0) {
      el.myOrdersTbody.innerHTML = `
        <tr class="empty-row" id="my-orders-empty">
          <td colspan="8">No active orders placed yet in this session.</td>
        </tr>
      `;
      return;
    }

    // Build DOM via single fragment for zero reflow overhead
    const frag = document.createDocumentFragment();

    state.orders.forEach(order => {
      const tr = document.createElement('tr');
      const isBuy = order.side === 'BUY';
      const isCancelled = order.status === 'CANCELLED';
      const isFilled = order.status === 'FILLED';
      const isPendingCancel = order.status === 'PENDING_CANCEL';
      const isPartial = order.status === 'PARTIAL';
      const isTerminalState = isCancelled || isFilled;
      const disableCancel = isTerminalState || isPendingCancel;

      let cancelBtnText = 'Cancel';
      if (isPendingCancel) cancelBtnText = 'Cancelling...';
      else if (isCancelled) cancelBtnText = 'Cancelled';
      else if (isFilled) cancelBtnText = 'Filled';

      const displayQty = isPartial
        ? `${order.remainingQuantity} <span style="font-size:11px; opacity:0.6;">/ ${order.initialQuantity}</span>`
        : (order.remainingQuantity || order.quantity);

      const statusBadge = isPartial
        ? '<span class="status-pill status-partial">PARTIAL</span>'
        : `<span class="status-pill status-${order.status.toLowerCase().replace('_', '-')}">${order.status}</span>`;

      tr.innerHTML = `
        <td class="font-mono text-muted">#${order.orderId}</td>
        <td>${order.time}</td>
        <td><span class="badge-side ${isBuy ? 'badge-buy' : 'badge-sell'}">${order.side}</span></td>
        <td><span class="badge-type">${order.orderType}</span></td>
        <td class="font-mono font-bold">${formatDisplayPrice(order.price)}</td>
        <td class="font-mono">${displayQty}</td>
        <td>${statusBadge}</td>
        <td>
          <button type="button" class="btn-cancel" data-order-id="${order.orderId}" ${disableCancel ? 'disabled' : ''}>
            ${cancelBtnText}
          </button>
        </td>
      `;

      const cancelBtn = tr.querySelector('.btn-cancel');
      if (cancelBtn && !disableCancel) {
        cancelBtn.addEventListener('click', () => cancelOrder(order.orderId));
      }

      frag.appendChild(tr);
    });

    el.myOrdersTbody.innerHTML = '';
    el.myOrdersTbody.appendChild(frag);
  }

  function renderAuditDOM() {
    if (!el.auditTbody) return;

    if (state.auditLogs.length === 0) {
      el.auditTbody.innerHTML = `
        <tr class="empty-row" id="audit-empty">
          <td colspan="5">Gateway ingress audit log ready. Awaiting requests...</td>
        </tr>
      `;
      return;
    }

    const frag = document.createDocumentFragment();
    state.auditLogs.forEach(entry => {
      const tr = document.createElement('tr');
      const isOk = entry.status >= 200 && entry.status < 300;
      tr.innerHTML = `
        <td class="font-mono text-muted">${entry.time}</td>
        <td class="font-mono font-bold">${entry.event}</td>
        <td><span class="pill-spec ${isOk ? 'text-success' : 'text-sell'}">${entry.status || 'ERR'}</span></td>
        <td class="font-mono text-cyan">${entry.elapsedMs}ms</td>
        <td class="text-secondary">${entry.details}</td>
      `;
      frag.appendChild(tr);
    });

    el.auditTbody.innerHTML = '';
    el.auditTbody.appendChild(frag);
  }

  function logAudit(event, httpStatus, latencyMs, details) {
    state.auditLogs.unshift({
      time: new Date().toLocaleTimeString(),
      event,
      status: httpStatus,
      elapsedMs: latencyMs,
      details
    });

    if (state.auditLogs.length > 50) {
      state.auditLogs.pop();
    }

    scheduleAuditRender();
  }

  // ==========================================================================
  // 6. ORDER FINITE STATE MACHINE (FSM) & OUT-OF-ORDER RECONCILIATION
  // ==========================================================================

  /**
   * Deterministic State Transition:
   * Handles asynchronous racing between HTTP Ingress and WebSocket Executions.
   */
  const OrderFSM = {
    /**
     * Called when HTTP response resolves with orderId from Ingress Disruptor.
     */
    handleIngressAccepted(orderId, side, orderType, priceStr, qtyStr, timestampNs) {
      // 1. Check if engine executed this order before the HTTP response finished
      const alreadyFilled = state.executedOrderIds.has(orderId);
      const alreadyCancelled = state.cancelledOrderIds.has(orderId);

      let initialStatus = 'QUEUED';
      if (alreadyFilled) {
        initialStatus = 'FILLED';
      } else if (alreadyCancelled) {
        initialStatus = 'CANCELLED';
      }

      // Check if order already staged in local array
      const existing = state.orders.find(o => o.orderId === orderId);
      if (existing) {
        // If it was already marked FILLED, CANCELLED, or PARTIAL by WS, never overwrite with QUEUED!
        if (existing.status !== 'FILLED' && existing.status !== 'CANCELLED' && existing.status !== 'PARTIAL') {
          existing.status = initialStatus;
        }
      } else {
        const orderTime = timestampNs 
          ? new Date(Math.round(timestampNs / 1_000_000)).toLocaleTimeString()
          : new Date().toLocaleTimeString();

        state.orders.unshift({
          orderId,
          userId: state.user?.userId,
          time: orderTime,
          side,
          orderType,
          price: priceStr,
          initialQuantity: qtyStr,
          remainingQuantity: qtyStr,
          quantity: qtyStr,
          status: initialStatus
        });
      }

      saveOrdersToStorage();
      scheduleOrdersRender();
    },

    /**
     * Called when a TRADE execution arrives over WebSocket.
     */
    handleExecution(trade) {
      const tradePrice = parseFloat(trade.price);
      state.prevPrice = state.lastTradedPrice;
      state.lastTradedPrice = tradePrice;

      updateLtpDisplay(tradePrice, state.prevPrice);

      const currentUserId = state.user?.userId;
      const isMyBuyer = trade.buyerId === currentUserId;
      const isMySeller = trade.sellerId === currentUserId;

      if (!isMyBuyer && !isMySeller) {
        logAudit(
          'TRADE_EXEC',
          200,
          0,
          `Public Trade #${trade.tradeId}: ${trade.quantity} BTC @ ${tradePrice.toLocaleString('en-US', { minimumFractionDigits: 2 })} USD (B:#${trade.buyerId} S:#${trade.sellerId})`
        );
        return;
      }

      const tradeSats = Math.round(parseFloat(trade.quantity) * 100_000_000);

      // Update existing orders in state with exact satoshi math
      let updatedCount = 0;
      state.orders.forEach(o => {
        const isBuyerMatch = isMyBuyer && o.orderId === trade.buyerOrderId;
        const isSellerMatch = isMySeller && o.orderId === trade.sellerOrderId;

        if (isBuyerMatch || isSellerMatch) {
          const initQtyStr = o.initialQuantity || o.quantity || trade.quantity;
          const initSats = Math.round(parseFloat(initQtyStr) * 100_000_000);
          const currentRemStr = o.remainingQuantity !== undefined ? o.remainingQuantity : (o.quantity || trade.quantity);
          const currentRemSats = Math.round(parseFloat(currentRemStr) * 100_000_000);
          const newRemSats = Math.max(0, currentRemSats - tradeSats);

          o.initialQuantity = (initSats / 100_000_000).toFixed(8);
          o.remainingQuantity = (newRemSats / 100_000_000).toFixed(8);
          o.quantity = o.remainingQuantity;

          if (newRemSats <= 0) {
            o.status = 'FILLED';
            state.executedOrderIds.add(o.orderId);
          } else {
            o.status = 'PARTIAL';
          }
          updatedCount++;
        }
      });

      // If buyer or seller order wasn't in state yet (HTTP response in flight)
      if (updatedCount === 0) {
        if (isMyBuyer) state.executedTrades.set(trade.buyerOrderId, trade);
        if (isMySeller) state.executedTrades.set(trade.sellerOrderId, trade);
      }

      saveOrdersToStorage();
      scheduleOrdersRender();
      fetchBalance();

      showBanner(
        'success',
        `MATCH EXECUTED (Trade #${trade.tradeId})`,
        `Filled ${trade.quantity} BTC @ ${tradePrice.toLocaleString('en-US', { minimumFractionDigits: 2 })} USD | Buyer #${trade.buyerId} x Seller #${trade.sellerId}`
      );

      logAudit(
        'TRADE_EXEC',
        200,
        0,
        `Trade #${trade.tradeId}: ${trade.quantity} BTC @ ${tradePrice.toLocaleString('en-US', { minimumFractionDigits: 2 })} USD (B:#${trade.buyerId} S:#${trade.sellerId})`
      );
    },

    /**
     * Called when CANCEL confirmation arrives over WebSocket.
     */
    handleCancelConfirmed(cancel) {
      state.cancelledOrderIds.add(cancel.orderId);

      let matched = false;
      state.orders.forEach(o => {
        if (o.orderId === cancel.orderId) {
          o.status = 'CANCELLED';
          matched = true;
        }
      });

      saveOrdersToStorage();
      scheduleOrdersRender();

      if (state.user && cancel.userId === state.user.userId) {
        fetchBalance();
        showBanner('success', `ORDER #${cancel.orderId} CANCELLED`, 'Engine confirmed order cancellation.');
      }

      logAudit('WS_CANCEL', 200, 0, `Cancel confirmed for Order #${cancel.orderId} (Trader #${cancel.userId})`);
    },

    /**
     * Called when CANCEL rejection arrives over WebSocket (already filled or not on book).
     */
    handleCancelRejected(reject) {
      state.orders.forEach(o => {
        if (o.orderId === reject.orderId) {
          // If cancel was rejected because it already filled in engine, mark FILLED!
          if (state.executedOrderIds.has(reject.orderId)) {
            o.status = 'FILLED';
          } else if (o.status === 'PENDING_CANCEL') {
            o.status = 'QUEUED';
          }
        }
      });

      saveOrdersToStorage();
      scheduleOrdersRender();

      if (state.user && reject.userId === state.user.userId) {
        showBanner('error', 'CANCEL REJECTED', `Order #${reject.orderId} could not be cancelled (already executed or missing from book).`);
      }

      logAudit('WS_CANCEL_REJECT', 409, 0, `Cancel rejected for Order #${reject.orderId} (Trader #${reject.userId})`);
    },

    /**
     * Reconciles local orders with Gateway's currently active orders.
     * NEVER marks an order CANCELLED unless explicitly cancelled.
     */
    reconcileWithServer(activeOrdersList) {
      if (!Array.isArray(activeOrdersList)) return;

      const serverMap = new Map();
      activeOrdersList.forEach(item => {
        if (item && item.orderId) {
          serverMap.set(item.orderId, item);
        }
      });

      // 1. Reconcile existing orders in local state
      state.orders.forEach(o => {
        if (serverMap.has(o.orderId)) {
          // Still active on Gateway ledger
          const s = serverMap.get(o.orderId);
          o.initialQuantity = s.initialQuantity;
          o.remainingQuantity = s.remainingQuantity;
          o.quantity = s.remainingQuantity;

          const initSats = Math.round(parseFloat(s.initialQuantity) * 100_000_000);
          const remSats = Math.round(parseFloat(s.remainingQuantity) * 100_000_000);

          if (s.pendingCancel) {
            o.status = 'PENDING_CANCEL';
          } else if (remSats < initSats && remSats > 0) {
            o.status = 'PARTIAL';
          } else {
            o.status = 'QUEUED';
          }
          serverMap.delete(o.orderId);
        } else {
          // No longer in active orders list!
          // If already FILLED or CANCELLED, preserve truth!
          if (o.status === 'FILLED' || o.status === 'CANCELLED') {
            return;
          }
          // If user had requested cancel and it vanished, mark CANCELLED
          if (o.status === 'PENDING_CANCEL') {
            o.status = 'CANCELLED';
            state.cancelledOrderIds.add(o.orderId);
          } else {
            // Otherwise, it was matched and completely filled by engine!
            o.status = 'FILLED';
            o.remainingQuantity = '0.00000000';
            state.executedOrderIds.add(o.orderId);
          }
        }
      });

      // 2. Add active orders from server that weren't in local state
      serverMap.forEach(s => {
        const initSats = Math.round(parseFloat(s.initialQuantity) * 100_000_000);
        const remSats = Math.round(parseFloat(s.remainingQuantity) * 100_000_000);
        let sStatus = 'QUEUED';
        if (s.pendingCancel) sStatus = 'PENDING_CANCEL';
        else if (remSats < initSats && remSats > 0) sStatus = 'PARTIAL';

        state.orders.unshift({
          orderId: s.orderId,
          userId: state.user?.userId,
          time: new Date(s.timestampNs ? Math.round(s.timestampNs / 1_000_000) : Date.now()).toLocaleTimeString(),
          side: s.side,
          orderType: 'LIMIT',
          price: s.price,
          initialQuantity: s.initialQuantity,
          remainingQuantity: s.remainingQuantity,
          quantity: s.remainingQuantity,
          status: sStatus
        });
      });

      saveOrdersToStorage();
      scheduleOrdersRender();
    }
  };

  // ==========================================================================
  // 7. WEBSOCKET STREAM MANAGER
  // ==========================================================================

  let socket = null;
  let socketReconnectTimer = null;
  let reconnectAttempts = 0;

  function connectWebSocket() {
    if (socket && (socket.readyState === WebSocket.OPEN || socket.readyState === WebSocket.CONNECTING)) {
      return;
    }

    if (socketReconnectTimer) {
      clearTimeout(socketReconnectTimer);
      socketReconnectTimer = null;
    }

    try {
      socket = new WebSocket(WS_URL);

      socket.onopen = () => {
        state.wsConnected = true;
        reconnectAttempts = 0;
        if (el.wsDot) el.wsDot.className = 'status-dot dot-online';
        if (el.wsStatusText) el.wsStatusText.textContent = 'WS STREAM: LIVE (BTC/USDT)';
        logAudit('WS_CONNECTED', 101, 0, `Subscribed to live market stream at ${WS_URL}`);
      };

      socket.onmessage = (event) => {
        try {
          const msg = JSON.parse(event.data);
          handleWsMessage(msg);
        } catch (err) {
          console.error('[WebSocket] Unparseable message frame:', err);
        }
      };

      socket.onclose = () => {
        state.wsConnected = false;
        if (el.wsDot) el.wsDot.className = 'status-dot dot-offline';
        if (el.wsStatusText) el.wsStatusText.textContent = 'WS STREAM: DISCONNECTED';
        scheduleWsReconnect();
      };

      socket.onerror = () => {
        state.wsConnected = false;
        if (el.wsDot) el.wsDot.className = 'status-dot dot-offline';
        if (el.wsStatusText) el.wsStatusText.textContent = 'WS STREAM: RETRYING...';
        try { socket.close(); } catch (e) {}
      };
    } catch {
      scheduleWsReconnect();
    }
  }

  function scheduleWsReconnect() {
    if (socketReconnectTimer) return;
    const backoffMs = Math.min(8000, 1500 * Math.pow(1.5, reconnectAttempts++));
    socketReconnectTimer = setTimeout(() => {
      socketReconnectTimer = null;
      if (state.token) {
        connectWebSocket();
      }
    }, backoffMs);
  }

  function handleWsMessage(msg) {
    if (!msg || !msg.type) return;

    if (msg.type === 'TRADE') {
      OrderFSM.handleExecution(msg);
    } else if (msg.type === 'CANCEL') {
      OrderFSM.handleCancelConfirmed(msg);
    } else if (msg.type === 'CANCEL_REJECTED') {
      OrderFSM.handleCancelRejected(msg);
    }
  }

  // ==========================================================================
  // 8. DUAL-ASSET LEDGER & BALANCE SYNCHRONIZER
  // ==========================================================================

  function formatUsdDisplay(val) {
    if (val === undefined || val === null || val === '---') return '---';
    const num = Number(val);
    return isNaN(num) ? '$0.00' : '$' + num.toLocaleString('en-US', { minimumFractionDigits: 2, maximumFractionDigits: 2 });
  }

  function formatBtcDisplay(val) {
    if (val === undefined || val === null || val === '---') return '---';
    const num = Number(val);
    return isNaN(num) ? '0.00000000 BTC' : num.toLocaleString('en-US', { minimumFractionDigits: 8, maximumFractionDigits: 8 }) + ' BTC';
  }

  function formatDisplayPrice(priceVal) {
    if (priceVal === 'MARKET') return 'MARKET';
    const num = Number(priceVal);
    return isNaN(num) ? String(priceVal) : num.toLocaleString('en-US', { minimumFractionDigits: 2, maximumFractionDigits: 2 });
  }

  async function fetchBalance() {
    if (!state.token) return;
    const res = await apiRequest('/api/users/balance');
    if (res.ok && res.data) {
      state.balance = res.data;
      updateBalanceUI();
    }
  }

  function updateBalanceUI() {
    if (!state.balance) return;

    const rawUsd = state.balance.availableUsd ?? state.balance.availableUsdt;
    const usdStr = formatUsdDisplay(rawUsd);
    const btcStr = formatBtcDisplay(state.balance.availableBtc);

    if (el.lpAvailableUsd) el.lpAvailableUsd.textContent = usdStr;
    if (el.lpAvailableBtc) el.lpAvailableBtc.textContent = btcStr;
    if (el.terminalBalanceUsd) el.terminalBalanceUsd.textContent = usdStr;
    if (el.terminalBalanceBtc) el.terminalBalanceBtc.textContent = btcStr;

    if (el.formAvailableMargin && el.formMarginLabel) {
      if (state.activeSide === 'BUY') {
        el.formMarginLabel.textContent = 'Required USD Margin:';
        el.formAvailableMargin.textContent = `Available: ${usdStr}`;
      } else {
        el.formMarginLabel.textContent = 'Required BTC Holding:';
        el.formAvailableMargin.textContent = `Available: ${btcStr}`;
      }
    }
  }

  async function syncActiveOrders() {
    if (!state.token || !state.user) return;
    const res = await apiRequest('/api/v1/orders');
    if (res.ok && Array.isArray(res.data)) {
      OrderFSM.reconcileWithServer(res.data);
    }
  }

  // ==========================================================================
  // 9. CARET-PRESERVING NUMERIC INPUT CONTROLLERS
  // ==========================================================================

  function handleNumericKeydown(e) {
    // 1. Allow navigation & edit keys
    if (['Backspace', 'Delete', 'Tab', 'Escape', 'Enter', 'ArrowLeft', 'ArrowRight', 'Home', 'End'].includes(e.key)) {
      return;
    }
    // 2. Allow clipboard / selection shortcuts
    if ((e.ctrlKey || e.metaKey) && ['a', 'c', 'v', 'x', 'z'].includes(e.key.toLowerCase())) {
      return;
    }
    // 3. Allow a single decimal point (block second dot)
    if (e.key === '.') {
      if (e.target.value.includes('.')) {
        e.preventDefault();
      }
      return;
    }
    // 4. Allow digits 0-9
    if (/^[0-9]$/.test(e.key)) {
      return;
    }
    // 5. Block everything else (including '-', '+', 'e', 'E', symbols, letters)
    e.preventDefault();
  }

  function handleNumericInput(e, maxDecimals) {
    const inputEl = e.target;
    const val = inputEl.value;
    if (!val) {
      updateNotional();
      return;
    }

    // Strip any pasted non-numeric characters (except digits and dot)
    let sanitized = val.replace(/[^0-9.]/g, '');

    // Ensure only one dot exists (in case of paste)
    const firstDot = sanitized.indexOf('.');
    if (firstDot !== -1) {
      const intPart = sanitized.slice(0, firstDot);
      let decPart = sanitized.slice(firstDot + 1).replace(/\./g, '');
      if (decPart.length > maxDecimals) {
        decPart = decPart.slice(0, maxDecimals);
      }
      sanitized = `${intPart}.${decPart}`;
    }

    // Only overwrite inputEl.value if it actually changed, preserving caret position
    if (inputEl.value !== sanitized) {
      inputEl.value = sanitized;
    }
    updateNotional();
  }

  el.inputPrice.addEventListener('keydown', handleNumericKeydown);
  el.inputQty.addEventListener('keydown', handleNumericKeydown);

  el.inputPrice.addEventListener('input', (e) => handleNumericInput(e, 2));
  el.inputQty.addEventListener('input', (e) => handleNumericInput(e, 8));

  // Steppers
  el.btnPriceMinus.addEventListener('click', () => {
    const cur = parseFloat(el.inputPrice.value) || 50000;
    el.inputPrice.value = Math.max(0.01, cur - 10).toFixed(2);
    updateNotional();
  });
  el.btnPricePlus.addEventListener('click', () => {
    const cur = parseFloat(el.inputPrice.value) || 50000;
    el.inputPrice.value = (cur + 10).toFixed(2);
    updateNotional();
  });
  el.btnQtyMinus.addEventListener('click', () => {
    const cur = parseFloat(el.inputQty.value) || 1;
    const step = cur <= 1 ? 0.1 : 1;
    el.inputQty.value = Math.max(0.00000001, parseFloat((cur - step).toFixed(8)));
    updateNotional();
  });
  el.btnQtyPlus.addEventListener('click', () => {
    const cur = parseFloat(el.inputQty.value) || 1;
    const step = cur < 1 ? 0.1 : 1;
    el.inputQty.value = parseFloat((cur + step).toFixed(8));
    updateNotional();
  });

  // Quick Presets
  document.querySelectorAll('.preset-tag, .btn-preset').forEach(btn => {
    btn.addEventListener('click', () => {
      if (btn.dataset.priceOffset !== undefined) {
        const offset = Number(btn.dataset.priceOffset);
        const base = state.lastTradedPrice || 50000;
        el.inputPrice.value = Math.max(0.01, base + offset).toFixed(2);
      }
      if (btn.dataset.qty !== undefined) {
        el.inputQty.value = btn.dataset.qty;
      }
      updateNotional();
    });
  });

  function updateNotional() {
    const qty = parseFloat(el.inputQty.value) || 0;
    const price = state.activeOrderType === 'LIMIT' 
      ? (parseFloat(el.inputPrice.value) || 0) 
      : (state.lastTradedPrice || 50000);
    const notional = qty * price;
    if (el.summaryNotional) {
      el.summaryNotional.textContent = `${notional.toLocaleString('en-US', { minimumFractionDigits: 2, maximumFractionDigits: 2 })} USDT`;
    }
  }

  function updateLtpDisplay(tradePrice, prevPrice) {
    if (!el.ltpDisplay) return;
    el.ltpDisplay.textContent = tradePrice.toLocaleString('en-US', { minimumFractionDigits: 2, maximumFractionDigits: 2 });
    el.ltpDisplay.classList.remove('ltp-flash-up', 'ltp-flash-down');
    if (tradePrice >= prevPrice) {
      el.ltpDisplay.classList.add('ltp-flash-up');
    } else {
      el.ltpDisplay.classList.add('ltp-flash-down');
    }
    setTimeout(() => {
      if (el.ltpDisplay) el.ltpDisplay.classList.remove('ltp-flash-up', 'ltp-flash-down');
    }, 500);
  }

  // ==========================================================================
  // 10. ORDER PLACEMENT, CANCELLATION & INGRESS TEST
  // ==========================================================================

  // Side & Type Toggles
  el.btnSideBuy.addEventListener('click', () => {
    state.activeSide = 'BUY';
    el.btnSideBuy.className = 'side-btn active-buy';
    el.btnSideSell.className = 'side-btn';
    el.btnSubmitOrder.className = 'btn btn-order-submit btn-buy-cta';
    updateSubmitButton();
    updateBalanceUI();
  });

  el.btnSideSell.addEventListener('click', () => {
    state.activeSide = 'SELL';
    el.btnSideSell.className = 'side-btn active-sell';
    el.btnSideBuy.className = 'side-btn';
    el.btnSubmitOrder.className = 'btn btn-order-submit btn-sell-cta';
    updateSubmitButton();
    updateBalanceUI();
  });

  el.btnTypeLimit.addEventListener('click', () => {
    state.activeOrderType = 'LIMIT';
    el.btnTypeLimit.className = 'type-btn active';
    el.btnTypeMarket.className = 'type-btn';
    el.inputPrice.disabled = false;
    el.groupPrice.style.opacity = '1';
    updateSubmitButton();
    updateNotional();
  });

  el.btnTypeMarket.addEventListener('click', () => {
    state.activeOrderType = 'MARKET';
    el.btnTypeMarket.className = 'type-btn active';
    el.btnTypeLimit.className = 'type-btn';
    el.inputPrice.disabled = true;
    el.groupPrice.style.opacity = '0.4';
    updateSubmitButton();
    updateNotional();
  });

  function updateSubmitButton() {
    el.btnSubmitOrder.textContent = `Submit ${state.activeSide} ${state.activeOrderType} Order`;
  }

  let bannerTimeout = null;
  function showBanner(type, title, desc) {
    if (bannerTimeout) {
      clearTimeout(bannerTimeout);
      bannerTimeout = null;
    }
    el.statusBanner.className = `status-banner banner-${type}`;
    el.bannerTitle.textContent = title;
    el.bannerDesc.textContent = desc;
    el.statusBanner.classList.remove('hidden');

    bannerTimeout = setTimeout(() => {
      el.statusBanner.classList.add('hidden');
      bannerTimeout = null;
    }, 6000);
  }

  // Order Submit Form
  el.orderForm.addEventListener('submit', async (e) => {
    e.preventDefault();
    if (!state.token || !state.user) {
      showBanner('error', 'UNAUTHENTICATED', 'You must sign in to place orders.');
      return;
    }

    const qtyStr = el.inputQty.value.trim();
    const priceStr = state.activeOrderType === 'LIMIT' 
      ? el.inputPrice.value.trim() 
      : (state.lastTradedPrice || 50000).toFixed(2);

    const qtyNum = Number(qtyStr);
    const priceNum = Number(priceStr);

    if (!qtyStr || isNaN(qtyNum) || qtyNum <= 0) {
      showBanner('error', 'INVALID QUANTITY', 'Quantity must be greater than zero.');
      return;
    }
    if (state.activeOrderType === 'LIMIT' && (!priceStr || isNaN(priceNum) || priceNum <= 0)) {
      showBanner('error', 'INVALID PRICE', 'Price must be greater than zero for LIMIT orders.');
      return;
    }

    // Precision enforcement
    if (state.activeOrderType === 'LIMIT' && priceStr.includes('.')) {
      if (priceStr.split('.')[1].length > 2) {
        showBanner('error', 'INVALID PRICE PRECISION', 'Price cannot exceed 2 decimal places (USDT cents).');
        return;
      }
    }
    if (qtyStr.includes('.') && qtyStr.split('.')[1].length > 8) {
      showBanner('error', 'INVALID QUANTITY PRECISION', 'Quantity cannot exceed 8 decimal places (Satoshis).');
      return;
    }

    const payload = {
      side: state.activeSide,
      price: priceStr,
      quantity: qtyStr,
      orderType: state.activeOrderType
    };

    el.btnSubmitOrder.disabled = true;

    const res = await apiRequest('/api/v1/orders', {
      method: 'POST',
      body: JSON.stringify(payload)
    });

    el.btnSubmitOrder.disabled = false;

    if (res.ok && res.data && res.data.status === 'ACCEPTED') {
      showBanner(
        'success',
        `ORDER ACCEPTED BY INGRESS DISRUPTOR (#${res.data.orderId})`,
        `Ingress Timestamp: ${res.data.timestampNs} ns | Roundtrip: ${res.elapsedMs} ms`
      );

      // Ingest into FSM (handles out-of-order execution seamlessly)
      OrderFSM.handleIngressAccepted(
        res.data.orderId,
        state.activeSide,
        state.activeOrderType,
        state.activeOrderType === 'LIMIT' ? priceStr : 'MARKET',
        qtyStr,
        res.data.timestampNs
      );

      logAudit(
        'ORDER_ACCEPTED',
        res.status,
        res.elapsedMs,
        `${state.activeSide} ${state.activeOrderType} #${res.data.orderId} - ${qtyStr} BTC @ ${priceStr} USDT`
      );
      fetchBalance();
    } else {
      const errMsg = res.data?.message || res.data?.error || res.error || 'Order rejected by Gateway.';
      showBanner('error', 'ORDER REJECTED BY GATEWAY', errMsg);
      logAudit('ORDER_REJECTED', res.status, res.elapsedMs, errMsg);
      fetchBalance();
    }
  });

  // Cancel Order
  async function cancelOrder(orderId) {
    if (!state.token) return;

    // Immediately reflect pending state in UI
    const target = state.orders.find(o => o.orderId === orderId);
    if (target) {
      target.status = 'PENDING_CANCEL';
      saveOrdersToStorage();
      scheduleOrdersRender();
    }

    const res = await apiRequest(`/api/v1/orders/${orderId}`, {
      method: 'DELETE'
    });

    if (res.ok && res.data && res.data.status === 'ACCEPTED') {
      showBanner('success', `CANCEL DISPATCHED (#${orderId})`, `Cancel forwarded to matching engine.`);
      logAudit('CANCEL_DISPATCHED', res.status, res.elapsedMs, `Cancel order #${orderId} sent to engine`);
    } else {
      const errMsg = res.data?.message || res.data?.error || `Unable to cancel #${orderId}`;
      if (res.status === 409) {
        showBanner('error', 'CANCEL ALREADY PENDING', errMsg);
      } else {
        showBanner('error', 'CANCEL FAILED', errMsg);
        if (target && target.status === 'PENDING_CANCEL') {
          // If cancel failed because already filled, mark FILLED!
          if (state.executedOrderIds.has(orderId)) {
            target.status = 'FILLED';
          } else {
            target.status = 'QUEUED';
          }
          saveOrdersToStorage();
          scheduleOrdersRender();
        }
      }
      logAudit('CANCEL_FAILED', res.status, res.elapsedMs, errMsg);
    }
  }

  // Cross Test Match Helper
  el.btnDemoCross.addEventListener('click', async () => {
    if (!state.token || !state.user) {
      showBanner('error', 'UNAUTHENTICATED', 'Sign in first to run cross test.');
      return;
    }

    el.btnDemoCross.disabled = true;
    el.btnDemoCross.textContent = '⏳ Injecting Buy & Sell Orders...';

    const testPrice = "50000.00";
    const testQty = "1.00000000";

    // 1. Post BUY
    const buyRes = await apiRequest('/api/v1/orders', {
      method: 'POST',
      body: JSON.stringify({
        side: 'BUY',
        price: testPrice,
        quantity: testQty,
        orderType: 'LIMIT'
      })
    });

    if (buyRes.ok && buyRes.data) {
      OrderFSM.handleIngressAccepted(
        buyRes.data.orderId,
        'BUY',
        'LIMIT',
        testPrice,
        testQty,
        buyRes.data.timestampNs
      );
      logAudit('CROSS_TEST_BUY', buyRes.status, buyRes.elapsedMs, `BUY #${buyRes.data.orderId} @ ${testPrice}`);
    }

    // Small yield to let event loop tick
    await new Promise(r => setTimeout(r, 60));

    // 2. Post SELL
    const sellRes = await apiRequest('/api/v1/orders', {
      method: 'POST',
      body: JSON.stringify({
        side: 'SELL',
        price: testPrice,
        quantity: testQty,
        orderType: 'LIMIT'
      })
    });

    if (sellRes.ok && sellRes.data) {
      OrderFSM.handleIngressAccepted(
        sellRes.data.orderId,
        'SELL',
        'LIMIT',
        testPrice,
        testQty,
        sellRes.data.timestampNs
      );
      logAudit('CROSS_TEST_SELL', sellRes.status, sellRes.elapsedMs, `SELL #${sellRes.data.orderId} @ ${testPrice}`);
    }

    fetchBalance();

    showBanner('success', 'CROSS TEST INGESTED', 'Injected BUY and SELL order pair into Disruptor RingBuffer.');
    el.btnDemoCross.disabled = false;
    el.btnDemoCross.textContent = '⚡ Fire Cross Test (Buy @ 50000 + Sell @ 50000)';
  });

  el.btnClearOrders.addEventListener('click', () => {
    state.orders = state.orders.filter(o => o.status !== 'CANCELLED' && o.status !== 'FILLED');
    saveOrdersToStorage();
    scheduleOrdersRender();
  });

  el.btnClearAudit.addEventListener('click', () => {
    state.auditLogs = [];
    scheduleAuditRender();
  });

  // ==========================================================================
  // 11. AUTHENTICATION & SESSION LIFECYCLE
  // ==========================================================================

  function setAuthAlert(type, msg) {
    if (!el.authAlert) return;
    el.authAlert.className = `alert-banner alert-${type}`;
    el.authAlert.textContent = msg;
    el.authAlert.classList.remove('hidden');
  }

  function clearAuthAlert() {
    if (!el.authAlert) return;
    el.authAlert.className = 'alert-banner hidden';
    el.authAlert.textContent = '';
  }

  el.tabLogin.addEventListener('click', () => {
    el.tabLogin.classList.add('active');
    el.tabRegister.classList.remove('active');
    el.formLogin.classList.remove('hidden');
    el.formRegister.classList.add('hidden');
    clearAuthAlert();
  });

  el.tabRegister.addEventListener('click', () => {
    el.tabRegister.classList.add('active');
    el.tabLogin.classList.remove('active');
    el.formRegister.classList.remove('hidden');
    el.formLogin.classList.add('hidden');
    clearAuthAlert();
  });

  // Login Form
  el.formLogin.addEventListener('submit', async (e) => {
    e.preventDefault();
    clearAuthAlert();

    const email = el.loginEmail.value.trim();
    const password = el.loginPassword.value;

    if (!email || !password) {
      setAuthAlert('error', 'Please enter both email and password.');
      return;
    }

    el.btnLoginSubmit.disabled = true;
    el.btnLoginSubmit.textContent = 'Authenticating...';

    const res = await apiRequest('/api/auth/login', {
      method: 'POST',
      body: JSON.stringify({ email, password }),
      skipAuth: true
    });

    el.btnLoginSubmit.disabled = false;
    el.btnLoginSubmit.textContent = 'Sign In to Gateway';

    if (res.ok && res.data && res.data.token) {
      state.token = res.data.token;
      state.user = {
        userId: res.data.userId,
        email: res.data.email,
        role: 'ROLE_TRADER'
      };

      sessionStorage.setItem(STORAGE_TOKEN_KEY, state.token);
      sessionStorage.setItem(STORAGE_USER_KEY, JSON.stringify(state.user));

      state.orders = loadOrdersFromStorage();
      state.orders.forEach(o => {
        if (o.status === 'FILLED') state.executedOrderIds.add(o.orderId);
        if (o.status === 'CANCELLED') state.cancelledOrderIds.add(o.orderId);
      });

      logAudit('LOGIN_SUCCESS', res.status, res.elapsedMs, `Authenticated as #${state.user.userId} (${email})`);

      hydrateProfile();
      updateAuthUI();
    } else {
      const errMsg = res.data?.message || res.data?.error || 'Invalid email or password.';
      setAuthAlert('error', `Login Failed: ${errMsg}`);
      logAudit('LOGIN_FAILED', res.status, res.elapsedMs, errMsg);
    }
  });

  // Register Form
  el.formRegister.addEventListener('submit', async (e) => {
    e.preventDefault();
    clearAuthAlert();

    const email = el.regEmail.value.trim();
    const password = el.regPassword.value;

    if (!email || !password) {
      setAuthAlert('error', 'Please fill in all registration fields.');
      return;
    }
    if (password.length < 6) {
      setAuthAlert('error', 'Password must be at least 6 characters.');
      return;
    }

    el.btnRegSubmit.disabled = true;
    el.btnRegSubmit.textContent = 'Creating Account...';

    const res = await apiRequest('/api/auth/register', {
      method: 'POST',
      body: JSON.stringify({ email, password }),
      skipAuth: true
    });

    el.btnRegSubmit.disabled = false;
    el.btnRegSubmit.textContent = 'Create Account';

    if (res.ok && res.data) {
      logAudit('REGISTER_SUCCESS', res.status, res.elapsedMs, `Account created for ${email} (ID: #${res.data.userId})`);
      setAuthAlert('success', `Account #${res.data.userId} created! Please sign in with your credentials.`);

      el.loginEmail.value = email;
      el.loginPassword.value = '';
      el.tabLogin.click();
    } else {
      const errMsg = res.data?.message || res.data?.error || 'Registration failed.';
      setAuthAlert('error', `Registration Error: ${errMsg}`);
      logAudit('REGISTER_FAILED', res.status, res.elapsedMs, errMsg);
    }
  });

  async function hydrateProfile() {
    if (!state.token) return;
    const res = await apiRequest('/api/users/me');
    if (res.ok && res.data) {
      state.user = {
        ...state.user,
        userId: res.data.userId,
        email: res.data.email
      };
      sessionStorage.setItem(STORAGE_USER_KEY, JSON.stringify(state.user));
      updateAuthUI();
    }
  }

  function handleLogout() {
    state.token = null;
    state.user = null;
    state.balance = null;
    state.orders = [];
    state.executedOrderIds.clear();
    state.cancelledOrderIds.clear();

    sessionStorage.removeItem(STORAGE_TOKEN_KEY);
    sessionStorage.removeItem(STORAGE_USER_KEY);

    if (socket) {
      try { socket.close(); } catch (e) {}
      socket = null;
    }

    updateAuthUI();
    scheduleOrdersRender();

    el.terminalView.classList.add('hidden');
    el.welcomeView.classList.remove('hidden');
  }

  el.btnLogout.addEventListener('click', handleLogout);
  el.btnTerminalLogout.addEventListener('click', handleLogout);

  // Upgrade to Terminal
  el.btnUpgradeTerminal.addEventListener('click', () => {
    el.welcomeView.classList.add('hidden');
    el.terminalView.classList.remove('hidden');
    fetchBalance();
    syncActiveOrders();
    scheduleOrdersRender();
    connectWebSocket();
    logAudit('UPGRADE_TERMINAL', 200, 0, 'Entered Trading Terminal. WebSocket stream connected.');
  });

  el.btnBackLaunchpad.addEventListener('click', () => {
    el.terminalView.classList.add('hidden');
    el.welcomeView.classList.remove('hidden');
  });

  function updateAuthUI() {
    if (state.token && state.user) {
      el.authContainer.classList.add('hidden');
      el.launchpadContainer.classList.remove('hidden');

      if (el.lpUserEmail) el.lpUserEmail.textContent = state.user.email;
      if (el.lpUserId) el.lpUserId.textContent = `#${state.user.userId}`;
      if (el.lpTokenSnippet) {
        el.lpTokenSnippet.textContent = `${state.token.slice(0, 24)}...${state.token.slice(-12)}`;
      }

      if (el.terminalUserEmail) el.terminalUserEmail.textContent = state.user.email;
      if (el.terminalUserId) el.terminalUserId.textContent = `#${state.user.userId}`;

      fetchBalance();
      syncActiveOrders();
      scheduleOrdersRender();
    } else {
      el.authContainer.classList.remove('hidden');
      el.launchpadContainer.classList.add('hidden');
    }
  }

  // ==========================================================================
  // 12. GATEWAY HEALTH POLLER & TARGET SWITCHER
  // ==========================================================================

  async function checkGatewayHealth() {
    const res = await apiRequest('/api/health', { skipAuth: true });
    if (res.ok) {
      state.gatewayOnline = true;
      if (el.gatewayDot) el.gatewayDot.className = 'status-dot dot-online';
      if (el.gatewayStatusText) el.gatewayStatusText.textContent = `Gateway Online (${REST_BASE_URL}) [${res.elapsedMs}ms]`;
    } else {
      state.gatewayOnline = false;
      if (el.gatewayDot) el.gatewayDot.className = 'status-dot dot-offline';
      if (el.gatewayStatusText) el.gatewayStatusText.textContent = `Gateway Offline - Connection Refused (${REST_BASE_URL})`;
    }
  }

  if (el.btnToggleEnv) {
    el.btnToggleEnv.textContent = `🎯 Target: ${API_HOST}`;
    el.btnToggleEnv.title = 'Click to switch or configure target backend host:port';
    el.btnToggleEnv.addEventListener('click', () => {
      const customHost = prompt(
        `Enter backend host:port (current: ${API_HOST})\nLeave blank to reset to auto-detect:`,
        API_HOST
      );
      if (customHost === null) return;
      if (customHost.trim() === '') {
        localStorage.removeItem('apextrade_target_host');
      } else {
        localStorage.setItem('apextrade_target_host', customHost.trim());
      }
      window.location.reload();
    });
  }

  checkGatewayHealth();
  setInterval(checkGatewayHealth, 10000);

  // Initialize Terminal View if already authenticated
  updateAuthUI();
  updateNotional();
  updateSubmitButton();

  if (state.token && !el.welcomeView.classList.contains('hidden') && !el.terminalView.classList.contains('hidden')) {
    connectWebSocket();
  }
})();
