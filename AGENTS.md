# Car Wash Life Codex Guide

## 공통 원칙

- 관련 코드와 기존 구현을 먼저 확인하고, 요청 범위만 변경한다. 새 기능·의존성·계층을 임의로 추가하지 않는다.
- 사용자와 다른 작업자의 변경을 보존한다. 위임 시 담당 파일을 지정하고 수정 범위가 겹치지 않게 한다.
- 기능은 `application/{feature}/{api,domain,service}`, DTO는 `api/dto`, Spring Data Repository는 `infrastructure/persistence`에 둔다. 현재 JPA Entity를 도메인 모델로 사용한다.
- Java 포맷은 루트 `GoogleStyle.xml`을 따른다. DI는 `@RequiredArgsConstructor`와 `private final` 필드를 사용하며, 검증·초기화 본문이 필요한 생성자는 명시한다.
- 기존 동작과 테스트 의도를 보존하고 관련 테스트를 실행한다. 미실행·실패·검증 한계는 보고한다. 로컬 검증과 외부 CI 결과를 구분한다.

## 브랜치

- 브랜치 이름은 `<type>/<이슈 번호>-<영문 설명>` 형식을 사용한다. 영문 설명은 소문자와 하이픈으로 작성한다. 예: `feat/16-car-wash-exploration`.
- type은 작업 성격에 따라 `feat`, `fix`, `refactor`, `test`, `docs`, `chore` 중 선택한다.
- 새 이슈의 작업 브랜치는 최신 `origin/main`에서 분기한다. 사용자가 기준 브랜치를 별도로 지정했다면 해당 지시를 따른다.
- 지정한 브랜치에서만 수정한다. 연관 브랜치로 전파하거나 푸시하려면 별도 요청이 필요하다.

## 필요한 지침만 읽기

다음 문서는 해당 작업을 수행하는 에이전트가 직접 읽는다. 전체 참고 문서를 한꺼번에 로드하지 않는다.

| 작업 | 지침 |
|---|---|
| Java 소스·테스트 작성 또는 포맷 검토 | [.agents/skills/develop-feature/references/java-style.md](.agents/skills/develop-feature/references/java-style.md) |
| 객체·모듈 책임, API·도메인·DTO·JPA·예외 설계 변경 또는 해당 코드 리뷰 | [.agents/skills/develop-feature/references/domain-design.md](.agents/skills/develop-feature/references/domain-design.md) |
| 테스트 작성·실행 또는 테스트 적절성 검토 | [.agents/skills/develop-feature/references/testing.md](.agents/skills/develop-feature/references/testing.md) |
| 인증·회원가입·토큰 관련 작업 | [.agents/skills/develop-feature/references/authentication.md](.agents/skills/develop-feature/references/authentication.md) |

여러 계층의 구현은 `develop-feature`, 독립 검토는 `review-change`, PR·SonarCloud 지적 반영은 `apply-review` 스킬을 사용한다. 단순 변경에 불필요한 위임을 추가하지 않는다.

## 커밋

- 요청이 금지하지 않았다면 작업 완료와 관련 검증 성공 후 커밋한다. 검증 실패 상태에서는 커밋하지 않는다.
- 하나의 커밋은 하나의 논리적인 변경만 포함한다.
- 메시지는 한글로 `<type>: <변경 내용>` 형식을 사용한다. type은 `feat`, `fix`, `refactor`, `test`, `docs`, `chore` 중 선택한다.
