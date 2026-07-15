package com.brainos.auth.domain;

import java.util.Optional;

public interface UserRepository {

    Optional<UserAccount> findByUsername(String username);

    Optional<UserAccount> findById(long userId);
}
