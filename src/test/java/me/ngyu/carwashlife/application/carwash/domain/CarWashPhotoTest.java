package me.ngyu.carwashlife.application.carwash.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class CarWashPhotoTest {

  private static final OffsetDateTime NOW = OffsetDateTime.parse("2026-10-07T12:00:00+09:00");

  @DisplayName("사진은 소유자가 새 관찰 근거로 제출한 이력에 한 번만 연결한다.")
  @Test
  void aPhotoCanBeAttachedOnceToItsOwnersEvidenceHistory() {
    CarWashPhoto photo = CarWashPhoto.create(1L, UUID.randomUUID().toString(), "image/png", 100, NOW);
    ReflectionTestUtils.setField(photo, "id", 10L);
    CarWashHistory history = registration(1L, List.of(10L));

    photo.attachTo(history);

    assertThat(photo.isPending()).isFalse();
    assertThat(photo.getAttachedHistory()).isSameAs(history);
    assertThatThrownBy(() -> photo.attachTo(history)).isInstanceOf(IllegalArgumentException.class);
  }

  @DisplayName("다른 작성자의 이력에 사진을 연결하려 하면 미첨부 상태를 유지하고 거절한다.")
  @Test
  void anotherOwnersHistoryCannotAttachThePhoto() {
    CarWashPhoto photo = CarWashPhoto.create(1L, UUID.randomUUID().toString(), "image/png", 100, NOW);
    ReflectionTestUtils.setField(photo, "id", 10L);

    assertThatThrownBy(() -> photo.attachTo(registration(2L, List.of(10L)))).isInstanceOf(IllegalArgumentException.class);
    assertThat(photo.isPending()).isTrue();
  }

  @DisplayName("상속한 사진은 전체 정보에 유지하되 새 관찰 근거로 취급하지 않고 원래 출처를 보존한다.")
  @Test
  void inheritedPhotosStayInTheSnapshotWithoutBecomingNewEvidence() {
    CarWashHistory original = registration(1L, List.of(10L));
    CarWashHistory next = CarWashHistory.builder().carWash(original.getCarWash()).baseHistory(original).memberId(2L)
                                        .type(CarWashHistoryType.MODIFICATION).visitExperience(VisitExperience.USED)
                                        .observedAt(NOW).submittedAt(NOW).name("새 이름").latitude(37.5).longitude(127.0).build();

    assertThat(next.getPhotoIds()).containsExactly(10L);
    assertThat(next.hasEvidencePhotos()).isFalse();
    assertThat(next.getSourceHistory(CarWashHistoryField.PHOTOS)).isSameAs(original);
    assertThatThrownBy(() -> next.getPhotoIds().clear()).isInstanceOf(UnsupportedOperationException.class);
  }

  @DisplayName("새 사진만 추가해도 실제 정보 변경으로 기록하고 기존 사진을 보존한다.")
  @Test
  void newPhotosAloneAreAnInformationChangeAndPreserveOlderPhotos() {
    CarWashHistory original = registration(1L, List.of(10L));
    CarWashHistory next = CarWashHistory.builder().carWash(original.getCarWash()).baseHistory(original).memberId(2L)
                                        .type(CarWashHistoryType.MODIFICATION).visitExperience(VisitExperience.USED)
                                        .observedAt(NOW).submittedAt(NOW).name("세차생활").latitude(37.5).longitude(127.0).newPhotoIds(List.of(20L)).build();

    assertThat(next.getPhotoIds()).containsExactly(10L, 20L);
    assertThat(next.getEvidencePhotoIds()).containsExactly(20L);
    assertThat(next.getChangedFields()).containsExactly(CarWashHistoryField.PHOTOS);
    assertThat(next.hasEvidencePhotos()).isTrue();
    assertThat(original.getPhotoIds()).containsExactly(10L);
  }

  private CarWashHistory registration(Long memberId, List<Long> photos) {
    return CarWashHistory.builder().carWash(CarWash.create("세차생활", 37.5, 127.0, NOW)).memberId(memberId)
                         .type(CarWashHistoryType.REGISTRATION).visitExperience(VisitExperience.USED)
                         .observedAt(NOW).submittedAt(NOW).name("세차생활").latitude(37.5).longitude(127.0).newPhotoIds(photos).build();
  }
}
