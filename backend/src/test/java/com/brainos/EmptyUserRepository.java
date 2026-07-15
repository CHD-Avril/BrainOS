package com.brainos;

import com.brainos.auth.domain.UserAccount;
import com.brainos.auth.domain.UserRepository;
import java.util.Optional;

public final class EmptyUserRepository implements UserRepository {

    @Override
    public Optional<UserAccount> findByUsername(String username) {
        return Optional.empty();
    }

    @Override
    public Optional<UserAccount> findById(long userId) {
        return Optional.empty();
    }
}
