# Trạng thái bàn giao bản demo

## Phần đã có trong mã

| Phần | Màn hình và thao tác |
| --- | --- |
| Tài khoản | Đăng nhập, đổi mật khẩu, hồ sơ cá nhân, danh bạ có tìm kiếm, thêm/sửa và khóa/mở khóa tài khoản, cấp mật khẩu tạm, xóa HLV chưa có dữ liệu nghiệp vụ |
| Gói và QR | Gán gói, xem hạn và lượt, mở mã QR từng gói, gia hạn bằng gói mới, đổi HLV phụ trách PT/lớp 10 |
| Lịch | Lịch ngày/tuần có cập nhật SSE, lưới giờ PT, ca làm và giờ bị đóng, đổi HLV cho từng buổi, đặt/duyệt/đổi/hủy PT, quản lý lớp 30, duyệt và đăng ký/đổi lớp 10, giữ chỗ cố định cho buổi lớp 10 mới sinh |
| Điểm danh | Quét và xem trước trên Android, điểm danh thủ công, ghi vắng, hiển thị danh sách theo buổi, hoàn lượt có lý do, lịch sử, tập tự do |
| Hành trình | Nội dung từng buổi, nhận xét và đánh giá PT, mục tiêu theo tháng |
| Vận hành | Danh sách cần gia hạn/chăm sóc, hộp thư và thông báo trung tâm, tác vụ bổ sung lịch lớp và xóa lịch sử điểm danh hết hạn |
| Triển khai | Docker Compose, database riêng theo dịch vụ, Flyway, APK debug và lệnh sao lưu PostgreSQL |

## Cách kiểm thử

- [README cài đặt và kịch bản demo](../README.md) có lệnh chạy Docker, build APK và chạy kiểm thử trên database riêng.
- [Kiểm thử API](../tests/acceptance.mjs) kiểm các vai trò, đổi mật khẩu, gói, đặt/đổi lịch, xác nhận QR, gửi lặp và đồng thời, hoàn lượt, hộp thư và mục tiêu.
- Bộ kiểm thử cũng kiểm xóa HLV, thu hồi phiên đang dùng, từ chối xóa HLV đã có ca và tình huống xóa đồng thời với đặt ca đầu tiên. Kiểm thử định kỳ dùng một buổi lớp 10 mới sinh để xác nhận hệ thống giữ chỗ tự động.
- Bản sao SQL đã được nhập vào một cụm PostgreSQL thử nghiệm. Bốn database đều có dữ liệu ở bảng nghiệp vụ được đối chiếu.
- Kiểm thử QR đã đối chiếu trường hợp đến sớm trong vòng một giờ và chưa vào cửa sổ của lớp 10 đã đặt. Sau khi lịch đó được hủy, quét lại gói chọn đúng lớp walk-in và trừ một lượt.
- Kiểm thử gửi đồng thời yêu cầu hủy và xác nhận điểm danh đối chiếu trạng thái buổi, lịch sử và lượt gói. Khi Check-in tạm ngừng, yêu cầu hủy được xử lý tiếp sau khi dịch vụ hoạt động lại.
- Kiểm thử đổi HLV đối chiếu lịch đã đặt, mã QR, lượt gói và quyền truy cập. Luồng SSE qua gateway đã nhận sự kiện ở cả ba vai trò.
- APK debug đã cài và đăng nhập với tài khoản quản trị viên trên máy ảo Android. Điều hướng từ màn quét QR tới Quản lý và danh sách HLV đã được chạy thử.
- Khi nghiệm thu Android trên điện thoại, mở hai thiết bị hoặc hai phiên ADB, dùng học viên hiện QR trên màn hình thứ nhất và dùng quản trị viên quét trên màn hình thứ hai. Đối chiếu học viên, buổi và lượt còn lại trước và sau xác nhận.

## Vị trí các phần mã

- `android/app/src/main/java/vn/muaythai/app` chứa các màn Java. Màn theo nghiệp vụ dùng chung điều hướng, bảng màu và thành phần nhập liệu của workspace.
- `backend` chứa các module Maven. API xử lý hợp đồng và phân quyền, service áp dụng quy tắc nghiệp vụ, repository truy vấn dữ liệu, migration đặt ràng buộc ở PostgreSQL.
- `deploy` chứa cấu hình Docker, khởi tạo database và sao lưu. Giao diện tham chiếu là [Prototype Tối giản](Prototype%20Giai%20đoạn%201%20-%20MuayThai%20%26%20Fitness%20Center%20-%20Tối%20giản.html).

## Phạm vi còn phải chốt

- Khi lịch đang mở, app nhận tín hiệu cập nhật SSE rồi đọc dữ liệu theo quyền. Mốc tải lại 15 giây giúp đồng bộ sau khi kết nối gián đoạn. Thông báo nền còn ngoài phạm vi hiện tại.
- Các lượt gán lớp 10 từ trước khi có quy tắc lịch cố định vẫn tồn tại. Quản trị viên lưu lại quy tắc một lần để hệ thống tự giữ chỗ ở các đợt sinh lịch sau.
- Cần thử thêm các cửa sổ đi sớm, đến muộn và walk-in của lớp nhóm theo BR12 và kiểm thử quét bằng camera điện thoại.
- Các hạng mục còn thiếu và các luồng cần chạy trên APK nằm trong [bảng đối chiếu FR01–FR59](NGHIEM_THU_V2_3.md). Bản phát hành ký số dùng máy chủ HTTPS vẫn cần nghiệm thu.
