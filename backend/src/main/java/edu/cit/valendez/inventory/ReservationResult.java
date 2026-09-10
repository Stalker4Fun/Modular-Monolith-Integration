package edu.cit.valendez.inventory;

public class ReservationResult {
    private boolean success;
    private String reason;
    private InventoryItemDto inventory;

    public ReservationResult() {
    }

    public ReservationResult(boolean success, String reason, InventoryItemDto inventory) {
        this.success = success;
        this.reason = reason;
        this.inventory = inventory;
    }

    public static ReservationResult confirmed(InventoryItemDto inventory) {
        return new ReservationResult(true, null, inventory);
    }

    public static ReservationResult rejected(String reason, InventoryItemDto inventory) {
        return new ReservationResult(false, reason, inventory);
    }

    public boolean isSuccess() {
        return success;
    }

    public void setSuccess(boolean success) {
        this.success = success;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public InventoryItemDto getInventory() {
        return inventory;
    }

    public void setInventory(InventoryItemDto inventory) {
        this.inventory = inventory;
    }
}

