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
@Table(name = "registration_challenge",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_registration_challenge_token", columnNames = { "challenge_token" })
        },
        indexes = {
                @Index(name = "idx_registration_challenge_expires_at", columnList = "expires_at")
        })
public class RegistrationChallenge extends PanacheEntityBase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Column(name = "challenge_token", nullable = false, length = 80)
    public String challengeToken;

    @Column(name = "prompt", nullable = false, length = 255)
    public String prompt;

    @Column(name = "answer_hash", nullable = false, length = 128)
    public String answerHash;

    @Column(name = "expires_at", nullable = false)
    public Instant expiresAt;

    @Column(name = "created_at", nullable = false)
    public Instant createdAt;
}
