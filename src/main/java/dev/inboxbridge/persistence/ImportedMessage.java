package dev.inboxbridge.persistence;

import java.time.Instant;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(name = "imported_message",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_imported_message_destination_source_key", columnNames = { "destination_key", "source_account_id", "source_message_key" }),
                @UniqueConstraint(name = "uk_imported_message_destination_sha", columnNames = { "destination_key", "raw_sha256" })
        },
        indexes = {
                @Index(name = "idx_imported_message_destination", columnList = "destination_key"),
                @Index(name = "idx_imported_message_account", columnList = "source_account_id"),
                @Index(name = "idx_imported_message_gmail", columnList = "gmail_message_id")
        })
public class ImportedMessage extends PanacheEntityBase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Column(name = "source_account_id", nullable = false, length = 100)
    public String sourceAccountId;

    @Column(name = "source_message_key", nullable = false, length = 500)
    public String sourceMessageKey;

    @Column(name = "message_id_header", length = 1000)
    public String messageIdHeader;

    @Column(name = "raw_sha256", nullable = false, length = 64)
    public String rawSha256;

    @Column(name = "destination_key", nullable = false, length = 160)
    public String destinationKey;

    @Column(name = "gmail_message_id", nullable = false, length = 255)
    public String gmailMessageId;

    @Column(name = "gmail_thread_id", length = 255)
    public String gmailThreadId;

    @Column(name = "imported_at", nullable = false)
    public Instant importedAt;
}
