package me.ngyu.carwashlife.application.carwash;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import java.time.OffsetDateTime;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import me.ngyu.carwashlife.application.carwash.api.dto.CarWashDetailDto;
import me.ngyu.carwashlife.application.carwash.api.dto.ModifyCarWashDto;
import me.ngyu.carwashlife.application.carwash.api.dto.RegisterCarWashDto;
import me.ngyu.carwashlife.application.carwash.domain.CarWash;
import me.ngyu.carwashlife.application.carwash.domain.CarWashHistory;
import me.ngyu.carwashlife.application.carwash.domain.CarWashInformation;
import me.ngyu.carwashlife.application.carwash.domain.CarWashModification;
import me.ngyu.carwashlife.application.carwash.domain.CarWashRegistration;
import me.ngyu.carwashlife.application.carwash.domain.Coordinates;
import me.ngyu.carwashlife.application.carwash.domain.VisitExperience;
import me.ngyu.carwashlife.application.carwash.service.CarWashService;
import me.ngyu.carwashlife.application.member.domain.Member;
import me.ngyu.carwashlife.infrastructure.persistence.CarWashHistoryRepository;
import me.ngyu.carwashlife.infrastructure.persistence.CarWashPhotoRepository;
import me.ngyu.carwashlife.infrastructure.persistence.CarWashRepository;
import me.ngyu.carwashlife.infrastructure.persistence.MemberRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest
@Import(CarWashRegistrationApiIntegrationTest.FixedClockConfig.class)
class CarWashRepresentativePersistenceIntegrationTest {

  private static final OffsetDateTime NOW = OffsetDateTime.parse("2026-10-07T12:00:00+09:00");

  @Autowired
  private CarWashRepository carWashRepository;
  @Autowired
  private CarWashHistoryRepository historyRepository;
  @Autowired
  private CarWashPhotoRepository photoRepository;
  @Autowired
  private MemberRepository memberRepository;
  @Autowired
  private CarWashService carWashService;
  @Autowired
  private PlatformTransactionManager transactionManager;
  @Autowired
  private EntityManager entityManager;
  @Autowired
  private JdbcTemplate jdbcTemplate;

  private Long memberId;
  private RegisterCarWashDto.Response original;

  @BeforeEach
  void setUp() {
    photoRepository.deleteAllInBatch();
    jdbcTemplate.update("update car_wash set target_history_id = null");
    historyRepository.deleteAllInBatch();
    carWashRepository.deleteAllInBatch();
    memberRepository.deleteAllInBatch();
    memberId = memberRepository.saveAndFlush(Member.create("locking@example.com", "encoded", null, null, null, NOW)).getId();
    original = register("세차생활");
  }

