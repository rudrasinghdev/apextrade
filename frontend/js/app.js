/**
 * ApexTrade Core - High-Performance Trading Terminal Client
 * Connects to Spring Boot Ingress REST API & Egress WebSocket
 */

(() => {
  'use strict';

  // --- Configuration & Dynamic Gateway ---
  const DEFAULT_PORT = 8080;
  const GATEWAY_STORAGE_KEY = 'apextrade_gateway_host';

  function resolveGatewayHost() {
    const saved = localStorage.getItem(GATEWAY_STORAGE_KEY);
    if (saved && saved.trim()) {
      return saved.trim().replace(/^https?:\/\//, '').replace(/\/$/, '');
    }
    // If running on Vercel, there is no local backend on Vercel
    if (window.location.hostname.includes('vercel.app')) {
      return '';
    }
    if (window.location.protocol.startsWith('http')) {
      return window.location.host;
    }
    return `localhost:${DEFAULT_PORT}`;
  }

  let API_HOST = resolveGatewayHost();

  function getRestBase() {
    if (!API_HOST) return '';
    const isHttps = window.location.protocol === 'https:' || API_HOST.includes('onrender.com');
    const proto = isHttps ? 'https:' : (window.location.protocol.startsWith('http') ? window.location.protocol : 'http:');
    return `${proto}//${API_HOST}`;
  }

  function getWsUrl() {
    if (!API_HOST) return '';
    const isWss = window.location.protocol === 'https:' || API_HOST.includes('onrender.com');
    const proto = isWss ? 'wss:' : 'ws:';
    return `${proto}//${API_HOST}/ws/trades`;
  }

  let REST_BASE_URL = getRestBase();
  let WS_URL = getWsUrl();

  function updateFooterUrls() {
    const footerRest = document.getElementById('footer-rest-url');
    const footerWs = document.getElementById('footer-ws-url');
    if (footerRest) footerRest.textContent = REST_BASE_URL ? `${REST_BASE_URL}/api/v1/orders` : 'Not Set (Enter Render URL)';
    if (footerWs) footerWs.textContent = WS_URL ? WS_URL : 'Not Set (Enter Render URL)';
  }
  updateFooterUrls();

  // --- State ---
  let socket = null;
  let reconnectTimer = null;
  let activeSide = 'BUY';
  let activeOrderType = 'LIMIT';
  let totalTradesCount = 0;
  let sessionVolume = 0;
  let lastPrice = null;
  const MAX_TAPE_ROWS = 50;

  // Local storage key for persistent order cache
  const STORAGE_KEY = 'apextrade_active_orders';
  let myOrders = loadOrdersFromStorage();

  // --- DOM Elements ---
  const views = {
    welcome: document.getElementById('welcome-view'),
    terminal: document.getElementById('terminal-view')
  };

  const nav = {
    btnEnter: document.getElementById('btn-enter-terminal'),
    btnExit: document.getElementById('btn-exit-terminal'),
    wsStatus: document.getElementById('ws-status'),
    wsDot: document.getElementById('ws-dot'),
    wsStatusText: document.getElementById('ws-status-text'),
    userIdInput: document.getElementById('trader-user-id')
  };

  const market = {
    ltp: document.getElementById('ltp-display'),
    lastQty: document.getElementById('last-qty-display'),
    totalVol: document.getElementById('total-vol-display'),
    tradeCount: document.getElementById('trade-count-display')
  };

  const form = {
    orderForm: document.getElementById('order-form'),
    btnBuy: document.getElementById('btn-side-buy'),
    btnSell: document.getElementById('btn-side-sell'),
    btnLimit: document.getElementById('btn-type-limit'),
    btnMarket: document.getElementById('btn-type-market'),
    inputPrice: document.getElementById('input-price'),
    inputQty: document.getElementById('input-qty'),
    groupPrice: document.getElementById('group-price'),
    btnSubmit: document.getElementById('btn-submit-order'),
    summaryNotional: document.getElementById('summary-notional'),
    statusBanner: document.getElementById('order-status-banner'),
    bannerTitle: document.getElementById('banner-title'),
    bannerDesc: document.getElementById('banner-desc'),
    btnDemoCross: document.getElementById('btn-demo-cross')
  };

  const myOrdersUI = {
    tbody: document.getElementById('my-orders-tbody'),
    emptyRow: document.getElementById('my-orders-empty'),
    countBadge: document.getElementById('my-orders-count'),
    btnClear: document.getElementById('btn-clear-orders')
  };

  const tradeTapeUI = {
    tbody: document.getElementById('trade-tape-tbody'),
    emptyRow: document.getElementById('trade-tape-empty'),
    pulse: document.getElementById('tape-pulse'),
    chkAutoscroll: document.getElementById('chk-autoscroll'),
    container: document.getElementById('trade-tape-container')
  };

  // --- View Switcher ---
  function showView(viewName) {
    if (viewName === 'terminal') {
      views.welcome.classList.add('hidden');
      views.terminal.classList.remove('hidden');
      connectWebSocket();
      renderMyOrders();
    } else {
      views.terminal.classList.add('hidden');
      views.welcome.classList.remove('hidden');
    }
  }

  nav.btnEnter.addEventListener('click', () => showView('terminal'));
  nav.btnExit.addEventListener('click', () => showView('welcome'));

  const welcomeGatewayInput = document.getElementById('welcome-gateway-input');
  const terminalGatewayInput = document.getElementById('gateway-host-input');
  const btnTestGateway = document.getElementById('btn-test-gateway');
  const testStatus = document.getElementById('gateway-test-status');

  function syncGatewayInputs(val) {
    if (welcomeGatewayInput) welcomeGatewayInput.value = val;
    if (terminalGatewayInput) terminalGatewayInput.value = val;
  }

  const initialHost = localStorage.getItem(GATEWAY_STORAGE_KEY) || API_HOST;
  syncGatewayInputs(initialHost);

  function applyGatewayChange(rawVal) {
    const clean = (rawVal || '').trim().replace(/^https?:\/\//, '').replace(/\/$/, '');
    if (clean) {
      localStorage.setItem(GATEWAY_STORAGE_KEY, clean);
    } else {
      localStorage.removeItem(GATEWAY_STORAGE_KEY);
    }
    API_HOST = resolveGatewayHost();
    REST_BASE_URL = getRestBase();
    WS_URL = getWsUrl();
    updateFooterUrls();
    syncGatewayInputs(clean || API_HOST);
    if (socket) {
      socket.close();
      socket = null;
    }
    if (!views.terminal.classList.contains('hidden')) {
      connectWebSocket();
    }
  }

  if (welcomeGatewayInput) {
    welcomeGatewayInput.addEventListener('change', () => applyGatewayChange(welcomeGatewayInput.value));
  }
  if (terminalGatewayInput) {
    terminalGatewayInput.addEventListener('change', () => applyGatewayChange(terminalGatewayInput.value));
  }

  if (btnTestGateway) {
    btnTestGateway.addEventListener('click', async () => {
      const raw = welcomeGatewayInput ? welcomeGatewayInput.value : '';
      const host = raw.trim().replace(/^https?:\/\//, '').replace(/\/$/, '') || API_HOST;

      if (!host) {
        testStatus.className = 'gateway-status-text error';
        testStatus.textContent = 'Please enter your Render host (e.g. apextrade-core.onrender.com)';
        return;
      }

      testStatus.className = 'gateway-status-text loading';
      testStatus.textContent = `Connecting to ${host}... (Free tier Render may take ~50s to wake up)`;

      const isHttps = window.location.protocol === 'https:' || host.includes('onrender.com');
      const testUrl = `${isHttps ? 'https:' : 'http:'}//${host}/api/v1/orders/health`;

      try {
        const res = await fetch(testUrl, { method: 'GET' });
        if (res.ok) {
          testStatus.className = 'gateway-status-text success';
          testStatus.textContent = '✓ Backend Online! Connection verified.';
          applyGatewayChange(host);
        } else {
          testStatus.className = 'gateway-status-text error';
          testStatus.textContent = `Server responded with HTTP ${res.status}. Check Render logs.`;
        }
      } catch (err) {
        testStatus.className = 'gateway-status-text error';
        testStatus.textContent = '✗ Connection failed. If Render was asleep, wait 30 seconds and test again.';
      }
    });
  }

  // --- WebSocket Connection ---
  function connectWebSocket() {
    if (!WS_URL) {
      setWsStatus('disconnected', 'NO GATEWAY HOST');
      return;
    }

    if (socket && (socket.readyState === WebSocket.OPEN || socket.readyState === WebSocket.CONNECTING)) {
      return;
    }

    setWsStatus('connecting', 'CONNECTING...');

    try {
      socket = new WebSocket(WS_URL);

      socket.onopen = () => {
        setWsStatus('connected', 'CONNECTED');
        if (reconnectTimer) {
          clearTimeout(reconnectTimer);
          reconnectTimer = null;
        }
      };

      socket.onmessage = (event) => {
        try {
          const trade = JSON.parse(event.data);
          handleIncomingTrade(trade);
        } catch (e) {
          console.error('[ApexTrade] Error parsing WS payload:', e, event.data);
        }
      };

      socket.onerror = (err) => {
        console.warn('[ApexTrade] WebSocket error:', err);
      };

      socket.onclose = () => {
        setWsStatus('disconnected', 'DISCONNECTED');
        if (!reconnectTimer) {
          reconnectTimer = setTimeout(() => {
            reconnectTimer = null;
            if (!views.terminal.classList.contains('hidden')) {
              connectWebSocket();
            }
          }, 2000);
        }
      };
    } catch (err) {
      setWsStatus('disconnected', 'OFFLINE');
      console.error('[ApexTrade] Connection failed:', err);
    }
  }

  function setWsStatus(state, text) {
    nav.wsDot.className = `status-dot ${state}`;
    nav.wsStatusText.textContent = text;
  }

  // --- Trade Feed Processing ---
  function handleIncomingTrade(trade) {
    totalTradesCount++;
    sessionVolume += Number(trade.quantity);

    // 1. Update Market Strip
    const priceNum = Number(trade.price);
    const qtyNum = Number(trade.quantity);

    if (lastPrice !== null) {
      market.ltp.classList.remove('price-up', 'price-down');
      void market.ltp.offsetWidth; // Trigger reflow for animation restart
      if (priceNum > lastPrice) {
        market.ltp.classList.add('price-up');
      } else if (priceNum < lastPrice) {
        market.ltp.classList.add('price-down');
      }
    }
    lastPrice = priceNum;

    market.ltp.textContent = formatPrice(priceNum);
    market.lastQty.textContent = qtyNum.toLocaleString();
    market.totalVol.textContent = sessionVolume.toLocaleString();
    market.tradeCount.textContent = totalTradesCount.toLocaleString();

    // 2. Pulse tape indicator
    tradeTapeUI.pulse.classList.remove('pulse-active');
    void tradeTapeUI.pulse.offsetWidth;
    tradeTapeUI.pulse.classList.add('pulse-active');

    // 3. Prepend to Trade Tape
    if (tradeTapeUI.emptyRow && tradeTapeUI.emptyRow.parentNode) {
      tradeTapeUI.emptyRow.remove();
    }

    const tr = document.createElement('tr');
    const isBuy = trade.takerSide === 'BUY';
    tr.className = isBuy ? 'row-flash-buy' : 'row-flash-sell';

    const timeStr = formatTimestamp(trade.timestamp);
    tr.innerHTML = `
      <td>${timeStr}</td>
      <td><span class="badge-side ${isBuy ? 'badge-buy' : 'badge-sell'}">${trade.takerSide}</span></td>
      <td class="font-mono font-bold">${formatPrice(priceNum)}</td>
      <td class="font-mono">${qtyNum.toLocaleString()}</td>
      <td class="font-mono text-muted">#${trade.tradeId}</td>
      <td class="font-mono text-muted">${trade.takerOrderId} / ${trade.makerOrderId}</td>
    `;

    tradeTapeUI.tbody.insertBefore(tr, tradeTapeUI.tbody.firstChild);

    // Cap tape rows
    while (tradeTapeUI.tbody.children.length > MAX_TAPE_ROWS) {
      tradeTapeUI.tbody.removeChild(tradeTapeUI.tbody.lastChild);
    }

    if (tradeTapeUI.chkAutoscroll.checked) {
      tradeTapeUI.container.scrollTop = 0;
    }

    // 4. Update My Orders match state if orderId matches
    updateOrderFills(trade);
  }

  function updateOrderFills(trade) {
    let changed = false;
    const makerId = Number(trade.makerOrderId);
    const takerId = Number(trade.takerOrderId);
    const fillQty = Number(trade.quantity);

    for (const order of myOrders) {
      if (order.orderId === makerId || order.orderId === takerId) {
        order.filledQty = (order.filledQty || 0) + fillQty;
        if (order.filledQty >= order.quantity) {
          order.status = 'FILLED';
        } else {
          order.status = 'PARTIALLY_FILLED';
        }
        changed = true;
      }
    }

    if (changed) {
      saveOrdersToStorage();
      renderMyOrders();
    }
  }

  // --- Order Placement Form Controls ---
  form.btnBuy.addEventListener('click', () => {
    activeSide = 'BUY';
    form.btnBuy.className = 'btn-toggle active-buy';
    form.btnSell.className = 'btn-toggle';
    form.btnSubmit.className = 'btn btn-buy btn-block';
    updateSubmitButtonText();
  });

  form.btnSell.addEventListener('click', () => {
    activeSide = 'SELL';
    form.btnSell.className = 'btn-toggle active-sell';
    form.btnBuy.className = 'btn-toggle';
    form.btnSubmit.className = 'btn btn-sell btn-block';
    updateSubmitButtonText();
  });

  form.btnLimit.addEventListener('click', () => {
    activeOrderType = 'LIMIT';
    form.btnLimit.className = 'btn-toggle active';
    form.btnMarket.className = 'btn-toggle';
    form.inputPrice.disabled = false;
    form.groupPrice.style.opacity = '1';
    updateSubmitButtonText();
    updateNotional();
  });

  form.btnMarket.addEventListener('click', () => {
    activeOrderType = 'MARKET';
    form.btnMarket.className = 'btn-toggle active';
    form.btnLimit.className = 'btn-toggle';
    form.inputPrice.disabled = true;
    form.groupPrice.style.opacity = '0.5';
    updateSubmitButtonText();
    updateNotional();
  });

  function updateSubmitButtonText() {
    form.btnSubmit.textContent = `Submit ${activeSide} ${activeOrderType} Order`;
  }

  // Price & Quantity Steppers
  document.getElementById('btn-price-plus').addEventListener('click', () => {
    form.inputPrice.value = Math.max(1, Number(form.inputPrice.value) + 10);
    updateNotional();
  });
  document.getElementById('btn-price-minus').addEventListener('click', () => {
    form.inputPrice.value = Math.max(1, Number(form.inputPrice.value) - 10);
    updateNotional();
  });
  document.getElementById('btn-qty-plus').addEventListener('click', () => {
    form.inputQty.value = Math.max(1, Number(form.inputQty.value) + 10);
    updateNotional();
  });
  document.getElementById('btn-qty-minus').addEventListener('click', () => {
    form.inputQty.value = Math.max(1, Number(form.inputQty.value) - 10);
    updateNotional();
  });

  // Quick Preset Buttons
  document.querySelectorAll('.btn-preset[data-price-offset]').forEach(btn => {
    btn.addEventListener('click', () => {
      const offset = Number(btn.getAttribute('data-price-offset'));
      const base = lastPrice ? lastPrice : Number(form.inputPrice.value || 50000);
      form.inputPrice.value = Math.max(1, base + offset);
      updateNotional();
    });
  });

  document.querySelectorAll('.btn-preset[data-qty]').forEach(btn => {
    btn.addEventListener('click', () => {
      form.inputQty.value = btn.getAttribute('data-qty');
      updateNotional();
    });
  });

  form.inputPrice.addEventListener('input', updateNotional);
  form.inputQty.addEventListener('input', updateNotional);

  function updateNotional() {
    const qty = Number(form.inputQty.value) || 0;
    if (activeOrderType === 'MARKET') {
      const estPrice = lastPrice || 50000;
      form.summaryNotional.textContent = `~ ${(qty * estPrice).toLocaleString()} USDT (Market Est.)`;
    } else {
      const price = Number(form.inputPrice.value) || 0;
      form.summaryNotional.textContent = `${(qty * price).toLocaleString()} USDT`;
    }
  }

  // Submit Order Handler
  form.orderForm.addEventListener('submit', async (e) => {
    e.preventDefault();
    await placeOrderFromForm();
  });

  async function placeOrderFromForm() {
    const userId = Number(nav.userIdInput.value) || 101;
    const qty = Number(form.inputQty.value);
    const price = activeOrderType === 'LIMIT' ? Number(form.inputPrice.value) : 0;

    if (qty <= 0) {
      showBanner('error', 'INVALID QUANTITY', 'Quantity must be greater than zero.');
      return;
    }
    if (activeOrderType === 'LIMIT' && price <= 0) {
      showBanner('error', 'INVALID PRICE', 'Limit price must be greater than zero.');
      return;
    }

    const payload = {
      userId: userId,
      side: activeSide,
      price: price,
      quantity: qty,
      orderType: activeOrderType
    };

    form.btnSubmit.disabled = true;

    try {
      const response = await fetch(`${REST_BASE_URL}/api/v1/orders`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(payload)
      });

      const data = await response.json();

      if (response.ok && data.status === 'ACCEPTED') {
        showBanner('success', 'ORDER ACCEPTED BY INGRESS DISRUPTOR', `Order ID: #${data.orderId} | Timestamp: ${data.timestampNs} ns`);

        // Cache order locally
        const newOrder = {
          orderId: data.orderId,
          userId: userId,
          time: new Date().toLocaleTimeString(),
          side: activeSide,
          orderType: activeOrderType,
          price: activeOrderType === 'LIMIT' ? price : 'MARKET',
          quantity: qty,
          filledQty: 0,
          status: 'QUEUED'
        };

        myOrders.unshift(newOrder);
        saveOrdersToStorage();
        renderMyOrders();
      } else {
        showBanner('error', 'ORDER REJECTED', data.message || 'Execution rejected by engine.');
      }
    } catch (err) {
      showBanner('error', 'NETWORK / GATEWAY ERROR', `Failed to reach ${REST_BASE_URL}: ${err.message}`);
    } finally {
      form.btnSubmit.disabled = false;
    }
  }

  // --- Order Cancellation ---
  async function cancelOrder(orderId) {
    try {
      const response = await fetch(`${REST_BASE_URL}/api/v1/orders/${orderId}`, {
        method: 'DELETE'
      });

      const data = await response.json();

      if (response.ok && data.status === 'ACCEPTED') {
        showBanner('success', 'ORDER CANCELLED', `Order #${orderId} cancelled in matching engine.`);
        updateOrderStatus(orderId, 'CANCELLED');
      } else {
        showBanner('error', 'CANCEL FAILED', data.message || `Unable to cancel order #${orderId}`);
      }
    } catch (err) {
      showBanner('error', 'GATEWAY ERROR', `Cancel request failed: ${err.message}`);
    }
  }

  function updateOrderStatus(orderId, newStatus) {
    const order = myOrders.find(o => o.orderId === orderId);
    if (order) {
      order.status = newStatus;
      saveOrdersToStorage();
      renderMyOrders();
    }
  }

  // --- Quick Demo Cross Match Helper ---
  form.btnDemoCross.addEventListener('click', async () => {
    form.btnDemoCross.disabled = true;
    form.btnDemoCross.textContent = '⏳ Executing Match...';

    const testPrice = 50000;
    const testQty = 25;
    const userId1 = 101;
    const userId2 = 102;

    try {
      // 1. Post BUY Order
      const buyRes = await fetch(`${REST_BASE_URL}/api/v1/orders`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ userId: userId1, side: 'BUY', price: testPrice, quantity: testQty, orderType: 'LIMIT' })
      });
      const buyData = await buyRes.json();
      if (buyData.status === 'ACCEPTED') {
        myOrders.unshift({
          orderId: buyData.orderId,
          userId: userId1,
          time: new Date().toLocaleTimeString(),
          side: 'BUY',
          orderType: 'LIMIT',
          price: testPrice,
          quantity: testQty,
          filledQty: 0,
          status: 'QUEUED'
        });
      }

      // Small delay
      await new Promise(r => setTimeout(r, 60));

      // 2. Post SELL Order (Crosses with BUY!)
      const sellRes = await fetch(`${REST_BASE_URL}/api/v1/orders`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ userId: userId2, side: 'SELL', price: testPrice, quantity: testQty, orderType: 'LIMIT' })
      });
      const sellData = await sellRes.json();
      if (sellData.status === 'ACCEPTED') {
        myOrders.unshift({
          orderId: sellData.orderId,
          userId: userId2,
          time: new Date().toLocaleTimeString(),
          side: 'SELL',
          orderType: 'LIMIT',
          price: testPrice,
          quantity: testQty,
          filledQty: 0,
          status: 'QUEUED'
        });
      }

      saveOrdersToStorage();
      renderMyOrders();
      showBanner('success', 'CROSS PAIR DISPATCHED', `BUY #${buyData.orderId} and SELL #${sellData.orderId} dispatched to Ingress Disruptor!`);
    } catch (err) {
      showBanner('error', 'DEMO FAILED', err.message);
    } finally {
      form.btnDemoCross.disabled = false;
      form.btnDemoCross.textContent = '⚡ Place Cross Pair (Match Test)';
    }
  });

  // --- Render My Orders Table ---
  function renderMyOrders() {
    myOrdersUI.countBadge.textContent = myOrders.length;

    if (myOrders.length === 0) {
      myOrdersUI.tbody.innerHTML = `
        <tr class="empty-row" id="my-orders-empty">
          <td colspan="9">No active orders placed yet in this session.</td>
        </tr>
      `;
      return;
    }

    myOrdersUI.tbody.innerHTML = '';

    for (const order of myOrders) {
      const tr = document.createElement('tr');
      const isBuy = order.side === 'BUY';
      const isFinished = order.status === 'FILLED' || order.status === 'CANCELLED' || order.status === 'REJECTED';

      let statusBadgeClass = 'status-queued';
      if (order.status === 'FILLED') statusBadgeClass = 'status-filled';
      else if (order.status === 'PARTIALLY_FILLED') statusBadgeClass = 'status-partial';
      else if (order.status === 'CANCELLED') statusBadgeClass = 'status-cancelled';
      else if (order.status === 'REJECTED') statusBadgeClass = 'status-rejected';

      tr.innerHTML = `
        <td class="font-bold">#${order.orderId}</td>
        <td class="text-muted">${order.time}</td>
        <td><span class="badge-side ${isBuy ? 'badge-buy' : 'badge-sell'}">${order.side}</span></td>
        <td>${order.orderType}</td>
        <td>${typeof order.price === 'number' ? formatPrice(order.price) : order.price}</td>
        <td>${order.quantity}</td>
        <td>${order.filledQty || 0}</td>
        <td><span class="badge-status ${statusBadgeClass}">${order.status}</span></td>
        <td>
          <button type="button" class="btn-cancel" data-order-id="${order.orderId}" ${isFinished ? 'disabled' : ''}>
            Cancel
          </button>
        </td>
      `;

      // Cancel button event
      const cancelBtn = tr.querySelector('.btn-cancel');
      if (!isFinished) {
        cancelBtn.addEventListener('click', () => cancelOrder(order.orderId));
      }

      myOrdersUI.tbody.appendChild(tr);
    }
  }

  // Clear Completed Orders
  myOrdersUI.btnClear.addEventListener('click', () => {
    myOrders = myOrders.filter(o => o.status === 'QUEUED' || o.status === 'PARTIALLY_FILLED');
    saveOrdersToStorage();
    renderMyOrders();
  });

  // --- Helpers ---
  function showBanner(type, title, desc) {
    form.statusBanner.className = `status-banner ${type}`;
    form.bannerTitle.textContent = title;
    form.bannerDesc.textContent = desc;
  }

  function formatPrice(val) {
    return Number(val).toLocaleString(undefined, { minimumFractionDigits: 2, maximumFractionDigits: 2 });
  }

  function formatTimestamp(ts) {
    if (!ts) return new Date().toLocaleTimeString();
    // Nanosecond vs millisecond detection
    const ms = ts > 1e15 ? Math.floor(ts / 1e6) : (ts > 1e12 ? ts : ts * 1000);
    const d = new Date(ms);
    return isNaN(d.getTime()) ? new Date().toLocaleTimeString() : d.toLocaleTimeString() + '.' + String(d.getMilliseconds()).padStart(3, '0');
  }

  function loadOrdersFromStorage() {
    try {
      const data = localStorage.getItem(STORAGE_KEY);
      return data ? JSON.parse(data) : [];
    } catch {
      return [];
    }
  }

  function saveOrdersToStorage() {
    try {
      // Keep only latest 100 orders in storage
      const trimmed = myOrders.slice(0, 100);
      localStorage.setItem(STORAGE_KEY, JSON.stringify(trimmed));
    } catch (e) {
      console.warn('LocalStorage error:', e);
    }
  }

  // Initialize notional calculation on load
  updateNotional();

})();

