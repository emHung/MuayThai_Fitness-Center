# MuayThai & Fitness Center

Ứng dụng Android điều phối lịch tập, điểm danh theo gói và theo dõi hành trình tập luyện. Một APK phục vụ ba vai trò gồm quản lý/lễ tân, huấn luyện viên và học viên. Android dùng Java và XML. Backend dùng Java 21, Spring Boot, PostgreSQL và Docker Compose.

Bộ Markdown là thiết kế làm việc để xây và nghiệm thu. Hiện có prototype HTML chạy cục bộ. Các API, cấu hình triển khai và tiêu chí bên dưới là mục tiêu triển khai.

## Mở bản giao diện

Mở [Prototype Giai đoạn 1 Tối giản](<Prototype Giai đoạn 1 - MuayThai & Fitness Center - Tối giản.html>) bằng Chrome. Chọn **ADMIN**, **HLV** hoặc **Học viên** để xem sitemap tương ứng. Giao diện Android bám theo bố cục, điều hướng và bộ màu của bản này. Nội dung và trạng thái hiển thị lấy từ API khi app được triển khai.

## Đọc theo công việc

| Cần làm | Tài liệu |
| --- | --- |
| Xác định vai trò, chức năng và phạm vi demo | [Phạm vi và nghiệp vụ](SCOPE.md) |
| Dựng nền Java, service và Docker | [Kiến trúc và công nghệ](ARCHITECTURE.md) |
| Viết controller hoặc kết nối app | [Hợp đồng API](API.md) |
| Viết migration và xử lý ghi đồng thời | [Mô hình dữ liệu](DATA_MODEL.md) |
| Chuyển prototype sang Android XML | [Sitemap và giao diện](UI.md) |
| Lập lịch làm, kiểm thử và bàn giao | [Triển khai và nghiệm thu](IMPLEMENTATION.md) |

## Tài liệu gốc

- [Đặc tả nghiệp vụ v2.3](Dac_ta_v2.docx) chứa FR01–FR59 và các quy tắc nghiệp vụ.
- [Lộ trình Bản 1](<Bản 1 - Lộ trình hiện thực app microservice (tự làm 60_ + Claude Code 40_).docx>) chứa thiết kế kỹ thuật và mốc kế hoạch ban đầu.
- [Prototype Bản 1](<Ban1_Prototype 2.html>) dùng để tra các màn thuộc phạm vi mở rộng.

Khi tên màn hoặc nhãn phạm vi trong prototype khác quyết định đang áp dụng, dùng [phạm vi demo](SCOPE.md) để xác định chức năng và [hướng dẫn UI](UI.md) để dựng màn. Các quyết định còn mở nằm cuối tài liệu phạm vi.
