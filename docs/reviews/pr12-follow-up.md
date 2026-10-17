# PR #12 리뷰를 후속 PR에 적용한 판단

이 문서는 작업 근거이며 에이전트가 매번 읽는 지침이 아니다. 재사용할 기준은 `domain-design.md`와 `apply-review`에 둔다.
원본은 [PR #12](https://github.com/f-lab-edu/car-wash-life/pull/12)의 실제 리뷰·후속 답변이며, 승인 커밋은 `1bf62b6`이다.

## 리뷰의 성격

| 원문 | 반복해서 확인할 질문 |
|---|---|
| [신뢰도 Policy](https://github.com/f-lab-edu/car-wash-life/pull/12#discussion_r4241369565) | 한 객체가 가진 정보의 계산을 밖으로 옮겨 중복 검증·주입을 늘렸는가? |
| [필드 enum](https://github.com/f-lab-edu/car-wash-life/pull/12#discussion_r4241369568) | 실제 필드와 별도 목록을 함께 고쳐야 하는가? 생성 코드를 사용해도 저장 이름·응답 계약은 유지되는가? |
| [등록·수정 생성](https://github.com/f-lab-edu/car-wash-life/pull/12#discussion_r4241369569) | 호출자가 잘못된 상태 조합을 만들 수 있는가? 생성 의도와 불변조건의 주인이 일치하는가? |
| [전체 스냅샷](https://github.com/f-lab-edu/car-wash-life/pull/12#discussion_r4241369572) | 서비스가 처리 순서 외에 긴 필드 목록의 복사와 변환까지 관리하는가? |
| [좌표 값](https://github.com/f-lab-edu/car-wash-life/pull/12#discussion_r4241369577) | 함께 유효해야 하는 값의 규칙이 원장·이력에 흩어져 있는가? |
| [시설 상태](https://github.com/f-lab-edu/car-wash-life/pull/12#discussion_r4241369579) | 상태의 의미를 외부에서 직접 비교하는가? 기존 도메인 타입이 판단할 수 있는가? |
| [검증 후 저장](https://github.com/f-lab-edu/car-wash-life/pull/12#discussion_r4241369582) | 저장 없이 거절할 요청을 첫 쓰기 전에 거절하는가? |
| [예외 경계](https://github.com/f-lab-edu/car-wash-life/pull/12#discussion_r4241369585) | 저장 장애를 입력 오류로 바꾸는가? 원인은 남고 공개 응답에는 내부 정보가 없는가? |

'Policy를 모두 없앤다', '중복이면 모두 공통화한다'로 일반화하지 않는다. 소유권·현재 요구·실패 분류를 근거로 판단한다.

## 지침 1차 개선

- 일반적인 SOLID 나열을 데이터 소유자·함께 바뀌는 지점·추상화 비용의 판단으로 교체했다.
- 목적이 다른 API 입력 검증과 도메인 불변조건 검증을 구분했다.
- 저장 전 검증은 입력·객체 생성에 한정했다. 동시성 잠금과 저장된 후보의 대표 선택까지 앞당기지 않는다.
- 예외 범위 축소와 타입 구분의 선택 기준, 서비스와 DTO의 변환 책임을 명시했다.
- 에이전트는 요청한 비교 범위와 관련 호출부를 확인하고, 테스트 통과와 설계 적절성을 별도로 판단하게 했다.
- 공통 AGENTS.md는 늘리지 않았다. 세 역할 지침은 각 역할의 판단만 다룬다.

## PR #14 적용과 피드백

검토 범위는 마지막 병합 커밋만이 아닌 `feat/7-member-car-wash-registration...feat/8-member-car-wash-query` 전체와 호출부다.

- **수정:** 조회 서비스의 긴 상세 응답 매핑을 `CarWashDetailDto.Response.from`으로 이동했다. 평탄한 JSON과 필드 값은 유지한다.
- **이미 충족:** 이력의 신뢰도 계산, 생성 메서드, Coordinates·시설 enum의 검증, 생성 enum과 과거 저장 이름 읽기, 검증 후 저장, 좁은 예외 변환과 cause 보존.
- **유지 근거:** 대표 선택 Policy는 여러 이력의 비교 규칙을 소유한다. 조회의 쓰기 트랜잭션·잠금은 대표 반영에 필요하다.
- **제외:** 공통 인증 서비스, 별도 Mapper 계층, 새 이름 값 객체는 이번 리뷰에서 필요성이 확인되지 않았다.
- **검증:** 전체 테스트 187개 성공. 상세 응답의 전체 값·시간·회원 ID 비노출, 동점 선택, 90일 재평가, 갱신 시각과 동시 수정 테스트를 유지했다.
- **지침 평가:** 원문과 갱신 지침만 받은 독립 분석도 같은 매핑 문제를 찾고 대표 Policy 유지 근거를 설명했다. PR #14에서 새 지침 결함은 확인되지 않았다.

로컬 DB 검증은 H2 PostgreSQL 모드이며 실제 PostgreSQL의 모든 동작을 검증한 것은 아니다.
