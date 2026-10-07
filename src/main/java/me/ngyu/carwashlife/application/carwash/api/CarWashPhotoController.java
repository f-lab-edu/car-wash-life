package me.ngyu.carwashlife.application.carwash.api;

import lombok.RequiredArgsConstructor;
import me.ngyu.carwashlife.application.carwash.api.dto.CarWashPhotoDto;
import me.ngyu.carwashlife.application.carwash.service.CarWashPhotoService;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/car-wash-photos")
@RequiredArgsConstructor
public class CarWashPhotoController {

  private final CarWashPhotoService photoService;

  @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  @ResponseStatus(HttpStatus.CREATED)
  public CarWashPhotoDto.Response upload(@AuthenticationPrincipal Long memberId, @RequestPart("file") MultipartFile file) {
    return photoService.upload(memberId, file);
  }

  @GetMapping("/{photoId}")
  public ResponseEntity<byte[]> read(@AuthenticationPrincipal Long memberId, @PathVariable Long photoId) {
    CarWashPhotoService.PhotoContent photo = photoService.read(memberId, photoId);
    return ResponseEntity.ok().contentType(MediaType.parseMediaType(photo.contentType()))
                         .header("X-Content-Type-Options", "nosniff").body(photo.content());
  }
}
