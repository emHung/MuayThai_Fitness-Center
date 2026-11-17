# Hợp đồng API Giai đoạn 1

Client dùng JSON qua gateway cho các thao tác và `text/event-stream` để nhận tín hiệu cập nhật lịch. Đường dẫn có xác thực dùng `Authorization: Bearer <token>`. Thời điểm trả theo ISO 8601, ví dụ `2026-11-18T17:00:00+07:00`. Danh bạ hỗ trợ `page`, `size` và trả `items`, `page`, `size`, `total`. Lịch nhận khoảng `from` và `to`.

## Xác thực và hồ sơ

| Phương thức và đường dẫn | Quyền | Dữ liệu chính |
| --- | --- | --- |
| `POST /api/auth/login` | Công khai | Nhận `username`, `password`. Trả token, ID phiên, hạn, user và `mustChangePassword`. |
| `POST /api/auth/change-password` | Phiên của chính mình | Nhận mật khẩu hiện tại và mật khẩu mới. Trả phiên mới sau đổi mật khẩu. |
| `POST /api/auth/logout` | Phiên của chính mình | Thu hồi phiên hiện tại. |
| `GET /api/users/me`, `PUT /api/users/me` | Tài khoản đang hoạt động | Xem/sửa thông tin được phép. Role và trạng thái lấy từ hệ thống. |
| `GET /api/learners`, `GET /api/learners/{id}` | ADMIN, lễ tân, hoặc HLV trong phạm vi được phân công | Tìm theo `q` và tra hồ sơ. |
| `POST /api/learners`, `PUT /api/learners/{id}` | ADMIN | Tạo/sửa hồ sơ. |
| `GET /api/trainers`, `GET /api/trainers/{id}`, `POST /api/trainers`, `PUT /api/trainers/{id}` | ADMIN | Xem, tạo, sửa hồ sơ HLV và cấp mật khẩu tạm khi tạo. |
| `GET /api/receptionists`, `GET /api/receptionists/{id}`, `POST /api/receptionists` | ADMIN | Xem hoặc tạo tài khoản lễ tân. Tài khoản mới phải đổi mật khẩu khi đăng nhập lần đầu. |
| `PATCH /api/users/{id}/status` | ADMIN | Nhận `status` và `reason`. Áp dụng quyền trên đối tượng và thu hồi phiên khi khóa. |
| `POST /api/users/{id}/password-reset` | ADMIN, đối với tài khoản lễ tân, HLV hoặc học viên | Nhận `reason` và trả mật khẩu tạm với `Cache-Control: no-store`. Phiên và mật khẩu cũ mất hiệu lực. |

Phiên cần đổi mật khẩu lần đầu có thể gọi đổi mật khẩu hoặc đăng xuất. Sau khi đổi thành công, app dùng phiên mới để điều hướng theo vai trò.

## Gói, lịch lớp và lịch PT

