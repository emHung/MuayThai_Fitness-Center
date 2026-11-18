# Triển khai và nghiệm thu

Một chức năng hoàn thành khi app Android gọi API thật, service áp dụng đúng quyền và nghiệp vụ, dữ liệu vẫn đúng sau khi tải lại, và luồng có ca kiểm thử thành công cùng lỗi chính. Bốn vai trò dùng một APK. Bản demo dùng tài khoản và dữ liệu tạo sẵn có thể dựng lại.

Tài liệu này giữ kế hoạch gốc để đối chiếu; hiện trạng hiện hành ở [PROGRESS.md](../PROGRESS.md). Cài máy mới theo [hướng dẫn](SETUP_NEW_MACHINE.md), kiểm thử theo [TESTING.md](TESTING.md).

## Kế hoạch năm tuần

| Tuần | Kết quả bàn giao |
| --- | --- |
| 1 | Dựng app Java/XML, khung bốn service, gateway và PostgreSQL bằng Compose. Đăng nhập từ điện thoại thật, chặn phiên bị khóa và kiểm luồng đổi mật khẩu lần đầu. |
| 2 | Hoàn thành hồ sơ, gói, QR từng gói và lớp 30 sinh lịch. Chạy thử APK qua HTTPS. |
| 3 | Hoàn thành đặt/duyệt/hủy PT, quét QR qua xem trước và xác nhận, cùng điểm danh thủ công. Kiểm trùng lịch, nhầm gói, quét hai lần và mất phản hồi. |
| 4 | Hoàn thành đánh giá, hành trình, lịch sử và điều chỉnh điểm danh. Chạy lại các luồng trên thiết bị thật và xử lý lỗi. |
| 5 | Dựng Compose từ máy sạch, chạy migrations/seed, kiểm toàn bộ demo qua HTTPS/4G. Bàn giao APK, hướng dẫn chạy, sơ đồ và kịch bản trình diễn. |

Thứ tự trên dùng cho demo Giai đoạn 1. Nếu buổi nộp yêu cầu toàn bộ FR01–FR59, lên lại kế hoạch theo [phạm vi](SCOPE.md). Điều chỉnh tiến độ theo kết quả chạy thực của từng tuần.

## Ca nghiệm thu trọng tâm

[Bảng đối chiếu FR01–FR59 của đặc tả v2.3](NGHIEM_THU_V2_3.md) ghi phạm vi ca kiểm thử và các mục còn cần xác nhận.

| Luồng | Cách kiểm | Kết quả cần thấy |
| --- | --- | --- |
| Đăng nhập lần đầu | Đăng nhập HLV mới, thử mở lịch trước khi đổi mật khẩu, sau đó đổi | Chỉ truy cập lịch sau khi đổi và nhận phiên mới. |
| Khóa tài khoản | Đăng nhập HLV trên điện thoại, dùng ADMIN khóa, thử mở danh sách | Yêu cầu tiếp theo bị từ chối, mở khóa yêu cầu đăng nhập lại. |
| Quyền trên đối tượng | HLV xem buổi của HLV khác, học viên thử xem gói của người khác | Từ chối truy cập, dữ liệu chỉ trả đúng phạm vi. |
| Tạo lớp 30 | Tạo quy tắc lặp và chạy bước sinh buổi hai lần | Lịch xuất hiện đúng giờ, đúng lớp, không sinh trùng. |
| Đặt PT | Hai yêu cầu đồng thời tranh giờ cùng HLV. Thử thêm cùng học viên đặt hai HLV cùng giờ | Mỗi tình huống chỉ tạo được một lịch hợp lệ. |
| Hủy PT | Thử hủy đúng hạn và quá hạn bằng tài khoản học viên | Hủy đúng hạn cập nhật lịch hai bên và giữ lượt gói. Quá hạn có phản hồi rõ. |
| QR đúng gói | Một học viên có hai gói PT, thử QR khác với gói đặt | Báo sai gói. Số lượt cả hai gói giữ nguyên. QR đúng giảm một lượt sau khi xác nhận. |
| Trùng hoặc mất phản hồi | Quét hai máy hoặc gửi lại sau khi máy chủ lưu nhưng điện thoại chưa nhận kết quả | Một lượt duy nhất được ghi và chỉ trừ một buổi. App hiển thị thời điểm và số lượt. |
| Sĩ số lớp 30 | Hai lượt đến khi lớp còn một chỗ | Một lượt thành công. Lượt còn lại báo lớp đủ chỗ. |
| Điểm danh thủ công | HLV ghi cho buổi mình dạy, sau đó thử ghi cho buổi khác | Lượt hợp lệ có người thao tác và lý do. Buổi ngoài quyền bị từ chối. |
| Điều chỉnh | Người có quyền hoàn một lượt, rồi thử gửi lặp hoặc đồng thời | Một bản ghi điều chỉnh và một lượt hoàn, có thể tra được trong lịch sử. |
| Hành trình | HLV ghi nhận xét/điểm PT và học viên tải lại app | Học viên thấy đúng buổi và đánh giá đã lưu. |
| Thiết bị | Thử màn hẹp, nhập liệu với bàn phím, chuyển tab, trạng thái lỗi và giảm hiệu ứng | Giao diện dễ đọc, điều hướng cùng sitemap, thao tác có phản hồi phù hợp. |

Dùng Testcontainers cho các ca phụ thuộc ràng buộc PostgreSQL. Các ca qua APK cần có ít nhất một lần chạy qua gateway và HTTPS. Mã, migration và test của mỗi luồng đi cùng nhau để có thể kiểm tra khi review.

## Bàn giao và vận hành

- Hướng dẫn dựng bằng Docker Compose, biến môi trường cần có, cách seed dữ liệu demo và cách kiểm tra healthcheck.
- APK release, địa chỉ HTTPS, tài khoản demo qua kênh bàn giao phù hợp, kịch bản theo vai trò và video dự phòng.
- Xác nhận dữ liệu còn lại sau khi khởi động lại container. Nếu trung tâm chuẩn bị nhập dữ liệu thật, thử khôi phục cả bốn database trên môi trường tách riêng rồi kiểm tra lịch và số lượt.
- Ghi nhận phiên bản Java, thư viện, image Docker và thông tin bản build APK để dựng lại được.

[Kiến trúc và công cụ](ARCHITECTURE.md) · [Chuẩn UI](UI.md) · [Hợp đồng API](API.md)
