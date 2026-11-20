package com.example.farmerautomation.trade;

/**
 * Teklifteki bir eşya. id tam kimliktir (büyülü kitapta "#büyü=seviye" eki taşır),
 * baseId yalnızca kayıt kimliğidir ve envanter sayımında kullanılır.
 */
public record TradeItem(String id, String baseId, String name, int count) { }
