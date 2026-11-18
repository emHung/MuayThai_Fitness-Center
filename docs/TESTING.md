# Kiểm thử, chất lượng và bằng chứng

## Các bộ kiểm tra hiện có

| Tệp / cấu hình | Kiểm gì / điều kiện |
| --- | --- |
| [verify.yaml](../.github/workflows/verify.yaml) | CI push main/PR: Android assemble/lint; Docker API + ảnh với QA riêng; production smoke. |
| [acceptance.mjs](../tests/acceptance.mjs) | Auth, quyền, gói, PT/lớp, tranh chấp/điểm danh, mục tiêu, SSE; tạo dữ liệu thử, chỉ chạy QA. |
| [handoff.mjs](../tests/handoff.mjs) | Bổ sung nghiệm thu quyền, sức chứa, mốc hủy/đổi lịch, thủ công/vắng/đánh giá và tranh chấp. Chỉ nhận gateway 8180 và project QA khang-handoff hoặc khang-acceptance. Có SQL fixture thời gian, tuyệt đối không dùng dữ liệu thật. |
| [images.mjs](../tests/images.mjs) | Quyền API ảnh, upload/sửa/xóa và tính tương thích; không có nghĩa ảnh lớp/gói còn hiện trên UI. |
| [production-smoke.ps1](../tests/production-smoke.ps1) | Stack riêng mặc định cổng 8186, bootstrap/migration và quyền PostgreSQL, chính sách lưu trữ. Tự quản lý môi trường thử. |
| [capture-android.ps1](../tests/capture-android.ps1) | Đăng nhập/chụp bốn vai trò, chọn/xem trước/lưu avatar; yêu cầu emulator và APK cổng QA 8180. |
| [ui-fixtures.mjs](../tests/ui-fixtures.mjs) | Tạo tài khoản/lịch/gói QA; output chứa mật khẩu/token chỉ lưu trong android/app/build/. |
| [ui-redesign.ps1](../tests/ui-redesign.ps1) | Chạy bộ chụp vai trò rồi thử lịch tuần, form, QR, lưu hồ sơ, màn hẹp/cỡ chữ lớn. |
| [connectivity.ps1](../tests/connectivity.ps1) | Mất mạng/nối lại ở login/workspace, cài đặt và Recents; thay trạng thái mạng emulator, khôi phục cuối lượt. |
| [app-update.ps1](../tests/app-update.ps1) | Hồi quy nhắc cập nhật của bản probe; cần phiên bản thấp hơn release để hiện nhắc. |
| [app-update-install.ps1](../tests/app-update-install.ps1) | Tải release thật, xin quyền, hủy rồi cài đè trên emulator. Cần probe cùng chữ ký, version/code thấp hơn release và ExpectedVersion. |
| [coverage.ps1](../tests/coverage.ps1) | Dựng stack coverage riêng, chạy kiểm thử và xuất JaCoCo XML; cần JAVA_HOME có java/jar và Docker. |

Cài môi trường và lệnh API QA đầy đủ ở [SETUP_NEW_MACHINE.md](SETUP_NEW_MACHINE.md). Các bộ cài APK có thể xóa dữ liệu/cài đè và đổi mạng emulator, không dùng điện thoại chứa dữ liệu cá nhân để chạy tự động.

## Android

```powershell
Set-Location android
.\gradlew.bat :app:assembleDebug :app:lintDebug
Set-Location ..
```

Xem `android/app/build/reports/lint-results-debug.html`. Bản 0.1.8: 0 lỗi, 11 cảnh báo hiện hữu; không coi lint sạch lỗi là đã nghiệm thu UX hay bảo mật toàn hệ thống. Các kiểm tra bổ sung về tài khoản, lớp 10, bàn phím và cài đè nằm trong [báo cáo 0.1.8](UI_UPDATE_0.1.8.md).

Để chụp UI, dựng stack QA 8180 riêng theo hướng dẫn, build APK `'-PauthBaseUrl=http://10.0.2.2:8180/'`. Đặt `MUAYTHAI_TEST_COMPOSE_PROJECT` đúng project QA vừa dựng (ví dụ `muaythai-qa`), `MUAYTHAI_TEST_URL=http://127.0.0.1:8180` và `MUAYTHAI_UI_FIXTURE_OUT=android/app/build/ui-qa/accounts.json`, tạo thư mục output rồi chạy `node tests/ui-fixtures.mjs`; chép `tests/fixtures/course.jpg` cạnh accounts.json. Truyền FixturePath, ApkPath và OutputDirectory vào script. Fixture có credential, không in/chia sẻ/commit.

## SonarQube và coverage

Không bắt buộc cho lần chạy demo đầu tiên. [sonar.compose.yaml](../deploy/sonar.compose.yaml) dùng cổng loopback 9000, volume riêng và profile scan. Cấu hình tắt bootstrap check Elasticsearch chỉ dành cho đánh giá cục bộ, không coi là cấu hình production.

```powershell
# JAVA_HOME phải trỏ JDK có java và jar.
.\tests\coverage.ps1 -Port 8180
docker compose -f deploy/sonar.compose.yaml up -d sonarqube
# Mở http://127.0.0.1:9000, tạo token cục bộ và đặt SONAR_TOKEN trong môi trường terminal.
# Không ghi giá trị token vào lệnh lưu trong Git hoặc tài liệu.
docker compose -f deploy/sonar.compose.yaml --profile scan run --rm scanner
```

Scanner nhập `backend/target/coverage/acceptance.xml`. Đợi SonarQube sẵn sàng trước khi scan. Dashboard/Quality Gate từng đạt là bằng chứng lần đo đó; workflow CI hiện không chạy scan mỗi commit. Không công bố tỷ lệ coverage hiện tại nếu chưa đo lại.

## Ưu tiên nghiệm thu còn mở

1. Điện thoại vật lý: người sử dụng đã xác nhận quét/xác nhận QR thành công; còn kiểm lượt gói, thiết bị khác và cài ứng viên release ký riêng.
2. Nhiều thiết bị: lịch/SSE, quyền mỗi vai trò và kết nối gián đoạn.
3. Biên nghiệp vụ: cửa sổ check-in/hủy lịch, gói hết hạn, sức chứa và tranh chấp theo bảng FR.
4. Cập nhật: mất mạng khi tải, thiếu bộ nhớ, hủy quyền/cài, đóng tiến trình; chưa coi tất cả đã thử từ lượt tải thành công.
5. Doanh nghiệp: ký APK production, sao lưu/khôi phục, uptime và kiểm soát truy cập.

Xem [NGHIEM_THU_V2_3.md](NGHIEM_THU_V2_3.md). Không tăng mốc bàn giao chỉ vì có thêm ảnh hoặc tài liệu.

Các ca điểm danh dùng helper tests/support/qa-time.mjs: tạo lịch hợp lệ qua API rồi chuẩn bị cửa sổ thời gian trong database QA để chạy ổn định cả sát nửa đêm. Helper bắt buộc localhost, cổng khác 8080 và project thử nằm trong danh sách cho phép; baseline và UI fixture kiểm điều kiện trước khi tạo dữ liệu. Không đổi đồng hồ hoặc sửa dữ liệu môi trường đang sử dụng.
