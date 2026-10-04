"""Swagger/curl 테스트용 로그인 토큰을 만든다. (개발 전용)

    uv run python -m scripts.make_token

Spring의 JwtService가 발급하는 것과 같은 모양이다 (같은 JWT_SECRET으로 서명).
실제 로그인 없이 /api/chat을 테스트하려고 쓴다.
"""
import datetime

import jwt

from app.config import settings

HOURS = 12


def main() -> None:
    settings.require("jwt_secret")
    now = datetime.datetime.now(datetime.UTC)
    token = jwt.encode(
        {
            "sub": "U_DEV_TEST",      # Spring에서는 slack_id가 들어간다 (JwtService:32)
            "name": "개발 테스트",
            "role": "admin",
            "iat": now,
            "exp": now + datetime.timedelta(hours=HOURS),
        },
        settings.jwt_secret,
        algorithm="HS256",
    )
    print(f"{HOURS}시간짜리 테스트 토큰:\n")
    print(token)
    print("\nSwagger(/docs)에서 쓰는 법:")
    print("  1. 오른쪽 위 'Authorize' 버튼 클릭")
    print("  2. 위 토큰을 붙여넣기 (Bearer 는 빼고 토큰만)")
    print("  3. Authorize → Close")


if __name__ == "__main__":
    main()
