package dev.inboxbridge.persistence;

import static org.junit.jupiter.api.Assertions.assertFalse;

import org.junit.jupiter.api.Test;

import jakarta.transaction.Transactional;

class UserEmailAccountRepositoryTest {

    @Test
    void asyncLookupMethodNoLongerOwnsTransactionBoundary() throws NoSuchMethodException {
        assertFalse(
                UserEmailAccountRepository.class
                        .getMethod("findByEmailAccountId", String.class)
                        .isAnnotationPresent(Transactional.class));
    }
}
