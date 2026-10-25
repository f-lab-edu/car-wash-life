package me.ngyu.carwashlife.application.carwash.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class CarWashSubmissionTest {

  private static final OffsetDateTime OBSERVED_AT = OffsetDateTime.parse("2026-10-07T12:00:00+09:00");
  private static final CarWashInformation INFORMATION = CarWashInformation.builder().name("세차생활").coordinates(new Coordinates(37.5, 127.0)).build();

  @Test
  @DisplayName("등록 정보의 사진 목록은 입력 목록과 반환 목록을 통해 변경할 수 없다.")
  void registrationProtectsItsPhotoIdsFromExternalChanges() {
    List<Long> photoIds = new ArrayList<>(List.of(1L));
    CarWashRegistration registration = new CarWashRegistration(VisitExperience.USED, OBSERVED_AT, INFORMATION, photoIds);

    photoIds.add(2L);

    assertThat(registration.photoIds()).containsExactly(1L);
    assertThatThrownBy(() -> registration.photoIds().add(3L)).isInstanceOf(UnsupportedOperationException.class);
  }

  @Test
  @DisplayName("수정 정보의 사진 목록은 입력 목록과 반환 목록을 통해 변경할 수 없다.")
  void modificationProtectsItsPhotoIdsFromExternalChanges() {
    List<Long> photoIds = new ArrayList<>(List.of(1L));
    CarWashModification modification = new CarWashModification(10L, VisitExperience.USED, OBSERVED_AT, INFORMATION, photoIds);

    photoIds.add(2L);

    assertThat(modification.photoIds()).containsExactly(1L);
    assertThatThrownBy(() -> modification.photoIds().add(3L)).isInstanceOf(UnsupportedOperationException.class);
  }
}
