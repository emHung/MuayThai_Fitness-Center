package vn.muaythai.progress;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = {"vn.muaythai.progress", "vn.muaythai.shared"})
public class Main {
  public static void main(String[] args) {
    SpringApplication.run(Main.class, args);
  }
}
