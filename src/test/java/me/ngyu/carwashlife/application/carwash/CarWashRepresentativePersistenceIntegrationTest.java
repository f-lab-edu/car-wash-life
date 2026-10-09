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
import me.ngyu.carwashlife.application.carwash.api.dto.ModifyCarWashDto;
import me.ngyu.carwashlife.application.carwash.api.dto.RegisterCarWashDto;
import me.ngyu.carwashlife.application.carwash.domain.CarWash;
import me.ngyu.carwashlife.application.carwash.domain.CarWashHistory;
import me.ngyu.carwashlife.application.carwash.domain.VisitExperience;
import me.ngyu.carwashlife.application.carwash.service.CarWashModificationService;
import me.ngyu.carwashlife.application.carwash.service.CarWashRegistrationService;
import me.ngyu.carwashlife.application.member.domain.Member;
import me.ngyu.carwashlife.infrastructure.persistence.CarWashHistoryRepository;
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
  private MemberRepository memberRepository;
  @Autowired
  private CarWashRegistrationService registrationService;
  @Autowired
  private CarWashModificationService modificationService;
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
    jdbcTemplate.update("update car_wash set target_history_id = null");
    historyRepository.deleteAllInBatch();
    carWashRepository.deleteAllInBatch();
    memberRepository.deleteAllInBatch();
    memberId = memberRepository.saveAndFlush(Member.create("locking@example.com", "encoded", null, null, null, NOW)).getId();
    original = registrationService.register(memberId, registration("세차생활"));
  }

  @DisplayName("원장 잠금 조회는 요청한 세차장에 비관적 쓰기 잠금을 적용하고 해당 세차장의 이력만 조회한다.")
  @Test
  void explicitMasterQueryLoadsOnlyTheRequestedMasterWithAPessimisticWriteLock() {
    RegisterCarWashDto.Response other = registrationService.register(memberId, registration("다른 세차장"));

    new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
      CarWash locked = carWashRepository.findByIdForUpdate(original.carWashId()).orElseThrow();
      assertThat(locked.getId()).isEqualTo(original.carWashId());
      assertThat(entityManager.getLockMode(locked)).isEqualTo(LockModeType.PESSIMISTIC_WRITE);
      assertThat(historyRepository.findAllByCarWashId(locked.getId())).extracting(CarWashHistory::getId).containsExactly(original.historyId());
      assertThat(carWashRepository.findByIdForUpdate(Long.MAX_VALUE)).isEmpty();
      assertThat(historyRepository.findAllByCarWashId(other.carWashId())).extracting(CarWashHistory::getId).containsExactly(other.historyId());
    });
  }

  @DisplayName("독립된 트랜잭션에서 동시에 수정해도 두 이력을 보존하고 최신 관찰 이력의 정보를 원장에 반영한다.")
  @Test
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
        return modificationService.modify(memberId, original.carWashId(), modification("최신 관찰", 37.6, NOW.minusDays(1)));
      });
      Future<ModifyCarWashDto.Response> second = executor.submit(() -> {
        ready.countDown();
        if (!start.await(10, TimeUnit.SECONDS)) {
          throw new IllegalStateException("동시 수정 시작 대기 시간 초과");
        }
        return modificationService.modify(memberId, original.carWashId(), modification("이전 관찰", 37.7, NOW.minusDays(3)));
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

  private RegisterCarWashDto.Request registration(String name) {
    return new RegisterCarWashDto.Request(name, null, 37.5, 127.0, VisitExperience.NOT_USED, NOW.minusDays(100),
                                          null, null, null, null, null, null, null, null, null);
  }

  private ModifyCarWashDto.Request modification(String name, double latitude, OffsetDateTime observedAt) {
    return new ModifyCarWashDto.Request(original.historyId(), name, null, latitude, 127.0, VisitExperience.USED, observedAt,
                                        null, null, null, null, null, null, null, null, null);
  }
}
