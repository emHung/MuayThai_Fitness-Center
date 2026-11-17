# Sitemap và giao diện Android

Giao diện ứng dụng Android tuân theo chuẩn Material 3 và sitemap bên dưới. Tra màn bằng `id` của section. Khi chạy APK, vai trò lấy từ phiên đăng nhập.

## Sitemap

| Vai trò | Tab gốc | Màn con tiêu biểu |
| --- | --- | --- |
| ADMIN | Quét `ad-scan` | Nhập mã, xem trước check-in, kết quả thành công/trùng/lỗi, điều chỉnh có quyền |
| ADMIN | Lịch `ad-cal` | Chi tiết buổi, điểm danh thủ công, quản lý và tạo lịch lớp 30 |
| ADMIN | Học viên `ad-students` | Tìm danh sách, hồ sơ, gán gói, lịch sử điểm danh |
| ADMIN | Quản lý `ad-more` | HLV, lớp 30 và tài khoản |
| HLV | Lịch dạy `hl-cal` | Chi tiết PT hoặc lớp 30, điểm danh thủ công, ghi nội dung buổi |
| HLV | Chờ xác nhận `hl-req` | Duyệt hoặc từ chối yêu cầu PT |
| HLV | Học viên PT `hl-students` | Tìm danh sách được phân công, hồ sơ và đánh giá |
| Học viên | Lịch `hv-cal` | Chi tiết PT, hủy buổi theo hạn |
| Học viên | Đặt lịch `hv-book-hub` | Gói PT, ngày giờ, kết quả gửi |
| Học viên | Thẻ QR `hv-cards` | QR và thông tin của từng gói |
| Học viên | Hành trình `hv-journey` | Nhận xét, đánh giá và lịch sử điểm danh |

Các màn dùng chung gồm đăng nhập `login`, tài khoản `acc`, đổi mật khẩu lần đầu `hl-pw-first` và lỗi mạng theo ngữ cảnh. Màn chi tiết có nút quay lại. Thanh tab bố trí ở màn gốc.

## Màu và bố cục

| Thành phần | Giá trị tham chiếu |
| --- | --- |
| Nền, bề mặt, viền | `#F6F7F5` · `#FFFFFF` · `#E3E7E2` |
| Chữ chính, chữ phụ | `#17211E` · `#4E6059` |
| ADMIN, HLV, học viên | `#243348` · `#1C6557` · `#AD2A3E` |
| Nền tab đang chọn theo vai trò | `#EAF0F5` · `#E5F1EB` · `#FAECEE` |
| Thành công, cảnh báo, lỗi | `#185B43` · `#755013` · `#9B2632` |

- Chuyển giá trị dùng chung vào Android resources cho màu, chữ, khoảng cách và kiểu component. Dùng Java + XML, Material Components và ViewBinding. Roboto hệ thống là mặc định dễ triển khai. Inter là font tham chiếu trong HTML.
- Giữ nền sáng, tiêu đề dễ quét, viền mảnh và thẻ bo vừa. Dùng màu vai trò cho thao tác chính, tab đang chọn và mốc lịch. Trạng thái phải có chữ đi kèm màu.
- Dùng [Remix Icon](https://remixicon.com/) cho biểu tượng điều hướng và thao tác trong app. Nhập từng SVG cần dùng thành `VectorDrawable`, đặt tên nhất quán và dùng màu từ theme. Đối chiếu [giấy phép Remix Icon](https://github.com/Remix-Design/RemixIcon/blob/master/License) cho bộ icon sử dụng.
- Chuyển màn nhẹ theo nhịp tham chiếu 170 ms, bảng trượt mở từ dưới. Tôn trọng thiết lập giảm hiệu ứng của thiết bị. Trạng thái đang gửi vô hiệu hóa nút gửi và hiện phản hồi rõ ràng.

## Chuyển màn giả lập sang luồng thật

- **Quét QR** · Hiện kết quả xem trước, hành động xác nhận, trạng thái đang lưu và kết quả cuối. Trường hợp nhập mã hội viên mở hồ sơ và yêu cầu chọn gói. Vùng tình huống quét mẫu là công cụ của HTML.
- **Xem lịch** · Ngày/Tuần lấy dữ liệu theo khoảng ngày và quyền. Nhãn ngày dẫn tới danh sách đúng ngày. Các bộ lọc xuất hiện trên màn phải đổi kết quả thực tế.
- **Thẻ QR** · Mỗi thẻ mở đúng gói, có mã và số lượt của gói đó. Dùng QR do thư viện sinh từ mã gói thật.
- **Điều chỉnh** · Dùng một bảng trượt từ chi tiết điểm danh. Hiển thị lượt gốc, số lượt dự kiến sau điều chỉnh, trường lý do và nút xác nhận. Kết quả đã lưu xuất hiện trong lịch sử.
- **Danh sách và biểu mẫu** · Phủ các trạng thái đang tải, dữ liệu rỗng, lỗi, mất phiên và gửi thành công. Duy trì nhãn cho ô nhập, mô tả cho nút icon và thứ tự focus hợp lý.

Kiểm tra tối thiểu ở màn hẹp tương đương 320 dp và 390 dp, có bàn phím, tăng cỡ chữ và khi Android tắt hiệu ứng. [Hợp đồng dữ liệu hiển thị](API.md) · [Ca nghiệm thu](IMPLEMENTATION.md)
