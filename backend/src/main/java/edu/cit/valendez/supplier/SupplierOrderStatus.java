package edu.cit.valendez.supplier;

enum SupplierOrderStatus {
    PENDING,
    ACCEPTED,
    PICKING,
    SHIPPED,
    DELIVERED,
    CANCELLED,
    FAILED;

    public static SupplierOrderStatus fromStatusCode(String statusCode) {
        if (statusCode == null) return PENDING;
        switch (statusCode.trim()) {
            case "10":
            case "Accepted":
                return ACCEPTED;
            case "20":
            case "Picking":
                return PICKING;
            case "30":
            case "Shipped":
                return SHIPPED;
            case "40":
            case "Delivered":
                return DELIVERED;
            case "50":
            case "90":
            case "Cancelled":
            case "CANCELLED":
                return CANCELLED;
            default:
                return PENDING;
        }
    }
}

