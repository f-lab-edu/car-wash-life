package me.ngyu.carwashlife.infrastructure.storage;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Set;
import java.util.UUID;
import me.ngyu.carwashlife.application.carwash.service.CarWashPhotoStorage;

public class LocalCarWashPhotoStorage implements CarWashPhotoStorage {

  private static final int MAX_BYTES = 5 * 1024 * 1024;
  private final Path root;

  public LocalCarWashPhotoStorage(String root) {
    this.root = Path.of(root).toAbsolutePath().normalize();
  }

  @Override
  public String write(byte[] content) throws IOException {
    String key = UUID.randomUUID().toString();
    Path path = resolve(key);
    boolean created = false;
    try (SeekableByteChannel channel = Files.newByteChannel(path, Set.of(StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE, LinkOption.NOFOLLOW_LINKS))) {
      created = true;
      ByteBuffer buffer = ByteBuffer.wrap(content);
      while (buffer.hasRemaining()) {
        channel.write(buffer);
      }
    } catch (IOException exception) {
      if (created) {
        try {
          Files.deleteIfExists(path);
        } catch (IOException cleanupFailure) {
          exception.addSuppressed(cleanupFailure);
        }
      }
      throw exception;
    }
    return key;
  }

  @Override
  public byte[] read(String key) throws IOException {
    try (InputStream input = Files.newInputStream(resolve(key), StandardOpenOption.READ, LinkOption.NOFOLLOW_LINKS)) {
      byte[] bytes = input.readNBytes(MAX_BYTES + 1);
      if (bytes.length == 0 || bytes.length > MAX_BYTES) {
        throw new IOException("저장된 사진 크기가 올바르지 않습니다.");
      }
      return bytes;
    }
  }

  @Override
  public void delete(String key) throws IOException {
    Files.deleteIfExists(resolve(key));
  }

  private Path resolve(String key) throws IOException {
    try {
      if (!UUID.fromString(key).toString().equals(key)) {
        throw new IllegalArgumentException();
      }
    } catch (IllegalArgumentException exception) {
      throw new IOException("사진 저장 키가 올바르지 않습니다.", exception);
    }
    Files.createDirectories(root);
    return root.toRealPath().resolve(key);
  }
}
