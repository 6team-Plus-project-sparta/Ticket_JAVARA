-- 채팅방 테이블에 종료 시간 필드 추가
-- 피드백 반영: 데이터 영속성 확보를 위한 마이그레이션
-- 더미데이터에 없어서 추가한 것. 배포시 삭제할것.

ALTER TABLE chat_room 
ADD COLUMN closed_at TIMESTAMP NULL COMMENT '채팅방 종료 시간';

-- 기존 COMPLETED 상태 채팅방들의 종료 시간을 updated_at으로 설정 (추정값)
UPDATE chat_room 
SET closed_at = updated_at 
WHERE status = 'COMPLETED' AND closed_at IS NULL;

-- 인덱스 추가 (통계 조회 성능 향상)
CREATE INDEX idx_chat_room_status_closed_at ON chat_room(status, closed_at);