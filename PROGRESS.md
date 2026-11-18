# Tiến độ bàn giao MuayThai & Fitness Center

**██████████░░ 5/6 mốc hoàn thành — chờ nghiệm thu thiết bị cuối cùng**

Tiến độ tính theo mốc bàn giao, không phải phần trăm hoàn thiện từng chức năng. [Bảng FR01–FR59](docs/NGHIEM_THU_V2_3.md) phân biệt kết quả kiểm thử hệ thống với xác nhận trên thiết bị sử dụng.

| Mốc | Trạng thái | Kết quả |
| --- | --- | --- |
| Kiến trúc và giao diện | Hoàn thành | Một app cho ADMIN, RECEPTION, TRAINER, LEARNER; [kiến trúc](docs/ARCHITECTURE.md), [giao diện](docs/UI.md). |
| Backend Docker và PostgreSQL | Hoàn thành | Gateway và bốn dịch vụ nghiệp vụ hoạt động; có migration, sao lưu và cấu hình database theo môi trường. |
| APK Android Java | Hoàn thành | Có bản cài đang sử dụng; đã đóng gói thêm ứng viên release 0.1.8/code 9 ký riêng qua HTTPS. |
| Kiểm thử tích hợp các luồng chính | Hoàn thành | Bốn vai trò, gói, lịch, QR/điểm danh, quyền, thông báo và đồng bộ nhiều phiên qua API đạt. |
| Nghiệm thu nghiệp vụ v2.3 ở lớp hệ thống | Hoàn thành | Bộ hồi quy và bộ nghiệm thu bổ sung đạt: sức chứa, tranh chấp, mốc hủy lịch, đổi lịch hết hạn, điểm danh/hoàn lượt, đánh giá/mục tiêu và giới hạn quyền. [Kiểm thử](docs/TESTING.md). |
| Nghiệm thu thiết bị và chốt bộ cài bàn giao | Chờ xác nhận cuối | Người sử dụng đã xác nhận quét QR và xác nhận điểm danh thành công trên điện thoại. Cần đối chiếu lượt gói và cập nhật trên thiết bị khác; kiểm tra cài ứng viên release trước khi chốt. |

## Bản cài và cách triển khai

- Bản đang phát hành công khai: **0.1.7/code 8**, chuỗi ký debug hiện có, có cập nhật trong app.
- Ứng viên bàn giao: **release 0.1.8/code 9**, đã build, lint và xác minh chữ ký riêng; chưa phát hành thay thế chuỗi debug và chưa chốt nghiệm thu cài trên điện thoại.
- Không cài đè hai chuỗi ký khác nhau. Kế hoạch chuyển bản cài và trách nhiệm giữ khóa được ghi trong [hướng dẫn bàn giao](docs/HANDOVER.md).
- Bản sửa quyền backend đã được áp dụng sau sao lưu; đường HTTPS và đăng nhập được kiểm tra lại.

## Các điểm đã kiểm tra bổ sung

- Tranh suất cuối của lớp 10/lớp 30 chỉ cho một yêu cầu hợp lệ; yêu cầu bị từ chối không làm mất lượt gói.
- Gói sai lớp, gói hết hạn/hết lượt, tài khoản khóa và truy cập ngoài quyền bị chặn.
- Từ chối/hết hạn yêu cầu đổi PT giữ nguyên lịch cũ; mốc hủy PT 12 giờ và lớp 10 3 giờ được kiểm tra hai phía.
- Ghi đánh giá sau buổi, ghi vắng, điểm danh thủ công, hoàn lượt và phạm vi xem lịch sử được đối chiếu.
- Thay HLV, chỉnh/dừng lớp, mở/đóng giờ và thông báo hai phía đã có ca kiểm tra.
- Kết nối SSE ở ba phiên theo vai trò đạt; kết quả này không thay thế xác nhận sử dụng đồng thời trên các thiết bị thật.
- Android release lint: **0 lỗi, 11 cảnh báo hiện hữu**. Kiểm thử cấu hình triển khai riêng đạt; không suy ra môi trường đang phục vụ là đã chuyển sang production.

## Điều kiện chốt mốc 6

1. Xác nhận lượt gói sau điểm danh giảm đúng một lần và không giảm lần nữa khi gửi trùng.
2. Xác nhận tài khoản liên quan trên thiết bị khác thấy cập nhật lịch/điểm danh đúng quyền.
3. Cài và đăng nhập ứng viên release trên thiết bị dùng bàn giao, thống nhất chuyển chuỗi ký; lưu giữ khóa phát hành ngoài Git.

Không đánh dấu 6/6 chỉ từ việc build thành công. Cài máy mới theo [SETUP_NEW_MACHINE.md](docs/SETUP_NEW_MACHINE.md); cấu trúc [Android](android/README.md), [backend](backend/README.md).
