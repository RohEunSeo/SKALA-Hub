// Post 엔티티 DB 접근
package com.skalahub.repository;

import com.skalahub.entity.Post;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PostRepository extends JpaRepository<Post, Long> {
}
