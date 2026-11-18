# Hướng dẫn chốt bộ cài bàn giao

## Thành phần bàn giao

- Mã nguồn trên repository hiện tại, [hướng dẫn máy mới](SETUP_NEW_MACHINE.md) và tài liệu cấu trúc [Android](../android/README.md)/[backend](../backend/README.md).
- APK theo chuỗi ký đã thống nhất, mã SHA-256 đối chiếu tệp và hướng dẫn cài đặt.
- Cấu hình vận hành, tài khoản quản lý và khóa ký được chuyển qua kênh bảo mật riêng nếu có bàn giao quyền quản lý. Các giá trị này không nằm trong Git hoặc bộ APK.

## Phân biệt hai chuỗi ký

| Loại | Mục đích | Cài đè |
| --- | --- | --- |
| APK debug hiện hành trên Releases | Chuỗi dùng thử đang được sử dụng, có kiểm tra cập nhật GitHub | Chỉ cài đè bằng APK cùng chữ ký debug hiện có. |
| APK release ký riêng | Chuỗi phát hành production, cần đóng gói và nghiệm thu theo khóa được bàn giao | Cần thiết bị cài mới hoặc kế hoạch chuyển đổi; không cài đè APK debug khác chữ ký. |

Không tự gỡ app trên điện thoại đang dùng để xử lý lỗi chữ ký. Dữ liệu nghiệp vụ ở máy chủ cần được sao lưu; việc gỡ app có thể làm mất phiên và thiết lập cục bộ. Xem [quy tắc ký ứng dụng của Android](https://developer.android.com/studio/publish/app-signing).

## Kiểm tra trước khi ký nhận

1. Đối chiếu đúng tên tệp, phiên bản/code và checksum của gói được giao.
2. Xác nhận máy chủ, HTTPS, tài khoản và môi trường dữ liệu dùng bàn giao. Có APK release không tự chuyển database demo thành production.
3. Cài trên thiết bị đã thống nhất, đăng nhập/đổi mật khẩu tạm, kiểm quyền theo vai trò.
4. Cấp/xem gói, đặt/duyệt lịch, quét và xác nhận điểm danh; kiểm số lượt, lịch sử và thiết bị khác.
5. Thử gửi trùng, mất mạng/nối lại và trở về app. Kiểm cập nhật bằng bản cùng chuỗi ký.
6. Chốt người giữ khóa ký, nơi sao lưu và trách nhiệm duy trì máy chủ. Người dùng cần được biết máy chủ phải hoạt động để app sử dụng được.

## Hiện trạng

Người sử dụng đã xác nhận quét QR và xác nhận điểm danh thành công trên điện thoại thật. Các ca nghiệp vụ tự động và cấu hình triển khai đã đạt. Bản công khai 0.1.8/code 9 đã cài đè 0.1.7, giữ dữ liệu app và đăng nhập/đăng xuất HTTPS trên emulator. Backend đã được sao lưu, cập nhật và kiểm tra lại. Còn xác nhận đồng bộ ở thiết bị sử dụng; vì vậy mốc chốt bàn giao còn mở trong [PROGRESS.md](../PROGRESS.md).

APK hiện hành trên GitHub vẫn dùng chuỗi debug đã có. Nếu chọn chuỗi production ký riêng, cần đóng gói và nghiệm thu riêng trước khi thay thế. Khóa ký không được đính kèm vào tài liệu hoặc kho mã nguồn.
