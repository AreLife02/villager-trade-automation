package com.example.farmerautomation;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public final class AutomationConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public String shopCommand = "/shop";
    public String carrotItemName = "Havuç";
    public String emeraldItemName = "Zümrüt";
    public String redShulkerName = "Kırmızı Shulker Kutusu";
    public int targetCarrotStacks = 12;
    public int maxCarrotsPerPurchase = 64;
    public int villagerSearchRadius = 32;
    public int navigationTimeoutTicks = 600;
    public int actionTimeoutTicks = 100;
    public int tradeIntervalTicks = 10;
    public boolean openTradeScreenAfterNavigation = false;

    public boolean unlimitedCycle = false;
    public boolean storageEnabled = false;
    public int storageSearchRadius = 8;
    public Set<String> selectedStorageTypes = new LinkedHashSet<>(List.of("chest"));

    /** Seçili meslekler, öncelik sırasıyla (ilk = en yüksek öncelik). Varsayılan yalnızca çiftçi. */
    public List<String> professionOrder = new java.util.ArrayList<>(List.of("farmer"));
    /** UUID listesi: yönetim ekranında seçilen köylüler ve kullanıcının belirlediği sıra. */
    public List<String> selectedVillagerIds = new java.util.ArrayList<>();
    /** false ise eski davranış korunur; true ise yalnızca listedeki köylüler işlenir. */
    public boolean villagerSelectionEnabled = false;
    /** TradeAura-style outlines: selected villagers are green, other detected villagers are dim gray. */
    public Boolean villagerOverlayEnabled = true;
    /** Kullanıcının seçtiği takas kuralları; her mesleğin kuralları kendi içinde liste sırasıyla önceliklidir. */
    public List<com.example.farmerautomation.trade.TradeRule> tradeRules =
            com.example.farmerautomation.trade.DefaultTradeRules.create();

    private static AutomationConfig instance;

    private AutomationConfig() { }

    public static AutomationConfig get() {
        if (instance == null) instance = new AutomationConfig();
        return instance;
    }

    public static void load() {
        Path file = getConfigFile();
        if (!Files.exists(file)) {
            get().save();
            ErrorHandler.info("Yeni ayar dosyası oluşturuldu: " + file);
            return;
        }
        try {
            AutomationConfig loaded = GSON.fromJson(Files.readString(file, StandardCharsets.UTF_8), AutomationConfig.class);
            if (loaded == null) {
                ErrorHandler.error("Ayar dosyası boş veya bozuk. Varsayılan ayarlar kullanılacak.");
                return;
            }
            if (loaded.selectedStorageTypes == null) loaded.selectedStorageTypes = new LinkedHashSet<>(List.of("chest"));
            loaded.villagerSearchRadius = Math.max(8, Math.min(256, loaded.villagerSearchRadius));
            loaded.storageSearchRadius = Math.max(1, Math.min(32, loaded.storageSearchRadius));
            if (loaded.professionOrder == null) loaded.professionOrder = new java.util.ArrayList<>(List.of("farmer"));
            if (loaded.selectedVillagerIds == null) loaded.selectedVillagerIds = new java.util.ArrayList<>();
            // 10 client ticks are approximately 0.5 seconds at 20 TPS.
            boolean tradeIntervalUpdated = loaded.tradeIntervalTicks != 10;
            loaded.tradeIntervalTicks = 10;
            if (loaded.tradeRules == null || loaded.tradeRules.isEmpty()) {
                loaded.tradeRules = com.example.farmerautomation.trade.DefaultTradeRules.create();
                loaded.save();
            }
            instance = loaded;
            if (tradeIntervalUpdated) loaded.save();
            ErrorHandler.info("Ayarlar yüklendi: " + file);
        } catch (IOException | RuntimeException exception) {
            ErrorHandler.error("Ayar dosyası okunamadı: " + exception.getClass().getSimpleName());
        }
    }

    public boolean isStorageTypeSelected(String type) {
        return selectedStorageTypes != null && selectedStorageTypes.contains(type);
    }

    public void toggleStorageType(String type) {
        if (selectedStorageTypes == null) selectedStorageTypes = new LinkedHashSet<>();
        if (!selectedStorageTypes.add(type)) selectedStorageTypes.remove(type);
        save();
    }

    public void save() {
        Path file = getConfigFile();
        try {
            Files.createDirectories(file.getParent());
            Files.writeString(file, GSON.toJson(this), StandardCharsets.UTF_8);
        } catch (IOException exception) {
            ErrorHandler.error("Ayar dosyası kaydedilemedi: " + exception.getClass().getSimpleName());
        }
    }

    private static Path getConfigFile() {
        return FabricLoader.getInstance().getConfigDir().resolve("farmerautomation.json");
    }
}
