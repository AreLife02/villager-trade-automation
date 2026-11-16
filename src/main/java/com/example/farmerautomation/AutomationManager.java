package com.example.farmerautomation;

import net.minecraft.client.Minecraft;

public final class AutomationManager {
    public static final AutomationManager INSTANCE = new AutomationManager();

    private AutomationState state = AutomationState.IDLE;
    private AutomationState stateBeforePause = AutomationState.IDLE;
    private boolean running = false;

    private int currentVillagerIndex = -1;
    private int villagerCount = 0;
    private int totalEmeralds = 0;
    private int carrotsInInventory = -1;
    private int shulkerUsedSlots = -1;
    private int shulkerTotalSlots = -1;

    private AutomationManager() {
    }

    public synchronized AutomationState getState() {
        return state;
    }

    public synchronized boolean isRunning() {
        return running;
    }

    public synchronized int getCurrentVillagerIndex() {
        return currentVillagerIndex;
    }

    public synchronized int getVillagerCount() {
        return villagerCount;
    }

    public synchronized int getTotalEmeralds() {
        return totalEmeralds;
    }

    public synchronized int getCarrotsInInventory() {
        return carrotsInInventory;
    }

    public synchronized int getShulkerUsedSlots() {
        return shulkerUsedSlots;
    }

    public synchronized int getShulkerTotalSlots() {
        return shulkerTotalSlots;
    }

    public synchronized void start() {
        if (running) {
            ErrorHandler.info("Otomasyon zaten çalışıyor.");
            return;
        }

        if (state == AutomationState.PAUSED) {
            ErrorHandler.info("Duraklatılmış otomasyon için 'Devam' düğmesini kullan.");
            return;
        }

        if (state != AutomationState.IDLE
                && state != AutomationState.ERROR
                && state != AutomationState.COMPLETED) {
            ErrorHandler.info("Otomasyon şu anda başlatılamaz. Durum: " + state);
            return;
        }

        running = true;
        state = AutomationState.OPENING_SHOP;
        stateBeforePause = AutomationState.OPENING_SHOP;
        currentVillagerIndex = -1;

        ErrorHandler.info("Otomasyon başlatıldı.");
    }

    public synchronized void pause() {
        if (!running) {
            ErrorHandler.info("Duraklatılacak çalışan bir otomasyon yok.");
            return;
        }

        if (state == AutomationState.PAUSED
                || state == AutomationState.ERROR
                || state == AutomationState.COMPLETED) {
            return;
        }

        stateBeforePause = state;
        running = false;
        state = AutomationState.PAUSED;

        ErrorHandler.info("Otomasyon duraklatıldı. Kaldığı aşama: " + stateBeforePause);
    }

    public synchronized void resume() {
        if (running) {
            ErrorHandler.info("Otomasyon zaten çalışıyor.");
            return;
        }

        if (state != AutomationState.PAUSED) {
            ErrorHandler.info("Devam edilecek duraklatılmış otomasyon yok. Durum: " + state);
            return;
        }

        state = stateBeforePause;
        running = true;

        ErrorHandler.info("Otomasyon kaldığı yerden devam ediyor. Durum: " + state);
    }

    public synchronized void stop() {
        if (!running && state == AutomationState.IDLE) {
            ErrorHandler.info("Otomasyon zaten durdurulmuş.");
            return;
        }

        running = false;
        state = AutomationState.IDLE;
        stateBeforePause = AutomationState.IDLE;
        currentVillagerIndex = -1;

        ErrorHandler.info("Otomasyon durduruldu.");
    }

    public synchronized void fail(String message) {
        ErrorHandler.error(message);

        running = false;
        state = AutomationState.ERROR;
        stateBeforePause = AutomationState.ERROR;
    }

    public synchronized void setState(AutomationState newState) {
        if (newState == null) {
            return;
        }

        if (state == AutomationState.PAUSED && newState != AutomationState.PAUSED) {
            ErrorHandler.info("Duraklatılmış otomasyon durum değiştiremez.");
            return;
        }

        state = newState;
        stateBeforePause = newState;
    }

    public synchronized void setVillagerCount(int count) {
        if (count >= 0) {
            villagerCount = count;
        }
    }

    public synchronized void setCurrentVillagerIndex(int index) {
        currentVillagerIndex = index;
    }

    public synchronized void addEmeralds(int amount) {
        if (amount > 0) {
            totalEmeralds += amount;
        }
    }

    public synchronized void setCarrotsInInventory(int amount) {
        if (amount >= 0) {
            carrotsInInventory = amount;
        }
    }

    public synchronized void setShulkerUsage(int usedSlots, int totalSlots) {
        if (usedSlots >= 0 && totalSlots > 0) {
            shulkerUsedSlots = usedSlots;
            shulkerTotalSlots = totalSlots;
        }
    }

    public void tick(Minecraft client) {
        boolean shouldRun;

        synchronized (this) {
            shouldRun = running;
        }

        if (!shouldRun || client == null || client.player == null || client.level == null) {
            return;
        }

        try {
            AutomationState currentState;

            synchronized (this) {
                currentState = state;
            }

            switch (currentState) {
                case OPENING_SHOP -> handleOpeningShop(client);
                case BUYING_CARROTS -> handleBuyingCarrots(client);
                case SCANNING_VILLAGERS -> handleScanningVillagers(client);
                case SELECTING_VILLAGER -> handleSelectingVillager(client);
                case NAVIGATING -> handleNavigating(client);
                case TRADING -> handleTrading(client);
                case CHECKING_STOCK -> handleCheckingStock(client);
                case COLLECTING_EMERALDS -> handleCollectingEmeralds(client);
                case STORING_ITEMS -> handleStoringItems(client);
                case SWITCHING_VILLAGER -> handleSwitchingVillager(client);
                default -> {
                    // IDLE, PAUSED, ERROR ve COMPLETED burada işlem görmez.
                }
            }
        } catch (Exception exception) {
            fail("Beklenmeyen hata: " + exception.getClass().getSimpleName());
            exception.printStackTrace();
        }
    }

    private void handleOpeningShop(Minecraft client) {
        fail("Shop modülü henüz bağlı değil.");
    }

    private void handleBuyingCarrots(Minecraft client) {
        // Henüz uygulanmadı.
    }

    private void handleScanningVillagers(Minecraft client) {
        // Henüz uygulanmadı.
    }

    private void handleSelectingVillager(Minecraft client) {
        // Henüz uygulanmadı.
    }

    private void handleNavigating(Minecraft client) {
        // Henüz uygulanmadı.
    }

    private void handleTrading(Minecraft client) {
        // Henüz uygulanmadı.
    }

    private void handleCheckingStock(Minecraft client) {
        // Henüz uygulanmadı.
    }

    private void handleCollectingEmeralds(Minecraft client) {
        // Henüz uygulanmadı.
    }

    private void handleStoringItems(Minecraft client) {
        // Henüz uygulanmadı.
    }

    private void handleSwitchingVillager(Minecraft client) {
        // Henüz uygulanmadı.
    }
}
