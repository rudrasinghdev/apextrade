/**
 * ApexTrade Core - High-Performance Trading Terminal Client
 * REST Ingress & Gateway Risk Subsystem (Zero-WebSocket Staging Mode)
 */

(() => {
  'use strict';

  // --- Configuration ---
  const DEFAULT_PORT = 8080;
  const isLocal = window.location.hostname === 'localhost' || window.location.hostname === '127.0.0.1';
  const API_HOST = isLocal ? `localhost:${DEFAULT_PORT}` : 'apextrade-core-backend.onrender.com';
  const REST_BASE_URL = isLocal ? `http://${API_HOST}` : `https://${API_HOST}`;

  // --- Persistent Storage Keys ---
  const STORAGE_TOKEN_KEY = 'apextrade_jwt';
  const STORAGE_USER_KEY = 'apextrade_user';
  const STORAGE_ORDERS_KEY = 'apextrade_active_orders';

  // --- Application State ---
  const state = {
    token: sessionStorage.getItem(STORAGE_TOKEN_KEY) || null,
    user: JSON.parse(sessionStorage.getItem(STORAGE_USER_KEY) || 'null'),
    gatewayOnline: false,
    activeSide: 'BUY',
    activeOrderType: 'LIMIT',
    myOrders: loadOrdersFromStorage(),
    auditLogs: []
  };

  // --- DOM Elements ---
  const elements = {
    // Top Health Strip
    gatewayDot: document.getElementById('gateway-dot'),
    gatewayStatusText: document.getElementById('gateway-status-text'),

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
    btnUpgradeTerminal: document.getElementById('btn-upgrade-terminal'),
    btnLogout: document.getElementById('btn-logout'),

    // Terminal Navbar
    terminalUserEmail: document.getElementById('terminal-user-email'),
    terminalUserId: document.getElementById('terminal-user-id'),
    btnBackLaunchpad: document.getElementById('btn-back-launchpad'),
    btnTerminalLogout: document.getElementById('btn-terminal-logout'),
    latencyDisplay: document.getElementById('latency-display'),
    ltpDisplay: document.getElementById('ltp-display'),

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

  // Poll health immediately and every 10 seconds
  checkGatewayHealth();
  setInterval(checkGatewayHealth, 10000);

  // ==========================================================================
  // Auth & Session Management
  // ==========================================================================

  function updateAuthUI() {
    if (state.token && state.user) {
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
  }

  // Logout Handlers
  function handleLogout() {
    state.token = null;
    state.user = null;
    sessionStorage.removeItem(STORAGE_TOKEN_KEY);
    sessionStorage.removeItem(STORAGE_USER_KEY);
    logAudit('LOGOUT', 200, 0, 'User logged out and session cleared.');
    updateAuthUI();
  }

  elements.btnLogout.addEventListener('click', handleLogout);
  elements.btnTerminalLogout.addEventListener('click', handleLogout);

  // Upgrade to Terminal (WebSocket Staging Button)
  elements.btnUpgradeTerminal.addEventListener('click', () => {
    // Switch to Terminal View (No active WS connection per specification)
    elements.welcomeView.classList.add('hidden');
    elements.terminalView.classList.remove('hidden');
    renderMyOrders();
    logAudit('UPGRADE_TERMINAL', 200, 0, 'Entered Trading Terminal. WebSocket stream held in standby.');
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
    elements.btnSideBuy.className = 'btn-toggle active-buy';
    elements.btnSideSell.className = 'btn-toggle';
    elements.btnSubmitOrder.className = 'btn btn-buy btn-block';
    updateSubmitButton();
  });

  elements.btnSideSell.addEventListener('click', () => {
    state.activeSide = 'SELL';
    elements.btnSideSell.className = 'btn-toggle active-sell';
    elements.btnSideBuy.className = 'btn-toggle';
    elements.btnSubmitOrder.className = 'btn btn-sell btn-block';
    updateSubmitButton();
  });

  elements.btnTypeLimit.addEventListener('click', () => {
    state.activeOrderType = 'LIMIT';
    elements.btnTypeLimit.className = 'btn-toggle active';
    elements.btnTypeMarket.className = 'btn-toggle';
    elements.inputPrice.disabled = false;
    elements.groupPrice.style.opacity = '1';
    updateSubmitButton();
    updateNotional();
  });

  elements.btnTypeMarket.addEventListener('click', () => {
    state.activeOrderType = 'MARKET';
    elements.btnTypeMarket.className = 'btn-toggle active';
    elements.btnTypeLimit.className = 'btn-toggle';
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
  document.querySelectorAll('.btn-preset').forEach(btn => {
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

  function showBanner(type, title, desc) {
    elements.statusBanner.className = `status-banner banner-${type}`;
    elements.bannerTitle.textContent = title;
    elements.bannerDesc.textContent = desc;
    elements.statusBanner.classList.remove('hidden');
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
      userId: state.user.userId,
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
    } else {
      const errMsg = res.data?.message || res.data?.error || res.error || 'Order rejected by Gateway.';
      showBanner('error', 'ORDER REJECTED BY GATEWAY', errMsg);
      logAudit('ORDER_REJECTED', res.status, res.elapsedMs, errMsg);
    }
  });

  // Cancel Order
  async function cancelOrder(orderId) {
    if (!state.token) return;

    const res = await apiRequest(`/api/v1/orders/${orderId}`, {
      method: 'DELETE'
    });

    if (res.ok && res.data && res.data.status === 'ACCEPTED') {
      showBanner('success', `ORDER #${orderId} CANCELLED`, `Cancelled in matching pipeline in ${res.elapsedMs} ms`);
      const target = state.myOrders.find(o => o.orderId === orderId);
      if (target) {
        target.status = 'CANCELLED';
        saveOrdersToStorage();
        renderMyOrders();
      }
      logAudit('ORDER_CANCELLED', res.status, res.elapsedMs, `Order #${orderId} cancelled`);
    } else {
      const errMsg = res.data?.message || res.data?.error || `Unable to cancel #${orderId}`;
      showBanner('error', 'CANCEL FAILED', errMsg);
      logAudit('CANCEL_FAILED', res.status, res.elapsedMs, errMsg);
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
        userId: state.user.userId,
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
        userId: state.user.userId,
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

      tr.innerHTML = `
        <td class="font-mono text-muted">#${order.orderId}</td>
        <td>${order.time}</td>
        <td><span class="badge-side ${isBuy ? 'badge-buy' : 'badge-sell'}">${order.side}</span></td>
        <td><span class="badge-type">${order.orderType}</span></td>
        <td class="font-mono font-bold">${typeof order.price === 'number' ? order.price.toLocaleString() : order.price}</td>
        <td class="font-mono">${order.quantity}</td>
        <td><span class="status-pill status-${order.status.toLowerCase()}">${order.status}</span></td>
        <td>
          <button type="button" class="btn-cancel" data-order-id="${order.orderId}" ${isCancelled ? 'disabled' : ''}>
            ${isCancelled ? 'Cancelled' : 'Cancel'}
          </button>
        </td>
      `;

      const cancelBtn = tr.querySelector('.btn-cancel');
      if (cancelBtn && !isCancelled) {
        cancelBtn.addEventListener('click', () => cancelOrder(order.orderId));
      }

      elements.myOrdersTbody.appendChild(tr);
    });
  }

  elements.btnClearOrders.addEventListener('click', () => {
    state.myOrders = state.myOrders.filter(o => o.status !== 'CANCELLED');
    saveOrdersToStorage();
    renderMyOrders();
  });

  function logAudit(event, httpStatus, latencyMs, details) {
    if (elements.auditEmpty && elements.auditEmpty.parentNode) {
      elements.auditEmpty.remove();
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
      return JSON.parse(localStorage.getItem(STORAGE_ORDERS_KEY) || '[]');
    } catch {
      return [];
    }
  }

  function saveOrdersToStorage() {
    try {
      localStorage.setItem(STORAGE_ORDERS_KEY, JSON.stringify(state.myOrders));
    } catch {
      // storage unavailable or full
    }
  }

  // Initialize
  updateAuthUI();
  updateNotional();
  updateSubmitButton();
})();
