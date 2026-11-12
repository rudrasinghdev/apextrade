/**
 * ApexTrade Core - High-Performance Trading Terminal Client
 * REST Ingress & Real-Time WebSocket Execution Stream
 */

(() => {
  'use strict';

  // --- Configuration ---
  const DEFAULT_PORT = 8080;
  const ORACLE_CLOUD_HOST = '152.67.4.206:8080';
  let savedHost = localStorage.getItem('apextrade_target_host');

  // Purge any stale legacy Render host cache
  if (savedHost && savedHost.includes('onrender.com')) {
    localStorage.removeItem('apextrade_target_host');
    savedHost = null;
  }

  // Intelligent host auto-detection:
  // 1. If explicitly configured via prompt / localStorage, use it.
  // 2. If loaded in browser from a server (e.g. Oracle Cloud IP), dynamically bind to the current origin host.
  // 3. If opened locally from disk (file:///), connect directly to the live Oracle Cloud server!
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

  // --- Persistent Storage Keys ---
  const STORAGE_TOKEN_KEY = 'apextrade_jwt';
  const STORAGE_USER_KEY = 'apextrade_user';
  const STORAGE_ORDERS_KEY = 'apextrade_active_orders';

  function getOrdersStorageKey() {
    return state.user && state.user.userId ? `${STORAGE_ORDERS_KEY}_${state.user.userId}` : STORAGE_ORDERS_KEY;
  }

  // --- Application State ---
  const state = {
    token: sessionStorage.getItem(STORAGE_TOKEN_KEY) || null,
    user: JSON.parse(sessionStorage.getItem(STORAGE_USER_KEY) || 'null'),
    balance: null,
    gatewayOnline: false,
    activeSide: 'BUY',
    activeOrderType: 'LIMIT',
    myOrders: [],
    auditLogs: [],
    lastTradedPrice: 50000,
    wsConnected: false
  };
  state.myOrders = loadOrdersFromStorage();

  // --- DOM Elements ---
  const elements = {
    // Top Health Strip
    gatewayDot: document.getElementById('gateway-dot'),
    gatewayStatusText: document.getElementById('gateway-status-text'),
    btnToggleEnv: document.getElementById('btn-toggle-env'),

    // Views
    welcomeView: document.getElementById('welcome-view'),
    terminalView: document.getElementById('terminal-view'),

    // Auth Container
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

    // Launchpad Container
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

    // DMA Order Form
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

    // Steppers & Presets
    btnPriceMinus: document.getElementById('btn-price-minus'),
    btnPricePlus: document.getElementById('btn-price-plus'),
    btnQtyMinus: document.getElementById('btn-qty-minus'),
    btnQtyPlus: document.getElementById('btn-qty-plus'),

    // My Orders Table
    myOrdersTbody: document.getElementById('my-orders-tbody'),
    myOrdersEmpty: document.getElementById('my-orders-empty'),
    myOrdersCount: document.getElementById('my-orders-count'),
    btnClearOrders: document.getElementById('btn-clear-orders'),

    // Audit Trail Table
    auditTbody: document.getElementById('audit-tbody'),
    auditEmpty: document.getElementById('audit-empty'),
    auditPulse: document.getElementById('audit-pulse'),
    btnClearAudit: document.getElementById('btn-clear-audit')
  };

  // ==========================================================================
  // HTTP Client (Zero-Dependency Async Fetch with Bearer Auth)
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

    const startTime = performance.now();
    try {
      const response = await fetch(url, {
        ...options,
        headers
      });

      const elapsedMs = Math.round(performance.now() - startTime);
      elements.latencyDisplay.textContent = `${elapsedMs} ms`;

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
      const elapsedMs = Math.round(performance.now() - startTime);
      return {
        ok: false,
        status: 0,
        error: err.message,
        elapsedMs
      };
    }
  }

  // ==========================================================================
  // Gateway Health Poller
  // ==========================================================================

  async function checkGatewayHealth() {
    const res = await apiRequest('/api/health', { skipAuth: true });
    if (res.ok) {
      state.gatewayOnline = true;
      elements.gatewayDot.className = 'status-dot dot-online';
      elements.gatewayStatusText.textContent = `Gateway Online (${REST_BASE_URL}) [${res.elapsedMs}ms]`;
    } else {
      state.gatewayOnline = false;
      elements.gatewayDot.className = 'status-dot dot-offline';
      elements.gatewayStatusText.textContent = `Gateway Offline - Connection Refused (${REST_BASE_URL})`;
    }
  }

  // Target Host Switcher
  function updateToggleEnvButton() {
    if (elements.btnToggleEnv) {
      elements.btnToggleEnv.textContent = `🎯 Target: ${API_HOST}`;
      elements.btnToggleEnv.title = 'Click to switch or configure target backend host:port';
    }
  }

  if (elements.btnToggleEnv) {
    updateToggleEnvButton();
    elements.btnToggleEnv.addEventListener('click', () => {
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

  // Poll health immediately and every 10 seconds
  checkGatewayHealth();
  setInterval(checkGatewayHealth, 10000);

  // ==========================================================================
  // Dual-Asset Risk Ledger Synchronization
  // ==========================================================================

  function formatUsd(amount) {
    if (amount === undefined || amount === null) return '$0.00';
    return '$' + (amount / 100).toLocaleString('en-US', { minimumFractionDigits: 2, maximumFractionDigits: 2 });
  }

  function formatBtc(amount) {
    if (amount === undefined || amount === null) return '0.00000000 BTC';
    return (amount / 100_000_000).toLocaleString('en-US', { minimumFractionDigits: 8, maximumFractionDigits: 8 }) + ' BTC';
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

    const usdStr = formatUsd(state.balance.availableUsd);
    const btcStr = formatBtc(state.balance.availableBtc);

    if (elements.lpAvailableUsd) elements.lpAvailableUsd.textContent = usdStr;
    if (elements.lpAvailableBtc) elements.lpAvailableBtc.textContent = btcStr;
    if (elements.terminalBalanceUsd) elements.terminalBalanceUsd.textContent = usdStr;
    if (elements.terminalBalanceBtc) elements.terminalBalanceBtc.textContent = btcStr;

    if (elements.formAvailableMargin && elements.formMarginLabel) {
      if (state.activeSide === 'BUY') {
        elements.formMarginLabel.textContent = 'Required USD Margin:';
        elements.formAvailableMargin.textContent = `Available: ${usdStr}`;
      } else {
        elements.formMarginLabel.textContent = 'Required BTC Holding:';
        elements.formAvailableMargin.textContent = `Available: ${btcStr}`;
      }
    }
  }

  // ==========================================================================
  // Auth & Session Management
  // ==========================================================================

  function updateAuthUI() {
    if (state.token && state.user) {
      state.myOrders = loadOrdersFromStorage();
      renderMyOrders();
      fetchBalance();
      syncActiveOrders();
      connectWebSocket();

      // User is logged in -> show Launchpad, hide Auth Box
      elements.authContainer.classList.add('hidden');
      elements.launchpadContainer.classList.remove('hidden');

      elements.lpUserEmail.textContent = state.user.email;
      elements.lpUserId.textContent = `#${state.user.userId}`;
      elements.lpUserRole.textContent = state.user.role || 'ROLE_TRADER';
      elements.lpTokenSnippet.textContent = `${state.token.substring(0, 24)}...`;

      // Update terminal navbar chips
      elements.terminalUserEmail.textContent = state.user.email;
      elements.terminalUserId.textContent = `#${state.user.userId}`;
    } else {
      if (socket) {
        try { socket.close(); } catch(e){}
        socket = null;
      }
      state.myOrders = [];
      renderMyOrders();

      // User is logged out -> show Auth Box, hide Launchpad
      elements.launchpadContainer.classList.add('hidden');
      elements.authContainer.classList.remove('hidden');
      elements.terminalView.classList.add('hidden');
      elements.welcomeView.classList.remove('hidden');
    }
  }

  function setAuthAlert(type, message) {
    elements.authAlert.className = `alert-banner alert-${type}`;
    elements.authAlert.textContent = message;
    elements.authAlert.classList.remove('hidden');
  }

  function clearAuthAlert() {
    elements.authAlert.classList.add('hidden');
    elements.authAlert.textContent = '';
  }

  // Auth Tab Switchers
  elements.tabLogin.addEventListener('click', () => {
    elements.tabLogin.classList.add('active');
    elements.tabRegister.classList.remove('active');
    elements.formLogin.classList.remove('hidden');
    elements.formRegister.classList.add('hidden');
    clearAuthAlert();
  });

  elements.tabRegister.addEventListener('click', () => {
    elements.tabRegister.classList.add('active');
    elements.tabLogin.classList.remove('active');
    elements.formRegister.classList.remove('hidden');
    elements.formLogin.classList.add('hidden');
    clearAuthAlert();
  });

  // Login Form Submission
  elements.formLogin.addEventListener('submit', async (e) => {
    e.preventDefault();
    clearAuthAlert();

    const email = elements.loginEmail.value.trim();
    const password = elements.loginPassword.value;

    if (!email || !password) {
      setAuthAlert('error', 'Please enter both email and password.');
      return;
    }

    elements.btnLoginSubmit.disabled = true;
    elements.btnLoginSubmit.textContent = 'Authenticating...';

    const res = await apiRequest('/api/auth/login', {
      method: 'POST',
      body: JSON.stringify({ email, password }),
      skipAuth: true
    });

    elements.btnLoginSubmit.disabled = false;
    elements.btnLoginSubmit.textContent = 'Sign In to Gateway';

    if (res.ok && res.data && res.data.token) {
      state.token = res.data.token;
      state.user = {
        userId: res.data.userId,
        email: res.data.email,
        role: 'ROLE_TRADER'
      };

      sessionStorage.setItem(STORAGE_TOKEN_KEY, state.token);
      sessionStorage.setItem(STORAGE_USER_KEY, JSON.stringify(state.user));

      logAudit('LOGIN_SUCCESS', res.status, res.elapsedMs, `Authenticated as #${state.user.userId} (${email})`);

      // Hydrate full profile from /api/users/me in background
      hydrateProfile();

      updateAuthUI();
    } else {
      const errMsg = res.data?.message || res.data?.error || 'Invalid email or password.';
      setAuthAlert('error', `Login Failed: ${errMsg}`);
      logAudit('LOGIN_FAILED', res.status, res.elapsedMs, errMsg);
    }
  });

  // Register Form Submission
  elements.formRegister.addEventListener('submit', async (e) => {
    e.preventDefault();
    clearAuthAlert();

    const email = elements.regEmail.value.trim();
    const password = elements.regPassword.value;

    if (!email || !password) {
      setAuthAlert('error', 'Please fill in all registration fields.');
      return;
    }

    if (password.length < 6) {
      setAuthAlert('error', 'Password must be at least 6 characters.');
      return;
    }

    elements.btnRegSubmit.disabled = true;
    elements.btnRegSubmit.textContent = 'Creating Account...';

    const res = await apiRequest('/api/auth/register', {
      method: 'POST',
      body: JSON.stringify({ email, password }),
      skipAuth: true
    });

    elements.btnRegSubmit.disabled = false;
    elements.btnRegSubmit.textContent = 'Register Account';

    if (res.ok && res.data) {
      logAudit('REGISTER_SUCCESS', res.status, res.elapsedMs, `Account created for ${email} (ID: #${res.data.userId})`);
      setAuthAlert('success', `Account #${res.data.userId} created! Please sign in with your credentials.`);

      // Pre-fill login email and switch to login tab
      elements.loginEmail.value = email;
      elements.loginPassword.value = '';
      elements.tabLogin.click();
    } else {
      const errMsg = res.data?.message || res.data?.error || 'Registration failed.';
      setAuthAlert('error', `Registration Error: ${errMsg}`);
      logAudit('REGISTER_FAILED', res.status, res.elapsedMs, errMsg);
    }
  });

  // Hydrate user profile from /api/users/me
  async function hydrateProfile() {
    if (!state.token) return;
    const res = await apiRequest('/api/users/me');
    if (res.ok && res.data) {
      state.user = {
        ...state.user,
        userId: res.data.userId,
        email: res.data.email,
        role: res.data.role
      };
      sessionStorage.setItem(STORAGE_USER_KEY, JSON.stringify(state.user));
      elements.lpUserRole.textContent = res.data.role || 'ROLE_TRADER';
      elements.lpUserId.textContent = `#${res.data.userId}`;
    }
    await fetchBalance();
  }

  // Logout Handlers
  function handleLogout() {
    if (socket) {
      try { socket.close(); } catch (e) {}
      socket = null;
    }
    state.token = null;
    state.user = null;
    state.balance = null;
    state.myOrders = [];
    sessionStorage.removeItem(STORAGE_TOKEN_KEY);
    sessionStorage.removeItem(STORAGE_USER_KEY);
    logAudit('LOGOUT', 200, 0, 'User logged out and session cleared.');
    updateAuthUI();
  }

  // ==========================================================================
  // Real-Time WebSocket Market & Execution Stream (/ws/trades)
  // ==========================================================================
  let socket = null;
  let socketReconnectTimer = null;

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
        if (elements.wsDot) {
          elements.wsDot.className = 'status-dot dot-online';
        }
        if (elements.wsStatusText) {
          elements.wsStatusText.textContent = 'WS STREAM: LIVE (BTC/USDT)';
        }
        logAudit('WS_CONNECTED', 101, 0, `Subscribed to live market stream at ${WS_URL}`);
      };

      socket.onmessage = (event) => {
        try {
          const msg = JSON.parse(event.data);
          handleWsMessage(msg);
        } catch (err) {
          console.error('[WebSocket] Failed to parse message:', err);
        }
      };

      socket.onclose = () => {
        state.wsConnected = false;
        if (elements.wsDot) {
          elements.wsDot.className = 'status-dot dot-offline';
        }
        if (elements.wsStatusText) {
          elements.wsStatusText.textContent = 'WS STREAM: DISCONNECTED';
        }
        scheduleWsReconnect();
      };

      socket.onerror = () => {
        state.wsConnected = false;
        if (elements.wsDot) {
          elements.wsDot.className = 'status-dot dot-offline';
        }
        if (elements.wsStatusText) {
          elements.wsStatusText.textContent = 'WS STREAM: RETRYING...';
        }
        try { socket.close(); } catch (e) {}
      };
    } catch (e) {
      scheduleWsReconnect();
    }
  }

  function scheduleWsReconnect() {
    if (socketReconnectTimer) return;
    socketReconnectTimer = setTimeout(() => {
      socketReconnectTimer = null;
      if (state.token) {
        connectWebSocket();
      }
    }, 2000);
  }

  function handleWsMessage(msg) {
    if (!msg || !msg.type) return;

    if (msg.type === 'TRADE') {
      handleWsTrade(msg);
    } else if (msg.type === 'CANCEL') {
      handleWsCancel(msg);
    } else if (msg.type === 'CANCEL_REJECTED') {
      handleWsCancelRejected(msg);
    }
  }

  function handleWsTrade(trade) {
    const tradePrice = Number(trade.price);
    const prevPrice = state.lastTradedPrice || tradePrice;
    state.lastTradedPrice = tradePrice;

    // 1. Update Reference Price / LTP in navbar with flash effect
    if (elements.ltpDisplay) {
      elements.ltpDisplay.textContent = tradePrice.toLocaleString('en-US', { minimumFractionDigits: 2 });
      elements.ltpDisplay.classList.remove('ltp-flash-up', 'ltp-flash-down');
      if (tradePrice >= prevPrice) {
        elements.ltpDisplay.classList.add('ltp-flash-up');
      } else {
        elements.ltpDisplay.classList.add('ltp-flash-down');
      }
      setTimeout(() => {
        if (elements.ltpDisplay) {
          elements.ltpDisplay.classList.remove('ltp-flash-up', 'ltp-flash-down');
        }
      }, 500);
    }

    // 2. Check if current user is buyer or seller
    const currentUserId = state.user?.userId;
    const isMyBuyer = trade.buyerId === currentUserId;
    const isMySeller = trade.sellerId === currentUserId;

    if (isMyBuyer || isMySeller) {
      let matched = false;
      state.myOrders.forEach(o => {
        if (o.orderId === trade.buyerOrderId || o.orderId === trade.sellerOrderId) {
          o.status = 'FILLED';
          matched = true;
        }
      });

      if (matched) {
        saveOrdersToStorage();
        renderMyOrders();
      }
      fetchBalance();

      showBanner(
        'success',
        `MATCH EXECUTED (Trade #${trade.tradeId})`,
        `Filled ${trade.quantity} BTC @ ${tradePrice.toLocaleString()} USD | Buyer #${trade.buyerId} x Seller #${trade.sellerId}`
      );
    }

    logAudit(
      'TRADE_EXEC',
      200,
      0,
      `Trade #${trade.tradeId}: ${trade.quantity} BTC @ ${tradePrice.toLocaleString()} USD (B:#${trade.buyerId} S:#${trade.sellerId})`
    );
  }

  function handleWsCancel(cancel) {
    let matched = false;
    state.myOrders.forEach(o => {
      if (o.orderId === cancel.orderId) {
        o.status = 'CANCELLED';
        matched = true;
      }
    });

    if (matched) {
      saveOrdersToStorage();
      renderMyOrders();
    }

    if (state.user && cancel.userId === state.user.userId) {
      fetchBalance();
      showBanner('success', `ORDER #${cancel.orderId} CANCELLED`, 'Engine confirmed order cancellation.');
    }

    logAudit(
      'WS_CANCEL',
      200,
      0,
      `Cancel confirmed for Order #${cancel.orderId} (Trader #${cancel.userId})`
    );
  }

  function handleWsCancelRejected(reject) {
    let matched = false;
    state.myOrders.forEach(o => {
      if (o.orderId === reject.orderId) {
        if (o.status === 'PENDING_CANCEL') {
          o.status = 'QUEUED';
        }
        matched = true;
      }
    });

    if (matched) {
      saveOrdersToStorage();
      renderMyOrders();
    }

    if (state.user && reject.userId === state.user.userId) {
      showBanner('error', 'CANCEL REJECTED', `Order #${reject.orderId} could not be cancelled (already executed or missing from book).`);
    }

    logAudit(
      'WS_CANCEL_REJECT',
      409,
      0,
      `Cancel rejected for Order #${reject.orderId} (Trader #${reject.userId})`
    );
  }

  elements.btnLogout.addEventListener('click', handleLogout);
  elements.btnTerminalLogout.addEventListener('click', handleLogout);

  // Upgrade to Terminal (WebSocket Live Stream)
  elements.btnUpgradeTerminal.addEventListener('click', () => {
    elements.welcomeView.classList.add('hidden');
    elements.terminalView.classList.remove('hidden');
    fetchBalance();
    syncActiveOrders();
    renderMyOrders();
    connectWebSocket();
    logAudit('UPGRADE_TERMINAL', 200, 0, 'Entered Trading Terminal. WebSocket stream connected.');
  });

  elements.btnBackLaunchpad.addEventListener('click', () => {
    elements.terminalView.classList.add('hidden');
    elements.welcomeView.classList.remove('hidden');
  });

  // ==========================================================================
  // DMA Order Placement Form Controls
  // ==========================================================================

  elements.btnSideBuy.addEventListener('click', () => {
    state.activeSide = 'BUY';
    elements.btnSideBuy.className = 'side-btn active-buy';
    elements.btnSideSell.className = 'side-btn';
    elements.btnSubmitOrder.className = 'btn btn-order-submit btn-buy-cta';
    updateSubmitButton();
    updateBalanceUI();
  });

  elements.btnSideSell.addEventListener('click', () => {
    state.activeSide = 'SELL';
    elements.btnSideSell.className = 'side-btn active-sell';
    elements.btnSideBuy.className = 'side-btn';
    elements.btnSubmitOrder.className = 'btn btn-order-submit btn-sell-cta';
    updateSubmitButton();
    updateBalanceUI();
  });

  elements.btnTypeLimit.addEventListener('click', () => {
    state.activeOrderType = 'LIMIT';
    elements.btnTypeLimit.className = 'type-btn active';
    elements.btnTypeMarket.className = 'type-btn';
    elements.inputPrice.disabled = false;
    elements.groupPrice.style.opacity = '1';
    updateSubmitButton();
    updateNotional();
  });

  elements.btnTypeMarket.addEventListener('click', () => {
    state.activeOrderType = 'MARKET';
    elements.btnTypeMarket.className = 'type-btn active';
    elements.btnTypeLimit.className = 'type-btn';
    elements.inputPrice.disabled = true;
    elements.groupPrice.style.opacity = '0.4';
    updateSubmitButton();
    updateNotional();
  });

  function updateSubmitButton() {
    elements.btnSubmitOrder.textContent = `Submit ${state.activeSide} ${state.activeOrderType} Order`;
  }

  function updateNotional() {
    const qty = Number(elements.inputQty.value) || 0;
    const price = state.activeOrderType === 'LIMIT' ? (Number(elements.inputPrice.value) || 0) : 50000;
    const notional = qty * price;
    elements.summaryNotional.textContent = `${notional.toLocaleString()} USDT`;
  }

  // Steppers
  elements.btnPriceMinus.addEventListener('click', () => {
    elements.inputPrice.value = Math.max(1, Number(elements.inputPrice.value) - 10);
    updateNotional();
  });
  elements.btnPricePlus.addEventListener('click', () => {
    elements.inputPrice.value = Math.max(1, Number(elements.inputPrice.value) + 10);
    updateNotional();
  });
  elements.btnQtyMinus.addEventListener('click', () => {
    elements.inputQty.value = Math.max(1, Number(elements.inputQty.value) - 10);
    updateNotional();
  });
  elements.btnQtyPlus.addEventListener('click', () => {
    elements.inputQty.value = Math.max(1, Number(elements.inputQty.value) + 10);
    updateNotional();
  });

  elements.inputPrice.addEventListener('input', updateNotional);
  elements.inputQty.addEventListener('input', updateNotional);

  // Quick Presets
  document.querySelectorAll('.preset-tag, .btn-preset').forEach(btn => {
    btn.addEventListener('click', () => {
      if (btn.dataset.priceOffset !== undefined) {
        const offset = Number(btn.dataset.priceOffset);
        const base = 50000;
        elements.inputPrice.value = Math.max(1, base + offset);
      }
      if (btn.dataset.qty !== undefined) {
        elements.inputQty.value = Number(btn.dataset.qty);
      }
      updateNotional();
    });
  });

  let bannerTimeout = null;
  function showBanner(type, title, desc) {
    if (bannerTimeout) {
      clearTimeout(bannerTimeout);
      bannerTimeout = null;
    }
    elements.statusBanner.className = `status-banner banner-${type}`;
    elements.bannerTitle.textContent = title;
    elements.bannerDesc.textContent = desc;
    elements.statusBanner.classList.remove('hidden');

    bannerTimeout = setTimeout(() => {
      elements.statusBanner.classList.add('hidden');
      bannerTimeout = null;
    }, 6000);
  }

  // ==========================================================================
  // Order Submission & Cancellation
  // ==========================================================================

  elements.orderForm.addEventListener('submit', async (e) => {
    e.preventDefault();
    if (!state.token || !state.user) {
      showBanner('error', 'UNAUTHENTICATED', 'You must sign in to place orders.');
      return;
    }

    const qty = Number(elements.inputQty.value);
    const price = state.activeOrderType === 'LIMIT' ? Number(elements.inputPrice.value) : 0;

    if (qty <= 0) {
      showBanner('error', 'INVALID QUANTITY', 'Quantity must be greater than zero.');
      return;
    }
    if (state.activeOrderType === 'LIMIT' && price <= 0) {
      showBanner('error', 'INVALID PRICE', 'Price must be greater than zero for LIMIT orders.');
      return;
    }

    const payload = {
      side: state.activeSide,
      price: price,
      quantity: qty,
      orderType: state.activeOrderType
    };

    elements.btnSubmitOrder.disabled = true;

    const res = await apiRequest('/api/v1/orders', {
      method: 'POST',
      body: JSON.stringify(payload)
    });

    elements.btnSubmitOrder.disabled = false;

    if (res.ok && res.data && res.data.status === 'ACCEPTED') {
      showBanner(
        'success',
        `ORDER ACCEPTED BY INGRESS DISRUPTOR (#${res.data.orderId})`,
        `Ingress Timestamp: ${res.data.timestampNs} ns | Roundtrip: ${res.elapsedMs} ms`
      );

      const newOrder = {
        orderId: res.data.orderId,
        userId: state.user.userId,
        time: new Date().toLocaleTimeString(),
        side: state.activeSide,
        orderType: state.activeOrderType,
        price: state.activeOrderType === 'LIMIT' ? price : 'MARKET',
        quantity: qty,
        status: 'QUEUED'
      };

      state.myOrders.unshift(newOrder);
      saveOrdersToStorage();
      renderMyOrders();

      logAudit(
        'ORDER_ACCEPTED',
        res.status,
        res.elapsedMs,
        `${state.activeSide} ${state.activeOrderType} #${res.data.orderId} - ${qty} @ ${price}`
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

    // Immediately debounce in UI: mark as PENDING_CANCEL
    const target = state.myOrders.find(o => o.orderId === orderId);
    if (target) {
      target.status = 'PENDING_CANCEL';
      saveOrdersToStorage();
      renderMyOrders();
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
          target.status = 'QUEUED';
          saveOrdersToStorage();
          renderMyOrders();
        }
      }
      logAudit('CANCEL_FAILED', res.status, res.elapsedMs, errMsg);
    }
  }

  // Active Orders Synchronization with Gateway Ledger
  async function syncActiveOrders() {
    if (!state.token || !state.user) return;
    const res = await apiRequest('/api/v1/orders');
    if (res.ok && Array.isArray(res.data)) {
      const serverActiveMap = new Map();
      res.data.forEach(item => {
        if (item && item.orderId) {
          serverActiveMap.set(item.orderId, item);
        }
      });

      // 1. Reconcile existing orders in local state
      state.myOrders.forEach(o => {
        if (serverActiveMap.has(o.orderId)) {
          const sOrder = serverActiveMap.get(o.orderId);
          o.quantity = sOrder.remainingQuantity;
          if (sOrder.pendingCancel) {
            o.status = 'PENDING_CANCEL';
          }
          serverActiveMap.delete(o.orderId);
        } else if (o.status === 'QUEUED' || o.status === 'PENDING_CANCEL' || o.status === 'NEW') {
          o.status = 'CANCELLED';
        }
      });

      // 2. Add active orders from server not yet in local state (e.g. reload or multi-tab)
      serverActiveMap.forEach(sOrder => {
        state.myOrders.unshift({
          orderId: sOrder.orderId,
          userId: state.user.userId,
          time: new Date(sOrder.timestampNs ? Math.round(sOrder.timestampNs / 1_000_000) : Date.now()).toLocaleTimeString(),
          side: sOrder.side,
          orderType: 'LIMIT',
          price: sOrder.price,
          quantity: sOrder.remainingQuantity,
          status: sOrder.pendingCancel ? 'PENDING_CANCEL' : 'QUEUED'
        });
      });

      saveOrdersToStorage();
      renderMyOrders();
    }
  }

  // Cross Test Match Helper
  elements.btnDemoCross.addEventListener('click', async () => {
    if (!state.token || !state.user) {
      showBanner('error', 'UNAUTHENTICATED', 'Sign in first to run cross test.');
      return;
    }

    elements.btnDemoCross.disabled = true;
    elements.btnDemoCross.textContent = '⏳ Posting Buy & Sell Orders...';

    const testPrice = 50000;
    const testQty = 25;

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
      state.myOrders.unshift({
        orderId: buyRes.data.orderId,
        userId: state.user.userId,
        time: new Date().toLocaleTimeString(),
        side: 'BUY',
        orderType: 'LIMIT',
        price: testPrice,
        quantity: testQty,
        status: 'QUEUED'
      });
      logAudit('CROSS_TEST_BUY', buyRes.status, buyRes.elapsedMs, `BUY #${buyRes.data.orderId} @ ${testPrice}`);
    }

    // Small delay
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
      state.myOrders.unshift({
        orderId: sellRes.data.orderId,
        userId: state.user.userId,
        time: new Date().toLocaleTimeString(),
        side: 'SELL',
        orderType: 'LIMIT',
        price: testPrice,
        quantity: testQty,
        status: 'QUEUED'
      });
      logAudit('CROSS_TEST_SELL', sellRes.status, sellRes.elapsedMs, `SELL #${sellRes.data.orderId} @ ${testPrice}`);
    }

    saveOrdersToStorage();
    renderMyOrders();
    fetchBalance();

    showBanner('success', 'CROSS TEST INGESTED', 'Injected BUY and SELL order pair into Disruptor RingBuffer.');
    elements.btnDemoCross.disabled = false;
    elements.btnDemoCross.textContent = '⚡ Fire Cross Test (Buy @ 50000 + Sell @ 50000)';
  });

  // ==========================================================================
  // Renderers & Cache
  // ==========================================================================

  function renderMyOrders() {
    elements.myOrdersCount.textContent = state.myOrders.length;

    if (state.myOrders.length === 0) {
      elements.myOrdersTbody.innerHTML = `
        <tr class="empty-row" id="my-orders-empty">
          <td colspan="8">No active orders placed yet in this session.</td>
        </tr>
      `;
      return;
    }

    elements.myOrdersTbody.innerHTML = '';
    state.myOrders.forEach(order => {
      const tr = document.createElement('tr');
      const isBuy = order.side === 'BUY';
      const isCancelled = order.status === 'CANCELLED';
      const isFilled = order.status === 'FILLED';
      const isPendingCancel = order.status === 'PENDING_CANCEL';
      const isTerminalState = isCancelled || isFilled;
      const disableCancel = isTerminalState || isPendingCancel;

      let cancelBtnText = 'Cancel';
      if (isPendingCancel) cancelBtnText = 'Cancelling...';
      else if (isCancelled) cancelBtnText = 'Cancelled';
      else if (isFilled) cancelBtnText = 'Filled';

      tr.innerHTML = `
        <td class="font-mono text-muted">#${order.orderId}</td>
        <td>${order.time}</td>
        <td><span class="badge-side ${isBuy ? 'badge-buy' : 'badge-sell'}">${order.side}</span></td>
        <td><span class="badge-type">${order.orderType}</span></td>
        <td class="font-mono font-bold">${typeof order.price === 'number' ? order.price.toLocaleString() : order.price}</td>
        <td class="font-mono">${order.quantity}</td>
        <td><span class="status-pill status-${order.status.toLowerCase().replace('_', '-')}">${order.status}</span></td>
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

      elements.myOrdersTbody.appendChild(tr);
    });
  }

  elements.btnClearOrders.addEventListener('click', () => {
    state.myOrders = state.myOrders.filter(o => o.status !== 'CANCELLED' && o.status !== 'FILLED');
    saveOrdersToStorage();
    renderMyOrders();
  });

  function logAudit(event, httpStatus, latencyMs, details) {
    const emptyRow = elements.auditTbody.querySelector('.empty-row');
    if (emptyRow) {
      emptyRow.remove();
    }

    const tr = document.createElement('tr');
    const isOk = httpStatus >= 200 && httpStatus < 300;

    tr.innerHTML = `
      <td class="font-mono text-muted">${new Date().toLocaleTimeString()}</td>
      <td class="font-mono font-bold">${event}</td>
      <td><span class="pill-spec ${isOk ? 'text-success' : 'text-sell'}">${httpStatus || 'ERR'}</span></td>
      <td class="font-mono text-cyan">${latencyMs}ms</td>
      <td class="text-secondary">${details}</td>
    `;

    elements.auditTbody.insertBefore(tr, elements.auditTbody.firstChild);

    while (elements.auditTbody.children.length > 50) {
      elements.auditTbody.removeChild(elements.auditTbody.lastChild);
    }
  }

  elements.btnClearAudit.addEventListener('click', () => {
    elements.auditTbody.innerHTML = `
      <tr class="empty-row" id="audit-empty">
        <td colspan="5">Gateway ingress audit log cleared.</td>
      </tr>
    `;
  });

  function loadOrdersFromStorage() {
    try {
      const key = getOrdersStorageKey();
      return JSON.parse(localStorage.getItem(key) || '[]');
    } catch {
      return [];
    }
  }

  function saveOrdersToStorage() {
    try {
      const key = getOrdersStorageKey();
      localStorage.setItem(key, JSON.stringify(state.myOrders));
    } catch {
      // storage unavailable or full
    }
  }

  // Initialize
  updateAuthUI();
  updateNotional();
  updateSubmitButton();
})();
