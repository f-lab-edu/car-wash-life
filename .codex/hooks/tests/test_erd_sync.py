import importlib.util
import json
from pathlib import Path
import tempfile
import unittest


SCRIPT = Path(__file__).resolve().parents[1] / "erd_sync.py"
SPEC = importlib.util.spec_from_file_location("erd_sync", SCRIPT)
ERD = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(ERD)
EVENT = {"hook_event_name": "Stop", "stop_hook_active": False}
MODEL = '''package sample;
import jakarta.persistence.*;
@Entity
@Table(name = "shops")
public class Shop {
  @Id
  private Long id;
  @ManyToOne(optional = false)
  @JoinColumn(name = "owner_id", nullable = false)
  private Member owner;
  public String label() { return "old"; }
}
'''


class ErdSyncTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        self.models = self.root / "src/main/java/sample"
        self.models.mkdir(parents=True)
        (self.root / "docs/erd").mkdir(parents=True)
        self.shop = self.models / "Shop.java"
        self.shop.write_text(MODEL, encoding="utf-8")
        ERD.record_state(self.root)

    def test_unchanged_model_does_not_resume(self):
        """영속 구조가 같으면 작업을 다시 시작하지 않는다."""
        self.assertEqual({}, ERD.stop_result(EVENT, self.root))

    def test_mapping_change_resumes_with_update_instructions(self):
        """관계의 필수 조건이 바뀌면 ERD 확인을 요청한다."""
        self.shop.write_text(MODEL.replace("optional = false", "optional = true"), encoding="utf-8")
        result = ERD.stop_result(EVENT, self.root)
        self.assertEqual("block", result["decision"])
        self.assertIn("Shop.java", result["reason"])
        self.assertIn("--record", result["reason"])

    def test_field_addition_is_detected_after_an_unrelated_doc_edit(self):
        """문서를 임의로 수정해도 새 영속 필드의 확인 요청은 유지된다."""
        self.shop.write_text(MODEL.replace("private Long id;", "private Long id; private String name;"), encoding="utf-8")
        (self.root / "docs/erd/detailed.md").write_text("changed", encoding="utf-8")
        self.assertEqual("block", ERD.stop_result(EVENT, self.root)["decision"])

    def test_method_comments_format_and_lombok_do_not_trigger(self):
        """메서드·주석·포맷·Lombok 변경은 ERD 갱신을 요청하지 않는다."""
        changed = MODEL.replace('return "old";', 'if (true) { return "@Entity { private Long fake; }"; } return "new";')
        changed = changed.replace("private Long id;", "// comment\n @Getter\n private  Long\n id ;")
        changed = changed.replace("@Entity\n", "@Getter\n@Entity\n")
        self.shop.write_text(changed, encoding="utf-8")
        self.assertEqual({}, ERD.stop_result(EVENT, self.root))

    def test_new_and_deleted_models_are_detected(self):
        """엔티티 추가와 삭제를 모두 감지한다."""
        other = self.models / "Member.java"
        other.write_text("@Entity public class Member { @Id private Long id; }", encoding="utf-8")
        self.assertEqual("block", ERD.stop_result(EVENT, self.root)["decision"])
        ERD.record_state(self.root)
        self.shop.unlink()
        self.assertEqual("block", ERD.stop_result(EVENT, self.root)["decision"])

    def test_embedded_mapping_and_nonpersistent_fields(self):
        """내장 객체 매핑은 감지하고 비영속 필드는 제외한다."""
        embedded = self.models / "Location.java"
        embedded.write_text('@Embeddable public class Location { @Column(nullable=false) private double latitude; }', encoding="utf-8")
        ERD.record_state(self.root)
        embedded.write_text(embedded.read_text().replace("nullable=false", "nullable=true"), encoding="utf-8")
        self.assertEqual("block", ERD.stop_result(EVENT, self.root)["decision"])
        ERD.record_state(self.root)
        self.shop.write_text(MODEL.replace("private Long id;", "private Long id; @Transient private String label; private static String cached;"), encoding="utf-8")
        self.assertEqual({}, ERD.stop_result(EVENT, self.root))

    def test_nested_attribute_override_changes_are_detected(self):
        """중첩 매핑 어노테이션의 컬럼 제약 변경을 감지한다."""
        mapping = '@Embedded @AttributeOverrides({@AttributeOverride(name="latitude", column=@Column(nullable=false))}) private Location location;'
        self.shop.write_text(MODEL.replace("private Long id;", "private Long id; " + mapping), encoding="utf-8")
        ERD.record_state(self.root)
        self.shop.write_text(self.shop.read_text().replace('column=@Column(nullable=false)', 'column=@Column(nullable=true)'), encoding="utf-8")
        self.assertEqual("block", ERD.stop_result(EVENT, self.root)["decision"])

    def test_resume_guard_prevents_an_endless_stop_loop(self):
        """이미 재개한 작업에는 동일한 종료 차단을 반복하지 않는다."""
        self.shop.write_text(MODEL.replace("Long id", "String id"), encoding="utf-8")
        self.assertEqual({}, ERD.stop_result({"hook_event_name": "Stop", "stop_hook_active": True}, self.root))

    def test_record_accepts_the_reviewed_model(self):
        """확인 기준을 갱신하면 동일한 모델을 다시 요청하지 않는다."""
        self.shop.write_text(MODEL.replace("Long id", "String id"), encoding="utf-8")
        ERD.record_state(self.root)
        self.assertEqual({}, ERD.stop_result(EVENT, self.root))
        saved = json.loads((self.root / ERD.STATE_PATH).read_text())
        self.assertEqual(1, saved["version"])


if __name__ == "__main__":
    unittest.main()
