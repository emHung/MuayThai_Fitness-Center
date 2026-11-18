# Mục lục tài liệu

Hiện trạng: APK **0.1.7/code 8**, bốn vai trò, 5/6 mốc bàn giao có bằng chứng. Tài liệu lịch sử mô tả thời điểm đo/chụp, không thay cho trạng thái hiện tại.

## Cài đặt và đọc mã

| Công việc | Tài liệu |
| --- | --- |
| Chuẩn bị môi trường, clone, Docker, emulator/USB/HTTPS, máy chủ mới | [SETUP_NEW_MACHINE.md](SETUP_NEW_MACHINE.md) |
| Từng tệp frontend Android và luồng màn hình | [android/README.md](../android/README.md) |
| Từng tệp backend, service, migration và cấu hình | [backend/README.md](../backend/README.md) |
| Kiến trúc thực tế và công cụ đang dùng | [ARCHITECTURE.md](ARCHITECTURE.md) |
| Bộ kiểm thử, dữ liệu QA, lint/SonarQube | [TESTING.md](TESTING.md) |
| Triển khai doanh nghiệp, khóa và database | [PRODUCTION.md](PRODUCTION.md) |
| Danh mục được giữ/gỡ khỏi GitHub | [REPOSITORY_REVIEW.md](REPOSITORY_REVIEW.md) |

## Nghiệp vụ và bàn giao

| Nội dung | Tài liệu |
| --- | --- |
| Mốc đã kiểm chứng và việc còn mở | [PROGRESS.md](../PROGRESS.md), [DEMO_STATUS.md](DEMO_STATUS.md) |
| Phạm vi và quyền | [SCOPE.md](SCOPE.md) |
| Hợp đồng API và API ảnh | [API.md](API.md), [IMAGE_API.md](IMAGE_API.md) |
| Mô hình dữ liệu, đối chiếu migration khi thay đổi | [DATA_MODEL.md](DATA_MODEL.md) |
| Sitemap / chuẩn UI | [UI.md](UI.md) |
| Kế hoạch gốc và tiêu chí nghiệm thu | [IMPLEMENTATION.md](IMPLEMENTATION.md) |
| 59 yêu cầu và ca kiểm thử còn thiếu | [NGHIEM_THU_V2_3.md](NGHIEM_THU_V2_3.md) |
| Tải và cài cập nhật APK trong app | [IN_APP_UPDATE.md](IN_APP_UPDATE.md) |

[Chốt bộ cài bàn giao và chuyển chuỗi ký](HANDOVER.md).

## Bằng chứng và lịch sử

- [Bố cục, ảnh bốn vai trò và kiểm thử 0.1.5](UI_REDESIGN_0.1.5.md): bộ ảnh gần nhất, không phải ảnh chụp lại sau sửa khoảng cách 0.1.7.
- [Cập nhật 0.1.6](IN_APP_UPDATE.md): luồng tải/cài đã thử trên emulator; còn nghiệm thu điện thoại thật.
- [UI 0.1.4](UI_UPDATE_0.1.4.md): bỏ ảnh lớp/gói và thông báo kết nối. Ảnh lịch sử dẫn tới tag cố định.
- [Gallery 0.1.2](UI_GALLERY.md), [kế hoạch ảnh cũ](IMAGE_FEATURE_PLAN.md), [QA ảnh](QA_IMAGES_2026-11-18.md): giữ để giải thích API ảnh tương thích, không phải hướng dẫn thêm ảnh lớp/gói trở lại UI.
- [QA nền tảng ngày 18/11](QA_2026-11-18.md): kết quả tại lần kiểm thử tương ứng.

Khi thay đổi code, cập nhật tài liệu sở hữu phần đó và ghi bằng chứng ở PROGRESS. Không tự đổi kết quả thử cũ thành kết quả thử mới.
