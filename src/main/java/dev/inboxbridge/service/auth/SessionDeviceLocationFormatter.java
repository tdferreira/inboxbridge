package dev.inboxbridge.service.auth;

import java.util.Locale;

public final class SessionDeviceLocationFormatter {

    private SessionDeviceLocationFormatter() {
    }

    public static String format(Double latitude, Double longitude, Double accuracyMeters) {
        if (latitude == null || longitude == null) {
            return null;
        }
        String latitudeValue = String.format(Locale.ROOT, "%.4f", latitude);
        String longitudeValue = String.format(Locale.ROOT, "%.4f", longitude);
        if (accuracyMeters == null || accuracyMeters <= 0d) {
            return latitudeValue + ", " + longitudeValue;
        }
        long roundedAccuracy = Math.round(accuracyMeters);
        return latitudeValue + ", " + longitudeValue + " (\u00b1" + roundedAccuracy + " m)";
    }
}
