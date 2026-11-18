package com.example.farmerautomation;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;

public final class AutomationManager {
    public static final AutomationManager INSTANCE = new AutomationManager();

    private final ShopController shopController = new ShopController();
    private final DiagnosticController diagnosticController = new DiagnosticController();
    private final CycleController cycleController = new CycleController();
    private final FarmerNavigationController navigationController = new FarmerNavigationController();

    private AutomationState state = AutomationState.IDLE;
    private AutomationState stateBeforePause = AutomationState.IDLE;
    private boolean running = false;

    private int currentVillagerIndex = -1;
    private int villagerCount = 0;
    private int totalEmeralds = 0;
    private int carrotsInInventory = -1;
    private int shulkerUsedSlots = -1;
    private int shulkerTotalSlots = -1;

    private AutomationManager() { }

    public synchronized AutomationState getState() { return state; }
    public synchronized boolean isRunning() { return running; }
    public synchronized int getCurrentVillagerIndex() { return currentVillagerIndex; }
    public synchronized int getVillagerCount() { return villagerCount; }
    public synchronized int getTotalEmeralds() { return totalEmeralds; }
    public synchronized int getCarrotsInInventory() { return carrotsInInventory; }
    public synchronized int getShulkerUsedSlots() { return shulkerUsedSlots; }
    public synchronized int getShulkerTotalSlots() { return shulkerTotalSlots; }

    /** Havuçları tamamla, çiftçileri bul, bağımsız yol bulma ile ulaş ve takas et. */
    public synchronized void start() {
        if (!canStart("Otomasyon")) return;
        running = true;
        state = AutomationState.CYCLE;
        stateBeforePause = AutomationState.CYCLE;
        currentVillagerIndex = -1;
        cycleController.reset();
        ErrorHandler.info("Otomatik çiftçi takası başlatıldı. Hedef: havuçları zümrüte çevirmek.");
    }

    /** Aşama A: yalnızca tanılama; satın alma ve takas yapılmaz. */
    public synchronized void startDiagnostic() {
        if (!canStart("Teşhis")) return;
        running = true;
        state = AutomationState.DIAGNOSTIC;
        stateBeforePause = AutomationState.DIAGNOSTIC;
        currentVillagerIndex = -1;
        diagnosticController.reset();
        ErrorHandler.info("Teşhis başlatıldı. Satın alma ve takas yapılmayacak.");
    }

    /** En yakın çiftçiye bağımsız yol bulma ile git; takas ekranı tercihe bağlıdır. */
    public synchronized void startNavigation() {
        if (!canStart("Köylüye git")) return;
        running = true;
        state = AutomationState.SCANNING_VILLAGERS;
        stateBeforePause = AutomationState.SCANNING_VILLAGERS;
        currentVillagerIndex = -1;
        navigationController.reset(AutomationConfig.get().openTradeScreenAfterNavigation);
        ErrorHandler.info("Köylüye ulaşma başlatıldı. Otomatik takas kapalı.");
    }

    public synchronized void startCycle() {
        if (!canStart("Döngü")) return;
        running = true;
        state = AutomationState.CYCLE;
        stateBeforePause = AutomationState.CYCLE;
        currentVillagerIndex = -1;
        cycleController.reset();
        ErrorHandler.info("Döngü başlatıldı.");
    }

    private boolean canStart(String label) {
        if (running) {
            ErrorHandler.info("Otomasyon zaten çalışıyor.");
            return false;
        }
        if (state == AutomationState.PAUSED) {
            ErrorHandler.info("Duraklatılmış otomasyon için 'Devam' düğmesini kullan.");
            return false;
        }
        if (state != AutomationState.IDLE && state != AutomationState.ERROR && state != AutomationState.COMPLETED) {
            ErrorHandler.info(label + " şu anda başlatılamaz. Durum: " + state);
            return false;
        }
        return true;
    }

    public synchronized void pause() {
        if (!running) {
            ErrorHandler.info("Duraklatılacak çalışan bir otomasyon yok.");
            return;
        }
        if (state == AutomationState.PAUSED || state == AutomationState.ERROR || state == AutomationState.COMPLETED) return;
        stateBeforePause = state;
        if (state == AutomationState.CYCLE) cycleController.pause();
        else if (isNavigationState(state)) navigationController.pause();
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
        Minecraft client = Minecraft.getInstance();
        if (stateBeforePause == AutomationState.CYCLE && !cycleController.resume(client)) return;
        if (isNavigationState(stateBeforePause) && !navigationController.resume(client)) return;
        state = stateBeforePause;
        running = true;
        ErrorHandler.info("Otomasyon kaldığı yerden devam ediyor. Durum: " + state);
    }

    public synchronized void stop() {
        running = false;
        state = AutomationState.IDLE;
        stateBeforePause = AutomationState.IDLE;
        currentVillagerIndex = -1;
        shopController.reset();
        diagnosticController.reset();
        cycleController.reset();
        navigationController.stop();
        closeOpenContainer();
        ErrorHandler.info("Otomasyon durduruldu; açık menü kapatıldı.");
    }

