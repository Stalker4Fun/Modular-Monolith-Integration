package edu.cit.valendez.tiangge;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Spring Data JPA repository for TianggeOrder.
 * Package-private to enforce module isolation.
 */
@Repository
interface TianggeOrderRepository extends JpaRepository<TianggeOrder, Long> {

    Optional<TianggeOrder> findByTianggeOrderId(String tianggeOrderId);

    boolean existsByTianggeOrderId(String tianggeOrderId);

    List<TianggeOrder> findByProductIdAndStatus(String productId, TianggeOrderStatus status);

    List<TianggeOrder> findByStatus(TianggeOrderStatus status);
}
