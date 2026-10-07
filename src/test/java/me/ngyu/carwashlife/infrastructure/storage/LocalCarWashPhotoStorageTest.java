package me.ngyu.carwashlife.infrastructure.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.file.FileSystemException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LocalCarWashPhotoStorageTest {

  @TempDir
  Path root;

  @DisplayName("설정한 저장 루트에서 생성한 상대 키로 파일을 저장하고 읽고 삭제한다.")
  @Test
  void generatedRelativeKeysWorkUnderAConfiguredRootAndCanBeDeleted() throws Exception {
    LocalCarWashPhotoStorage storage = new LocalCarWashPhotoStorage(root.resolve("nested/photos").toString());
    byte[] content = new byte[]{1, 2, 3};

    String key = storage.write(content);

    assertThat(UUID.fromString(key).toString()).isEqualTo(key);
    assertThat(Files.readAllBytes(root.resolve("nested/photos").resolve(key))).isEqualTo(content);
    assertThat(storage.read(key)).isEqualTo(content);
    storage.delete(key);
    assertThat(root.resolve("nested/photos").resolve(key)).doesNotExist();
  }

  @DisplayName("UUID 형식이 아닌 경로나 키로 저장 루트 밖의 파일을 읽으려 하면 거절한다.")
  @Test
  void nonUuidKeysCannotEscapeTheRoot() {
    LocalCarWashPhotoStorage storage = new LocalCarWashPhotoStorage(root.toString());

    assertThatThrownBy(() -> storage.read("../outside.png")).isInstanceOf(IOException.class);
    assertThatThrownBy(() -> storage.read("..\\outside.png")).isInstanceOf(IOException.class);
    assertThatThrownBy(() -> storage.read("/etc/passwd")).isInstanceOf(IOException.class);
  }

  @DisplayName("사진 파일이 심볼릭 링크이면 읽기를 거절하고 링크와 원본 파일을 보존한다.")
  @Test
  void aSymbolicLinkAtTheStoredLeafCannotBeRead() throws Exception {
    Path target = Files.write(root.resolve("original"), new byte[]{1, 2, 3});
    String key = UUID.randomUUID().toString();
    Path link = supportedSymbolicLink(root.resolve(key), target);
    LocalCarWashPhotoStorage storage = new LocalCarWashPhotoStorage(root.toString());

    assertThatThrownBy(() -> storage.read(key)).isInstanceOf(IOException.class);
    assertThat(Files.isSymbolicLink(link)).isTrue();
    assertThat(Files.readAllBytes(target)).isEqualTo(new byte[]{1, 2, 3});
  }

  @DisplayName("관리자가 저장 루트를 심볼릭 링크로 설정하면 실제 디렉터리에 사진을 저장한다.")
  @Test
  void anAdministratorConfiguredRootSymbolicLinkIsResolved() throws Exception {
    Path actual = Files.createDirectory(root.resolve("actual"));
    Path alias = supportedSymbolicLink(root.resolve("alias"), actual);
    LocalCarWashPhotoStorage storage = new LocalCarWashPhotoStorage(alias.toString());

    String key = storage.write(new byte[]{1});

    assertThat(actual.resolve(key)).exists();
    assertThat(storage.read(key)).isEqualTo(new byte[]{1});
  }

  private Path supportedSymbolicLink(Path link, Path target) throws IOException {
    try {
      return Files.createSymbolicLink(link, target);
    } catch (UnsupportedOperationException | FileSystemException exception) {
      Assumptions.assumeTrue(false, "현재 환경에서 심볼릭 링크 생성 권한/지원을 사용할 수 없습니다.");
      throw exception;
    }
  }
}
