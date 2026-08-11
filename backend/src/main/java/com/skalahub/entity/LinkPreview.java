// 미리보기(attachments)가 없는 본문 텍스트 링크의 og:title/og:image 캐시 (link_previews 테이블) - URL당 1행
package com.skalahub.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "link_previews")
@Getter
@Setter
@NoArgsConstructor
public class LinkPreview {

    @Id
    private String url;

    private String title;

    @Column(name = "image_url")
    private String imageUrl;

    @Column(name = "service_name")
    private String serviceName;

    @Column(name = "fetch_failed")
    private Boolean fetchFailed;

    @Column(name = "fetched_at")
    private LocalDateTime fetchedAt;

    // 관리자가 링크 모음 화면에서 직접 수정한 값 - 자동 fetch 결과보다 항상 우선함
    @Column(name = "admin_title")
    private String adminTitle;

    // 카드 제목 위에 뜨는 출처(도메인) 텍스트 수동 수정 - 실제 이동 주소(href)는 안 바꾸고 화면 표시 텍스트만 교체
    @Column(name = "admin_source")
    private String adminSource;

    // "만든 사람" 표시 수동 수정 - 비어 있으면 이 URL을 올린 게시글 작성자를 자동 수집해서 보여주고,
    // 값이 있으면 이 텍스트가 항상 우선함(쉼표로 구분된 이름 목록)
    @Column(name = "admin_creators")
    private String adminCreators;

    // 학습자료 하위 유형(영상/블로그·글/깃허브) 배지 수동 지정 - 비어 있으면 게시글 태그/서비스명 기반
    // 자동 추정을 쓰고, 값이 있으면 이게 항상 우선함(링크 단위로만 적용, 게시글의 실제 tags는 안 건드림)
    @Column(name = "admin_type_tag")
    private String adminTypeTag;

    // 관리자가 링크 모음에서 숨김 처리했는지 - true면 이 URL은 어느 게시글에서도 표시 안 함.
    // DB 컬럼이 NOT NULL이라 새로 생성되는 행에서도 항상 값이 채워지도록 기본값을 둠
    // (안 두면 신규 INSERT 시 null이 들어가 제약조건 위반으로 저장이 실패함)
    private Boolean hidden = false;
}
