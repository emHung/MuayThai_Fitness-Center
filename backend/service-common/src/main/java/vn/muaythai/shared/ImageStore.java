package vn.muaythai.shared;

import java.awt.Color;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Map;
import java.util.UUID;
import javax.imageio.ImageIO;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

/** Stores normalized images in an owning service's database. Table names are supplied by code. */
public final class ImageStore {
  private final JdbcClient db;
  private final String table;
  private final long maxPixels;
  private final int maxEdge;

  public ImageStore(JdbcClient db, String table, long maxPixels, int maxEdge) {
    if (!table.matches("(profile|class|package_kind)_images") || maxPixels < 1 || maxEdge < 1) {
      throw new IllegalArgumentException("Invalid image store configuration");
    }
    this.db = db;
    this.table = table;
    this.maxPixels = maxPixels;
    this.maxEdge = maxEdge;
  }

  public ResponseEntity<byte[]> get(Object key) {
    return db.sql("SELECT content, version FROM " + table + " WHERE resource_key=:key")
        .param("key", key).query((rs, row) -> ResponseEntity.ok()
            .contentType(MediaType.IMAGE_JPEG).cacheControl(CacheControl.noCache().cachePrivate())
            .header("X-Content-Type-Options", "nosniff").eTag(rs.getString("version"))
            .body(rs.getBytes("content"))).optional()
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Image not found"));
  }

  public Map<String, String> put(Object key, MultipartFile file) {
    byte[] content = normalize(file);
    String version = UUID.randomUUID().toString();
    db.sql("INSERT INTO " + table + " (resource_key,content,version) VALUES (:key,:content,:version)"
        + " ON CONFLICT (resource_key) DO UPDATE SET content=excluded.content,version=excluded.version")
        .param("key", key).param("content", content).param("version", version).update();
    return Map.of("version", version);
  }

  public void delete(Object key) {
    db.sql("DELETE FROM " + table + " WHERE resource_key=:key").param("key", key).update();
  }

  private byte[] normalize(MultipartFile file) {
    try (var original = file.getInputStream(); var input = ImageIO.createImageInputStream(original)) {
      if (file.isEmpty() || input == null) {
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Empty image");
      }
      var readers = ImageIO.getImageReaders(input);
      if (!readers.hasNext()) {
        throw new ResponseStatusException(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "Use a JPEG or PNG image");
      }
      var reader = readers.next();
      try {
        String format = reader.getFormatName();
        if (!format.equalsIgnoreCase("JPEG") && !format.equalsIgnoreCase("PNG")) {
          throw new ResponseStatusException(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "Use a JPEG or PNG image");
        }
        reader.setInput(input, true, true);
        int width = reader.getWidth(0);
        int height = reader.getHeight(0);
        if (width < 1 || height < 1 || (long) width * height > maxPixels) {
          throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE, "Image dimensions exceed the limit");
        }
        // Subsampling bounds memory before decoding a large source image.
        int sample = Math.max(1, Math.max(width, height) / maxEdge);
        var parameters = reader.getDefaultReadParam();
        parameters.setSourceSubsampling(sample, sample, 0, 0);
        BufferedImage source = reader.read(0, parameters);
        double scale = Math.min(1.0, (double) maxEdge / Math.max(source.getWidth(), source.getHeight()));
        BufferedImage target = new BufferedImage(Math.max(1, (int) (source.getWidth() * scale)),
            Math.max(1, (int) (source.getHeight() * scale)), BufferedImage.TYPE_INT_RGB);
        var graphics = target.createGraphics();
        try {
          graphics.setColor(Color.WHITE);
          graphics.fillRect(0, 0, target.getWidth(), target.getHeight());
          graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
          graphics.drawImage(source, 0, 0, target.getWidth(), target.getHeight(), null);
        } finally {
          graphics.dispose();
          source.flush();
        }
        try (var bytes = new ByteArrayOutputStream()) {
          if (!ImageIO.write(target, "jpeg", bytes)) {
            throw new IOException("JPEG encoder unavailable");
          }
          return bytes.toByteArray();
        } finally {
          target.flush();
        }
      } finally {
        reader.dispose();
      }
    } catch (IOException | IllegalArgumentException error) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Image cannot be decoded");
    }
  }
}
