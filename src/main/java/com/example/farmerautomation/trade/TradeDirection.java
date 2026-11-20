package com.example.farmerautomation.trade;

/** EARN: eşya verip zümrüt kazan. SPEND: zümrüt verip eşya al. */
public enum TradeDirection {
    EARN("Zümrüt kazan"),
    SPEND("Zümrüt harca");

    private final String label;

    TradeDirection(String label) { this.label = label; }

    public String label() { return label; }
}
