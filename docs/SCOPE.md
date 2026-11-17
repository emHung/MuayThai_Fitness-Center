# Phạm vi và nghiệp vụ

Mốc triển khai đầu tiên bám theo prototype Giai đoạn 1 Tối giản. Mọi thao tác được đưa vào demo phải đi từ app Android qua API tới cơ sở dữ liệu. Dữ liệu thử có thể tạo sẵn bằng bộ seed riêng.

## Người dùng và quyền

| Vai trò | Quyền trong bản đầu |
| --- | --- |
| ADMIN/Sale | Quản lý học viên, HLV, gói, lớp 30, lịch và điểm danh tại quầy. |
| HLV | Xem lịch và học viên được phân công, xử lý yêu cầu PT, điểm danh thủ công và ghi đánh giá buổi mình phụ trách. |
| Học viên | Xem lịch, gói, QR, kết quả và lịch sử của chính mình. Đặt PT với HLV phụ trách gói. |

Giữ một vai trò ADMIN theo đặc tả. Thao tác điều chỉnh điểm danh yêu cầu quyền riêng trong vai trò đó. Quyền sở hữu và quyền thao tác được kiểm tại service. Tài khoản bị khóa mất quyền truy cập từ yêu cầu được xác thực tiếp theo. HLV dùng mật khẩu tạm phải đổi trước khi truy cập chức năng.

## Chức năng bắt buộc của mốc đầu

- **Tài khoản và hồ sơ** · Đăng nhập, đăng xuất, cập nhật thông tin, đổi mật khẩu, tạo và khóa/mở khóa tài khoản, tạo hồ sơ học viên và HLV. Tìm kiếm cơ bản trong danh sách theo quyền khi màn có ô tìm.
- **Gói và QR** · ADMIN gán gói PT, lớp 30 hoặc tự do như trên biểu mẫu HTML. Học viên xem thẻ và hạn của từng gói. Điểm danh theo buổi áp dụng cho PT và lớp 30, còn ghi lượt tập tự do thuộc Giai đoạn 2. QR định danh một gói. Mã hội viên dùng để tra hồ sơ, sau đó chọn gói cụ thể trước khi ghi điểm danh.
- **Lịch lớp 30** · ADMIN tạo và dừng lịch lặp. Hệ thống sinh buổi theo quy tắc lặp mà không sinh trùng. HLV và học viên xem buổi thuộc phạm vi của mình. Áp giới hạn 30 lượt check-in.
- **Lịch PT** · Học viên chọn gói, ngày và giờ. Schedule kiểm trùng HLV cùng trùng lịch của học viên khi ghi. HLV duyệt hoặc từ chối yêu cầu của mình. Học viên được hủy PT ít nhất 12 giờ trước giờ bắt đầu. HLV phụ trách và ADMIN ghi lý do khi hủy. Hủy hợp lệ chưa phát sinh điểm danh giữ nguyên số lượt.
- **Điểm danh** · Sale quét hoặc nhập mã, kiểm tra thông tin và xác nhận. HLV/ADMIN ghi nhận thủ công trong phạm vi được phân công. Lịch sử hiển thị theo quyền, kể cả lượt đã được điều chỉnh.
- **Hành trình** · HLV lưu nội dung/nhận xét, đánh giá PT và xem lịch sử học viên được phân công. Học viên xem kết quả của chính mình.

Mốc đầu dùng danh sách FR01–05, FR07–08, FR10–13, FR17–18, FR20, FR30–37, FR40 và FR48–50 của lộ trình. FR21, FR39 và FR41 là phần bắt buộc của demo. FR06 được triển khai theo từng danh sách có tìm kiếm. Luồng điều chỉnh điểm danh là phần bổ sung để demo xử lý được thao tác nhầm. Các FR làm một phần ở mốc đầu tiếp tục được hoàn thiện theo đặc tả.

## Quy tắc ghi điểm danh

1. Quét hoặc nhập mã để tạo bản xem trước gồm học viên, buổi, gói và số lượt hiện tại. Với PT, gói quét phải trùng gói của buổi đã xác nhận. Với lớp 30, gói phải dùng được cho đúng lớp. Cửa sổ của bản đầu mở từ 30 phút trước giờ bắt đầu đến giờ kết thúc buổi.
2. Khi Sale xác nhận, kiểm lại quyền, giờ, trạng thái buổi, hạn gói và số lượt. Ghi lượt điểm danh và cập nhật gói trong cùng transaction. Ghi nhận thủ công dùng cùng nguyên tắc và lưu người thực hiện cùng lý do.
3. Quét lại hoặc gửi lại sau khi mất mạng trả trạng thái đã ghi, thời điểm và số lượt nhất quán. Gói sai, hết hạn hoặc không còn lượt giữ nguyên dữ liệu.
4. Khi được phép điều chỉnh, lưu lượt gốc, người điều chỉnh, lý do, thời điểm và số lượt trước/sau. Một lượt điểm danh đã trừ buổi có thể được hoàn một lần. Kiểm thử cả hai thao tác hoàn gửi đồng thời.

## Phạm vi tiếp theo

- **Giai đoạn 2** · Lớp 10, trao đổi để đổi lịch PT, walk-in/tự do, quy tắc check-in nâng cao, thông báo trong app và chăm sóc học viên.
- **Giai đoạn 3** · Ca làm, lưới giờ khả dụng, lịch cố định, tìm kiếm toàn hệ thống, mục tiêu tháng và cập nhật gần thời gian thực.
- **Sĩ số lớp 10** · Chốt 10 là giới hạn cứng. Schedule quản lý suất đặt trước và walk-in. Check-in dùng lại suất đã giữ. Việc kiểm và cấp suất cuối thực hiện trong một transaction. Thời điểm giải phóng suất giữ trước cần được trung tâm chốt trước khi triển khai lớp 10.

Đặc tả đủ 59 FR vẫn là đích nghiệp vụ. Mốc đầu được dùng để xây bản Giai đoạn 1 hoàn chỉnh. Nếu buổi nộp yêu cầu đủ 59 FR, cần cập nhật phạm vi từng hạng mục và lập lại lịch giao.

## Quyết định còn mở

- Chốt với giảng viên phạm vi buổi nộp và cách tính số service. Kiến trúc hiện gồm bốn service nghiệp vụ và gateway.
- Xác định quy trình khi cần hủy một buổi đã có check-in, gồm quyền xử lý và việc điều chỉnh số lượt đã dùng.
- Chốt thời gian giữ suất đã đăng ký của lớp 10 trước khi mở cho walk-in.

[Nguồn nghiệp vụ v2.3](Dac_ta_v2.docx) · [Tiêu chí nghiệm thu](IMPLEMENTATION.md)
