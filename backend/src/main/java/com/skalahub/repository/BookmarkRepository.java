// Bookmark 엔티티 DB 접근
package com.skalahub.repository;

import com.skalahub.entity.Bookmark;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BookmarkRepository extends JpaRepository<Bookmark, Long> {
}
