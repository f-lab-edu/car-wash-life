# car-wash-life
> 셀프세차에 관심이 있는 사람들을 위한 서비스

### Project Spec

`Java 21` `Spring Boot 4.x` `PostgreSQL`

### 패키지 구조

```text
com.carwashlife
├── application
│   └── {domain}
│       ├── api
│       ├── domain
│       └── service
├── infrastructure
│   ├── persistence
│   ├── client
│   └── ...
└── common
    ├── config
    ├── exception
    └── ...
```

회원 세차장 등록·수정 API와 사진 저장 경로 설정은 [MVP 기능 문서](docs/car-wash-mvp.md)를 참고한다.
