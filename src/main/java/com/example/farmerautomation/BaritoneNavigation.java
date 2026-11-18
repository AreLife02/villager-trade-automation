package com.example.farmerautomation;

import net.minecraft.core.BlockPos;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Baritone'un isteğe bağlı API bağlantısı.
 * Derleme zamanında Baritone bağımlılığı gerektirmez; oyun açıldığında kurulu API'yi arar.
 */
public final class BaritoneNavigation {
    public enum PathStatus { ACTIVE, IDLE }

    private static final String[] SAFE_SETTINGS = {
            "allowBreak",
            "allowPlace",
            "allowParkour",
            "allowParkourPlace",
            "allowDiagonalDescend",
            "allowDownward",
            "allowWaterBucketFall",
            "assumeWalkOnLava",
            "allowSprint",
            "allowInventory",
            "avoidance"
    };

    private final Map<String, SettingSnapshot> previousSettings = new LinkedHashMap<>();
    private Object baritone;
    private Object pathingBehavior;
    private boolean settingsApplied;
    private String lastError;

    public boolean start(BlockPos target) {
        stop();
        lastError = null;

        try {
            Class<?> apiClass = Class.forName("baritone.api.BaritoneAPI");
            Object provider = apiClass.getMethod("getProvider").invoke(null);
            Class<?> providerClass = Class.forName("baritone.api.IBaritoneProvider");
            baritone = providerClass.getMethod("getPrimaryBaritone").invoke(provider);

            Class<?> baritoneClass = Class.forName("baritone.api.IBaritone");
            pathingBehavior = baritoneClass.getMethod("getPathingBehavior").invoke(baritone);
            Object goalProcess = baritoneClass.getMethod("getCustomGoalProcess").invoke(baritone);

            applySafeSettings(apiClass);

            Class<?> goalClass = Class.forName("baritone.api.pathing.goals.Goal");
            Class<?> goalNearClass = Class.forName("baritone.api.pathing.goals.GoalNear");
            Object goal = goalNearClass.getConstructor(BlockPos.class, int.class).newInstance(target, 2);
            Class<?> processClass = Class.forName("baritone.api.process.ICustomGoalProcess");
            processClass.getMethod("setGoalAndPath", goalClass).invoke(goalProcess, goal);
            return true;
        } catch (Throwable exception) {
            lastError = rootMessage(exception);
            restoreSettings();
            baritone = null;
            pathingBehavior = null;
            return false;
        }
    }

    public PathStatus getPathStatus() throws ReflectiveOperationException {
        if (pathingBehavior == null) {
            throw new IllegalStateException("Baritone hareket bağlantısı hazır değil.");
        }

        Class<?> behaviorClass = Class.forName("baritone.api.behavior.IPathingBehavior");
        boolean pathing = (boolean) behaviorClass.getMethod("isPathing").invoke(pathingBehavior);
        boolean hasPath = (boolean) behaviorClass.getMethod("hasPath").invoke(pathingBehavior);
        Object inProgress = behaviorClass.getMethod("getInProgress").invoke(pathingBehavior);
        boolean calculating = inProgress instanceof Optional<?> optional && optional.isPresent();

        return pathing || hasPath || calculating ? PathStatus.ACTIVE : PathStatus.IDLE;
    }

    public void finish() {
        restoreSettings();
        baritone = null;
        pathingBehavior = null;
    }

    public void stop() {
        if (pathingBehavior != null) {
            try {
                Class<?> behaviorClass = Class.forName("baritone.api.behavior.IPathingBehavior");
                behaviorClass.getMethod("cancelEverything").invoke(pathingBehavior);
            } catch (Throwable ignored) {
                // Mod kapanırken veya Baritone kaldırılmışsa da yerel otomasyon durdurulabilsin.
            }
        }
        finish();
    }

    public String getLastError() {
        return lastError;
    }

    private void applySafeSettings(Class<?> apiClass) throws ReflectiveOperationException {
        Object settings = apiClass.getMethod("getSettings").invoke(null);
        setBoolean(settings, "allowBreak", false);
        setBoolean(settings, "allowPlace", false);
        setBoolean(settings, "allowParkour", false);
        setBoolean(settings, "allowParkourPlace", false);
        setBoolean(settings, "allowDiagonalDescend", false);
        setBoolean(settings, "allowDownward", false);
        setBoolean(settings, "allowWaterBucketFall", false);
        setBoolean(settings, "assumeWalkOnLava", false);
        setBoolean(settings, "allowSprint", false);
        setBoolean(settings, "allowInventory", false);
        setBoolean(settings, "avoidance", true);
        settingsApplied = true;
    }

    private void setBoolean(Object settings, String name, boolean value) throws ReflectiveOperationException {
        Field settingField = settings.getClass().getField(name);
        Object setting = settingField.get(settings);
        Field valueField = setting.getClass().getField("value");
        Object oldValue = valueField.get(setting);
        previousSettings.put(name, new SettingSnapshot(setting, valueField, oldValue));
        valueField.set(setting, value);
    }

    private void restoreSettings() {
        if (!settingsApplied && previousSettings.isEmpty()) return;

        for (SettingSnapshot snapshot : previousSettings.values()) {
            try {
                snapshot.valueField().set(snapshot.setting(), snapshot.previousValue());
            } catch (IllegalAccessException ignored) {
                // Diğer ayarların geri yüklenmesine devam et.
            }
        }
        previousSettings.clear();
        settingsApplied = false;
    }

    private static String rootMessage(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null) current = current.getCause();
        String message = current.getMessage();
        return current.getClass().getSimpleName() + (message == null ? "" : ": " + message);
    }

    private record SettingSnapshot(Object setting, Field valueField, Object previousValue) { }
}
