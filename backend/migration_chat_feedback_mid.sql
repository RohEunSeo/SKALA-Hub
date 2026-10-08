-- 챗봇 피드백을 3단계로 (좋아요 1 / 보통 0 / 별로 -1)
--
-- 기존엔 -1, 1 둘만 허용했다. 👍👎 두 개만 두면 "맞긴 한데 아쉽다"를 표현할 곳이 없어
-- 아무것도 안 누르게 되고, 그러면 신호 자체가 안 모인다. 가운데를 하나 연다.
-- feedback_reason(관련 없음/틀림/너무 김)은 그대로 둔다 - 어디가 고장났는지 가리는 값이라
-- 3단계로 바꿔도 버리면 안 된다.

alter table chat_logs drop constraint if exists chat_logs_feedback_check;
alter table chat_logs add  constraint chat_logs_feedback_check
  check (feedback is null or feedback in (-1, 0, 1));
