"""Supabase Postgres 접속.

접속을 여는 곳은 여기 하나다. 다른 모듈은 `with get_connection() as conn:` 만 쓴다.

주의: Spring 백엔드와 같은 **트랜잭션 모드 풀러**(6543)를 쓴다.
- 직접 연결(db.<ref>.supabase.co:5432)은 이 프로젝트에선 DNS에 없다.
- 풀러는 PgBouncer라 커넥션이 트랜잭션마다 바뀔 수 있어 서버사이드 prepared statement를 꺼야 한다.
  (Spring도 같은 이유로 application.yml에 prepareThreshold=0 을 붙여놨다)
"""
from contextlib import contextmanager
from typing import Any, Iterator

import psycopg
from psycopg.rows import dict_row

from app.config import settings

CONNECT_TIMEOUT = 20  # Render 콜드스타트와 무료 플랜 지연을 고려


@contextmanager
def get_connection() -> Iterator[psycopg.Connection]:
    """읽기용 커넥션. 결과는 dict로 받는다(컬럼이 늘어도 인덱스가 밀리지 않게)."""
    with psycopg.connect(
        settings.postgres_dsn,
        connect_timeout=CONNECT_TIMEOUT,
        row_factory=dict_row,
    ) as conn:
        conn.prepare_threshold = None  # PgBouncer 트랜잭션 모드 필수
        yield conn


def fetch_all(sql: str, params: dict[str, Any] | None = None) -> list[dict[str, Any]]:
    """SELECT 한 번 돌리고 전부 가져온다."""
    with get_connection() as conn, conn.cursor() as cur:
        cur.execute(sql, params or {})
        return cur.fetchall()
