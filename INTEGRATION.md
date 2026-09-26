# Lab 3 — LegacySupply Integration & Anti-Corruption Layer (ACL)

**Student ID**: `21-3360-213`  
**Base URL**: `https://legacysupply.onrender.com/api/v1`  
LegacySupply Self-Check**  

---

## Architectural Overview

This document details the Anti-Corruption Layer (ACL) built within the modular monolith package `edu.cit.valendez.supplier` to integrate with the external **LegacySupply** replenishment system (`https://legacysupply.onrender.com/api/v1`).

The goal of the ACL is to isolate legacy, third-party protocol details (XML formats, session headers, external SKUs, case/pack unit conversions, external status codes) from our domain model (`Order`, `Inventory`, `Notification`). 

```
                                      IN-MONOLITH DOMAIN
 ┌─────────────────┐  LowStockEvent   ┌──────────────────────┐  SupplierOrderDeliveredEvent  ┌──────────────────┐
 │ Inventory Module│ ───────────────> │ Supplier ACL Module  │ ────────────────────────────> │ Inventory Module │
 └─────────────────┘                  │ edu.cit.valendez.    │                               └──────────────────┘
                                      │       supplier       │
                                      └──────────┬───────────┘
                                                 │ HTTP XML
                                                 ▼
                                      ┌──────────────────────┐
                                      │ External LegacySupply│
                                      └──────────────────────┘
```

---

## 1. Anti-Corruption Layer Package Boundary

- **Public Exports**:
  - `SupplierGateway`: Interface for triggering reorders, retrieving supplier order history, and syncing order status.
  - `SupplierOrderDto`: Domain DTO exposing clean status summary without third-party XML annotations.
  - `SupplierOrderController`: Public REST controller (`/api/supplier/*`).
- **Package-Private Encapsulation** (`edu.cit.valendez.supplier`):
  - `SupplierOrder`: JPA Entity for persisting purchase order state in `supplier_orders`.
  - `SupplierOrderRepository`: Spring Data JPA repository.
  - `SupplierOrderStatus`: Domain enum (`PENDING`, `ACCEPTED`, `PICKING`, `SHIPPED`, `DELIVERED`, `FAILED`).
  - `SupplierProductMapper`: Translates internal product IDs to external SKUs and calculates case quantities.
  - `LegacySupplySessionManager`: Handles token authentication via `POST /auth/token`, caching, and thread-safe renewal.
  - `LegacySupplyClient`: Formats request/response XML documents, executes HTTP calls with 3-second timeouts, and translates third-party errors (`LSError`).
  - `XmlAuthRequest`, `XmlAuthResponse`, `XmlPurchaseOrder`, `XmlPurchaseOrderAck`, `XmlPurchaseOrderStatus`, `XmlLSError`: Package-private Jackson XML DTOs.

---

## 2. Real Product Catalog & Unit-of-Measure Mapping

LegacySupply processes orders in **cases** based on each item's `PackSize`. The ACL converts internal requested units into whole case orders.

$$\text{Cases} = \left\lceil \frac{\text{Target Units}}{\text{PackSize}} \right\rceil$$

| Internal Product ID | Item Description | Supplier Catalog SKU | Pack Size | Target Reorder Units | Calculated Cases Sent | Restocked Units on Delivery |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| `P100` | Wireless Mouse 2.4GHz | `ZTY-3082` | 6 units / case | 10 units | 2 cases (`Uom="CS"`) | 12 units |
| `P200` | Keyboard Mech TKL | `ZTY-8985` | 12 units / case | 10 units | 1 case (`Uom="CS"`) | 12 units |
| `P300` | USB Hub 4-Port | `ZTY-1364` | 10 units / case | 10 units | 1 case (`Uom="CS"`) | 10 units |

---

## 3. Session Authentication & Protocol Translation

1. **Credentials**: Client ID (`LS_CLIENT_ID` = `21-3360-213`) and API key (`LS_API_KEY` read from environment / `.env`).
2. **Session Lifecycle**:
   - `LegacySupplySessionManager` sends `POST /auth/token` with an `<AuthRequest>` XML body.
   - Upon receiving `200 OK` with `<AuthResponse>`, the returned `SessionToken` is cached.
   - All subsequent requests supply `X-LS-Session: <SessionToken>`.
   - If LegacySupply returns `401` or `E-AUTH-*`, the token is invalidated and automatically re-acquired on retry.

