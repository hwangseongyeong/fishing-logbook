# 낚시 로그북 (Fishing Logbook) — Phase 1 MVP

내 조행을 기록하는 로그북. 이후 공개 조행이 커뮤니티/포인트 지도로 확장됩니다.

## 스택
- Kotlin + Spring Boot 3.3
- Spring Data JPA, Spring Security + JWT
- H2(기본, 즉시 실행) / PostgreSQL(프로필 전환)

## 실행
```bash
./gradlew bootRun
# → http://localhost:8080
# H2 콘솔: http://localhost:8080/h2-console (JDBC URL: jdbc:h2:file:./data/logbook)
# 데이터는 ./data/logbook.mv.db 에 유지됩니다.
```

PostgreSQL로 실행:
```bash
SPRING_PROFILES_ACTIVE=postgres ./gradlew bootRun
```

## API 요약

### 인증 (토큰 불필요)
| 메서드 | 경로 | 설명 |
|---|---|---|
| POST | `/api/auth/signup` | 회원가입 → JWT 발급 |
| POST | `/api/auth/login` | 로그인 → JWT 발급 |

### 조행 (헤더 `Authorization: Bearer <token>` 필요)
| 메서드 | 경로 | 설명 |
|---|---|---|
| POST | `/api/trips` | 조행 등록(조과 포함) |
| GET | `/api/trips` | 내 조행 목록 |
| GET | `/api/trips/{id}` | 조행 상세 |
| PUT | `/api/trips/{id}` | 조행 수정 |
| DELETE | `/api/trips/{id}` | 조행 삭제 |
| GET | `/api/stats/me` | 내 통계(총 출조수, 어종별 마릿수) |
| POST | `/api/uploads` | 사진 업로드(multipart `file`) → `{ url }` |
| GET | `/api/map/nearby` | 내 주변 공개 조행(포인트) 검색 `?lat=&lng=&radiusKm=20&limit=50` |

### Phase 2 자동화
- **환경 자동 채움**: 조행 등록 시 `latitude`/`longitude`가 있고 `weather`/`waterTemp`/`tideInfo`가
  비어 있으면 `EnvironmentDataProvider`가 자동으로 채웁니다. 현재는 `StubEnvironmentDataProvider`(외부 API 불필요)가 기본.
  실제 연동은 `environment/EnvironmentData.kt`의 `RealEnvironmentDataProvider` 주석 참고 (기상청 단기예보 + 바다누리 조석예보).
- **지도 검색**: `/api/map/nearby`는 바운딩 박스로 1차 조회 후 Haversine으로 반경을 정밀 필터링하고 거리순 정렬.

### Phase 3 커뮤니티 (토큰 필요)
| 메서드 | 경로 | 설명 |
|---|---|---|
| GET | `/api/feed` | 전체 최신 공개 조행 피드 `?page=0&size=20` |
| GET | `/api/feed/following` | 내가 팔로우하는 사용자들의 공개 조행 피드 |
| GET | `/api/spots` | 포인트별 조황 집계 `?minTripCount=1` |
| GET | `/api/users/{id}` | 사용자 프로필(조행/팔로워/팔로잉 수) |
| POST/DELETE | `/api/users/{id}/follow` | 팔로우 / 언팔로우 |
| POST/DELETE | `/api/trips/{id}/like` | 좋아요 / 취소 |
| GET/POST | `/api/trips/{id}/comments` | 댓글 목록 / 작성 |
| DELETE | `/api/comments/{id}` | 댓글 삭제(본인만) |

- **피드**: 공개(PUBLIC) 조행만 노출. 좋아요/댓글 수·작성자·내가 좋아요했는지를 배치 조회(N+1 방지)로 함께 내려줌.
- **포인트 집계**: 공개 조행을 약 1km 격자(좌표 소수점 2자리)로 묶어 어종별 조황을 합산 → "이 포인트에서 뭐가 잡히나" 요약.

## 빠른 테스트 (curl)
```bash
# 1) 회원가입
TOKEN=$(curl -s -X POST localhost:8080/api/auth/signup \
  -H 'Content-Type: application/json' \
  -d '{"email":"me@test.com","password":"password1","nickname":"조사"}' \
  | sed -E 's/.*"token":"([^"]+)".*/\1/')

# 2) 조행 등록
curl -s -X POST localhost:8080/api/trips \
  -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d '{
    "title":"방파제 광어 조행",
    "fishedAt":"2026-11-27T06:00:00",
    "placeName":"대부도 방아머리",
    "latitude":37.25,"longitude":126.58,
    "weather":"맑음","waterTemp":14.5,"tideInfo":"들물",
    "visibility":"PUBLIC",
    "gears":["원투대","2000번릴"],
    "catches":[{"species":"광어","count":2,"lengthCm":45.0}]
  }'

# 3) 통계
curl -s localhost:8080/api/stats/me -H "Authorization: Bearer $TOKEN"
```

## 다음 단계 (Phase 2+)
- 위치: geohash 또는 PostGIS로 "내 주변 포인트" 검색
- 날씨/물때: 기상청·국립해양조사원(바다누리) API 자동 채움
- 커뮤니티: 공개(PUBLIC) 조행 피드, 댓글/좋아요/팔로우, 포인트별 조황 집계
- 사진 저장: 로컬 → S3 + CDN
