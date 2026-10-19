package com.dpworld.fms.api;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.CacheControl;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Serves the static MapLibre style, PMTiles archive, glyphs, and assets from the
 * Java API. Requests remain behind the application's normal authentication.
 */
@Configuration
public class MapAssetsConfiguration implements WebMvcConfigurer {
  private final String fileLocation;

  public MapAssetsConfiguration(
      @Value("${dpwfms.map.assets-directory:}") String assetsDirectory) {
    Path assetsPath = resolveAssetsDirectory(assetsDirectory);
    String location = assetsPath.toUri().toString();
    this.fileLocation = location.endsWith("/") ? location : location + "/";
  }

  private static Path resolveAssetsDirectory(String configuredDirectory) {
    if (configuredDirectory != null && !configuredDirectory.isBlank()) {
      return Path.of(configuredDirectory).toAbsolutePath().normalize();
    }

    Path workingDirectory = Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize();
    for (Path directory = workingDirectory; directory != null; directory = directory.getParent()) {
      Path mapAssets = directory.resolve("map-assets");
      if (Files.isDirectory(mapAssets)) return mapAssets;
    }
    for (Path directory = workingDirectory; directory != null; directory = directory.getParent()) {
      Path legacyMaps = directory.resolve("maps");
      if (Files.isDirectory(legacyMaps)) return legacyMaps;
    }
    return workingDirectory.resolve("map-assets").normalize();
  }

  @Override
  public void addResourceHandlers(ResourceHandlerRegistry registry) {
    registry.addResourceHandler("/api/maps/**")
        .addResourceLocations(fileLocation, "classpath:/static/maps/")
        .setCacheControl(CacheControl.maxAge(Duration.ofHours(6)).cachePrivate());
  }
}
