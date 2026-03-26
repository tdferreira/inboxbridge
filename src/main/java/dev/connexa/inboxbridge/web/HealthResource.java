package dev.connexa.inboxbridge.web;

import java.util.Map;

import dev.connexa.inboxbridge.persistence.ImportedMessageRepository;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

@Path("/api/health/summary")
@Produces(MediaType.APPLICATION_JSON)
public class HealthResource {

    @Inject
    ImportedMessageRepository importedMessageRepository;

    @GET
    public Map<String, Object> summary() {
        return Map.of(
                "status", "UP",
                "importedMessages", importedMessageRepository.count());
    }
}
