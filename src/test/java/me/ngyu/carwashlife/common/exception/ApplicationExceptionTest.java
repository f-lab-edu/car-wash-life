package me.ngyu.carwashlife.common.exception;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ApplicationExceptionTest {

  @DisplayName("원인을 전달하면 오류 코드와 공개 메시지를 유지하면서 원래 예외를 보존한다.")
  @Test
  void preservesOriginalCause() {
    IllegalArgumentException cause = new IllegalArgumentException("에어건을 사용할 수 없으면 가격을 입력할 수 없습니다.");

    ApplicationException exception = new ApplicationException(ErrorCode.VALIDATION_ERROR, cause);

    assertThat(exception.getCause()).isSameAs(cause);
    assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.VALIDATION_ERROR);
    assertThat(exception.getMessage()).isEqualTo(ErrorCode.VALIDATION_ERROR.getMessage());
  }

  @DisplayName("원인이 있는 예외를 처리해도 응답에는 공개 오류 코드와 메시지만 포함한다.")
  @Test
  void responseDoesNotExposeCause() {
    ApplicationException exception = new ApplicationException(ErrorCode.VALIDATION_ERROR,
                                                              new IllegalArgumentException("내부 검증 원인"));

    var response = new GlobalExceptionHandler().handleApplicationException(exception);

    assertThat(response.getStatusCode()).isEqualTo(ErrorCode.VALIDATION_ERROR.getStatus());
    assertThat(response.getBody()).isEqualTo(ErrorResponse.from(ErrorCode.VALIDATION_ERROR));
  }
}
