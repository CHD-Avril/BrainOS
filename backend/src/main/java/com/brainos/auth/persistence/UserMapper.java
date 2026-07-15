package com.brainos.auth.persistence;

import com.brainos.auth.domain.UserAccount;
import com.brainos.auth.domain.UserRepository;
import java.util.Optional;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface UserMapper extends UserRepository {

    @Override
    @Select("""
            SELECT id, username, password_hash, display_name, role, status
            FROM sys_user
            WHERE username = #{username}
            LIMIT 1
            """)
    Optional<UserAccount> findByUsername(String username);

    @Override
    @Select("""
            SELECT id, username, password_hash, display_name, role, status
            FROM sys_user
            WHERE id = #{userId}
            LIMIT 1
            """)
    Optional<UserAccount> findById(long userId);
}
