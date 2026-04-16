docker compose up -d
- 도커 컴포즈
docker ps
- 도커 확인
netstat -ano | findstr :포트번호
- 포트번호 탐색
taskkill /F /PID 프로세스id
- 강종


# MySQL 접속
mysql -u db사용자이름 -p
db비밀번호

# DB 선택
use ticketjavara;

# 테이블 목록 확인
show tables;

# Redis 접속(exec)
redis-cli -p 6379

# 인기검색어 카운트 확인
ZREVRANGE search-keywords 0 9 WITHSCORES

# Redis 초기화(사용주의)
FLUSHALL

# Redis 접속 및 UTF-8 -> 한글 전환
redis-cli -p 6379 --no-auth-warning --resp2 --raw