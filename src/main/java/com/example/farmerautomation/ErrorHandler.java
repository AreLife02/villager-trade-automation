package com.example.farmerautomation;

public final class ErrorHandler {
    private static volatile String lastError = "Hata yok.";
    private static volatile String lastInfo = "Hazır.";

    private ErrorHandler() {
    }

    public static void error(String message) {
        lastError = message;
        System.out.println("[FarmerAutomation][HATA] " + message);
    }

    public static void info(String message) {
        lastInfo = message;
        System.out.println("[FarmerAutomation] " + message);
    }

    public static String getLastError() {
        return lastError;
    }

    public static String getLastInfo() {
        return lastInfo;
    }
}
