package com.brainos.admin.user;

import com.brainos.auth.domain.UserRole;
import com.brainos.auth.domain.UserStatus;
import java.util.List;
import java.util.Optional;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface AdminUserMapper {

    @Select("SELECT COUNT(*) FROM sys_user")
    long countAll();

    @Select("""
            SELECT id, username, display_name AS displayName, role, status,
                   last_login_at AS lastLoginAt, created_at AS createdAt, updated_at AS updatedAt
            FROM sys_user
            ORDER BY created_at DESC, id DESC
            LIMIT #{limit} OFFSET #{offset}
            """)
    List<AdminUserView> findPage(@Param("limit") int limit, @Param("offset") long offset);

    @Select("""
            SELECT id, username, display_name AS displayName, role, status,
                   last_login_at AS lastLoginAt, created_at AS createdAt, updated_at AS updatedAt
            FROM sys_user WHERE id = #{id}
            """)
    Optional<AdminUserView> findViewById(long id);

    @Select("SELECT COUNT(*) > 0 FROM sys_user WHERE username = #{username}")
    boolean existsByUsername(String username);

    @Select("SELECT COUNT(*) FROM sys_user WHERE role = 'ADMIN' AND status = 'ENABLED'")
    long countEnabledAdmins();

    @Insert("""
            INSERT INTO sys_user (username, password_hash, display_name, role, status)
            VALUES (#{username}, #{passwordHash}, #{displayName}, #{role}, #{status})
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    void create(AdminUserEntity user);

    @Update("""
            <script>
            UPDATE sys_user
            SET display_name = #{displayName}, role = #{role}
            <if test="passwordHash != null">, password_hash = #{passwordHash}</if>
            WHERE id = #{id}
            </script>
            """)
    int update(
            @Param("id") long id,
            @Param("displayName") String displayName,
            @Param("role") UserRole role,
            @Param("passwordHash") String passwordHash);

    @Update("UPDATE sys_user SET status = #{status} WHERE id = #{id}")
    int updateStatus(@Param("id") long id, @Param("status") UserStatus status);
}
