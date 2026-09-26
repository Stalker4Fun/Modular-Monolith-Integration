# Lab 3 Reflection — LegacySupply Integration & ACL

**Student ID**: `21-3360-213`
**API key**: configured locally through `LS_API_KEY` and not committed
**Reflection date**: September 26, 2026

---

## Question 1

### LegacySupply holds more than one order for BuyerRef `RO-1`: `PO-100230` (11:59:14), `PO-100254` (13:37:53), and `PO-100263` (18:19:25). Reconstruct the sequence of events that produced the duplicate, and describe the change you made (or would make) so it cannot happen again.

`BuyerRef` was originally derived from the local supplier-order primary key: after saving a new local row, the adapter changed its reference to `RO-<local id>`. That reference is not the idempotency key. It is only a business label that LegacySupply stores, and the interface manual explicitly says LegacySupply does not check it for uniqueness.

The three remote POs show that the local sequence was restarted or a fresh local database was used more than once, so each fresh store assigned its first supplier-order row ID `1` and the adapter reused `RO-1`. Each submission also had a different generated `X-Request-Id`; otherwise LegacySupply would have returned the previous acknowledgement as an idempotent replay instead of creating another PO. The application could also emit repeated low-stock events while stock remained below the threshold, which made repeated replenishment attempts possible in the earlier implementation.

I changed the adapter so `reorderProduct` first looks for an existing open order for the same product in `PENDING`, `ACCEPTED`, `PICKING`, or `SHIPPED` status. If one exists, it returns that order and sends no second POST to LegacySupply. This prevents repeated low-stock events or use of the UI's reorder action from creating another active replenishment order for that product.

For a fully restart-safe reference, I would also generate and persist a non-recycled buyer reference such as `RO-<UUID>` when the local order is first created, and add a database uniqueness constraint for `buyer_ref`. The UUID still fits LegacySupply's 40-character BuyerRef limit, unlike a reference based only on a resettable local numeric ID.

---

## Question 2

### At 11:30:39 your request for BuyerRef `RO-OUTC-V2-2` (X-Request-Id `outcatch-v2-2-113038`) received a 503, but LegacySupply had already created `PO-100226`. Walk through exactly what your adapter did next, and explain why that did or did not result in a second order.

This is the ambiguous-response failure case: the supplier accepted and committed the purchase order, but the client received a `503` instead of the acknowledgement. The adapter could therefore not safely assume that no order existed.

`LegacySupplyClient` translated the non-success response into a `LegacySupplyException`. `SupplierGatewayImpl` kept the local supplier order in `PENDING`, incremented its retry count, saved the failure reason, and scheduled the next retry. It deliberately retained the original BuyerRef, XML body, and, most importantly, the original `X-Request-Id` (`outcatch-v2-2-113038`). The scheduler later replayed that exact request after the backoff period.

LegacySupply recognised the identical request ID and payload as an idempotent replay and returned the acknowledgement for the already-created `PO-100226`. The adapter then saved that PO number locally and changed the local status to the acknowledgement status. It did not create a second order because it never generated a new request ID or changed the request content between attempts. If the request ID had changed, the retry would have been a distinct supplier order and could have created a duplicate.

---

## Question 3

### `PO-100050` (BuyerRef `RO-P300-01`) ended with StatusCode 90, which is not in the documentation. How did you work out what it means, and what does your system now do with the stock that will never arrive?

I identified the code by tracking `PO-100050` with `GET /purchase-orders/PO-100050`. The returned XML contained `<StatusCode>90</StatusCode>`. The published manual lists only the normal lifecycle codes 10 (Accepted), 20 (Picking), 30 (Shipped), and 40 (Delivered), so 90 had to be interpreted from the observed terminal behaviour: it represented a cancelled or unfulfillable supplier order rather than a delayed delivery.

The ACL maps both `90` and `50` to the internal `CANCELLED` status in `SupplierOrderStatus`. Cancelled orders are excluded from the scheduler's active-status query, so they are no longer polled. The delivery event is published only for a transition to `DELIVERED` (status 40), so no `SupplierOrderDeliveredEvent` is emitted for `PO-100050` and no inventory is restocked for stock that will not arrive. The product remains low or out of stock until a later, separate replenishment order is placed.
