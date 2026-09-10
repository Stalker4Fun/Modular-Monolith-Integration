import React, { useState, useEffect } from 'react';

const INITIAL_PRODUCTS = [
  { productId: 'P100', name: 'Wireless Mouse', stock: 25 },
  { productId: 'P200', name: 'Mechanical Keyboard', stock: 10 },
  { productId: 'P300', name: 'USB-C Hub', stock: 0 },
];

export default function App() {
  const [products, setProducts] = useState(INITIAL_PRODUCTS);
  const [selectedProductId, setSelectedProductId] = useState('P100');
  const [quantity, setQuantity] = useState(1);
  const [loading, setLoading] = useState(false);
  const [result, setResult] = useState(null);
  const [orders, setOrders] = useState([]);
  const [apiConnected, setApiConnected] = useState(false);

  // Fetch live inventory
  const fetchInventory = async () => {
    try {
      const res = await fetch('/api/inventory');
      if (res.ok) {
        const data = await res.json();
        if (Array.isArray(data) && data.length > 0) {
          setProducts(data);
          setApiConnected(true);
        }
      }
    } catch (err) {
      console.warn('API /api/inventory not reachable yet, using initial seed data.', err);
    }
  };

  // Fetch recent orders
  const fetchOrders = async () => {
    try {
      const res = await fetch('/api/orders');
      if (res.ok) {
        const data = await res.json();
        if (Array.isArray(data)) {
          setOrders(data);
        }
      }
    } catch (err) {
      // ignore if offline
    }
  };

  useEffect(() => {
    fetchInventory();
    fetchOrders();
  }, []);

  // Handle Order Submission (POST /api/orders)
  const handleSubmit = async (e) => {
    e.preventDefault();
    if (!selectedProductId) return;

    setLoading(true);
    setResult(null);

    const payload = {
      productId: selectedProductId,
      quantity: parseInt(quantity, 10) || 1,
    };

    try {
      const res = await fetch('/api/orders', {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          'Accept': 'application/json',
        },
        body: JSON.stringify(payload),
      });

      const data = await res.json();
      setResult({
        ...data,
        timestamp: new Date().toLocaleTimeString(),
        requestPayload: payload,
      });

      // Refresh inventory and order history
      await fetchInventory();
      await fetchOrders();
    } catch (err) {
      setResult({
        status: 'ERROR',
        reason: err.message || 'Network error communicating with backend',
        inventory: null,
        timestamp: new Date().toLocaleTimeString(),
        requestPayload: payload,
      });
    } finally {
      setLoading(false);
    }
  };

  // Quick test triggers
  const handleQuickTest = (prodId, qty) => {
    setSelectedProductId(prodId);
    setQuantity(qty);
  };

  const selectedProduct = products.find((p) => p.productId === selectedProductId);

  return (
    <div className="container">
      <header className="app-header">
        <div className="badge-wrapper">
          <span className="badge module-badge">Modular Monolith</span>
          <span className="badge inprocess-badge">In-Process Integration</span>
          <span className={`badge status-badge ${apiConnected ? 'connected' : 'offline'}`}>
            {apiConnected ? '● Backend Connected' : '○ Standby / Offline'}
          </span>
        </div>
        <h1>Modular Monolith Store</h1>
        <p className="subtitle">
          In-process Integration between <strong>Order</strong> (<code>edu.cit.valendez.shop</code>) and{' '}
          <strong>Inventory</strong> (<code>edu.cit.valendez.inventory</code>) via Supabase PostgreSQL
        </p>
      </header>

      {/* Live Inventory Overview Cards */}
      <section className="card-section">
        <h2>Live Warehouse Inventory</h2>
        <div className="inventory-grid">
          {products.map((item) => {
            const isOutOfStock = item.stock <= 0;
            return (
              <div
                key={item.productId}
                className={`inventory-card ${isOutOfStock ? 'out-of-stock' : ''} ${
                  selectedProductId === item.productId ? 'selected' : ''
                }`}
                onClick={() => setSelectedProductId(item.productId)}
              >
                <div className="card-header">
                  <span className="product-id">{item.productId}</span>
                  <span className={`stock-pill ${isOutOfStock ? 'pill-empty' : 'pill-available'}`}>
                    {isOutOfStock ? 'Out of Stock' : `${item.stock} in stock`}
                  </span>
                </div>
                <h3 className="product-name">{item.name}</h3>
                <div className="card-footer">
                  <span>Available: <strong>{item.stock} units</strong></span>
                </div>
              </div>
            );
          })}
        </div>
      </section>

      {/* Order Placement Form */}
      <main className="main-content">
        <section className="form-section">
          <h2>Place a New Order</h2>
          <form onSubmit={handleSubmit} className="order-form">
            <div className="form-group">
              <label htmlFor="product-select">Select Product</label>
              <select
                id="product-select"
                value={selectedProductId}
                onChange={(e) => setSelectedProductId(e.target.value)}
                disabled={loading}
              >
                {products.map((p) => (
                  <option key={p.productId} value={p.productId}>
                    {p.productId} - {p.name} ({p.stock} in stock)
                  </option>
                ))}
              </select>
            </div>

            <div className="form-group">
              <label htmlFor="quantity-input">Quantity</label>
              <input
                id="quantity-input"
                type="number"
                min="1"
                max="999"
                value={quantity}
                onChange={(e) => setQuantity(e.target.value)}
                disabled={loading}
                required
              />
              {selectedProduct && selectedProduct.stock > 0 && (
                <small className="helper-text">
                  Max available: {selectedProduct.stock} units
                </small>
              )}
            </div>

            <div className="quick-test-bar">
              <span className="quick-test-label">Quick Test Scenarios:</span>
              <button
                type="button"
                className="btn-pill"
                onClick={() => handleQuickTest('P100', 2)}
              >
                Confirmed: P100 (qty 2)
              </button>
              <button
                type="button"
                className="btn-pill"
                onClick={() => handleQuickTest('P300', 1)}
              >
                Rejected: P300 (qty 1 - Zero Stock)
              </button>
              <button
                type="button"
                className="btn-pill"
                onClick={() => handleQuickTest('P200', 50)}
              >
                Rejected: P200 (qty 50 - Exceeds Stock)
              </button>
            </div>

            <button
              type="submit"
              className="btn btn-primary"
              disabled={loading || !selectedProductId || quantity < 1}
            >
              {loading ? 'Processing Order...' : 'Place Order (POST /api/orders)'}
            </button>
          </form>
        </section>

        {/* Result Area */}
        <section className="result-section">
          <h2>Order Result Area</h2>
          {!result && !loading && (
            <div className="result-placeholder">
              <p>Submit an order above to view the in-process integration result.</p>
              <p className="placeholder-sub">
                Expect <code>CONFIRMED</code> when stock is available or <code>REJECTED</code> when requested quantity exceeds stock.
              </p>
            </div>
          )}

          {loading && (
            <div className="loading-spinner-box">
              <div className="spinner"></div>
              <p>Calling in-process InventoryService &amp; persisting order...</p>
            </div>
          )}

          {result && !loading && (
            <div
              className={`result-box ${
                result.status === 'CONFIRMED'
                  ? 'box-confirmed'
                  : result.status === 'REJECTED'
                  ? 'box-rejected'
                  : 'box-error'
              }`}
            >
              <div className="result-status-header">
                <span className={`status-badge-lg ${result.status.toLowerCase()}`}>
                  {result.status === 'CONFIRMED' ? '✓ CONFIRMED' : '✕ REJECTED'}
                </span>
                <span className="timestamp">{result.timestamp}</span>
              </div>

              {result.orderId && (
                <p className="order-id-line">
                  Order ID: <strong>#{result.orderId}</strong>
                </p>
              )}

              {result.reason && (
                <div className="rejection-reason">
                  <strong>Rejection Reason:</strong> {result.reason}
                </div>
              )}

              {result.status === 'CONFIRMED' && (
                <div className="confirmation-details">
                  <p>Order successfully confirmed and recorded to the <code>orders</code> table!</p>
                </div>
              )}

              {result.inventory && (
                <div className="inventory-snapshot">
                  <h4>Inventory State:</h4>
                  <ul>
                    <li>Product: <strong>{result.inventory.name} ({result.inventory.productId})</strong></li>
                    <li>
                      Remaining Stock:{' '}
                      <strong className={result.inventory.stock <= 0 ? 'text-danger' : 'text-success'}>
                        {result.inventory.stock} units
                      </strong>
                    </li>
                  </ul>
                </div>
              )}

              <details className="raw-response-details" open>
                <summary>Raw API Response Payload (REST Contract)</summary>
                <pre>{JSON.stringify(result, null, 2)}</pre>
              </details>
            </div>
          )}
        </section>
      </main>

      {/* Order Audit History Table */}
      {orders.length > 0 && (
        <section className="history-section">
          <h2>Order Audit Log (Database: <code>orders</code> table)</h2>
          <div className="table-responsive">
            <table className="orders-table">
              <thead>
                <tr>
                  <th>Order ID</th>
                  <th>Product ID</th>
                  <th>Quantity</th>
                  <th>Status</th>
                  <th>Reason</th>
                  <th>Created At</th>
                </tr>
              </thead>
              <tbody>
                {orders.map((o) => (
                  <tr key={o.orderId}>
                    <td>#{o.orderId}</td>
                    <td><code>{o.productId}</code></td>
                    <td>{o.quantity}</td>
                    <td>
                      <span className={`badge-pill ${o.status.toLowerCase()}`}>
                        {o.status}
                      </span>
                    </td>
                    <td>{o.reason || '—'}</td>
                    <td>{new Date(o.createdAt).toLocaleString()}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </section>
      )}

      <footer className="app-footer">
        <p>
          Modular Monolith Integration Lab &bull; Package: <code>edu.cit.valendez</code> &bull; Student: Denzel Valendez
        </p>
      </footer>
    </div>
  );
}

