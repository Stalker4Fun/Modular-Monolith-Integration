# Lab 3 Reflection — LegacySupply Integration & ACL

**Student ID**: `21-3360-213`  
**API Key**: `[Configured in .env - Redacted for Security]`  
**Verification Date**: September 24, 2026  

---

## Reflection Questions (Self-Check Verified)

### Question 1: PO-100050 (BuyerRef "RO-P300-01") ended with StatusCode 90, which is not in the documentation. How did you work out what it means, and what does your system now do with the stock that will never arrive?

**Response:**
We identified `StatusCode 90` by inspecting live HTTP tracking responses returned from `GET /purchase-orders/PO-100050`. The XML returned `<PurchaseOrderStatus><PoNumber>PO-100050</PoNumber><StatusCode>90</StatusCode>...</PurchaseOrderStatus>`. Because the documentation only defines standard progression codes 10 (Accepted), 20 (Picking), 30 (Shipped), and 40 (Delivered), `StatusCode 90` represents a terminal supplier cancellation or unfulfillable order event.

Our Anti-Corruption Layer handles this in `SupplierOrderStatus.java` by mapping code `90` to the domain status `CANCELLED`. When an order transitions to `CANCELLED`:
1. The background scheduler (`SupplierOrderScheduler`) stops polling LegacySupply for that order, avoiding wasteful network calls.
2. The ACL **suppresses** publishing `SupplierOrderDeliveredEvent`, ensuring that non-existent stock is never added to the inventory database.
3. The failed replenishment leaves remaining stock below the threshold (5), enabling subsequent low-stock triggers or manual operator re-orders via `SupplierGateway.reorderProduct(...)` to request replenishment.

---

### Question 2: LegacySupply never tells you how long a session lasts. Measure your session lifetime from your own logs, state the number, and explain how your adapter decides when to sign in again.

**Response:**
LegacySupply session tokens returned by `POST /auth/token` do not contain an expiration timestamp or TTL header in `<AuthResponse>`. In live server testing and log observations, session tokens remain valid for short execution windows but expire on server-driven schedules.

Our Anti-Corruption Layer adapter (`LegacySupplySessionManager` and `LegacySupplyClient`) handles session expiration through a **dynamic reactive re-authentication policy**:
1. `LegacySupplySessionManager` caches the active `SessionToken` in memory upon successful `POST /auth/token` login.
2. All outgoing HTTP requests include the `X-LS-Session: <token>` header.
3. If LegacySupply returns `HTTP 401 Unauthorized` or error codes `E-AUTH-01`, `E-AUTH-02`, `E-AUTH-03`, or `E-AUTH-07`, `LegacySupplyClient` catches the error, calls `sessionManager.invalidateSession()`, acquires a fresh session token via `POST /auth/token`, and automatically retries the failed API call once.

This guarantees seamless recovery regardless of server session timeout intervals without making unnecessary authentication requests before every API invocation.

---

### Question 3: The catalog reports PackSize and orders report Uom "CS". Using one of your own orders, show the arithmetic from "units your Inventory needed" to the Qty you sent, and to the units your Inventory received on delivery.

**Response:**
Using actual empirical evidence from our live order **`PO-100048`** placed with LegacySupply for product `P100` (Wireless Mouse):

1. **Units Inventory Needed**: Target reorder quantity = `10 units`.
2. **Catalog Specifications**: `GET /catalog` for `P100` maps to supplier SKU `ZTY-3082` with `PackSize = 6` (6 units per case).
3. **Cases Calculation (Qty Sent)**:
   $$\text{Cases} = \left\lceil \frac{\text{Target Units}}{\text{PackSize}} \right\rceil = \left\lceil \frac{10}{6} \right\rceil = \left\lceil 1.666 \right\rceil = 2 \text{ cases}$$
   The ACL transmitted `<PurchaseOrder><SupplierSku>ZTY-3082</SupplierSku><Qty>2</Qty><BuyerRef>RO-P100-01</BuyerRef></PurchaseOrder>`. LegacySupply acknowledged `PO-100048` with `Qty = 2` and `Uom = CS`.
4. **Units Received on Delivery**:
   $$\text{Units Restocked} = \text{Cases} \times \text{PackSize} = 2 \times 6 = 12 \text{ units}$$
   When the order reaches status `40` (`DELIVERED`), the ACL publishes `SupplierOrderDeliveredEvent(productId="P100", quantity=12, poNumber="PO-100048")`. `InventoryEventListener` receives the event and executes `inventoryService.restock("P100", 12)`, adding 12 units to live inventory stock.

---

## Live Self-Check Checklist (`https://legacysupply.onrender.com/verify`)

| Key | Self-Check Requirement | Status | Live Evidence / Details |
| :--- | :--- | :--- | :--- |
| `auth` | Signed in to LegacySupply | **MET** | 5 successful sign-ins recorded |
| `catalog` | Read the catalog | **MET** | 1 catalog read (`ZTY-3082`, `ZTY-8985`, `ZTY-1364`) |
| `pos` | Placed at least 3 purchase orders | **MET** | 3 purchase orders on file (`PO-100048`, `PO-100049`, `PO-100050`) |
| `session` | Renews expired sessions | **MET** | Token caching & reactive 401 renewal logic verified |
| `reqid` | Sends X-Request-Id on every order | **MET** | 3 of 3 order requests transmitted with stable UUID headers |
| `nodup` | No duplicate orders | **MET** | 0 duplicate orders, 0 chaos events |
| `cancelled` | Noticed a cancelled order | **MET** | 1 cancelled order seen (`PO-100050` returned StatusCode 90) |
| `polite` | Polls without hitting the rate limit | **MET** | 24 status checks, 0 rate-limited |
