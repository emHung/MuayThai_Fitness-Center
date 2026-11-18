# Rà soát repository — 18/11/2026

## Phạm vi và cơ sở

Rà soát các tệp Git theo dõi trên main sau bản 0.1.7, đối chiếu cấu hình build, Compose, workflow, tài liệu và liên kết. Không xóa dữ liệu máy chủ, release APK, database, lịch sử commit hoặc khóa ký trên máy gốc.

## Đã gỡ khỏi nhánh hiện tại

| Nhóm | Số tệp | Lý do / giữ bằng chứng |
| --- | --- | --- |
| `docs/screenshots/2026-11-18/` | 30 PNG | UI 0.1.2 đã được thay thế, gồm màn ảnh lớp/gói không còn trên app. Gallery cũ vẫn xem ảnh ở tag v0.1.7. |
| `docs/screenshots/0.1.4/` | 11 PNG | Bộ ảnh trước đợt làm lại bố cục 0.1.5. Báo cáo 0.1.4 dùng đường dẫn tag v0.1.7. |

Tổng 41 ảnh, 3.158.609 byte (khoảng 3,01 MiB). Gỡ bản sao trong cây mã hiện tại, **không viết lại lịch sử Git**; clone toàn bộ lịch sử vẫn có thể tải các object cũ. Không coi đây là đã giảm kích thước lịch sử repository.

## Giữ lại có chủ đích

- `docs/screenshots/0.1.5/` và `0.1.6/`: bằng chứng bố cục gần nhất và luồng cập nhật, có báo cáo dẫn tới từng ảnh.
- Các báo cáo QA, thiết kế ảnh cũ và kế hoạch: còn giá trị truy vết, đã phân biệt lịch sử/hiện tại trong mục lục. Giữ API ảnh lớp/gói tương thích, không xóa mã hay migration chỉ vì UI không dùng ảnh đó nữa.
- `android/gradle/wrapper/gradle-wrapper.jar`, wrapper scripts: cần build từ máy mới; đây không phải build output dư.
- Migration SQL, init SQL/shell, production grant script: cần tạo/nâng schema và chạy production smoke.
- `tests/fixtures/course.jpg`: ảnh đầu vào tự tạo cho kiểm thử upload, không phải ảnh UI dư.
- Giấy phép Remix Icon và danh mục bên thứ ba: phải đi cùng tài nguyên sử dụng.
- Script kiểm thử, coverage, backup, đẩy Git: phục vụ kiểm chứng/vận hành. Script lịch đẩy Git không tự cài Task Scheduler trên máy mới.
- `.ignore`: cấu hình tìm kiếm mã dành cho người phát triển; không phải yêu cầu cài graft/Codex để chạy app.

## Tài liệu đã chỉnh

- README rút gọn thành trang bắt đầu; bỏ tỷ lệ 50/59/85% chưa có tiêu chí chấm từng FR, sửa ba vai trò thành bốn.
- PROGRESS/DEMO_STATUS ghi rõ bản 0.1.7, kết quả CI/lint, giới hạn thiết bị thật và SonarQube lần đo trước.
- ARCHITECTURE sửa mô tả Fragment, Dockerfile mỗi service, JUnit/Testcontainers/Spotless đã tích hợp: các mô tả đó không đúng mã hiện tại.
- Thêm hướng dẫn [máy mới](SETUP_NEW_MACHINE.md), [frontend](../android/README.md), [backend](../backend/README.md), [kiểm thử](TESTING.md); cập nhật mục lục, production và kế hoạch bàn giao.

## Những thứ không được đưa lên Git

`.env`, mật khẩu/token, keystore, bản sao database, build/cache, APK và log cá nhân được loại qua ignore. APK đi vào Releases, không commit vào source. Rà soát tập tệp được theo dõi và staged diff trước khi push; không in giá trị secret trong báo cáo.

## Kiểm chứng đợt rà soát

Kiểm tra liên kết nội bộ Markdown/HTML, đối chiếu danh mục với tệp Java hiện hữu, whitespace diff, phạm vi xóa ảnh và ignore. Thử từ bản export source sạch: init cấu hình hai lần cho kết quả giữ nguyên, Compose chấp nhận cấu hình. Bộ CI kiểm lại Android/API/production sau khi push. Không coi kiểm chứng cấu hình này là đã cài thử trên một máy Windows vật lý mới.

Kết quả kiểm tra tài liệu: 256 liên kết nội bộ hợp lệ tại lượt rà soát; danh mục mô tả đủ 27 tệp Java Android và 53 tệp Java backend. Không phát hiện tệp secret/build theo nhóm đường dẫn kiểm tra trong tập source được theo dõi. Kiểm tra tên tệp và staged diff không thay thế kiểm toán bảo mật toàn bộ lịch sử Git.