  @Test
  @DisplayName("원장 잠금 조회는 요청한 세차장에 비관적 쓰기 잠금을 적용하고 해당 세차장의 이력만 조회한다.")
  void explicitMasterQueryLoadsOnlyTheRequestedMasterWithAPessimisticWriteLock() {
    RegisterCarWashDto.Response other = register("다른 세차장");

    new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
      CarWash locked = carWashRepository.findByIdForUpdate(original.carWashId()).orElseThrow();
      assertThat(locked.getId()).isEqualTo(original.carWashId());
      assertThat(entityManager.getLockMode(locked)).isEqualTo(LockModeType.PESSIMISTIC_WRITE);
      assertThat(historyRepository.findAllByCarWashId(locked.getId())).extracting(CarWashHistory::getId).containsExactly(original.historyId());
      assertThat(carWashRepository.findByIdForUpdate(Long.MAX_VALUE)).isEmpty();
      assertThat(historyRepository.findAllByCarWashId(other.carWashId())).extracting(CarWashHistory::getId).containsExactly(other.historyId());
    });
  }

  @Test
  @DisplayName("독립된 트랜잭션에서 동시에 수정해도 두 이력을 보존하고 최신 관찰 이력의 정보를 원장에 반영한다.")
  void concurrentIndependentModificationsPreserveBothHistoriesAndSelectOneConsistentWinner() throws Exception {
    CountDownLatch ready = new CountDownLatch(2);
    CountDownLatch start = new CountDownLatch(1);
    ExecutorService executor = Executors.newFixedThreadPool(2);
    try {
      Future<ModifyCarWashDto.Response> first = executor.submit(() -> {
        ready.countDown();
        if (!start.await(10, TimeUnit.SECONDS)) {
          throw new IllegalStateException("동시 수정 시작 대기 시간 초과");
        }
        return modify("최신 관찰", 37.6, NOW.minusDays(1));
      });
      Future<ModifyCarWashDto.Response> second = executor.submit(() -> {
        ready.countDown();
        if (!start.await(10, TimeUnit.SECONDS)) {
          throw new IllegalStateException("동시 수정 시작 대기 시간 초과");
        }
        return modify("이전 관찰", 37.7, NOW.minusDays(3));
      });
      assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
      start.countDown();
      ModifyCarWashDto.Response winner = first.get(20, TimeUnit.SECONDS);
      ModifyCarWashDto.Response other = second.get(20, TimeUnit.SECONDS);

      assertThat(winner.confidence()).isEqualTo(4);
      assertThat(other.confidence()).isEqualTo(4);
      assertThat(historyRepository.count()).isEqualTo(3);
      assertThat(historyRepository.findById(original.historyId()).orElseThrow().getName()).isEqualTo("세차생활");
      CarWash master = carWashRepository.findById(original.carWashId()).orElseThrow();
      assertThat(master.getTargetHistory().getId()).isEqualTo(winner.historyId());
      assertThat(master.getName()).isEqualTo("최신 관찰");
      assertThat(master.getLatitude()).isEqualTo(37.6);
      assertThat(master.getLongitude()).isEqualTo(127.0);
    } finally {
      start.countDown();
      executor.shutdownNow();
      assertThat(executor.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
    }
  }

  @DisplayName("수정 중 상세 조회는 잠금을 기다리지 않고 이전 대표를 반환하며 커밋 후에는 새 대표를 반환한다.")
  @Test
  void queriesReadTheCommittedRepresentativeWithoutWaitingForAnOngoingModification() throws Exception {
    CountDownLatch modified = new CountDownLatch(1);
    CountDownLatch commit = new CountDownLatch(1);
    ExecutorService executor = Executors.newFixedThreadPool(2);
    try {
      Future<ModifyCarWashDto.Response> writer = executor.submit(() -> new TransactionTemplate(transactionManager).execute(status -> {
        ModifyCarWashDto.Response response = modify("새 대표", 37.6, NOW.minusDays(1));
        entityManager.flush();
        modified.countDown();
        try {
          if (!commit.await(10, TimeUnit.SECONDS)) {
            throw new IllegalStateException("수정 트랜잭션 커밋 대기 시간 초과");
          }
        } catch (InterruptedException exception) {
          Thread.currentThread().interrupt();
          throw new IllegalStateException("수정 트랜잭션 커밋 대기 중 인터럽트", exception);
        }
        return response;
      }));
      assertThat(modified.await(10, TimeUnit.SECONDS)).isTrue();

      Future<CarWashDetailDto.Response> reader = executor.submit(() -> carWashService.findDetail(original.carWashId()));
      CarWashDetailDto.Response beforeCommit = reader.get(5, TimeUnit.SECONDS);
      assertThat(beforeCommit.historyId()).isEqualTo(original.historyId());
      assertThat(beforeCommit.name()).isEqualTo("세차생활");
      assertThat(beforeCommit.latitude()).isEqualTo(37.5);
      assertThat(beforeCommit.confidence()).isEqualTo(1);
      assertThat(writer.isDone()).isFalse();

      commit.countDown();
      ModifyCarWashDto.Response modification = writer.get(10, TimeUnit.SECONDS);
      CarWashDetailDto.Response afterCommit = carWashService.findDetail(original.carWashId());
      assertThat(afterCommit.historyId()).isEqualTo(modification.historyId());
      assertThat(afterCommit.name()).isEqualTo("새 대표");
      assertThat(afterCommit.latitude()).isEqualTo(37.6);
      assertThat(afterCommit.confidence()).isEqualTo(4);
      assertThat(historyRepository.count()).isEqualTo(2);
    } finally {
      commit.countDown();
      executor.shutdownNow();
      assertThat(executor.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
    }
  }

  private RegisterCarWashDto.Response register(String name) {
    CarWashInformation information = CarWashInformation.builder().name(name).coordinates(new Coordinates(37.5, 127.0)).build();
    return carWashService.register(memberId, new CarWashRegistration(VisitExperience.NOT_USED, NOW.minusDays(100), information, null));
  }

  private ModifyCarWashDto.Response modify(String name, double latitude, OffsetDateTime observedAt) {
    CarWashInformation information = CarWashInformation.builder().name(name).coordinates(new Coordinates(latitude, 127.0)).build();
    return carWashService.modify(memberId, original.carWashId(), new CarWashModification(original.historyId(), VisitExperience.USED, observedAt, information, null));
  }
}