    private void closeOpenContainer() {
        Minecraft client = Minecraft.getInstance();
        if (client != null && client.player != null && client.screen instanceof AbstractContainerScreen<?>) {
            client.player.closeContainer();
        }
    }

    public synchronized void complete() {
        running = false;
        state = AutomationState.COMPLETED;
        stateBeforePause = AutomationState.COMPLETED;
    }

    public synchronized void fail(String message) {
        ErrorHandler.error(message);
        cycleController.reset();
        navigationController.stop();
        running = false;
        state = AutomationState.ERROR;
        stateBeforePause = AutomationState.ERROR;
    }

    public synchronized void setState(AutomationState newState) {
        if (newState == null) return;
        if (state == AutomationState.PAUSED && newState != AutomationState.PAUSED) {
            ErrorHandler.info("Duraklatılmış otomasyon durum değiştiremez.");
            return;
        }
        state = newState;
        stateBeforePause = newState;
    }

    public synchronized void setVillagerCount(int count) { if (count >= 0) villagerCount = count; }
    public synchronized void setCurrentVillagerIndex(int index) { currentVillagerIndex = index; }
    public synchronized void addEmeralds(int amount) { if (amount > 0) totalEmeralds += amount; }
    public synchronized void setCarrotsInInventory(int amount) { if (amount >= 0) carrotsInInventory = amount; }

    public synchronized void setShulkerUsage(int usedSlots, int totalSlots) {
        if (usedSlots >= 0 && totalSlots > 0) {
            shulkerUsedSlots = usedSlots;
            shulkerTotalSlots = totalSlots;
        }
    }

    public void tick(Minecraft client) {
        boolean shouldRun;
        synchronized (this) { shouldRun = running; }
        if (!shouldRun || client == null || client.player == null || client.level == null) return;

        try {
            AutomationState currentState;
            synchronized (this) { currentState = state; }
            switch (currentState) {
                case OPENING_SHOP -> handleOpeningShop(client);
                case BUYING_CARROTS -> handleBuyingCarrots(client);
                case DIAGNOSTIC -> handleDiagnostic(client);
                case CYCLE -> handleCycle(client);
                case SCANNING_VILLAGERS, SELECTING_VILLAGER, NAVIGATING, TRADING -> handleNavigation(client);
                case CHECKING_STOCK -> handleCheckingStock(client);
                case COLLECTING_EMERALDS -> handleCollectingEmeralds(client);
                case STORING_ITEMS -> handleStoringItems(client);
                case SWITCHING_VILLAGER -> handleSwitchingVillager(client);
                default -> { }
            }
        } catch (Exception exception) {
            navigationController.stop();
            cycleController.reset();
            closeOpenContainer();
            if (client.player != null) {
                client.player.displayClientMessage(net.minecraft.network.chat.Component.literal(
                        "§6[Otomasyon]§r HATA: Beklenmeyen hata; açık menü kapatıldı."), false);
            }
            fail("Beklenmeyen hata: " + exception.getClass().getSimpleName());
            exception.printStackTrace();
        }
    }

    private void handleOpeningShop(Minecraft client) {
        String command = AutomationConfig.get().shopCommand;
        if (client.player.connection != null) {
            client.player.connection.sendCommand(command.startsWith("/") ? command.substring(1) : command);
        }
        setState(AutomationState.BUYING_CARROTS);
    }

    private void handleBuyingCarrots(Minecraft client) {
        int carrots = InventoryManager.countCarrots(client);
        setCarrotsInInventory(Math.max(0, carrots));
        if (carrots < 0) {
            fail("Oyuncu envanteri okunamadı.");
            return;
        }
        if (shopController.tick(client)) {
            int finalCarrots = InventoryManager.countCarrots(client);
            ErrorHandler.info("Shop alımı tamamlandı, menü kapatıldı: " + finalCarrots + "/768 havuç.");
            complete();
        }
    }

    private void handleDiagnostic(Minecraft client) {
        boolean finished = diagnosticController.tick(client);
        if (finished && getState() == AutomationState.DIAGNOSTIC) complete();
    }

    private void handleCycle(Minecraft client) {
        boolean finished = cycleController.tick(client);
        if (finished && getState() == AutomationState.CYCLE) complete();
    }

    private static boolean isNavigationState(AutomationState candidate) {
        return candidate == AutomationState.SCANNING_VILLAGERS
                || candidate == AutomationState.SELECTING_VILLAGER
                || candidate == AutomationState.NAVIGATING
                || candidate == AutomationState.TRADING;
    }

    private void handleNavigation(Minecraft client) {
        boolean finished = navigationController.tick(client);
        if (finished && getState() != AutomationState.ERROR) complete();
    }

    private void handleCheckingStock(Minecraft client) { }
    private void handleCollectingEmeralds(Minecraft client) { }
    private void handleStoringItems(Minecraft client) { }
    private void handleSwitchingVillager(Minecraft client) { }
}
