package me.ngyu.carwashlife.application.carwash.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import me.ngyu.carwashlife.application.carwash.domain.CarWashHistory.Fields;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class CarWashPhotoTest {

  private static final OffsetDateTime NOW = OffsetDateTime.parse("2026-10-07T12:00:00+09:00");

  @Test
  @DisplayName("미첨부 사진은 소유자만 조회하고 첨부할 수 있다.")
  void aPendingPhotoIsReadableAndAttachableOnlyByItsOwner() {
    CarWashPhoto photo = CarWashPhoto.create(1L, UUID.randomUUID().toString(), "image/png", 100, NOW);

    assertThat(photo.canReadBy(1L)).isTrue();
    assertThat(photo.canAttachBy(1L)).isTrue();
    assertThat(photo.canReadBy(2L)).isFalse();
    assertThat(photo.canAttachBy(2L)).isFalse();
    assertThat(photo.canReadBy(null)).isFalse();
    assertThat(photo.canAttachBy(null)).isFalse();
    assertThat(photo.canReadBy(0L)).isFalse();
    assertThat(photo.canAttachBy(0L)).isFalse();
  }

  @Test
  @DisplayName("첨부한 사진은 다른 회원도 조회할 수 있지만 누구도 다시 첨부할 수 없다.")
  void anAttachedPhotoIsReadableByOtherMembersButCannotBeAttachedAgain() {
    CarWashPhoto photo = CarWashPhoto.create(1L, UUID.randomUUID().toString(), "image/png", 100, NOW);
    ReflectionTestUtils.setField(photo, "id", 10L);
    photo.attachTo(registration(1L, List.of(10L)));

    assertThat(photo.canReadBy(1L)).isTrue();
    assertThat(photo.canReadBy(2L)).isTrue();
    assertThat(photo.canReadBy(null)).isFalse();
    assertThat(photo.canReadBy(0L)).isFalse();
    assertThat(photo.canAttachBy(1L)).isFalse();
    assertThat(photo.canAttachBy(2L)).isFalse();
  }

  @Test
  @DisplayName("사진은 소유자가 새 관찰 근거로 제출한 이력에 한 번만 연결한다.")
  void aPhotoCanBeAttachedOnceToItsOwnersEvidenceHistory() {
    CarWashPhoto photo = CarWashPhoto.create(1L, UUID.randomUUID().toString(), "image/png", 100, NOW);
    ReflectionTestUtils.setField(photo, "id", 10L);
    CarWashHistory history = registration(1L, List.of(10L));

    photo.attachTo(history);

    assertThat(photo.isPending()).isFalse();
    assertThat(photo.getAttachedHistory()).isSameAs(history);
    assertThatThrownBy(() -> photo.attachTo(history)).isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  @DisplayName("다른 작성자의 이력에 사진을 연결하려 하면 미첨부 상태를 유지하고 거절한다.")
  void anotherOwnersHistoryCannotAttachThePhoto() {
    CarWashPhoto photo = CarWashPhoto.create(1L, UUID.randomUUID().toString(), "image/png", 100, NOW);
    ReflectionTestUtils.setField(photo, "id", 10L);

    assertThatThrownBy(() -> photo.attachTo(registration(2L, List.of(10L)))).isInstanceOf(IllegalArgumentException.class);
    assertThat(photo.isPending()).isTrue();
  }

  @Test
  @DisplayName("소유자의 이력이라도 새 관찰 근거로 제출하지 않은 사진은 연결할 수 없다.")
  void aPhotoOutsideTheHistoryEvidenceCannotBeAttached() {
    CarWashPhoto photo = CarWashPhoto.create(1L, UUID.randomUUID().toString(), "image/png", 100, NOW);
    ReflectionTestUtils.setField(photo, "id", 10L);

    assertThatThrownBy(() -> photo.attachTo(registration(1L, List.of()))).isInstanceOf(IllegalArgumentException.class);
    assertThat(photo.isPending()).isTrue();
  }

  @Test
  @DisplayName("상속한 사진은 전체 정보에 유지하되 새 관찰 근거로 취급하지 않고 원래 출처를 보존한다.")
  void inheritedPhotosStayInTheSnapshotWithoutBecomingNewEvidence() {
    CarWashHistory original = registration(1L, List.of(10L));
    CarWashHistory next = CarWashHistory.modify(original.getCarWash(), original, 2L, VisitExperience.USED, NOW, NOW,
                                                CarWashInformation.builder().name("새 이름").coordinates(new Coordinates(37.5, 127.0)).build());

    assertThat(next.getPhotoIds()).containsExactly(10L);
    assertThat(next.hasEvidencePhotos()).isFalse();
    assertThat(original.calculateConfidence(NOW)).isEqualTo(5);
    assertThat(next.calculateConfidence(NOW)).isEqualTo(4);
    assertThat(next.getSourceHistory(Fields.PHOTOS)).isSameAs(original);
    assertThatThrownBy(() -> next.getPhotoIds().clear()).isInstanceOf(UnsupportedOperationException.class);
  }

  @Test
  @DisplayName("새 사진만 추가해도 실제 정보 변경으로 기록하고 기존 사진을 보존한다.")
  void newPhotosAloneAreAnInformationChangeAndPreserveOlderPhotos() {
    CarWashHistory original = registration(1L, List.of(10L));
    CarWashHistory next = CarWashHistory.modify(original.getCarWash(), original, 2L, VisitExperience.USED, NOW, NOW,
                                                CarWashInformation.builder().name("세차생활").coordinates(new Coordinates(37.5, 127.0)).build(), List.of(20L));

    assertThat(next.getPhotoIds()).containsExactly(10L, 20L);
    assertThat(next.getEvidencePhotoIds()).containsExactly(20L);
    assertThat(next.getChangedFields()).containsExactly(Fields.PHOTOS);
    assertThat(next.hasEvidencePhotos()).isTrue();
    assertThat(next.calculateConfidence(NOW)).isEqualTo(5);
    assertThat(original.getPhotoIds()).containsExactly(10L);
  }

  private CarWashHistory registration(Long memberId, List<Long> photos) {
    return CarWashHistory.register(CarWash.create("세차생활", new Coordinates(37.5, 127.0), NOW), memberId, VisitExperience.USED, NOW, NOW,
                                   CarWashInformation.builder().name("세차생활").coordinates(new Coordinates(37.5, 127.0)).build(), photos);
  }
}
