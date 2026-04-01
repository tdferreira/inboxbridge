package dev.inboxbridge.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import jakarta.transaction.Transactional;

class UserEmailAccountRepositoryTest {

    @Test
    void asyncLookupMethodRemainsTransactional() throws NoSuchMethodException {
        assertEquals(
                true,
                UserEmailAccountRepository.class
                        .getMethod("findByEmailAccountId", String.class)
                        .isAnnotationPresent(Transactional.class));
    }
}
