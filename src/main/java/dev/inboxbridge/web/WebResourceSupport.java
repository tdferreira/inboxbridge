package dev.inboxbridge.web;

import jakarta.ws.rs.BadRequestException;

final class WebResourceSupport {

    private WebResourceSupport() {
    }

    static <T> T badRequest(UnsafeSupplier<T> action) {
        try {
            return action.get();
        } catch (IllegalArgumentException | IllegalStateException exception) {
            throw badRequest(exception);
        }
    }

    static void badRequest(UnsafeRunnable action) {
        try {
            action.run();
        } catch (IllegalArgumentException | IllegalStateException exception) {
            throw badRequest(exception);
        }
    }

    static BadRequestException badRequest(RuntimeException exception) {
        return new BadRequestException(exception.getMessage(), exception);
    }

    @FunctionalInterface
    interface UnsafeSupplier<T> {
        T get();
    }

    @FunctionalInterface
    interface UnsafeRunnable {
        void run();
    }
}
