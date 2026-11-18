# Tiến độ bàn giao MuayThai & Fitness Center

**██████████░░ 5/6 mốc hoàn thành — chờ nghiệm thu thiết bị cuối cùng**

Tiến độ tính theo mốc bàn giao, không phải phần trăm hoàn thiện từng chức năng. [Bảng FR01–FR59](docs/NGHIEM_THU_V2_3.md) phân biệt kết quả kiểm thử hệ thống với xác nhận trên thiết bị sử dụng.

| Mốc | Trạng thái | Kết quả |
| --- | --- | --- |
| Kiến trúc và giao diện | Hoàn thành | Một app cho ADMIN, RECEPTION, TRAINER, LEARNER; [kiến trúc](docs/ARCHITECTURE.md), [giao diện](docs/UI.md). |
| Backend Docker và PostgreSQL | Hoàn thành | Gateway và bốn dịch vụ nghiệp vụ hoạt động; có migration, sao lưu và cấu hình database theo môi trường. |
| APK Android Java | Hoàn thành | 0.1.9/code 10 đã build/lint và xác minh chữ ký hiện hành; thử đặt PT và tự tải giờ khi đổi ngày trên emulator. |
| Kiểm thử tích hợp các luồng chính | Hoàn thành | Bốn vai trò, gói, lịch, QR/điểm danh, quyền, thông báo và đồng bộ nhiều phiên qua API đạt. |
| Nghiệm thu nghiệp vụ v2.3 ở lớp hệ thống | Hoàn thành | Bộ hồi quy và bộ nghiệm thu bổ sung đạt: sức chứa, tranh chấp, mốc hủy lịch, đổi lịch hết hạn, điểm danh/hoàn lượt, đánh giá/mục tiêu và giới hạn quyền. [Kiểm thử](docs/TESTING.md). |
| Nghiệm thu thiết bị và chốt bộ cài bàn giao | Chờ xác nhận cuối | Người sử dụng đã xác nhận quét QR và xác nhận điểm danh thành công trên điện thoại. Cài đè/đăng nhập/đăng xuất APK 0.1.8 trên emulator đạt. Còn xác nhận đồng bộ trên thiết bị sử dụng. |

## Bản cài và cách triển khai

- Bản cập nhật: [**0.1.9/code 10**](https://github.com/emHung/MuayThai_Fitness-Center/releases/tag/v0.1.9), cùng chuỗi ký debug hiện có, có cập nhật trong app và checksum đối chiếu tệp.
- Điện thoại dùng `app-debug-tailscale.apk`; emulator dùng `app-debug.apk`. Cài đè cùng chữ ký, không cần gỡ app. Chuỗi production ký riêng cần đóng gói/nghiệm thu riêng nếu chọn để bàn giao.
- Không cài đè hai chuỗi ký khác nhau. Kế hoạch chuyển bản cài và trách nhiệm giữ khóa được ghi trong [hướng dẫn bàn giao](docs/HANDOVER.md).
- Backend và migration 0.1.8 đã áp dụng sau sao lưu; HTTPS, đăng nhập, đọc lịch và đăng xuất được kiểm tra lại.

## Cập nhật 0.1.8 — 19/11/2026

- Chặn hủy buổi đã từng điểm danh, kể cả sau hoàn lượt; hiện lý do hủy.
- Mật khẩu tự nhập/tự sinh, sao chép khi cấp lại; xóa mềm học viên/lễ tân, giữ lịch sử.
- Gán gói lớp 10 theo lớp đang mở và HLV của lớp; chi tiết thứ/giờ cho đề xuất; chọn giờ PT có viền rõ.
- Sửa vùng nhập khi mở bàn phím; nhấn giữ mục đã kết thúc để ẩn, hoàn tác và hiện lại theo tài khoản trên thiết bị.
- [CI mã nguồn 0.1.8](https://github.com/emHung/MuayThai_Fitness-Center/actions/runs/44400005402) đạt cả Android và backend. API hồi quy, ảnh, nghiệm thu bổ sung và production smoke đạt. Lint: 0 lỗi, 11 cảnh báo hiện hữu.
- Emulator đã kiểm tra form/copy/cấp lại/xóa, gán lớp 10, chọn giờ, ẩn/hoàn tác/hiện lại, màn nhỏ/màn cao và chữ 130%; cài đè APK 0.1.7 giữ dữ liệu app. [Chi tiết](docs/UI_UPDATE_0.1.8.md).

Giữ **5/6 mốc** đến khi xác nhận đồng bộ trên thiết bị sử dụng; không thay xác nhận điện thoại thật bằng kết quả emulator.

## Tự tải giờ khi đổi ngày — 0.1.9

Đã tái hiện lỗi ở 0.1.8 và kiểm tra bản sửa cho ADMIN/học viên: đổi ngày tự tải đúng giờ trống, bỏ giờ đã chọn cũ và hiển thị giờ vừa đặt là không khả dụng khi quay lại. Build/lint đạt 0 lỗi, 11 cảnh báo hiện hữu; giữ chữ ký cài đè. [CI 0.1.9](https://github.com/emHung/MuayThai_Fitness-Center/actions/runs/44409598080) đạt Android và backend. Không thay backend/database trong lượt này. [Báo cáo 0.1.9](docs/UI_UPDATE_0.1.9.md).

## Các điểm đã kiểm tra bổ sung

- Tranh suất cuối của lớp 10/lớp 30 chỉ cho một yêu cầu hợp lệ; yêu cầu bị từ chối không làm mất lượt gói.
- Gói sai lớp, gói hết hạn/hết lượt, tài khoản khóa và truy cập ngoài quyền bị chặn.
- Từ chối/hết hạn yêu cầu đổi PT giữ nguyên lịch cũ; mốc hủy PT 12 giờ và lớp 10 3 giờ được kiểm tra hai phía.
- Ghi đánh giá sau buổi, ghi vắng, điểm danh thủ công, hoàn lượt và phạm vi xem lịch sử được đối chiếu.
- Thay HLV, chỉnh/dừng lớp, mở/đóng giờ và thông báo hai phía đã có ca kiểm tra.
- Kết nối SSE ở ba phiên theo vai trò đạt; kết quả này không thay thế xác nhận sử dụng đồng thời trên các thiết bị thật.
- Android release lint: **0 lỗi, 11 cảnh báo hiện hữu**. Kiểm thử cấu hình triển khai riêng đạt; không suy ra môi trường đang phục vụ là đã chuyển sang production.

## Điều kiện chốt mốc 6

1. **Đã kiểm:** bộ tự động đối chiếu giảm/hoàn/gửi trùng; đối chiếu dữ liệu gói với lịch sử trên máy chủ hiện tại không phát hiện sai lệch. Gói tập tự do không trừ lượt.
2. **Chờ người sử dụng xác nhận:** tài khoản liên quan trên thiết bị khác thấy cập nhật lịch/điểm danh đúng quyền. SSE nhiều phiên và ứng dụng trên emulator đã có kiểm tra riêng; không ghi thay cho xác nhận này.
3. **Đã chuẩn bị:** bộ release có APK, hướng dẫn và checksum; cài/đăng nhập/đăng xuất trên emulator đạt. Khóa phát hành lưu ngoài Git. Khi chuyển sang điện thoại đang có bản debug cần thực hiện đúng kế hoạch chuỗi ký trong hướng dẫn, không tự cài đè khác khóa.

Không đánh dấu 6/6 chỉ từ việc build thành công. Cài máy mới theo [SETUP_NEW_MACHINE.md](docs/SETUP_NEW_MACHINE.md); cấu trúc [Android](android/README.md), [backend](backend/README.md).
