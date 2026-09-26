package edu.cit.valendez.supplier;

import edu.cit.valendez.events.SupplierOrderDeliveredEvent;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SupplierGatewayImplTest {

    @Test
    void reusesAnOpenOrderForTheSameProductInsteadOfCreatingADuplicate() throws Exception {
        SupplierOrderRepository repository = mock(SupplierOrderRepository.class);
        LegacySupplyClient client = mock(LegacySupplyClient.class);
        ApplicationEventPublisher publisher = mock(ApplicationEventPublisher.class);
        SupplierGatewayImpl gateway = new SupplierGatewayImpl(
                repository, new SupplierProductMapper(), client, publisher);

        SupplierOrder existing = new SupplierOrder("P200", "RO-EXISTING", "existing-request-id", "ZTY-8985", 1, 12);
        existing.setStatus(SupplierOrderStatus.ACCEPTED);
        when(repository.findFirstByProductIdAndStatusInOrderByCreatedAtDesc(anyString(), anyList()))
                .thenReturn(Optional.of(existing));

        SupplierOrderDto result = gateway.reorderProduct("P200", 10);

        assertEquals("RO-EXISTING", result.getBuyerRef());
        verify(repository, never()).save(existing);
        verify(client, never()).placeOrder(anyString(), anyInt(), anyString(), anyString());
    }

    @Test
    void retainsA503OrderForAStableIdempotentReplayAfterBackoff() throws Exception {
        SupplierOrderRepository repository = mock(SupplierOrderRepository.class);
        LegacySupplyClient client = mock(LegacySupplyClient.class);
        ApplicationEventPublisher publisher = mock(ApplicationEventPublisher.class);
        SupplierGatewayImpl gateway = new SupplierGatewayImpl(
                repository, new SupplierProductMapper(), client, publisher);

        SupplierOrder order = new SupplierOrder("P100", "RO-OUTAGE", "outage-request-id", "ZTY-3082", 2, 12);
        when(repository.findByStatusIn(anyList())).thenReturn(List.of(order));
        when(client.placeOrder("ZTY-3082", 2, "RO-OUTAGE", "outage-request-id"))
                .thenThrow(new LegacySupplyException("E-SYS-99", "Service unavailable. Try later."));

        gateway.syncOrderStatus();

        assertEquals(SupplierOrderStatus.PENDING, order.getStatus());
        assertEquals(1, order.getRetryCount());
        assertNotNull(order.getNextRetryAt());
        verify(repository).save(order);
    }

    @Test
    void retriesAnOutageBlockedOrderEvenAfterThreeEarlierFailures() throws Exception {
        SupplierOrderRepository repository = mock(SupplierOrderRepository.class);
        LegacySupplyClient client = mock(LegacySupplyClient.class);
        ApplicationEventPublisher publisher = mock(ApplicationEventPublisher.class);
        SupplierGatewayImpl gateway = new SupplierGatewayImpl(
                repository, new SupplierProductMapper(), client, publisher);

        SupplierOrder order = new SupplierOrder("P100", "RO-42", "stable-request-id", "ZTY-3082", 2, 12);
        order.setRetryCount(3);
        when(repository.findByStatusIn(anyList())).thenReturn(List.of(order));

        XmlPurchaseOrderAck acknowledgement = new XmlPurchaseOrderAck();
        acknowledgement.setPoNumber("PO-OUTAGE-RECOVERED");
        acknowledgement.setStatusCode("10");
        when(client.placeOrder("ZTY-3082", 2, "RO-42", "stable-request-id"))
                .thenReturn(acknowledgement);

        gateway.syncOrderStatus();

        verify(client).placeOrder("ZTY-3082", 2, "RO-42", "stable-request-id");
        verify(repository).save(order);
        assertEquals(SupplierOrderStatus.ACCEPTED, order.getStatus());
        assertEquals("PO-OUTAGE-RECOVERED", order.getPoNumber());
        assertEquals(null, order.getNextRetryAt());
    }

    @Test
    void publishesDeliveryWhenTheSupplierReportsStatusForty() throws Exception {
        SupplierOrderRepository repository = mock(SupplierOrderRepository.class);
        LegacySupplyClient client = mock(LegacySupplyClient.class);
        ApplicationEventPublisher publisher = mock(ApplicationEventPublisher.class);
        SupplierGatewayImpl gateway = new SupplierGatewayImpl(
                repository, new SupplierProductMapper(), client, publisher);

        SupplierOrder order = new SupplierOrder("P200", "RO-43", "request-id", "ZTY-8985", 1, 12);
        order.setPoNumber("PO-DELIVERED");
        order.setStatus(SupplierOrderStatus.SHIPPED);
        when(repository.findByStatusIn(anyList())).thenReturn(List.of(order));

        XmlPurchaseOrderStatus status = new XmlPurchaseOrderStatus();
        status.setStatusCode("40");
        when(client.getOrderStatus("PO-DELIVERED")).thenReturn(status);

        gateway.syncOrderStatus();

        ArgumentCaptor<SupplierOrderDeliveredEvent> event = ArgumentCaptor.forClass(SupplierOrderDeliveredEvent.class);
        verify(publisher).publishEvent(event.capture());
        assertEquals(SupplierOrderStatus.DELIVERED, order.getStatus());
        assertEquals("P200", event.getValue().getProductId());
        assertEquals(12, event.getValue().getQuantity());
        assertEquals("PO-DELIVERED", event.getValue().getPoNumber());
    }
}
