package com.dpworld.fms.api;

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
      @Value("${dpwfms.map.assets-directory:${user.dir}/maps}") String assetsDirectory) {
    String location = Path.of(assetsDirectory).toAbsolutePath().normalize().toUri().toString();
    this.fileLocation = location.endsWith("/") ? location : location + "/";
  }

  @Override
  public void addResourceHandlers(ResourceHandlerRegistry registry) {
    registry.addResourceHandler("/api/maps/**")
        .addResourceLocations(fileLocation, "classpath:/static/maps/")
        .setCacheControl(CacheControl.maxAge(Duration.ofHours(6)).cachePrivate());
  }
}
