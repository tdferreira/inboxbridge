package dev.inboxbridge.persistence;

import java.time.Instant;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "geo_ip_cache_entry")
public class GeoIpCacheEntry extends PanacheEntityBase {

    @Id
    @Column(name = "ip_address", nullable = false, length = 128)
    public String ipAddress;

    @Column(name = "provider", length = 40)
    public String provider;

    @Column(name = "location_label", length = 160)
    public String locationLabel;

    @Column(name = "resolution_status", nullable = false, length = 32)
    public String resolutionStatus;

    @Column(name = "expires_at", nullable = false)
    public Instant expiresAt;

    @Column(name = "updated_at", nullable = false)
    public Instant updatedAt;
}
