# Ảnh hồ sơ, loại gói và lớp học

Từ 0.1.4, giao diện chỉ giữ avatar. Ảnh lớp/gói đã bỏ theo yêu cầu cập nhật; API và dữ liệu cũ được giữ tương thích. Xem [thay đổi UI](UI_UPDATE_0.1.4.md).

Đã được duyệt và triển khai trong bản 0.1.2. Xem [API](IMAGE_API.md), [giao diện](UI_GALLERY.md) và [kết quả kiểm thử](QA_IMAGES_2026-11-18.md). Các mục kiểm thử bên dưới là tiêu chí, trạng thái đạt hoặc còn mở nằm trong báo cáo.

## Phạm vi đã thống nhất

Thêm ảnh cho cả bốn loại gói PT, lớp 10, lớp 30, tập tự do và cho từng lớp cụ thể. Người dùng được thay ảnh đại diện của mình. Giao diện tiếp tục dùng các màn và thành phần Material hiện có.

## Luồng sử dụng

- Trong Tài khoản của tôi, hiển thị avatar và các thao tác Chọn ảnh, Xem trước, Lưu ảnh và Xóa ảnh.
- Trong Quản lý, thêm mục Ảnh loại gói. Quản trị viên chọn loại gói để thay ảnh bìa dùng chung.
- Trong chi tiết lớp, quản trị viên có thể thay hoặc xóa ảnh bìa riêng của lớp.
- Học viên nhìn thấy ảnh loại gói ở thẻ tập và khu vực đặt lịch. Ảnh lớp riêng được ưu tiên ở màn chi tiết lớp, tiếp theo là ảnh loại gói.
- Khi chưa có ảnh, dùng thành phần mặc định cùng bảng màu và Remix Icon đang có.
- Hủy chọn ảnh hoặc tải lên thất bại giữ lại ảnh đang sử dụng và hiển thị thông báo phù hợp.

## Quyền truy cập

- Người dùng đã đăng nhập và hoàn tất đổi mật khẩu tạm được cập nhật avatar của chính mình.
- Quản trị viên được cập nhật ảnh loại gói và ảnh lớp.
- Quyền xem avatar theo quyền xem hồ sơ hiện có. Quyền xem ảnh lớp theo quyền xem lớp hiện có.
- Kiểm tra tài khoản khóa, phiên hết hạn và quyền đối tượng ở backend cho mọi thao tác đọc, ghi và xóa.

## Cấu trúc triển khai

- Android có thành phần dùng chung cho chọn, xem trước và tải ảnh. Đăng ký trình chọn ảnh theo lifecycle của activity, xử lý xoay màn hình và rời màn hình lúc đang tải.
- Dùng Android Photo Picker để chọn ảnh. Dùng thư viện tải ảnh hỗ trợ Java, cache, placeholder và yêu cầu xác thực, sau khi đối chiếu tài liệu và license.
- Auth service sở hữu avatar. Schedule service sở hữu ảnh lớp. Ảnh loại gói được đặt trong Check-in service cùng nghiệp vụ gói.
- Dùng migration bổ sung và endpoint riêng để cập nhật ảnh, bảo toàn hợp đồng API sửa hồ sơ và lớp đang được sử dụng bởi APK cũ.

## Lưu trữ được đề xuất

Giai đoạn một lưu ảnh đã chuẩn hóa dưới dạng nhị phân trong PostgreSQL của service sở hữu. Bản sao database hiện tại có thể bao gồm ảnh. Cần đo kích thước database và thời gian sao lưu với dữ liệu thử trước khi áp dụng trên số lượng lớn hồ sơ.

Ảnh được gửi dưới dạng multipart. Backend kiểm tra kích thước tệp, định dạng thực, chiều ảnh và số pixel trước khi giải mã. Ảnh được thu nhỏ và mã hóa lại để loại bỏ metadata vị trí. Đặt giới hạn đầu vào, thời gian xử lý và tài nguyên giải mã trong cấu hình. Các bản cập nhật và xóa ảnh thực hiện trong transaction.

Ảnh trả về qua endpoint có kiểm tra quyền. Android tắt cache bộ nhớ và đĩa cho ảnh từ máy chủ để mỗi lần tải đều kiểm tra quyền hiện tại; HTTP trả private, no-cache và ETag.

## Hợp đồng API cần chốt trong triển khai

- Avatar có thao tác GET, PUT multipart và DELETE trên tài nguyên ảnh của người dùng.
- Ảnh lớp có thao tác GET, PUT multipart và DELETE trên tài nguyên ảnh của lớp.
- Ảnh loại gói có thao tác GET, PUT multipart và DELETE trên tài nguyên ảnh theo enum PT, GROUP10, GROUP30, MEMBERSHIP.
- GET trả nội dung ảnh và phiên bản cache. Khi chưa có ảnh trả HTTP 404 để Android hiển thị placeholder.
- Tệp quá lớn trả HTTP 413. Định dạng không hỗ trợ trả HTTP 415. Dữ liệu ảnh hỏng trả HTTP 400. Thiếu quyền trả HTTP 403.
- Tài liệu API phải ghi endpoint, giới hạn, lỗi và quyền trước khi viết phần xử lý.

## Kiểm thử nghiệm thu

- Chọn, xem trước, lưu, thay và xóa avatar trên Android.
- Đổi ảnh của từng loại gói và từng lớp, kiểm tra ảnh xuất hiện trên thiết bị khác.
- Thử APK cũ tiếp tục đăng nhập và sửa thông tin bằng hợp đồng API hiện có.
- Chặn người dùng sửa ảnh của người khác và học viên sửa ảnh gói hoặc lớp.
- Chặn tệp giả ảnh, ảnh hỏng, ảnh quá lớn và ảnh có kích thước giải mã vượt giới hạn.
- Kiểm tra quyền đọc ảnh khi đăng xuất, bị khóa hoặc chuyển tài khoản.
- Khởi động lại Docker, sao lưu và khôi phục database thử để xác minh ảnh tồn tại.
- Chạy build, lint và kiểm thử API. Nghiệm thu bằng thao tác thật trên Android trước khi phát hành APK mới.

## Trạng thái

Đã xác nhận phạm vi ảnh cho cả loại gói và từng lớp. Tài liệu này là bản thiết kế để duyệt trước khi triển khai theo AGENTS.md. Chức năng tải ảnh chưa được triển khai.
