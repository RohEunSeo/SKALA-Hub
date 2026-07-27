// User 엔티티 DB 접근
package com.skalahub.repository;

import com.skalahub.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, String> {
}
