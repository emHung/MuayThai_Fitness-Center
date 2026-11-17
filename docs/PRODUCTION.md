# Chuẩn bị vận hành tại một cơ sở

Cấu hình production dùng volume PostgreSQL mới. Giữ stack demo và database hiện tại cho phát triển. Thử khôi phục hoặc nhập dữ liệu đã đối soát vào staging trước buổi chuyển đổi thật.

## Chuẩn bị máy chủ

1. Tạo `deploy/.env.production` theo [mẫu](../deploy/production.env.example). Lấy từng bí mật riêng từ nơi quản lý khóa của doanh nghiệp. Dùng tên `PRODUCTION_DB_VOLUME` riêng cho từng môi trường. Đặt `ATTENDANCE_RETENTION` theo chính sách đã duyệt, dùng kỳ ISO 8601 như `P1Y` nếu doanh nghiệp chọn một năm.
2. Để tạo người quản lý đầu tiên trên database mới, bật `BOOTSTRAP_ADMIN_ENABLED` và điền email cùng mật khẩu tạm mạnh. Sau lần đổi mật khẩu đầu tiên, tắt bootstrap, xóa hai giá trị khởi tạo và áp dụng lại cấu hình. Tác vụ khởi tạo giữ nguyên tài khoản quản trị đã có.
3. Chạy gateway ở loopback và đặt [Caddyfile](../deploy/Caddyfile) trên cùng máy chủ. Đặt biến `MUAYTHAI_DOMAIN` qua cấu hình tiến trình Caddy, cấu hình DNS và cho reverse proxy phục vụ HTTPS. Đồng bộ cổng gateway nếu thay giá trị mặc định 8080.

```powershell
docker compose --env-file deploy/.env.production -p muaythai-prod -f deploy/compose.yaml -f deploy/production.compose.yaml up -d --build --wait
Invoke-RestMethod http://127.0.0.1:8080/actuator/health
```

Mỗi service có một vai trò PostgreSQL chạy nghiệp vụ và một vai trò Flyway quản lý schema. Vai trò nghiệp vụ kết nối vào đúng database của mình, có quyền đọc ghi các bảng cần thiết và quyền dùng sequence. Auth chỉ có quyền thêm và đọc các bản ghi lý do cấp lại mật khẩu và khóa tài khoản. Vai trò bootstrap PostgreSQL chỉ dùng cho chuẩn bị database và sao lưu. Triển khai này chạy Flyway khi khởi động, nên container ứng dụng vẫn nhận thông tin xác thực của vai trò migration tương ứng. Các service đang dùng chung khóa xác thực nội bộ.

## Tạo và bàn giao APK

Thiết lập `ANDROID_RELEASE_KEYSTORE`, `ANDROID_RELEASE_STORE_PASSWORD`, `ANDROID_RELEASE_KEY_ALIAS` và `ANDROID_RELEASE_KEY_PASSWORD` trong môi trường build được kiểm soát. Doanh nghiệp giữ keystore và đường khôi phục khóa.

```powershell
Set-Location android
.\gradlew.bat :app:assembleRelease '-PauthBaseUrl=https://ten-mien-doanh-nghiep/'
```

Quản lý có thể vào **Quản lý → Lễ tân** để tạo tài khoản, khóa hoặc cấp lại mật khẩu. Lễ tân đổi mật khẩu tạm lần đầu, được tra học viên, xem lịch và ghi điểm danh tại quầy. Lịch sử của lễ tân giới hạn ở lượt do chính tài khoản đó ghi.

## Kiểm tra trước khi chạy thử

- Chạy `.\tests\production-smoke.ps1` để xác minh PostgreSQL, Flyway, quyền runtime và tài khoản quản lý trong stack dùng dữ liệu giả. [Workflow CI](../.github/workflows/verify.yaml) cũng chạy kiểm thử API và Android lint. Báo cáo JaCoCo cho [SonarQube cục bộ](../README.md#phân-tích-backend-bằng-sonarqube) gộp ca kiểm thử demo và ca khởi tạo production.
- Dùng `deploy/backup.ps1 -Project muaythai-prod -ProductionEnvFile deploy/.env.production -Destination <tệp>` để tạo bản sao SQL. Dừng luồng ghi trong khung bảo trì nếu cần ảnh chụp nhất quán giữa các database. Lưu bản sao trên vùng lưu trữ mã hóa và diễn tập khôi phục thử trước khi nhập hồ sơ thật.
- Hoàn tất chính sách gói, MFA cho tài khoản đặc quyền, giới hạn đăng nhập, QR ngắn hạn và quy trình khi quầy mất mạng. Chốt người nhận cảnh báo, đầu mối xử lý yêu cầu dữ liệu cá nhân và thời gian khôi phục. Kiểm thử camera, đồng bộ nhiều máy và đối soát số lượt cuối ca với người vận hành thật.
- Nếu cơ sở vận hành ở Việt Nam, đối chiếu chính sách thu thập và lưu trữ dữ liệu với [Luật Bảo vệ dữ liệu cá nhân](https://vanban.chinhphu.vn/?docid=214590&pageid=27160) và [Nghị định 356/2025/NĐ-CP](https://vanban.chinhphu.vn/?docid=216387&pageid=27160) trước khi nhập hồ sơ hội viên.
