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

/**
 * Per-folder IMAP checkpoint state keyed by source and destination mailbox
 * identity.
 */
@Entity
@Table(name = "source_imap_checkpoint",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_source_imap_checkpoint_scope",
                        columnNames = { "source_id", "destination_key", "folder_name" })
        },
        indexes = {
                @Index(name = "idx_source_imap_checkpoint_source", columnList = "source_id"),
                @Index(name = "idx_source_imap_checkpoint_destination", columnList = "destination_key")
        })
public class SourceImapCheckpoint extends PanacheEntityBase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Column(name = "source_id", nullable = false, length = 120)
    public String sourceId;

    @Column(name = "destination_key", nullable = false, length = 160)
    public String destinationKey;

    @Column(name = "folder_name", nullable = false, length = 255)
    public String folderName;

    @Column(name = "uid_validity", nullable = false)
    public Long uidValidity;

    @Column(name = "last_seen_uid", nullable = false)
    public Long lastSeenUid;

    @Column(name = "updated_at", nullable = false)
    public Instant updatedAt;
}
