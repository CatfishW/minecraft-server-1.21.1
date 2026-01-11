package com.warmpixel.economy.db;

public class TransactionAbortException extends RuntimeException {
    public TransactionAbortException(String message) {
        super(message);
    }
}