---

## 4. Idempotency, Timeouts & Retry Resilience

- **Idempotent Transmission**:
  - Each supplier order generates a stable `X-Request-Id` (UUID) stored in `supplier_orders.request_id`.
  - Sends a unique, deterministic `BuyerRef` formatted as `RO-<supplier_order_id>`.
  - Re-transmitting with the same `X-Request-Id` prevents duplicate order placement on LegacySupply (`E-IDEM-04` protection).
- **Strict Timeout Control**:
  - All HTTP requests to LegacySupply enforce a strict 3-second timeout (`Duration.ofSeconds(3)`).
- **Retry Mechanism**:
- Unsuccessful transient requests (e.g. network failure, 503 service unavailable, rate limiting) record an increased `retry_count`, a persisted `next_retry_at`, and retain status `PENDING`.
- An `@Scheduled` background job (`SupplierOrderScheduler`) retries `PENDING` orders after exponential backoff (15 seconds through a five-minute cap) using the exact same `X-Request-Id` and `BuyerRef` until LegacySupply accepts them. Malformed or contradictory requests are marked `FAILED` instead.

---

## 5. Event-Driven Inventory Delivery Restock

1. **Trigger Reorder**: When stock drops below 5, `InventoryServiceImpl` publishes `LowStockEvent`. `LowStockEventListener` in `edu.cit.valendez.supplier` catches the event and invokes `supplierGateway.reorderProduct(...)`.
2. **Polling & Status Synchronization**: `SupplierOrderScheduler` polls active orders via `GET /purchase-orders/{poNumber}`.
3. **Delivery Restock**: When LegacySupply status transitions to `40` (`DELIVERED`), the ACL publishes `SupplierOrderDeliveredEvent(productId, units, poNumber)` into the domain event bus.
4. **Restock Execution**: `InventoryEventListener` in `edu.cit.valendez.inventory` receives `SupplierOrderDeliveredEvent` and executes `inventoryService.restock(productId, units)`.

---

## 6. Database Schema (`supplier_orders`)

```sql
CREATE TABLE supplier_orders (
    id BIGSERIAL PRIMARY KEY,
    product_id VARCHAR(50) NOT NULL,
    buyer_ref VARCHAR(100) NOT NULL,
    request_id VARCHAR(100) NOT NULL,
    po_number VARCHAR(100),
    supplier_sku VARCHAR(100) NOT NULL,
    cases INTEGER NOT NULL CHECK (cases > 0),
    units INTEGER NOT NULL CHECK (units > 0),
    status VARCHAR(30) NOT NULL,
    failure_reason VARCHAR(500),
    retry_count INTEGER NOT NULL DEFAULT 0,
    next_retry_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);
```

---

## 7. Empirical LegacySupply Live Verification Evidence

All operations were empirically verified against the live server `https://legacysupply.onrender.com/api/v1` for Student ID `21-3360-213`.

### Transmitted Purchase Orders on File

| PO Number | Buyer Reference | Supplier SKU | Order Qty (Cases) | Status Code | Server Creation Time (UTC) |
| :--- | :--- | :--- | :--- | :--- | :--- |
| `PO-100048` | `RO-P100-01` | `ZTY-3082` | 2 CS (12 units) | `10` (Accepted) | 2026-09-24T11:42:42.851Z |
| `PO-100049` | `RO-P200-01` | `ZTY-8985` | 1 CS (12 units) | `10` (Accepted) | 2026-09-24T11:42:43.192Z |
| `PO-100050` | `RO-P300-01` | `ZTY-1364` | 1 CS (10 units) | `10` (Accepted) | 2026-09-24T11:42:43.408Z |

### Self-Check API Response Summary (`https://legacysupply.onrender.com/verify`)

- **Signed in to LegacySupply**: `MET` (5 sign-ins)
- **Read the catalog**: `MET` (1 catalog read)
- **Placed at least 3 purchase orders**: `MET` (3 orders on file)
- **Renews expired sessions**: `MET` (5 sign-ins, 0 requests with expired session)
- **Sends X-Request-Id on every order**: `MET` (3 of 3 requests)
- **No duplicate orders**: `MET` (0 duplicates)
- **Noticed a cancelled order**: `MET` (1 cancelled order seen: `PO-100050` with `StatusCode 90`)
- **Polls without hitting the rate limit**: `MET` (24 status checks, 0 rate-limited)

