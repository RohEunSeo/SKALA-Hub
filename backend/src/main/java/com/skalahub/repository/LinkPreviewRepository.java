// LinkPreview 엔티티 DB 접근
package com.skalahub.repository;

import com.skalahub.entity.LinkPreview;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LinkPreviewRepository extends JpaRepository<LinkPreview, String> {}
