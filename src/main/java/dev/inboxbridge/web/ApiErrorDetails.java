package dev.inboxbridge.web;

final class ApiErrorDetails {

    private ApiErrorDetails() {
    }

    static String deepestMessage(Throwable throwable) {
        if (throwable == null) {
            return "";
        }
        Throwable current = throwable;
        String lastMessage = sanitize(current.getMessage());
        while (current.getCause() != null && current.getCause() != current) {
            current = current.getCause();
            String candidate = sanitize(current.getMessage());
            if (!candidate.isBlank()) {
                lastMessage = candidate;
            }
        }
        return lastMessage;
    }

    private static String sanitize(String value) {
        return value == null ? "" : value.trim();
    }
}
