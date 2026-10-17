# 배열을 가진 값 객체

배열을 생성할 때와 반환할 때 모두 복사한다. 내용으로 동등성을 정의했다면 외부 변경으로 해시가 달라져서도 안 된다.
다음은 사진 값에서 사용하는 방식이다. 큰 배열의 accessor 결과가 여러 번 필요하면 호출부의 지역 변수로 한 번만 받는다.

```java
public record PhotoContent(byte[] content, String contentType) {

  public PhotoContent {
    content = content.clone();
  }

  @Override
  public byte[] content() {
    return content.clone();
  }

  @Override
  public boolean equals(Object other) {
    return other instanceof PhotoContent photo && Arrays.equals(content, photo.content) && Objects.equals(contentType, photo.contentType);
  }

  @Override
  public int hashCode() {
    return 31 * Arrays.hashCode(content) + Objects.hashCode(contentType);
  }
}
```

`java.util.Arrays`, `java.util.Objects`를 사용한다. 기존 응답 계약과 필요한 문자열 표현은 별도로 보존한다.
