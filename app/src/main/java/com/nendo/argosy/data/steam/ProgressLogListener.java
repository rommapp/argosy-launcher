package com.nendo.argosy.data.steam;

import in.dragonbra.javasteam.util.log.LogListener;

public abstract class ProgressLogListener implements LogListener {
    @Override
    public void onLog(Class<?> clazz, String message, Throwable throwable) {
        onLogMessage(clazz, message);
    }

    @Override
    public void onError(Class<?> clazz, String message, Throwable throwable) {
        onErrorMessage(clazz, message, throwable);
    }

    protected abstract void onLogMessage(Class<?> clazz, String message);
    protected abstract void onErrorMessage(Class<?> clazz, String message, Throwable throwable);
}
