package dev.inboxbridge.dto;

public record SessionDeviceLocationRequest(
        Double latitude,
        Double longitude,
        Double accuracyMeters) {
}
