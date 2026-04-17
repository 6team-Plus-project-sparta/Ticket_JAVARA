# 인텔리제이 커멘드 모음
___
## docker
### 1. 이미지 빌드 (프로젝트 루트에 Dockerfile 필요)
docker build -t hoon3948/ticket-javara:latest .

### 2. Docker Hub 로그인
docker login

### 3. 이미지 push
docker push hoon3948/ticket-javara:latest

### 4. 도커 컴포즈
docker compose up -d

### 5. 도커 컴포즈 상태 확인
docker ps

### 6. MySQL 접속
mysql -u db사용자이름 -p
mysql -u db사용자이름 -p --default-character-set=utf8mb4 -> 얘는 한글 봐야할때
db비밀번호

### 7. DB 선택
use ticketjavara;

### 8. 테이블 목록 확인
show tables;

___

### 포트번호 탐색
netstat -ano | findstr :포트번호
### 강종
taskkill /F /PID 프로세스id

___
## 쿠폰 재고있는데 소진이라뜰때
### 레디스 접속
docker exec -it ticketjavara-redis redis-cli
### 더미 데이터 쿠폰 데이터 삭제
DEL coupon:stock:1 coupon:stock:2 coupon:stock:3
<br>이러면 발급 요청할때 다시 찾으니까 해결됨.
<br>입력창 나갈때는 ctrl+d 혹은 ctrl+c(입력창에 exit 혹은 quit 입력도 가능)

___
### Redis 접속(exec)
redis-cli -p 6379

### 인기검색어 카운트 확인
ZREVRANGE search-keywords 0 9 WITHSCORES

### Redis 초기화(사용주의)
FLUSHALL

### Redis 접속 및 UTF-8 -> 한글 전환
redis-cli -p 6379 --no-auth-warning --resp2 --raw