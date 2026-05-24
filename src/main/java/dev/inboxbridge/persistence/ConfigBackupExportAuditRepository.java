package dev.inboxbridge.persistence;

import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class ConfigBackupExportAuditRepository implements PanacheRepository<ConfigBackupExportAudit> {
}
