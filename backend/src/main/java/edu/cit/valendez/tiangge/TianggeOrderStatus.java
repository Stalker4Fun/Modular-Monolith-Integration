package edu.cit.valendez.tiangge;

/**
 * Domain status enum for Tiangge marketplace orders.
 * Package-private to enforce module isolation.
 */
enum TianggeOrderStatus {
    PENDING,
    ACCEPTED,
    REJECTED,
    BACKORDERED,
    CANCELLED
}
