package dev.inboxbridge.web;

import jakarta.ws.rs.BadRequestException;

public final class WebResourceSupport {

    private WebResourceSupport() {
    }

    public static <T> T badRequest(UnsafeSupplier<T> action) {
        try {
            return action.get();
        } catch (IllegalArgumentException | IllegalStateException exception) {
            throw badRequest(exception);
        }
    }

    public static void badRequest(UnsafeRunnable action) {
        try {
            action.run();
        } catch (IllegalArgumentException | IllegalStateException exception) {
            throw badRequest(exception);
        }
    }

    public static BadRequestException badRequest(RuntimeException exception) {
        return new BadRequestException(exception.getMessage(), exception);
    }

    @FunctionalInterface
    public interface UnsafeSupplier<T> {
        T get();
    }

    @FunctionalInterface
    public interface UnsafeRunnable {
        void run();
    }
}
