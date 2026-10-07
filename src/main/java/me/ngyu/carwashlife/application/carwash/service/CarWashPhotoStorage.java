package me.ngyu.carwashlife.application.carwash.service;

import java.io.IOException;

public interface CarWashPhotoStorage {

  String write(byte[] content) throws IOException;

  byte[] read(String key) throws IOException;

  void delete(String key) throws IOException;
}
