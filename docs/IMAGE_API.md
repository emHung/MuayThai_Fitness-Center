# API ảnh

Mọi endpoint yêu cầu Bearer token và phiên đang hoạt động. Ảnh được lưu trong database của service sở hữu.

| Tài nguyên | Endpoint | Quyền ghi |
| --- | --- | --- |
| Avatar cá nhân | `/api/users/me/image` | Chính chủ |
| Avatar theo hồ sơ | `/api/users/{id}/image` | Quản trị viên |
| Ảnh loại gói | `/api/packages/types/{type}/image` | Quản trị viên |
| Ảnh lớp | `/api/{classes10\|classes30}/{id}/image` | Quản trị viên |

GET trả JPEG, ETag và Cache-Control private, no-cache, phải kiểm tra quyền kể cả khi ảnh có trong cache. Avatar đọc theo quyền hồ sơ. Ảnh lớp đọc theo quyền xem lớp. Ảnh loại gói dành cho người dùng đã đăng nhập.

PUT nhận multipart với trường `file`, trả JSON chứa `version`. DELETE trả HTTP 204 và có thể gọi lại. GET khi chưa có ảnh trả 404. Giới hạn đầu vào 5 MiB, ảnh PNG/JPEG, tối đa 24 triệu pixel, thu nhỏ cạnh dài nhất về 1280 pixel và mã hóa lại JPEG. Có thể chỉnh bằng cấu hình multipart và `app.images.max-pixels`, `app.images.max-edge`.

Lỗi dùng HTTP 400 cho ảnh hỏng, 413 khi vượt giới hạn, 415 cho định dạng không hỗ trợ, 403 khi thiếu quyền và 401 khi phiên không hợp lệ.
