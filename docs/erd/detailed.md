현재 구현된 회원·세차장·제보 이력·사진의 관계와 저장 필드를 정리한다. 아직 구현하지 않은 탐색 기능이나 시설 항목은 포함하지 않는다.

![Car Wash Life 상세 ERD](detailed.svg)

[원본 SVG 크게 보기](detailed.svg) · [프로젝트 README](https://github.com/f-lab-edu/car-wash-life#readme)

## 구현 기준

- 기준 구현: [main의 `9dcf9c0`](https://github.com/f-lab-edu/car-wash-life/tree/9dcf9c0c7bf334fd36020dbf1736b6f15ee44c7b). 이 문서는 JPA 매핑을 설명하며, 운영 DB의 실제 DDL·인덱스 적용 여부를 보증하지 않는다.
- `PK`: 기본키, `FK`: 외래키, `ID`: 스칼라 ID 참조, `NN`: null 불가, `UQ`: 고유. 표의 빈 제약은 JPA 매핑에 해당 제약을 지정하지 않았다는 뜻이다.
- 필드명과 타입은 Java/JPA 기준이다. `Coordinates`는 값 객체이며 별도 엔티티·테이블이 아니다. `latitude`·`longitude` 컬럼이 소속 테이블에 저장된다.

## 관계

| 관계 | 구현 | 개수와 의미 |
|---|---|---|
| 세차장 → 소속 제보 | `CarWashHistory.carWash`, `car_wash_id`, `ManyToOne`, 필수 FK | 제보는 반드시 한 세차장에 속한다. 세차장은 여러 제보를 가진다. |
| 세차장 → 대표 제보 | `CarWash.targetHistory`, `target_history_id`, `OneToOne`, 선택 FK | 원장당 최대 한 대표 제보. 해당 제보가 같은 세차장에 속하는지는 도메인에서 검증한다. |
| 제보 → 기준 제보 | `CarWashHistory.baseHistory`, `base_history_id`, `ManyToOne`, 선택 FK | 등록 제보는 기준이 없고, 수정 제보는 같은 세차장의 기준 제보를 참조한다. 한 기준에서 여러 수정이 나올 수 있다. |
| 사진 → 최초 첨부 제보 | `CarWashPhoto.attachedHistory`, `attached_history_id`, `ManyToOne`, 선택 FK | 선업로드 상태는 null. 첨부 후 최초 연결을 보존하며, 한 제보에 여러 사진이 연결될 수 있다. |
| 회원 → 작성 제보 | `CarWashHistory.memberId`, 필수 ID 값 | 작성자를 서비스에서 확인한다. 회원 테이블에 대한 DB FK는 없다. |
| 회원 → 업로드 사진 | `CarWashPhoto.ownerId`, 필수 ID 값 | 업로드한 회원을 서비스에서 확인한다. 회원 테이블에 대한 DB FK는 없다. |

## 엔티티별 저장 필드

### Member · `members`

[구현 코드](https://github.com/f-lab-edu/car-wash-life/blob/9dcf9c0c7bf334fd36020dbf1736b6f15ee44c7b/src/main/java/me/ngyu/carwashlife/application/member/domain/Member.java)

| Java 필드 | Java 타입 | 제약·관계 | 의미 |
|---|---|---|---|
| `id` | `Long` | PK | 회원 ID |
| `email` | `String` | NN, UQ | 이메일 |
| `password` | `String` | NN | 해시된 비밀번호, 최대 60자 |
| `residenceRegionCode` | `String` | — | 거주 지역 코드 |
| `vehicleType` | `VehicleType` | — | 차량 종류, 문자열 enum |
| `washExperience` | `WashExperience` | — | 세차 경험, 문자열 enum |
| `emailVerified` | `boolean` | NN | 이메일 인증 여부 |
| `status` | `MemberStatus` | NN | 회원 상태, 문자열 enum |
| `createdAt` | `OffsetDateTime` | NN | 가입 시각, 갱신 불가 |

### CarWash · `car_wash`

[구현 코드](https://github.com/f-lab-edu/car-wash-life/blob/9dcf9c0c7bf334fd36020dbf1736b6f15ee44c7b/src/main/java/me/ngyu/carwashlife/application/carwash/domain/CarWash.java)

| Java 필드 | Java 타입 | 제약·관계 | 의미 |
|---|---|---|---|
| `id` | `Long` | PK | 세차장 ID |
| `name` | `String` | NN | 대표 제보의 이름을 원장에 반영 |
| `coordinates.latitude` | `double` | NN | 위도, Coordinates를 같은 테이블에 내장 |
| `coordinates.longitude` | `double` | NN | 경도, Coordinates를 같은 테이블에 내장 |
| `operatorManaged` | `boolean` | NN | 운영자 관리 여부 |
| `targetHistory` | `CarWashHistory` | FK, 0..1 | target_history_id, 일대일 대표 제보 참조 |
| `createdAt` | `OffsetDateTime` | NN | 서비스 최초 등록 시각, 갱신 불가 |
| `updatedAt` | `OffsetDateTime` | NN | 원장 갱신 시각; 최근 제보 제출 시각과 다름 |

### CarWashHistory · `car_wash_history`

[구현 코드](https://github.com/f-lab-edu/car-wash-life/blob/9dcf9c0c7bf334fd36020dbf1736b6f15ee44c7b/src/main/java/me/ngyu/carwashlife/application/carwash/domain/CarWashHistory.java)

| Java 필드 | Java 타입 | 제약·관계 | 의미 |
|---|---|---|---|
| `id` | `Long` | PK | 제보 ID |
| `carWash` | `CarWash` | FK, NN | car_wash_id, 소속 세차장 |
| `baseHistory` | `CarWashHistory` | FK, 0..1 | base_history_id, 수정 기준 제보 |
| `changedFieldNames` | `String` | — | 변경 항목 이름을 쉼표로 구분, 최대 1,000자 |
| `evidencePhotoIdValues` | `String (TEXT)` | — | 이번 제보의 신규 사진 ID 목록, 쉼표 구분 |
| `memberId` | `Long` | ID, NN | 제보 작성자 ID; 회원 FK 없음 |
| `type` | `CarWashHistoryType` | NN | REGISTRATION / MODIFICATION |
| `visitExperience` | `VisitExperience` | NN | NOT_USED / USED |
| `observedAt` | `OffsetDateTime` | NN | 회원이 정보를 관찰했다고 제보한 시각 |
| `submittedAt` | `OffsetDateTime` | NN | 서버가 제보를 접수한 시각 |
| `name` | `String` | NN | 이름 스냅샷 |
| `address` | `String` | — | 주소 스냅샷, null은 미확인 |
| `coordinates.latitude` | `double` | NN | 위도 스냅샷, 내장 값 |
| `coordinates.longitude` | `double` | NN | 경도 스냅샷, 내장 값 |
| `highPressureWaterPrice` | `Integer` | — | 고압수 가격, null은 미확인 |
| `foamLanceAvailability` | `FacilityAvailability` | NN | 폼랜스 사용 가능 여부 |
| `foamGunPrice` | `Integer` | — | 폼건 가격 |
| `airGunAvailability` | `FacilityAvailability` | NN | 에어건 사용 가능 여부 |
| `airGunPrice` | `Integer` | — | 에어건 가격 |
| `vacuumAvailability` | `FacilityAvailability` | NN | 진공청소기 사용 가능 여부 |
| `vacuumPrice` | `Integer` | — | 진공청소기 가격 |
| `washBayCount` | `Integer` | — | 세차 부스 개수 |
| `dryingBayCount` | `Integer` | — | 드라잉존 개수 |
| `photos` | `String (TEXT)` | — | photo_id_values, 기준 제보에서 이어받은 사진과 신규 사진 ID의 누적 목록 |

### CarWashPhoto · `car_wash_photo`

[구현 코드](https://github.com/f-lab-edu/car-wash-life/blob/9dcf9c0c7bf334fd36020dbf1736b6f15ee44c7b/src/main/java/me/ngyu/carwashlife/application/carwash/domain/CarWashPhoto.java)

| Java 필드 | Java 타입 | 제약·관계 | 의미 |
|---|---|---|---|
| `id` | `Long` | PK | 사진 ID |
| `ownerId` | `Long` | ID, NN | 업로드한 회원 ID; 회원 FK 없음 |
| `storageKey` | `String` | NN, UQ | 서버 생성 파일 키, 최대 36자 |
| `contentType` | `String` | NN | image/jpeg 또는 image/png |
| `size` | `long` | NN | 파일 크기, byte |
| `createdAt` | `OffsetDateTime` | NN | 업로드 시각 |
| `attachedHistory` | `CarWashHistory` | FK, 0..1 | attached_history_id, 최초 첨부 제보; 미첨부 상태면 null |

## 사진 목록과 관찰 근거

`CarWashPhoto.attachedHistory`는 사진이 최초로 첨부된 제보를 가리킨다. 이후 수정 제보에서 같은 사진을 이어받아도 이 FK를 새 제보로 옮기지 않는다.

`CarWashHistory.photos`는 `photo_id_values`라는 TEXT 컬럼에 누적 사진 ID를 쉼표로 구분해 저장한다. `evidencePhotoIdValues`도 TEXT 문자열이며, 이번 요청에서 새로 첨부한 사진 ID만 기록한다. 두 필드 모두 FK·관계 테이블·JPA 컬렉션이 아니다. 따라서 사진을 이어받는 관계를 별도의 다대다 테이블처럼 그리지 않았다.

새 사진만 신뢰도 가산점에 사용하며, 기준 제보에서 이어받은 사진에는 이번 제보의 사진 가산점이 없다. 사진은 요청당 최대 5개를 새로 첨부할 수 있고, 본인이 업로드한 미사용 사진만 최초 연결할 수 있다.

## 스냅샷과 대표 정보

제보는 이름·주소·좌표·가격·시설·베이 수를 전체 스냅샷으로 보존한다. 기준 제보에서 값이 달라진 항목을 `changedFieldNames`에 기록하고, 변경하지 않은 값의 출처는 기준 제보를 따라 찾는다. 이는 마지막 재확인 시각을 뜻하지 않는다.

대표 제보가 선택되면 원장의 이름·좌표·대표 포인터를 함께 갱신한다. 가장 최근 제출한 제보가 항상 대표는 아니다. 신뢰도는 이용 경험·관찰 최근성·신규 사진으로 계산하는 값이며 저장 컬럼이 아니다.

`CarWash.targetHistory`는 JPA 매핑상 nullable이지만, 정상 등록 서비스는 원장과 최초 제보를 생성하고 대표를 지정한 뒤 트랜잭션을 완료한다. 도메인은 대표 제보·기준 제보가 같은 세차장에 속하는지 검증한다. 단일 FK만으로 이 교차 조건을 보장하지 않는다.

## 제약과 인덱스

- 네 엔티티의 기본키는 `Long`, `GenerationType.IDENTITY`를 사용한다.
- 회원 이메일은 고유하며 필수다. 비밀번호 컬럼에는 원문이 아닌 해시를 저장하고 길이를 60자로 제한한다.
- 사진 저장 키는 고유하며 길이는 36자다. 서버가 생성한 상대 파일 키이고, 원본 파일명이나 서버 절대 경로가 아니다. 파일 본문은 DB가 아닌 설정된 파일 저장소에 저장한다.
- `CarWash.targetHistory`는 일대일 매핑이다. 다대일인 소속 제보·기준 제보·최초 첨부 제보와 구분한다.
- `car_wash_history.car_wash_id`에는 비고유 인덱스 `idx_car_wash_history_car_wash_id`를 선언한다. 실제 운영 DB의 생성 상태와 사용 여부는 별도 확인이 필요하다.
- 연관관계는 모두 지연 로딩이다. 명시적인 JPA cascade나 orphanRemoval, 삭제 전파 정책은 선언하지 않는다.
- 제보 스냅샷 필드와 사진의 업로드 메타데이터는 갱신 불가로 매핑한다. 사진의 최초 첨부 FK는 선업로드 후 연결하기 위해 갱신할 수 있다.
- 가격·베이 수는 null이면 미확인, 0이면 확인된 0이다. 시설 상태는 `UNKNOWN`, `AVAILABLE`, `UNAVAILABLE`이며 에어건·청소기가 불가능하면 가격을 입력할 수 없다. 이 값 검증은 도메인 규칙이며 DB CHECK 제약이 선언됐다는 의미가 아니다.

## Enum 값

| 타입 | 값 |
|---|---|
| `MemberStatus` | `ACTIVE`, `SUSPENDED`, `WITHDRAWN` |
| `VehicleType` | `SEDAN`, `SUV`, `COUPE`, `HATCHBACK`, `WAGON`, `VAN`, `TRUCK`, `OTHER` |
| `WashExperience` | `BEGINNER`, `OCCASIONAL`, `REGULAR`, `EXPERIENCED` |
| `CarWashHistoryType` | `REGISTRATION`, `MODIFICATION` |
| `VisitExperience` | `NOT_USED`, `USED` |
| `FacilityAvailability` | `UNKNOWN`, `AVAILABLE`, `UNAVAILABLE` |

모든 enum 영속 필드는 `EnumType.STRING`으로 매핑한다.

## 다이어그램 원본

요약본은 관계와 식별 필드만 보여주고, 상세본은 모든 영속 필드를 표시한다. 두 버전 모두 실제 엔티티 4개와 동일한 6개 관계를 사용한다. 값 객체·리뷰·미구현 시설을 별도 엔티티로 추가하지 않았다.

- [요약 HTML 원본](overview.html)
- [상세 HTML 원본](detailed.html)

HTML 원본은 웹 폰트를 사용한다. GitHub 이미지용 SVG는 외부 폰트를 요청하지 않으며, 설치된 폰트에 따라 대체 서체로 표시될 수 있다.
