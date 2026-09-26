import { useEffect, useState } from 'react';

const LOW_STOCK_THRESHOLD = 5;
const FALLBACK_PRODUCTS = [
  { productId: 'P100', name: 'Wireless Mouse', stock: 25 },
  { productId: 'P200', name: 'Mechanical Keyboard', stock: 10 },
  { productId: 'P300', name: 'USB-C Hub', stock: 0 },
];

async function getJson(url) {
  const response = await fetch(url);
  if (!response.ok) throw new Error(`${response.status} ${response.statusText}`);
  return response.json();
}

export default function App() {
  const [products, setProducts] = useState(FALLBACK_PRODUCTS);
  const [orders, setOrders] = useState([]);
  const [notifications, setNotifications] = useState([]);
  const [supplierOrders, setSupplierOrders] = useState([]);
  const [selectedProductId, setSelectedProductId] = useState('P100');
  const [selectedQuantity, setSelectedQuantity] = useState(1);
  const [cart, setCart] = useState([]);
  const [result, setResult] = useState(null);
  const [loading, setLoading] = useState(false);
  const [apiConnected, setApiConnected] = useState(false);
  const [supplierAvailable, setSupplierAvailable] = useState(null);

  const refreshDashboard = async () => {
    const [inventoryResult, ordersResult, notificationsResult, supplierResult, availabilityResult] = await Promise.allSettled([
      getJson('/api/inventory'),
      getJson('/api/orders'),
      getJson('/api/notifications'),
      getJson('/api/supplier/orders'),
      getJson('/api/supplier/availability'),
    ]);

    if (inventoryResult.status === 'fulfilled') {
      setProducts(inventoryResult.value);
      setApiConnected(true);
    } else {
      setApiConnected(false);
    }
    if (ordersResult.status === 'fulfilled') setOrders(ordersResult.value);
    if (notificationsResult.status === 'fulfilled') setNotifications(notificationsResult.value);
    if (supplierResult.status === 'fulfilled') setSupplierOrders(supplierResult.value);
    setSupplierAvailable(availabilityResult.status === 'fulfilled'
      ? availabilityResult.value.available
      : false);
  };

  useEffect(() => {
    refreshDashboard();
    const statusRefresh = window.setInterval(refreshDashboard, 30000);
    return () => window.clearInterval(statusRefresh);
  }, []);

  const addToCart = () => {
    const quantity = Number.parseInt(selectedQuantity, 10);
    if (!selectedProductId || !Number.isInteger(quantity) || quantity < 1) return;

    setCart((currentCart) => {
      const existingLine = currentCart.find((line) => line.productId === selectedProductId);
      if (existingLine) {
        return currentCart.map((line) => line.productId === selectedProductId
          ? { ...line, quantity: line.quantity + quantity }
          : line);
      }
      return [...currentCart, { productId: selectedProductId, quantity }];
    });
    setSelectedQuantity(1);
  };

  const changeCartQuantity = (productId, value) => {
    const quantity = Number.parseInt(value, 10);
    if (!Number.isInteger(quantity) || quantity < 1) return;
    setCart((currentCart) => currentCart.map((line) => line.productId === productId
      ? { ...line, quantity }
      : line));
  };

  const removeCartLine = (productId) => {
    setCart((currentCart) => currentCart.filter((line) => line.productId !== productId));
  };

  const submitOrder = async (event) => {
    event.preventDefault();
    if (!cart.length) return;

    const payload = { items: cart };
    setLoading(true);
    setResult(null);
    try {
      const response = await fetch('/api/orders', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json', Accept: 'application/json' },
        body: JSON.stringify(payload),
      });
      const data = await response.json();
      if (!response.ok) throw new Error(data.message || data.error || 'Order request failed');

      setResult({ ...data, timestamp: new Date().toLocaleTimeString(), requestPayload: payload });
      if (data.status === 'CONFIRMED') setCart([]);
      await refreshDashboard();
    } catch (error) {
      setResult({
        status: 'ERROR',
        reason: error.message || 'Network error communicating with the backend',
        timestamp: new Date().toLocaleTimeString(),
        requestPayload: payload,
      });
    } finally {
      setLoading(false);
    }
  };

  const cancelOrder = async (orderId) => {
    setLoading(true);
    try {
      const response = await fetch(`/api/orders/${orderId}/cancel`, { method: 'POST' });
      const data = await response.json();
      if (!response.ok) throw new Error(data.message || data.error || 'Cancellation failed');
      setResult({ ...data, timestamp: new Date().toLocaleTimeString() });
      await refreshDashboard();
    } catch (error) {
      setResult({ status: 'ERROR', reason: error.message || 'Cancellation failed', timestamp: new Date().toLocaleTimeString() });
    } finally {
      setLoading(false);
    }
  };

  const syncSupplierOrders = async () => {
    setLoading(true);
    try {
      await fetch('/api/supplier/sync', { method: 'POST' });
      await refreshDashboard();
    } catch (e) {
      console.error(e);
    } finally {
      setLoading(false);
    }
  };

  const triggerManualReorder = async (productId) => {
    setLoading(true);
    try {
      await fetch(`/api/supplier/reorder/${productId}`, { method: 'POST' });
      await refreshDashboard();
    } catch (e) {
      console.error(e);
    } finally {
      setLoading(false);
    }
  };

  const productFor = (productId) => products.find((product) => product.productId === productId);
  const statusClass = result?.status?.toLowerCase() || 'error';
  const totalUnits = products.reduce((total, product) => total + product.stock, 0);
  const lowStockProducts = products.filter((product) => product.stock < LOW_STOCK_THRESHOLD).length;
  const confirmedOrders = orders.filter((order) => order.status === 'CONFIRMED').length;

  return (
    <div className="container">
      <header className="app-header">
        <div className="header-top">
          <div className="badge-row">
            <span className="badge">Modular Monolith</span>
            <span className="badge event-badge">LegacySupply Anti-Corruption Layer</span>
          </div>
          <span className={`connection ${apiConnected ? 'online' : 'offline'}`}>
            <span className="connection-dot" />{apiConnected ? 'Backend connected' : 'Backend unavailable'}
          </span>
          <span className={`connection ${supplierAvailable === true ? 'online' : supplierAvailable === false ? 'offline' : 'checking'}`}>
            <span className="connection-dot" />{supplierAvailable === true
              ? 'LegacySupply available'
              : supplierAvailable === false ? 'LegacySupply outage' : 'Checking LegacySupply'}
          </span>
        </div>
        <div className="hero-layout">
          <div className="hero-copy">
            <span className="eyebrow">Operations workspace</span>
            <h1>Order, Inventory & Supplier Dashboard</h1>
            <p>Multi-item orders, atomic inventory reservations, cancellation restocks, and LegacySupply ACL integration.</p>
          </div>
          <div className="hero-stats" aria-label="Live dashboard summary">
            <div><strong>{products.length}</strong><span>Products</span></div>
            <div><strong>{totalUnits}</strong><span>Units in stock</span></div>
            <div className={lowStockProducts ? 'attention-stat' : ''}><strong>{lowStockProducts}</strong><span>Need reorder</span></div>
            <div><strong>{supplierOrders.length}</strong><span>Supplier POs</span></div>
          </div>
        </div>
      </header>

      <main className="dashboard-grid">
        <section className="panel order-panel">
          <div className="panel-heading">
            <div>
              <span className="section-kicker">Order entry</span>
              <h2>Build an order</h2>
              <p>Add one or more products to a cart, then submit them as one atomic order.</p>
            </div>
          </div>

          <form onSubmit={submitOrder}>
            <div className="product-picker">
              <label htmlFor="product-select">Product</label>
              <select id="product-select" value={selectedProductId}
                onChange={(event) => setSelectedProductId(event.target.value)} disabled={loading}>
                {products.map((product) => (
                  <option key={product.productId} value={product.productId}>
                    {product.productId} - {product.name} ({product.stock} in stock)
                  </option>
                ))}
              </select>
              <label htmlFor="product-quantity">Quantity</label>
              <input id="product-quantity" type="number" min="1" max="999" value={selectedQuantity}
                onChange={(event) => setSelectedQuantity(event.target.value)} disabled={loading} />
              <button type="button" className="button secondary" onClick={addToCart} disabled={loading}>
                Add to cart
              </button>
            </div>

            <div className="cart">
              <div className="cart-title"><h3>Cart</h3><span>{cart.length} line{cart.length === 1 ? '' : 's'}</span></div>
              {!cart.length && <p className="empty-copy">Your cart is empty. Add a product to create an order.</p>}
              {cart.map((line) => {
                const product = productFor(line.productId);
                return (
                  <div className="cart-line" key={line.productId}>
                    <div><strong>{line.productId}</strong><span>{product?.name || 'Unknown product'}</span></div>
                    <input aria-label={`${line.productId} quantity`} type="number" min="1" max="999"
                      value={line.quantity} disabled={loading}
                      onChange={(event) => changeCartQuantity(line.productId, event.target.value)} />
                    <button type="button" className="icon-button" aria-label={`Remove ${line.productId}`}
                      onClick={() => removeCartLine(line.productId)} disabled={loading}>Remove</button>
                  </div>
                );
              })}
            </div>

            <button type="submit" className="button primary" disabled={loading || !cart.length}>
              {loading ? 'Processing...' : 'Submit multi-item order'}
            </button>
          </form>
        </section>

        <section className="panel result-panel" aria-live="polite">
          <span className="section-kicker">Transaction response</span>
          <h2>Latest result</h2>
          {!result && <p className="empty-copy">Order and cancellation responses will appear here.</p>}
          {result && (
            <div className={`result ${statusClass}`}>
              <div className="result-title"><strong>{result.status}</strong><span>{result.timestamp}</span></div>
              {result.orderId && <p>Order #{result.orderId}</p>}
              {result.reason && <p className="reason">{result.reason}</p>}
              {result.items?.length > 0 && (
                <ul className="result-items">
                  {result.items.map((item, index) => (
                    <li key={`${item.productId}-${index}`}><code>{item.productId}</code> x {item.quantity}: {item.outcome}</li>
                  ))}
                </ul>
              )}
              <details><summary>Response payload</summary><pre>{JSON.stringify(result, null, 2)}</pre></details>
            </div>
          )}
        </section>
      </main>

      <section className="panel inventory-panel">
        <div className="panel-heading">
          <div><span className="section-kicker">Warehouse snapshot</span><h2>Live inventory</h2><p>Refreshes after each order, cancellation, and supplier delivery.</p></div>
          <button type="button" className="button secondary compact" onClick={refreshDashboard} disabled={loading}>Refresh</button>
        </div>
        <div className="table-wrap">
          <table>
            <thead><tr><th>Product</th><th>Name</th><th>Stock</th><th>State</th><th>Action</th></tr></thead>
            <tbody>
              {products.map((product) => {
                const state = product.stock <= 0 ? 'out' : product.stock < LOW_STOCK_THRESHOLD ? 'low' : 'available';
                return <tr key={product.productId} className={state === 'low' ? 'low-stock' : state === 'out' ? 'out-stock' : ''}>
                  <td><code>{product.productId}</code></td><td>{product.name}</td><td className="stock-number">{product.stock}</td>
                  <td><span className={`stock-state ${state}`}>{state === 'out' ? 'Out of stock' : state === 'low' ? 'Reorder needed' : 'Available'}</span></td>
                  <td>
                    <button type="button" className="button secondary compact" onClick={() => triggerManualReorder(product.productId)} disabled={loading}>
                      Reorder with Supplier
                    </button>
                  </td>
                </tr>;
              })}
            </tbody>
          </table>
        </div>
      </section>

      <section className="panel supplier-panel" style={{ marginTop: '24px' }}>
        <div className="panel-heading">
          <div>
            <span className="section-kicker">Anti-Corruption Layer (ACL)</span>
            <h2>LegacySupply Purchase Orders</h2>
            <p>Track external replenishment purchase orders, XML transmissions, and delivery restocks.</p>
          </div>
          <div className="supplier-actions">
            <span className={`status ${supplierAvailable === true ? 'available' : 'outage'}`}>
              {supplierAvailable === true ? 'Supplier online' : 'Supplier unavailable'}
            </span>
            <button type="button" className="button secondary compact" onClick={syncSupplierOrders} disabled={loading}>Sync Status</button>
          </div>
        </div>
        <div className="table-wrap">
          {!supplierOrders.length && <p className="empty-copy">No supplier orders placed yet. Reorders trigger automatically when stock falls below 5.</p>}
          {supplierOrders.length > 0 && (
            <table>
              <thead>
                <tr>
                  <th>ID</th>
                  <th>Product</th>
                  <th>PO Number</th>
                  <th>Buyer Ref</th>
                  <th>Supplier SKU</th>
                  <th>Cases / Units</th>
                  <th>Status</th>
                  <th>Retries</th>
                  <th>Details / Error</th>
                </tr>
              </thead>
              <tbody>
                {supplierOrders.map((so) => (
                  <tr key={so.id}>
                    <td><code>#{so.id}</code></td>
                    <td><code>{so.productId}</code></td>
                    <td><code>{so.poNumber || 'PENDING'}</code></td>
                    <td><small>{so.buyerRef}</small></td>
                    <td><code>{so.supplierSku}</code></td>
                    <td>{so.cases} cs / {so.units} units</td>
                    <td><span className={`status ${so.status?.toLowerCase()}`}>{so.status}</span></td>
                    <td>{so.retryCount}</td>
                    <td><small>{so.failureReason || '—'}</small></td>
                  </tr>
                ))}
              </tbody>
            </table>
          )}
        </div>
      </section>

      <div className="feed-grid" style={{ marginTop: '24px' }}>
        <section className="panel">
          <div className="feed-heading"><div><span className="section-kicker">Audit trail</span><h2>Order history</h2></div><span className="count-chip">{orders.length}</span></div>
          {!orders.length && <p className="empty-copy">No orders have been submitted.</p>}
          <div className="order-list">
            {orders.map((order) => (
              <article className="order-card" key={order.orderId}>
                <div className="order-card-header"><strong>Order #{order.orderId}</strong><span className={`status ${order.status.toLowerCase()}`}>{order.status}</span></div>
                <ul>{(order.items || []).map((item) => <li key={item.itemId || item.productId}><code>{item.productId}</code> x {item.quantity}</li>)}</ul>
                {order.reason && <p className="reason">{order.reason}</p>}
                <div className="order-card-footer"><span>{order.createdAt ? new Date(order.createdAt).toLocaleString() : ''}</span>
                  {order.status === 'CONFIRMED' && <button type="button" className="button danger compact" onClick={() => cancelOrder(order.orderId)} disabled={loading}>Cancel and restock</button>}
                </div>
              </article>
            ))}
          </div>
        </section>

        <section className="panel">
          <div className="feed-heading"><div><span className="section-kicker">Event stream</span><h2>Notification activity</h2></div><span className="count-chip accent-chip">{notifications.length}</span></div>
          {!notifications.length && <p className="empty-copy">Order and low-stock events will be logged here.</p>}
          <ol className="notification-list">
            {notifications.map((notification) => <li key={notification.notificationId}>
              <span className="notification-marker" /><div><p>{notification.message}</p><time>{notification.createdAt ? new Date(notification.createdAt).toLocaleString() : ''}</time></div>
            </li>)}
          </ol>
        </section>
      </div>
    </div>
  );
}
