"""새 글 한 건을 post_chunks에 반영한다. scripts/build_index.py의 글 1개짜리 버전.

Spring이 슬랙 글을 저장→분류→AI 제목 생성까지 끝낸 직후 이걸 호출하도록 설계했다
(routers/internal.py). 제목이 아직 없는 시점에 부르면 제목 없이 임베딩되고, 나중에
제목이 생겨도 저절로 다시 반영되지 않는다 - Spring 쪽에서 반드시 제목 생성 이후에
불러야 한다.

pgvector 전용이다. FAISS는 "평가용 고정 인덱스"라 운영 중 새 글을 끼워 넣는 대상이
아니다 (config.py 「벡터 저장소」 주석).
"""
import logging

from app.core.loader import load_post, split_documents
from app.core.retriever import pg_upsert_post

log = logging.getLogger(__name__)


def reindex_post(post_id: int) -> int:
    """글 하나를 다시 임베딩해 post_chunks에 넣는다. 반환: 저장한 조각 수(0이면 색인 대상 아님).

    post_chunks의 PK가 (post_id, chunk_index)라 ON CONFLICT ... DO UPDATE로 들어간다
    (retriever.py의 _INSERT_SQL) - 이미 색인된 글을 다시 불러도 안전하다.
    """
    doc = load_post(post_id)
    if doc is None:
        log.info("post_id=%s 색인 대상 아님 (삭제됨/본문·제목 없음)", post_id)
        return 0
    chunks = split_documents([doc])
    saved = pg_upsert_post(chunks)
    log.info("post_id=%s 색인 완료 (%d조각)", post_id, saved)
    return saved
