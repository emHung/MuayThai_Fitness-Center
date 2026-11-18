# Tự tải giờ trống khi đổi ngày — 0.1.9

Màn đặt PT của học viên và màn xếp lịch PT của ADMIN tự gọi lại danh sách giờ trống ngay khi chọn ngày khác. Không cần bấm **Cập nhật giờ trống** sau mỗi lần đổi ngày.

- Xóa lựa chọn giờ và viền chọn cũ khi tải ngày/HLV mới; ADMIN cần chọn giờ cho ngày đang xem trước khi xác nhận.
- Phản hồi của lần tải cũ không ghi đè danh sách hoặc hiển thị lỗi lên lần chọn mới.
- Giữ nút **Cập nhật giờ trống** để chủ động tra lại cùng ngày khi có thay đổi từ người khác.
- Giữ cách nhập giờ qua hộp chọn giờ của ADMIN; ô giờ trống vẫn mở được hộp chọn.

Thay đổi nằm ở thành phần dùng chung `PtSlotGrid` và hai màn gọi nó; không đổi API, quy tắc đặt PT hay database.

## Kiểm chứng

- Tái hiện trên APK QA 0.1.8: đổi ngày nhưng giờ 12:00 vẫn giữ trạng thái bận của ngày cũ nếu không bấm cập nhật.
- APK 0.1.9: ADMIN đổi qua lại hai ngày có trạng thái 12:00 khác nhau, danh sách tự đổi đúng; giờ 14:00 đã chọn bị xóa khi đổi ngày. Không xác nhận được khi chưa chọn lại giờ; hộp chọn giờ vẫn mở được từ ô trống.
- Học viên chọn ngày khác, đặt PT lúc 12:00, quay lại và đổi ngày mà không bấm cập nhật: giờ vừa đặt hiển thị không khả dụng.
- Android build/lint đạt: 0 lỗi, 11 cảnh báo hiện hữu. Hai APK 0.1.9/code 10 giữ chữ ký debug hiện hành để cài đè. Kiểm thử UI chạy trên emulator và database QA riêng.

Điện thoại cài `app-debug-tailscale.apk` từ Releases; backend hiện tại tiếp tục sử dụng bình thường.
