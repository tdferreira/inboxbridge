package dev.inboxbridge.web;

import java.util.Map;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

@Path("/api/health/summary")
@Produces(MediaType.APPLICATION_JSON)
public class HealthResource {

    @GET
    public Map<String, Object> summary() {
        // Public liveness must not expose cross-user mailbox activity.
        return Map.of("status", "UP");
    }
}
