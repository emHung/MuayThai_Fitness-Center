# Cập nhật 0.1.8

## Thao tác hằng ngày

- Không hủy hoặc đổi HLV của buổi đã từng có điểm danh, kể cả khi đã hoàn lượt. Hoàn lượt vẫn giữ lịch sử và khôi phục số buổi của gói.
- Chi tiết buổi đã hủy/từ chối hiển thị lý do đã nhập.
- ADMIN có thể nhập mật khẩu khi thêm học viên, lễ tân hoặc HLV. Bỏ trống để hệ thống tự sinh. Ô nhập nêu rõ tối thiểu 8 ký tự, tối đa 72 byte UTF-8.
- Hộp mật khẩu mới/cấp lại có **Sao chép** và một nút **Đóng**. Clipboard được đánh dấu nhạy cảm trên Android hỗ trợ.
- Xóa học viên/lễ tân sẽ khóa đăng nhập, thu hồi phiên và ẩn danh bạ. Gói, lịch, điểm danh và nhận xét vẫn được giữ; buổi tập không tự hủy.
- Gán gói lớp 10 bằng cách chọn lớp đang mở; HLV lấy từ lớp đó. App tiếp tục gán lịch cố định trong cùng thao tác. Nếu gán lịch lỗi, chỉ thử lại phần lịch, không tạo gói lần nữa. Có thể gán gói bắt đầu trong tương lai.
- Chi tiết đề xuất lớp hiển thị các thứ, giờ và HLV để ADMIN duyệt. HLV xem lại lớp đã đề xuất tại **Lớp 10**.
- Ô giờ PT đã chọn có viền nhấn mạnh. Khi mở bàn phím, nội dung cuộn để giữ ô nhập trong vùng nhìn thấy và tạm ẩn thanh điều hướng dưới.

## Ẩn các mục đã kết thúc

Nhấn giữ buổi đã hủy/từ chối, yêu cầu đổi lịch bị từ chối/hết hạn hoặc đề xuất lớp bị từ chối, rồi chọn **Ẩn**. Có **Hoàn tác** ngay sau thao tác; mở lại danh sách có nút **Hiện lại các mục đã ẩn**.

Lựa chọn ẩn thuộc từng tài khoản trên thiết bị hiện tại, không xóa dữ liệu server. Đổi thiết bị hoặc xóa dữ liệu app không giữ lựa chọn này.

## Kiểm tra

- Bộ API hồi quy, ảnh và nghiệm thu bổ sung đạt trên database QA riêng.
- Kiểm thêm mật khẩu tự nhập cho ba vai trò, quyền/xóa mềm tài khoản, giữ lịch sử, lớp 10 đúng HLV, gói tương lai và hủy sau hoàn lượt.
- Production smoke đạt với migration và quyền PostgreSQL.
- Android build/lint đạt: 0 lỗi, 11 cảnh báo hiện hữu. Hai APK có version 0.1.8/code 9 và cùng chữ ký debug hiện hành.
- Emulator đã kiểm tra: che mật khẩu/lỗi inline, Sao chép/Đóng, cấp lại/xóa tài khoản, chi tiết đề xuất, gán lớp 10, viền giờ PT và ẩn/hoàn tác/hiện lại. Bàn phím đạt ở màn 720×1280, 720×1640 và cỡ chữ 130%.
- APK HTTPS đã cài đè 0.1.7 lên 0.1.8, giữ dữ liệu app và đăng nhập/đăng xuất thành công. [CI](https://github.com/emHung/MuayThai_Fitness-Center/actions/runs/44400005402) đạt. Xác nhận lại trên điện thoại sử dụng vẫn cần khi cập nhật.

## Cài bản cập nhật

Điện thoại dùng `app-debug-tailscale.apk`; emulator dùng `app-debug.apk`. Cài đè đúng bản cùng chữ ký, không cần gỡ app. Máy chủ HTTPS phải đang hoạt động. Hai APK trên kênh dùng thử vẫn là bản debug; không đổi sang chuỗi ký production.
