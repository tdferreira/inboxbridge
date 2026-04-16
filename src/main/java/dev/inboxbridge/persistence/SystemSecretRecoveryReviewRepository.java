package dev.inboxbridge.persistence;

import java.util.List;
import java.util.Optional;

import io.quarkus.hibernate.orm.panache.PanacheRepository;
import io.quarkus.panache.common.Page;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class SystemSecretRecoveryReviewRepository implements PanacheRepository<SystemSecretRecoveryReview> {

    public Optional<SystemSecretRecoveryReview> findLatest() {
        return find("order by reviewedAt desc, id desc").firstResultOptional();
    }

    public List<SystemSecretRecoveryReview> listRecent(int maxResults) {
        return find("order by reviewedAt desc, id desc")
                .page(Page.ofSize(Math.max(1, maxResults)))
                .list();
    }
}
