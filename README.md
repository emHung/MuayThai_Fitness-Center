# MuayThai & Fitness Center

Ứng dụng Android Java/XML cho **ADMIN, RECEPTION, TRAINER, LEARNER**, kết nối gateway và bốn dịch vụ Spring Boot qua Docker/PostgreSQL.

[![GitHub Release](https://img.shields.io/github/v/release/emHung/MuayThai_Fitness-Center)](https://github.com/emHung/MuayThai_Fitness-Center/releases/latest)

## Tải và sử dụng

- **Điện thoại:** [app-debug-tailscale.apk 0.1.8](https://github.com/emHung/MuayThai_Fitness-Center/releases/download/v0.1.8/app-debug-tailscale.apk), dùng HTTPS qua máy chủ đã cấu hình. Điện thoại chỉ cần Internet khi máy chủ dùng Funnel.
- **Emulator cùng máy Docker:** [app-debug.apk 0.1.8](https://github.com/emHung/MuayThai_Fitness-Center/releases/download/v0.1.8/app-debug.apk), mặc định `http://10.0.2.2:8080/`.
- Máy chủ cần đang chạy. Hai tệp là APK **debug**, chưa phải bản ký production. Cài đè yêu cầu cùng chữ ký.
- Từ 0.1.6: **Tài khoản → Cập nhật ứng dụng**, tải trong app, có phần trăm và bước xác nhận cài Android. 0.1.7 tăng khoảng cách hai nút đổi/xóa ảnh lên 16dp. [Chi tiết cập nhật](docs/IN_APP_UPDATE.md).
- 0.1.8 sửa hủy sau điểm danh, mật khẩu/sao chép/xóa tài khoản, gán lớp 10, bàn phím và ẩn mục đã kết thúc. [Chi tiết 0.1.8](docs/UI_UPDATE_0.1.8.md). Cài đè cùng chữ ký, không cần gỡ app.

## Bắt đầu theo nhu cầu

| Cần làm | Đọc tại |
| --- | --- |
| Cài môi trường và chạy trên máy mới | [SETUP_NEW_MACHINE.md](docs/SETUP_NEW_MACHINE.md) |
| Hiểu frontend: cấu trúc và từng tệp | [android/README.md](android/README.md) |
| Hiểu backend: module và từng tệp | [backend/README.md](backend/README.md) |
| Xem tiến độ và phần chưa nghiệm thu | [PROGRESS.md](PROGRESS.md), [DEMO_STATUS.md](docs/DEMO_STATUS.md) |
| Chạy kiểm thử và SonarQube | [TESTING.md](docs/TESTING.md) |
| Chuẩn bị môi trường doanh nghiệp | [PRODUCTION.md](docs/PRODUCTION.md) |
| Tìm API, dữ liệu, UI và báo cáo cũ | [Mục lục tài liệu](docs/README.md) |
| Xem những gì được giữ/gỡ khỏi GitHub | [REPOSITORY_REVIEW.md](docs/REPOSITORY_REVIEW.md) |

## Chạy demo nhanh

Cần Git, PowerShell và Docker Desktop đang chạy Linux containers. Chạy từ gốc repo:

```powershell
.\deploy\init-demo.ps1
docker compose --env-file deploy/.env -p deploy -f deploy/compose.yaml up -d --build --wait --wait-timeout 300
Invoke-RestMethod http://127.0.0.1:8080/actuator/health
```

Mật khẩu seed được sinh trong `deploy/.env`; không đưa tệp này lên Git. Tài khoản ban đầu: `admin@example.test`, `trainer@example.test`, `learner@example.test`. Mật khẩu tạm phải đổi trước khi dùng nghiệp vụ. ADMIN tạo lễ tân và thiết lập ca làm HLV. Sửa `.env` không đổi mật khẩu tài khoản đã tồn tại.

Máy mới dùng URL Funnel khác cần build APK theo URL mới. Không dùng URL mẫu hoặc URL máy chủ cũ để thay cho cấu hình của máy mới. Hướng dẫn emulator, USB và HTTPS có trong [cài đặt](docs/SETUP_NEW_MACHINE.md).

## Chức năng hiện có

- Tài khoản, hồ sơ, avatar, khóa/mở khóa và thu hồi phiên theo quyền.
- Gói PT/lớp 10/lớp 30/tập tự do; QR riêng, hạn và lượt.
- Lịch ngày/tuần, ca làm, đặt/duyệt/đổi/hủy PT, lớp nhóm và sự kiện SSE.
- Xem trước/xác nhận QR, điểm danh thủ công, ghi vắng, lịch sử và hoàn lượt có lý do.
- Nội dung buổi, đánh giá, mục tiêu, hộp thư và danh sách chăm sóc.
- Thông báo mất mạng/nối lại; cập nhật APK trong app. Chỉ giữ avatar trên UI, không hiển thị ảnh lớp/gói.

Danh sách trên mô tả mã hiện có, không đồng nghĩa đã nghiệm thu mọi luồng. Xem [59 yêu cầu và ca còn mở](docs/NGHIEM_THU_V2_3.md).

## Trạng thái bàn giao

**5/6 mốc đã kiểm chứng**, nghiệm thu nghiệp vụ ở lớp hệ thống đã đạt; còn chốt thiết bị và bộ cài bàn giao. Không dùng tỷ lệ “50/59 = 85%” cũ vì bảng FR chưa có tiêu chí đánh dấu hoàn tất nhất quán cho từng hàng.

Bản 0.1.8 đã build và kiểm chữ ký; CI Android, API và production smoke đạt. Lint ghi nhận 0 lỗi, 11 cảnh báo hiện hữu. SonarQube có báo cáo đo trước đó, không chạy tự động trong workflow hiện tại và không đại diện cho mọi commit mới.

Người sử dụng đã xác nhận quét QR và xác nhận điểm danh thành công trên điện thoại. Đã đối chiếu lượt gói và thử cài đè 0.1.7 lên 0.1.8, đăng nhập/đăng xuất HTTPS trên emulator; còn xác nhận đồng bộ ở thiết bị sử dụng. Đã bổ sung kiểm thử các mốc biên nghiệp vụ; không coi đây là đã kiểm mọi tình huống gián đoạn trên mọi thiết bị. Xem [hướng dẫn chốt bàn giao](docs/HANDOVER.md). Các ảnh hiện tại nằm ở [bố cục 0.1.5](docs/UI_REDESIGN_0.1.5.md); ảnh này là bằng chứng phiên bản đó, không phải ảnh chụp lại 0.1.8.

## Nguyên tắc thay đổi

Không commit secret, keystore, bản sao database, build output hoặc APK vào mã nguồn. APK phát hành ở Releases. Giữ migration cũ, Gradle wrapper, fixture kiểm thử và giấy phép bên thứ ba. Tài liệu cấu trúc chỉ rõ chỗ sửa cho từng chức năng; các lệnh kiểm thử ghi dữ liệu phải dùng stack QA riêng.

[Script đẩy Git hằng ngày](scripts/push-daily.ps1) chỉ đẩy commit đã có trên `main`, không tự commit. Task Scheduler là thiết lập riêng trên máy; clone repo không tự tạo lịch. Xem trước bằng `-WhatIf` và truyền `-RepositoryUrl https://github.com/emHung/MuayThai_Fitness-Center.git`.
