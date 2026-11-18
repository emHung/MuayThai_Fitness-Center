# Tiến độ bàn giao MuayThai _Fitness Center

**████████░░░░ 4/6 mốc hoàn thành**

Thanh tiến độ đếm các mốc bàn giao đã kiểm chứng. Phạm vi và kết quả theo từng yêu cầu nằm trong [bảng FR01–FR59](docs/NGHIEM_THU_V2_3.md).

| Mốc | Trạng thái | Bằng chứng |
| --- | --- | --- |
| Kiến trúc và mẫu giao diện | Hoàn thành | [Kiến trúc](docs/ARCHITECTURE.md) và [chuẩn giao diện](docs/UI.md). |
| Backend Docker và PostgreSQL | Hoàn thành | Các dịch vụ và gateway đã khởi động thành công. |
| APK Android Java | Hoàn thành | Build APK debug thành công và xác minh chữ ký debug. |
| Kiểm thử tích hợp các luồng chính | Hoàn thành | [Bộ kiểm thử API](tests/acceptance.mjs) kiểm ba vai trò, hủy đồng thời với điểm danh, đổi HLV và đồng bộ lịch. |
| Nghiệm thu v2.3 | Đang làm | [Bảng 59 yêu cầu](docs/NGHIEM_THU_V2_3.md) ghi các chức năng và ca kiểm thử còn mở. |
| Nghiệm thu điện thoại và đóng gói bản nộp | Đang làm | Cần thử camera QR, đồng bộ nhiều máy và kiểm tra bản dùng để nộp. |

## Nhật ký cập nhật

| Ngày | Cập nhật |
| --- | --- |
| 2026-11-17 | Ghi nhận bản demo đầu tiên trên GitHub và đặt lịch cập nhật lúc 18:00. Backend đã kiểm qua các luồng chính, APK debug đã build và bảng nghiệm thu có đủ 59 mục cần đối chiếu. |
| 2026-11-17 | Kiểm thử trên emulator với ba vai trò, từ HLV duyệt PT đến quản trị viên điểm danh bằng mã và trừ lượt gói. Android lint có 0 lỗi. SonarQube đã quét backend và đạt Quality Gate sau khi nhập báo cáo JaCoCo của bộ kiểm thử Docker. |
| 2026-11-17 | Chuẩn bị pilot doanh nghiệp. Đã tách quyền lễ tân, kiểm thử cấp lại mật khẩu và thu hồi phiên, dựng stack production thử nghiệm với quyền PostgreSQL riêng, xác minh APK ký bằng khóa QA và khôi phục thử bản sao demo. SonarQube đạt Quality Gate với 81,0% độ phủ mã mới, có đo ca lưu trữ hồ sơ trên stack production thử nghiệm. [Hướng dẫn production](docs/PRODUCTION.md). |
| 2026-11-18 | Chạy lại bộ API trên database QA riêng, bật cả ca sinh lịch định kỳ và phục hồi Check-in. Các ca nghiệp vụ chạy đạt. Bộ production xác nhận bootstrap, migration, quyền PostgreSQL và chính sách lưu trữ. Android build thành công, lint ghi nhận 0 lỗi và 11 cảnh báo. Emulator xác nhận đăng nhập ADMIN, LEARNER và luồng TRAINER đổi mật khẩu tạm rồi vào đúng workspace. [Báo cáo kiểm thử](docs/QA_2026-11-18.md). |
| 2026-11-18 | Chẩn đoán lỗi đăng nhập APK GitHub. Backend cục bộ hoạt động nhưng Tailscale Windows ở `NoState`, DNS HTTPS của APK không phân giải được. Mở lại Tailscale, bật Unattended và kiểm tra đăng nhập cả ba tài khoản demo qua HTTPS thành công. Cần xác nhận lại đường truy cập từ điện thoại. [Hướng dẫn Tailscale](docs/PRODUCTION.md). |
| 2026-11-18 | Bật Tailscale Funnel chạy nền trên cổng HTTPS 443 tới gateway 8080, giữ URL của APK v0.1.1. Hai relay Internet công khai trả health `UP`, API chặn yêu cầu thiếu xác thực và cả ba tài khoản demo đăng nhập, kiểm tra phiên, đăng xuất thành công. [Hướng dẫn Funnel](docs/PRODUCTION.md). |

Khi sửa hoặc xác nhận một chức năng, thêm mục theo ngày và gắn ca kiểm thử hoặc tệp liên quan. Cập nhật thanh tiến độ khi một mốc có đủ bằng chứng.

## Tính năng ảnh — 18/11/2026

Đã triển khai avatar bốn vai trò, ảnh bốn loại gói và ảnh từng lớp. Bộ API ảnh, hồi quy, production smoke và sao lưu/khôi phục ảnh đều đạt. Android 0.1.2 build đạt với lint 0 lỗi, 11 cảnh báo. Có [30 ảnh giao diện theo vai trò](docs/UI_GALLERY.md) và [báo cáo kiểm thử](docs/QA_IMAGES_2026-11-18.md). Giữ nguyên các mốc nghiệm thu điện thoại thật còn mở.

## Giao diện và kết nối — 0.1.4

Đã bỏ ảnh lớp/gói khỏi giao diện, giữ avatar, chuyển thao tác phụ sang icon Remix Icon và thu gọn nút chính. Thông báo mất Internet/nối lại hoạt động ở đăng nhập và workspace, không tự gửi lại yêu cầu ghi. Bộ thử emulator xác nhận giữ biểu mẫu, giữ phiên, trở về từ cài đặt và Recents; build/lint đạt với 0 lỗi, 11 cảnh báo hiện hữu. [Ảnh và báo cáo UI 0.1.4](docs/UI_UPDATE_0.1.4.md). Các mốc nghiệm thu điện thoại thật vẫn còn mở.

## Bố cục theo tham chiếu — 0.1.5

Áp dụng cấu trúc header/hero/thẻ/menu/biểu mẫu cho bốn vai trò, giữ màu hiện tại và chức năng. Lịch có dải tuần lọc dữ liệu thật. Có bộ ảnh theo vai trò, kiểm tra đặt PT, QR, lưu hồ sơ, avatar và màn 320dp/cỡ chữ 130%. Android build/lint đạt 0 lỗi, 11 cảnh báo hiện hữu. [Báo cáo và ảnh](docs/UI_REDESIGN_0.1.5.md). Các mốc nghiệm thu điện thoại thật chưa thay đổi.

## Cập nhật trong app — 0.1.6

Đã thêm màn tiến độ DownloadManager, thử lại/hủy, kiểm tra package/phiên bản/chữ ký/SHA-256 (nếu release cung cấp), xin quyền và chuyển trình cài Android. Emulator tải và cài đè release thật sau khi Play Protect báo an toàn. [Báo cáo](docs/IN_APP_UPDATE.md).
