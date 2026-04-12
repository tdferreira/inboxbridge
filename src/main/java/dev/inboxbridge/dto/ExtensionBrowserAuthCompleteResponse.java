package dev.inboxbridge.dto;

public record ExtensionBrowserAuthCompleteResponse(
        String status) {

    public static ExtensionBrowserAuthCompleteResponse completed() {
        return new ExtensionBrowserAuthCompleteResponse("COMPLETED");
    }
}