| Phương thức và đường dẫn | Quyền | Dữ liệu chính |
| --- | --- | --- |
| `POST /api/packages`, `GET /api/packages?learnerId=...` | ADMIN | Gán/xem gói của học viên. Gói có loại, ngày hiệu lực, HLV với PT, lớp với gói lớp 30 và số lượt khi gói tính theo buổi. |
| `GET /api/packages/{id}`, `PUT /api/packages/{id}/trainer` | ADMIN | Xem gói hoặc đổi HLV phụ trách gói PT/lớp 10 bằng `trainerId`, `reason`. Gói giữ mã QR và số lượt. Lịch đã đặt vẫn dùng được. |
| `GET /api/packages/mine`, `GET /api/packages/mine/{id}` | Học viên | Thẻ gói, mã QR của chính mình, hạn và lượt còn lại. |
| `GET /api/classes10`, `GET /api/classes30` | Theo phạm vi | Hồ sơ lớp và quy tắc lịch lặp. Tham số `q` tùy chọn tìm theo tên lớp. |
| `GET /api/classes10/{id}`, `GET /api/classes30/{id}` | Theo phạm vi | Chi tiết hồ sơ lớp. |
| `POST /api/classes30`, `PUT /api/classes30/{id}`, `POST /api/classes30/{id}/stop` | ADMIN | Quản lý lớp, HLV phụ trách, các thứ và giờ mở lớp. |
| `GET /api/sessions`, `GET /api/sessions/{id}` | Theo phạm vi | Buổi tập gồm loại, giờ, HLV, lớp hoặc học viên, trạng thái. Danh sách hỗ trợ `from`, `to` và `q` để lọc khoảng ngày và tên buổi. |
| `GET /api/sessions/events` | ADMIN, lễ tân, HLV, học viên | Luồng SSE gửi `ready` lúc kết nối và `calendar` khi lịch có thay đổi. App tải lại `GET /api/sessions` với ngày đang xem để áp dụng đúng phạm vi của tài khoản. |
| `GET /api/pt/availability` | Học viên | Nhận `packageId`, `startAt`. Trả khả dụng tại thời điểm đọc. |
| `POST /api/pt/bookings` | Học viên | Nhận `packageId`, `startAt`. Trả lịch `PENDING_TRAINER`. |
| `POST /api/pt/direct` | ADMIN | Xếp buổi PT đã xác nhận và gửi ngay thông báo cho học viên, HLV. |
| `GET /api/pt/requests`, `POST /api/pt/{id}/confirm`, `POST /api/pt/{id}/reject` | HLV phụ trách | Duyệt trạng thái đang chờ, lưu lý do khi từ chối. |
| `POST /api/sessions/{id}/cancel` | Theo vai trò, buổi và mốc giờ | Nhận `reason` và trả trạng thái sau hủy. Buổi đang cập nhật trả `409 SESSION_UPDATE_PENDING`. |
| `PUT /api/sessions/{id}/trainer` | ADMIN | Nhận `trainerId`, `reason`. Kiểm ca làm, giờ trùng và lượt điểm danh trước khi đổi HLV cho một buổi tương lai. |
| `GET /api/sessions/{id}/trainer` | Người có quyền xem buổi | Trả ID và tên hiển thị của HLV đang dạy buổi đó. |

## Điểm danh và hoàn lượt

| Phương thức và đường dẫn | Quyền | Dữ liệu chính |
| --- | --- | --- |
| `POST /api/checkins/preview` | ADMIN hoặc lễ tân | Nhận code, codeKind và packageId nếu đã chọn gói. Trả học viên, buổi gợi ý, gói và lượt hiện tại. Nếu tra theo mã hội viên và chưa có gói, trả danh sách gói có thể chọn. |
| `POST /api/checkins/confirm` | ADMIN hoặc lễ tân | Nhận `sessionId`, `packageId`, `method` và header `Idempotency-Key`. Kiểm lại dữ liệu rồi ghi. |
| `POST /api/checkins/manual` | ADMIN hoặc HLV phụ trách | Nhận `sessionId`, `packageId`, `reason` và `Idempotency-Key`. |
| `GET /api/checkins`, `GET /api/checkins/{id}` | Theo phạm vi | Lọc `learnerId`, `sessionId` hoặc ngày. Học viên mặc định chỉ thấy lượt của mình. |
| `POST /api/checkins/{id}/adjustments` | ADMIN có quyền điều chỉnh | Nhận `reason` và `Idempotency-Key`. Hoàn lượt theo lượt gốc và ghi lịch sử. |

Xem trước chỉ đọc dữ liệu. codeKind nhận QR, PACKAGE_CODE hoặc MEMBER_CODE. Khi cần chọn gói, client gửi lại lượt xem trước với packageId đã chọn. Trong xác nhận, `method` là `QR` hoặc `CODE_ENTRY`. Điểm danh thủ công dùng `MANUAL` và lưu lý do. Danh tính học viên suy ra từ gói, còn nhân viên thao tác suy ra từ phiên. Server luôn kiểm quyền và dữ liệu lần nữa khi xác nhận.

```json
{
  "id": 501,
  "sessionId": 213,
  "packageId": 89,
  "status": "RECORDED",
  "checkedInAt": "2026-11-18T16:52:00+07:00",
  "remainingAfterEvent": 2
}
```

