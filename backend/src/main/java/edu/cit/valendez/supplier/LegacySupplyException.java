package edu.cit.valendez.supplier;

class LegacySupplyException extends Exception {

    private final String errorCode;

    public LegacySupplyException(String errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public LegacySupplyException(String errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }

    public String getErrorCode() {
        return errorCode;
    }
}

