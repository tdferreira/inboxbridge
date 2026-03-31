package dev.inboxbridge.persistence;

import java.time.Instant;
import java.util.Optional;

import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;

@ApplicationScoped
public class GeoIpCacheEntryRepository implements PanacheRepository<GeoIpCacheEntry> {

    public Optional<GeoIpCacheEntry> findValid(String ipAddress, Instant now) {
        return findByIpAddress(ipAddress)
                .filter(entry -> now.isBefore(entry.expiresAt));
    }

    public Optional<GeoIpCacheEntry> findByIpAddress(String ipAddress) {
        return find("ipAddress", ipAddress).firstResultOptional();
    }

    @Transactional
    public void deleteExpired(Instant now) {
        delete("expiresAt <= ?1", now);
    }
}
