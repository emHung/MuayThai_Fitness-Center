# MuayThai & Fitness Center

Ứng dụng Android điều phối lịch tập, điểm danh theo gói và theo dõi hành trình tập luyện. Một APK phục vụ bốn vai trò gồm quản trị viên, lễ tân, huấn luyện viên và học viên. Android dùng Java và XML. Backend dùng Java 21, Spring Boot, PostgreSQL và Docker Compose.

Android Java và backend Docker đã có bản chạy được. Bộ tài liệu mô tả thiết kế và nghiệm thu, trạng thái từng phần được đối chiếu trong tiến độ và báo cáo kiểm thử.

## Chuẩn giao diện và kiến trúc

Giao diện Android bám theo bố cục, điều hướng và bộ màu Material 3 được định nghĩa trong [Sitemap và giao diện](UI.md). Toàn bộ nghiệp vụ và hợp đồng dữ liệu được đối chiếu chi tiết trong các tài liệu Markdown bên dưới:

## Đọc theo công việc

| Cần làm | Tài liệu |
| --- | --- |
| Xác định vai trò, chức năng và phạm vi demo | [Phạm vi và nghiệp vụ](SCOPE.md) |
| Dựng nền Java, service và Docker | [Kiến trúc và công nghệ](ARCHITECTURE.md) |
| Viết controller hoặc kết nối app | [Hợp đồng API](API.md) |
| Viết migration và xử lý ghi đồng thời | [Mô hình dữ liệu](DATA_MODEL.md) |
| Thiết kế màn hình Android XML | [Sitemap và giao diện](UI.md) |
| Lập lịch làm, kiểm thử và bàn giao | [Triển khai và nghiệm thu](IMPLEMENTATION.md) |

Khi tên màn hoặc nhãn phạm vi có thay đổi, dùng [phạm vi demo](SCOPE.md) để xác định chức năng và [hướng dẫn UI](UI.md) để dựng màn. Các quyết định còn mở nằm cuối tài liệu phạm vi.

## Ảnh, giao diện và kiểm thử

- [Giao diện thực tế chia theo vai trò](UI_GALLERY.md)
- [Hợp đồng API ảnh](IMAGE_API.md)
- [Thiết kế tính năng ảnh](IMAGE_FEATURE_PLAN.md)
- [Kiểm thử ảnh ngày 18/11/2026](QA_IMAGES_2026-11-18.md)
