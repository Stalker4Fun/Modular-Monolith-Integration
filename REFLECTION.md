# Lab 4 Reflection - Tiangge Marketplace Integration

**Student ID:** `21-3360-213`  
**Instance ID:** `21-3360-213`  
**Reflection date:** October 2, 2026

## Question 1

### Tiangge order TG-T3EG8Y (1 x P200) was accepted at 10:20:15. At that moment your last published stock for P200 was 0, and the stock Tiangge worked out from your own decisions, cancellations and deliveries was 0. Where did your application's stock figure come from, and why did it disagree?

`TianggeDecisionEngine.evaluateAndProcessOrder` gets the quantity from the local `InventoryService.getItem(productId)` and accepts the order when that local quantity covers the request; it does not ask Tiangge to calculate availability. When the local inventory table is empty, `InventoryDataInitializer` seeds P200 with 10 units, so a fresh or reset local database can make one unit appear available even while Tiangge's event ledger is at zero. The two figures disagreed because they came from separate state: Tiangge's ledger reflected its last published stock and marketplace decisions, while the application made its decision from its own database. The startup publisher and `StockChangedEvent` listener send local stock back to Tiangge, but those updates do not make the remote ledger the decision engine's source of truth.

## Question 2

### Event evt_ce1d16b09b9df15b (order TG-8F79RG) reached your application twice, as seq 1 and seq 2, and you processed it once. Show the code and the stored data that made the second delivery harmless, and explain what would happen if your application restarted between the two.

`TianggeFeedReaderService.processEvent` checks `existsByTianggeOrderId(orderId)` before creating or evaluating an order, and `TianggeOrder` enforces a database unique constraint on `tiangge_order_id`; after seq 1, the `tiangge_orders` row for `TG-8F79RG` therefore makes seq 2 a duplicate, so it skips the reservation and advances the cursor. The `feed_cursor` row stores `last_event_id`, and `FeedCursorService` resumes polling after that sequence on restart; both the order row and cursor update occur in the feed-processing transaction. With the same durable database, a restart after seq 1 leaves the order recorded and seq 2 is harmless; a crash before the transaction commits rolls back the row and cursor together, so the event can be replayed. This protection depends on durable storage: the configured default is in-memory H2, which loses both records on a process restart, so the restart guarantee requires the persistent database configuration.

## Question 3

### Order TG-PF2WJY was backordered at 10:20:17 and accepted at 11:04:17, after PO-100576 was delivered at 10:26:13. Trace how the delivery reached your Inventory and what then resumed the backordered order.

`SupplierOrderScheduler` polls active supplier POs every 15 seconds, and `SupplierGatewayImpl.syncOrderStatus` publishes a `SupplierOrderDeliveredEvent` when it observes PO-100576 change to `DELIVERED`; the P200 catalog mapping is 12 units per case, so this PO's delivered case replenishes 12 units. `InventoryEventListener` handles that event by calling `inventoryService.restock`, which persists the added stock and emits `StockChangedEvent`; `TianggeEventListener` forwards the new quantity to Tiangge. The supplier gateway also publishes `DeliveryReceivedEvent`, which the Tiangge listener uses to call `fulfillBackordersForProduct`; that method finds P200 orders still marked `BACKORDERED`, reserves stock for each that can now be covered, and changes its status to `ACCEPTED`. For TG-PF2WJY, the engine then calls `resolveBackorder` and sends the accepted decision, so the replenishment event and newly available local stock resumed the waiting order. The reported 38-minute gap from delivery to acceptance is longer than the configured polling interval, so these code paths explain the mechanism but not that delay; the corresponding runtime logs would be needed to account for it.
