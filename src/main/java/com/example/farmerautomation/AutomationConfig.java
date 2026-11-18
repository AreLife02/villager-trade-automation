package com.example.farmerautomation;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public final class AutomationConfig {
    private static final Gson GSON = new GsonBuilder()
            .setPrettyPrinting()
            .create();

    public String shopCommand = "/shop";
    public String carrotItemName = "Havuç";
    public String emeraldItemName = "Zümrüt";
    public String redShulkerName = "Kırmızı Shulker Kutusu";
    public int targetCarrotStacks = 12;
    public int maxCarrotsPerPurchase = 64;
    public int villagerSearchRadius = 32;
    public int navigationTimeoutTicks = 600;
    public int actionTimeoutTicks = 100;
    /** İki takas seçimi arası en az tick (20 tick = 1 sn). 10 = 0,5 sn. */
    public int tradeIntervalTicks = 10;

    private static AutomationConfig instance;

    private AutomationConfig() {
    }

    public static AutomationConfig get() {
        if (instance == null) {
            instance = new AutomationConfig();
        }

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
            String json = Files.readString(file, StandardCharsets.UTF_8);
            AutomationConfig loaded = GSON.fromJson(json, AutomationConfig.class);

            if (loaded == null) {
                ErrorHandler.error("Ayar dosyası boş veya bozuk. Varsayılan ayarlar kullanılacak.");
                return;
            }

            instance = loaded;
            ErrorHandler.info("Ayarlar yüklendi: " + file);
        } catch (IOException exception) {
            ErrorHandler.error("Ayar dosyası okunamadı: " + exception.getClass().getSimpleName());
        } catch (Exception exception) {
            ErrorHandler.error("Ayar dosyası çözümlenemedi: " + exception.getClass().getSimpleName());
        }
    }

    public void save() {
        Path file = getConfigFile();

        try {
            Files.createDirectories(file.getParent());
            Files.writeString(
                    file,
                    GSON.toJson(this),
                    StandardCharsets.UTF_8
            );
        } catch (IOException exception) {
            ErrorHandler.error("Ayar dosyası kaydedilemedi: " + exception.getClass().getSimpleName());
        }
    }

    private static Path getConfigFile() {
        return FabricLoader.getInstance()
                .getConfigDir()
                .resolve("farmerautomation.json");
    }
}
