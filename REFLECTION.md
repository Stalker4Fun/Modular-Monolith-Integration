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

---

# Lab 4 Reflection — Tiangge Marketplace Integration

**Student ID**: `21-3360-213`  
**Instance ID**: `21-3360-213`  
**Reflection date**: October 2, 2026

---

## Question 1

### Tiangge order TG-T3EG8Y (1 x P200) was accepted at 10:20:15. At that moment your last published stock for P200 was 0, and the stock Tiangge worked out from your own decisions, cancellations and deliveries was 0. Where did your application's stock figure come from, and why did it disagree?

The stock figure evaluated by the decision engine came directly from the local inventory domain service (`InventoryService.getItem("P200").getStock()`), which queries the application's local `inventory` database table initialized during startup via `schema.sql` (where P200 has an initial balance of 25 units).

The local stock figure disagreed with Tiangge's calculated stock figure because Tiangge tracks available stock strictly based on stream accounting of historical stock publications (`PUT /stock`) and order decisions (`ACCEPTED` order reservations, `CANCELLED` order restocks). Before the application performs a bulk stock synchronization or pushes updated stock levels via `StockChangedEvent`, the internal domain logic evaluates order feasibility against local database balances. Because the local database contained unreserved stock units from schema initialization, the decision engine successfully reserved 1 unit of P200 and issued an `ACCEPTED` decision even though Tiangge's remote stream ledger calculated 0 units based solely on prior API traffic.

---

## Question 2

### Event evt_ce1d16b09b9df15b (order TG-8F79RG) reached your application twice, as seq 1 and seq 2, and you processed it once. Show the code and the stored data that made the second delivery harmless, and explain what would happen if your application restarted between the two.

The second delivery was rendered harmless by the deduplication check in `TianggeFeedReaderService.java` (`tianggeOrderRepository.existsByTianggeOrderId(orderId)`) backed by the database UNIQUE constraint on `tiangge_order_id` in the `tiangge_orders` table (`tiangge_order_id VARCHAR(64) UNIQUE NOT NULL`). Upon processing `seq 1`, the order `TG-8F79RG` was persisted to `tiangge_orders` with status `ACCEPTED`. When `seq 2` arrived, `TianggeFeedReaderService` detected that `existsByTianggeOrderId("TG-8F79RG")` returned `true`, logged the duplicate, advanced the cursor to `seq 2` via `FeedCursorService.updateCursor(2L)`, and skipped re-evaluating or double-reserving stock.

If the application had restarted between `seq 1` and `seq 2`, `FeedCursorService` would load `last_event_id = 1` from the stored `feed_cursor` table on startup. The feed reader would request `GET /feed?after=1` and receive `seq 2`. Because the order record `TG-8F79RG` remains safely persisted in `tiangge_orders`, the restarted instance would still detect the existing record, skip re-processing, update the cursor to `seq 2`, and maintain idempotent operation without double-reserving inventory.

---

## Question 3

### During your restart test your application was down for about 310 seconds while 8 orders arrived. How did the restarted application find those orders, and how did it avoid handling earlier ones again?

When the restarted application initialized, `TianggeStartupService` triggered feed polling using `FeedCursorService.getCurrentCursor()`, which retrieved the persistent sequence position `last_event_id` stored in the `feed_cursor` database table right before shutdown. The `TianggeClient` passed this cursor as the `after` query parameter in `GET /tiangge/v1/feed?after={last_event_id}&limit=50`. Because the Tiangge API feed uses monotonic sequence numbers (`seq`), passing `after={last_event_id}` instructed the server to return only the batch of 8 unread events generated during the 310-second downtime.

The application avoided re-handling earlier orders through a two-layered defense: first, the `?after` parameter filtered out all sequence numbers less than or equal to `last_event_id` at the API boundary; second, the feed processor sorted the incoming batch by `seq` ascending, checked `tianggeOrderRepository.existsByTianggeOrderId(orderId)`, and updated `feed_cursor` sequentially after each item. Any previously decided order was recognized by the `tiangge_orders` database constraint and skipped cleanly without re-executing stock reservations or decision calls.
