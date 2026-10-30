# 행정지역 검색 스냅샷

`administrative-regions-20261001.tsv`는 `vuski/admdongkor`의 2026-10-01 버전 행정동 폴리곤에서 파생한 검색용 스냅샷이다. 원본은 통계청 SGIS 자료를 바탕으로 가공한 데이터이며, 현재의 공식 행정구역 경계와 일부 차이가 있을 수 있다. 법정동·도로명·건물·POI 검색은 제공하지 않는다.

본 데이터는 통계청 통계지리정보서비스(SGIS, https://sgis.kostat.go.kr)에서 공공누리 제1유형으로 개방한 행정동 경계를 가공한 것이며(가공: vuski/admdongkor, https://github.com/vuski/admdongkor), CC BY 4.0으로 배포됩니다.

- 원본: https://github.com/vuski/admdongkor/blob/3bc00489b76696db864bdbea4b19985cdfa8b403/ver20261001/HangJeongDong_ver20261001.geojson
- 라이선스: CC BY 4.0, https://github.com/vuski/admdongkor/blob/master/LICENSE-DATA
- 출처 표기: 통계청 SGIS / vuski(admdongkor), 행정동 경계 2026-10-01 버전에서 파생
- 원본 SHA-256: `843ece3a0b3429742cd4a1b699cc13987a88b5e5401f382e7fdaaa194ea5e605`

3559개 행정 읍면동 폴리곤의 모든 점에서 WGS84 최소·최대 위도·경도를 구하고 상위 지역별로 합쳐 3843개 항목을 생성했다. 시도, 시군구, 행정 읍면동을 포함하며 일반구가 있는 시는 시 전체와 개별 일반구를 모두 제공한다. `수원시장안구` 같은 원본 이름은 `경기도 수원시 장안구`로 표시한다. 세종특별자치시와 원본의 세종시는 하나의 상위 지역 결과로 합쳤다. 시도 수와 명칭은 원본 버전에 따르며 원본의 `전남광주통합특별시` 표기도 유지한다.

`regionId`는 서비스 내부 식별자다. `SIDO:`, `SGG:`, `EMD:` 뒤의 코드는 원본 필드를 사용하며, `CITY:` 항목은 일반구가 있는 시 전체를 합성한 식별자로 공식 행정코드가 아니다. 검색 결과의 범위는 실제 폴리곤이 아닌 외접 사각형이다. 표시용 중심 좌표는 이 사각형의 중앙이며 실제 지역 내부를 보장하지 않는다. 범위 좌표는 반올림하지 않고 원본의 double 정밀도를 보존했다.

프로젝트 루트에서 원본을 받은 다음 표준 Python 3만으로 재생성한다.

```sh
curl -L https://raw.githubusercontent.com/vuski/admdongkor/3bc00489b76696db864bdbea4b19985cdfa8b403/ver20261001/HangJeongDong_ver20261001.geojson -o /tmp/carwash-admin-20261001.geojson
shasum -a 256 /tmp/carwash-admin-20261001.geojson
python3 scripts/generate_region_snapshot.py /tmp/carwash-admin-20261001.geojson src/main/resources/regions/administrative-regions-20261001.tsv
```

원본 URL은 커밋으로 고정했으며 재생성 전 다운로드한 파일의 해시를 위 값과 비교한다. 갱신 시에는 원본 버전·URL·해시, 스냅샷 파일명, 로더의 경로와 기대 항목 수(`EXPECTED_REGION_COUNT`)를 함께 수정하고, 동명 지역·일반구의 상위 시 집계·지도 범위 연결 테스트를 실행한다. 고정 기대 항목 수와 다른 파일은 중간에서 잘린 스냅샷일 수 있으므로 로드 실패로 처리한다. 스냅샷의 정상 로드 결과만 메모리에 캐시한다. 로드 실패는 빈 검색 결과로 처리하지 않고 503 `REGION_SEARCH_UNAVAILABLE`로 응답하며, 다음 요청에서 다시 로드하므로 원본 리소스를 복구한 뒤 재시도할 수 있다. 배포 산출물에 포함된 파일을 바꾸려면 새 산출물 배포가 필요하다.
