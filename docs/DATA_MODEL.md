# Mô hình dữ liệu Giai đoạn 1

Mô hình dự kiến cho PostgreSQL 17. Mỗi service dùng Flyway riêng. Khóa ngoại dùng trong cùng database. ID tham chiếu qua service được kiểm bằng API nội bộ. Mọi thời điểm nghiệp vụ lưu bằng `timestamptz`, app hiển thị theo `Asia/Ho_Chi_Minh`.

## Bảng theo database

| Database | Bảng | Nội dung và ràng buộc |
| --- | --- | --- |
| auth_db | `users` | ID, mã học viên, username chuẩn hóa duy nhất, mật khẩu đã băm, role, trạng thái, cờ đổi mật khẩu và phiên bản thu hồi phiên. Mã học viên duy nhất khi có giá trị. |
| auth_db | `user_sessions` | ID phiên, user, hạn, thời điểm thu hồi. Dùng để đăng xuất một thiết bị và kiểm phiên đang hoạt động. |
| schedule_db | `class30`, `class30_slots` | Lớp 30, HLV, trạng thái và quy tắc lặp. Mỗi khung có thứ trong tuần và khoảng giờ. |
| schedule_db | `sessions` | Loại buổi, khoảng giờ, HLV, lớp hoặc học viên và gói PT. Sinh buổi lặp với khóa duy nhất theo lớp và giờ bắt đầu. |
| checkin_db | `packages` | Mã QR duy nhất, chủ gói, loại, HLV/lớp, thời hạn, tổng và lượt đã dùng. Ràng buộc lượt trong khoảng hợp lệ cho PT và lớp 30. Gói tự do không tính lượt. |
| checkin_db | `checkins` | ID học viên, buổi, gói, phương thức, người ghi, giờ ghi và khóa yêu cầu. Cặp học viên/buổi duy nhất. |
| checkin_db | `checkin_adjustments` | Tham chiếu lượt gốc, lý do, người xử lý, thời điểm, số lượt trước/sau. Lượt hoàn duy nhất theo check-in. |
| progress_db | `session_notes` | Biên bản theo buổi, người ghi, nội dung, nhận xét và điểm nếu là PT. Mỗi buổi có một bản nhận xét chính. |

Các bảng phiên và điều chỉnh là phần mở rộng phục vụ yêu cầu khóa tài khoản, đăng xuất và xử lý thao tác nhầm. Giá trị mẫu chỉ được nạp bằng cấu hình demo.

## Giữ lịch

Gói lớp 10 mới lưu `class_id` cùng `trainer_id` lấy từ lớp đang mở; client không tự chỉ định HLV khác. Các gói cũ chỉ gắn HLV hoặc lớp vẫn được đọc. Gói được gán lịch cố định ngay từ biểu mẫu; chỉ đăng ký buổi nằm trong hạn gói. Nếu bước gán lịch thất bại, app giữ gói đã tạo và cho thử lại bước gán lịch.

Xóa học viên/lễ tân đặt `users.deleted_at`, khóa tài khoản, thu hồi phiên và ghi `account_status_events`. Không xóa ID, gói, lịch, điểm danh hay nhận xét. Hủy buổi bị chặn nếu có bất kỳ check-in lịch sử nào, kể cả lượt đã hoàn.

- Schedule kiểm khoảng giờ thực theo quy ước `[startAt, endAt)`. Hai giờ liền nhau có thể dùng chung mốc cuối/đầu.
- Với lịch HLV, dùng ràng buộc chống giao khoảng giờ cho các trạng thái đang giữ chỗ trên cùng HLV. Với lịch PT học viên, dùng ràng buộc tương ứng trên ID học viên. Một kiểm tra trước khi ghi giúp hiện lỗi sớm. Kết quả ghi là căn cứ cuối cùng khi có thao tác đồng thời.
- Giai đoạn 2 thêm đăng ký lớp 10. Khi đó, dùng dữ liệu giữ chỗ chung ở Schedule để so trùng giữa PT và lớp nhóm. Suất lớp 10 giới hạn 10 và được cấp lại theo chính sách giữ chỗ của trung tâm.
- Bản sao tên HLV/học viên, nếu cần để hiện lịch sử, là dữ liệu hiển thị tại thời điểm thao tác. Tra hồ sơ hiện tại qua Auth khi cần thông tin mới nhất.

## Ghi nhận và điều chỉnh điểm danh

Check-in kiểm lại dữ liệu buổi ở Schedule khi xác nhận. Với lớp 30, mỗi lần ghi khóa theo buổi, đếm lượt còn hiệu lực rồi mới ghi. Trong transaction của `checkin_db`, khóa gói, kiểm lại hiệu lực và số lượt, ghi check-in, sau đó cập nhật lượt đã dùng. Tranh chấp khóa duy nhất trả kết quả đã ghi. Hai yêu cầu cho suất cuối của lớp 30 chỉ có tối đa một yêu cầu ghi được.

Điều chỉnh dùng cùng thứ tự khóa theo buổi, gói rồi lượt gốc trong một transaction. Thứ tự này đồng nhất với lần ghi check-in. Nếu lượt chưa được hoàn, tạo điều chỉnh, cập nhật lượt đã dùng và trả trạng thái mới. Lịch sử giữ cả thao tác gốc và lần điều chỉnh. Với Giai đoạn 1, một lượt đã hoàn vẫn giữ khóa duy nhất học viên/buổi. Trường hợp cần ghi lại sau khi hoàn cần quy tắc nghiệp vụ riêng để phân biệt hoàn nhầm với check-in mới.

Khóa idempotency của lần xác nhận gắn với người thực hiện và nội dung yêu cầu. Gửi lại cùng khóa và nội dung trả kết quả đã lưu. Gửi lại với nội dung khác trả lỗi xung đột. Khóa duy nhất theo học viên/buổi bảo vệ thêm khi hai máy dùng hai khóa khác nhau.

## Dữ liệu demo và phục hồi

Seed một ADMIN có quyền điều chỉnh, một ADMIN thường, hai HLV, hai học viên và các gói độc lập. Chuẩn bị một học viên có hai QR PT, một gói hết hạn, lớp 30 chỉ còn một chỗ và lịch PT trùng giờ. Tạo thời gian mẫu tương đối với thời điểm chạy nghiệm thu để quét được trong cửa sổ hợp lệ.

Trước khi vận hành với dữ liệu thật, thử sao lưu và khôi phục cả bốn database cùng một đợt. Đối chiếu ID liên service, lịch PT, lịch sử và lượt còn lại sau khôi phục.

[API ghi nhận và điều chỉnh](API.md) · [Kiến trúc](ARCHITECTURE.md)

## Bảng ảnh bổ sung

Auth sở hữu profile_images, Schedule sở hữu class_images, Check-in sở hữu package_kind_images. Mỗi bảng lưu resource_key, content dạng bytea và version. Bảng hồ sơ/lớp có khóa ngoại xóa liên đới. Xem [API ảnh](IMAGE_API.md).