Ghi mới trả `201`. Gửi lại cùng khóa và nội dung trả kết quả thao tác gốc. Nếu lượt đã tồn tại từ lần quét khác, trả `409 ALREADY_CHECKED_IN` kèm thời điểm và dữ liệu đủ để hiển thị. App giữ khóa khi thử lại cùng thao tác sau timeout. Thao tác mới dùng khóa mới.

Khi hủy hoặc đổi HLV, Schedule lưu yêu cầu trước, sau đó đợi Check-in kiểm tra lượt đang xử lý của cùng buổi. Lượt điểm danh đã ghi làm yêu cầu cập nhật trả `409 CHECKIN_EXISTS`. Khi Check-in tạm mất kết nối, Schedule giữ yêu cầu và tự thử lại. App có thể đọc trạng thái hiện tại của buổi sau khi kết nối trở lại.

## Nhận xét và đánh giá

| Phương thức và đường dẫn | Quyền | Dữ liệu chính |
| --- | --- | --- |
| `GET /api/notes/session/{id}`, `PUT /api/notes/session/{id}` | Ghi bởi HLV phụ trách, xem theo phạm vi | Nội dung, nhận xét, điểm PT và giờ cập nhật. |
| `GET /api/notes?learnerId=...` | ADMIN, HLV phụ trách, học viên của chính mình | Danh sách đánh giá theo thứ tự thời gian. |
| `GET /api/goals?learnerId=...`, `PUT /api/goals/{learnerId}` | Ghi bởi HLV phụ trách, xem theo phạm vi | Mục tiêu theo tháng, phần trăm và trạng thái `NOT_STARTED`, `IN_PROGRESS`, `ACHIEVED`, `NOT_ACHIEVED`. Nếu gửi từ client cũ thiếu `status`, máy chủ suy ra từ phần trăm. |
| `GET /api/notifications` | Tài khoản đăng nhập | Hộp thư theo người nhận. Gồm nhắc đánh giá PT của HLV và nhắc buổi nhóm trong 24 giờ tới theo đăng ký lớp 10 hoặc gói lớp 30. |

## Lỗi và hợp đồng nội bộ

| HTTP | Ví dụ mã lỗi | Xử lý phía app |
| --- | --- | --- |
| 400 | `INVALID_INPUT` | Hiện lỗi ngay cạnh trường cần sửa. |
| 401 | `BAD_CREDENTIALS`, `SESSION_EXPIRED` | Yêu cầu kiểm tra thông tin hoặc đăng nhập lại. |
| 403 | `ACCOUNT_LOCKED`, `PASSWORD_CHANGE_REQUIRED`, `FORBIDDEN` | Hiện lý do phù hợp hoặc chuyển màn đổi mật khẩu. |
| 404 | `RESOURCE_NOT_FOUND`, `INVALID_CODE` | Hiện thông báo theo ngữ cảnh. |
| 409 | `TRAINER_BUSY`, `LEARNER_BUSY`, `WRONG_PACKAGE`, `PACKAGE_EXPIRED`, `PACKAGE_EXHAUSTED`, `CLASS_FULL`, `ALREADY_CHECKED_IN`, `ALREADY_ADJUSTED`, `IDEMPOTENCY_CONFLICT` | Làm mới dữ liệu hoặc xử lý đúng trạng thái đã ghi. |
| 503 | `DEPENDENCY_UNAVAILABLE` | Báo tạm thời, cho thử lại cùng khóa nếu đang chờ kết quả ghi. |

Lỗi có `code`, `message`, `requestId` và `details` khi cần. App quyết định hành vi theo `code`. Backend kiểm phạm vi theo tài khoản và dữ liệu thực.

API nội bộ cung cấp kiểm tra phiên từ Auth, xác minh gói đặt lịch từ Check-in và kiểm tra buổi hợp lệ từ Schedule. Dữ liệu trả về đủ cho quyết định nghiệp vụ, gồm trạng thái, chủ sở hữu, HLV/lớp và ID gói của buổi PT. Đường dẫn nội bộ nằm dưới `/internal/` và có xác thực giữa các service.

[Mô hình dữ liệu](DATA_MODEL.md) · [Phạm vi](SCOPE.md)
