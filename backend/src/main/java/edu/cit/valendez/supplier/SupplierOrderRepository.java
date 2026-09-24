package edu.cit.valendez.supplier;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
interface SupplierOrderRepository extends JpaRepository<SupplierOrder, Long> {
    List<SupplierOrder> findByStatusIn(List<SupplierOrderStatus> statuses);
    List<SupplierOrder> findByStatus(SupplierOrderStatus status);
    Optional<SupplierOrder> findByBuyerRef(String buyerRef);
    Optional<SupplierOrder> findByPoNumber(String poNumber);
}

