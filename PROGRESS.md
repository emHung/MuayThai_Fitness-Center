# Tiến độ bàn giao MuayThai _Fitness Center

**████████░░░░ 4/6 mốc hoàn thành**

Thanh tiến độ đếm các mốc bàn giao đã kiểm chứng. Phạm vi và kết quả theo từng yêu cầu nằm trong [bảng FR01–FR59](docs/NGHIEM_THU_V2_3.md).

| Mốc | Trạng thái | Bằng chứng |
| --- | --- | --- |
| Kiến trúc và mẫu giao diện | Hoàn thành | [Kiến trúc](docs/ARCHITECTURE.md) và [chuẩn giao diện](docs/UI.md). |
| Backend Docker và PostgreSQL | Hoàn thành | Các dịch vụ và gateway đã khởi động thành công. |
| APK Android Java | Hoàn thành | Build APK debug thành công và xác minh chữ ký debug. |
| Kiểm thử tích hợp các luồng chính | Hoàn thành | [Bộ kiểm thử API](tests/acceptance.mjs) kiểm ba vai trò, hủy đồng thời với điểm danh, đổi HLV và đồng bộ lịch. |
| Nghiệm thu v2.3 | Đang làm | [Bảng 59 yêu cầu](docs/NGHIEM_THU_V2_3.md) ghi các chức năng và ca kiểm thử còn mở. |
| Nghiệm thu điện thoại và đóng gói bản nộp | Đang làm | Cần thử camera QR, đồng bộ nhiều máy và kiểm tra bản dùng để nộp. |

## Nhật ký cập nhật

| Ngày | Cập nhật |
| --- | --- |
| 2026-11-17 | Ghi nhận bản demo đầu tiên trên GitHub và đặt lịch cập nhật lúc 18:00. Backend đã kiểm qua các luồng chính, APK debug đã build và bảng nghiệm thu có đủ 59 mục cần đối chiếu. |
| 2026-11-17 | Kiểm thử trên emulator với ba vai trò, từ HLV duyệt PT đến quản trị viên điểm danh bằng mã và trừ lượt gói. Android lint có 0 lỗi. SonarQube đã quét backend và đạt Quality Gate sau khi nhập báo cáo JaCoCo của bộ kiểm thử Docker. |
| 2026-11-17 | Chuẩn bị pilot doanh nghiệp. Đã tách quyền lễ tân, kiểm thử cấp lại mật khẩu và thu hồi phiên, dựng stack production thử nghiệm với quyền PostgreSQL riêng, xác minh APK ký bằng khóa QA và khôi phục thử bản sao demo. SonarQube đạt Quality Gate với 81,0% độ phủ mã mới, có đo ca lưu trữ hồ sơ trên stack production thử nghiệm. [Hướng dẫn production](docs/PRODUCTION.md). |

Khi sửa hoặc xác nhận một chức năng, thêm mục theo ngày và gắn ca kiểm thử hoặc tệp liên quan. Cập nhật thanh tiến độ khi một mốc có đủ bằng chứng.
