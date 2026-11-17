# Đối chiếu 59 yêu cầu v2.3

Đối chiếu với [đặc tả v2.3](SCOPE.md). Cột **Kiểm thử** ghi ca cụ thể có trong [bộ kiểm thử API](../tests/acceptance.mjs) hoặc việc cần chạy trên APK. Mỗi ca API chỉ bao phủ hành vi được mô tả tại hàng đó.

| Mã | Phần triển khai | Kiểm thử và phần còn lại |
| --- | --- | --- |
| FR01 | Phiên đăng nhập, đăng xuất, đổi mật khẩu tạm | API kiểm HLV bị chặn trước khi đổi. Thử đăng xuất trên APK theo ba vai trò. |
| FR02 | Phân quyền theo vai trò và theo đối tượng | API kiểm học viên bị chặn khi xóa HLV hoặc đổi người phụ trách. Kiểm thêm phạm vi lịch sử và hồ sơ. |
| FR03 | Hồ sơ học viên, khóa/mở khóa, mã hội viên | API kiểm tạo và sinh mã. Thử sửa và khóa/mở khóa trên APK. |
| FR04 | Hồ sơ HLV, mật khẩu tạm, ca làm, khóa/mở khóa, xóa | API kiểm xóa khi chưa có dữ liệu, thu hồi phiên và tranh chấp với gán ca. |
| FR05 | Cấp gói, chọn HLV/lớp, hạn, lượt và gia hạn qua gói mới | API kiểm cấp nhiều loại gói và bảo toàn mã QR khi đổi HLV. Thử cấp gói thay thế trên APK. |
| FR06 | Tìm theo từ khóa trong danh bạ, lớp và lịch ngày/tuần | API kiểm kết quả tìm lớp và lịch theo phạm vi HLV, học viên và gói đã gán. |
| FR07 | Sửa thông tin cá nhân và đổi mật khẩu | API kiểm đổi mật khẩu HLV. Thử lưu hồ sơ cá nhân trên APK. |
| FR08 | Lớp 30 lặp hàng tuần và sinh lịch | API kiểm tạo lớp và thấy buổi đã sinh. Chạy thêm ca chỉnh/dừng lớp. |
| FR09 | HLV đề xuất lớp 10 theo thứ/giờ | API kiểm trạng thái chờ duyệt và sinh buổi khi mở lớp. |
| FR10 | Khung PT theo ca, xếp trực tiếp, đề xuất đổi lịch | API kiểm xếp trực tiếp và đồng ý đổi giờ. Thử luồng HLV đề xuất trên APK. |
| FR11 | Chặn trùng HLV và học viên theo khoảng giờ | API kiểm đặt PT trùng. Cần kiểm thêm tranh chấp lúc đổi HLV hoặc đổi giờ. |
| FR12 | Lớp 10 giới hạn theo đăng ký. Lớp 30 giới hạn theo lượt ghi | Thử hai yêu cầu tranh chỗ cuối ở mỗi loại lớp. |
| FR13 | Học viên chọn khung PT. HLV duyệt/từ chối | API kiểm gửi và duyệt. Thử ô trống/ô kẹt và từ chối trên APK. |
| FR14 | HLV gửi đề xuất đổi giờ PT | Thử từ màn HLV tới hộp yêu cầu của học viên. |
| FR15 | Đồng ý mới cập nhật lịch cũ | API kiểm lịch cũ giữ nguyên đến lúc duyệt. Chạy thêm ca từ chối và hết hạn. |
| FR16 | Chuyển đăng ký lớp 10 sang buổi khác | API kiểm chuyển đăng ký và đối chiếu hai danh sách buổi. |
| FR17 | Lịch lớp 30 của học viên theo lớp được gán | Thử hai học viên khác lớp và quét QR của từng gói ở quầy. |
| FR18 | ADMIN quản lý lịch lớp 30 và phân công HLV | API kiểm tạo lớp. Thử sửa, hủy và chuyển HLV cho một buổi lớp 30. |
| FR19 | Màn lịch lắng nghe cập nhật SSE và tải dữ liệu theo quyền | API kiểm nhận cập nhật qua gateway với ba vai trò. Cần thử nhiều thiết bị cùng mở lịch trên APK. |
| FR20 | Lịch ngày và tuần | Kiểm chuyển ngày/tuần, cuộn danh sách và trạng thái trống trên APK. |
| FR21 | Hủy PT/lớp 10 theo hạn và hủy buổi có lý do | API kiểm hủy đăng ký và hủy buổi đồng thời với điểm danh. Chạy thêm ca sát mốc 3 giờ, 12 giờ. |
| FR22 | Học viên đề xuất và HLV xác nhận đổi PT | API kiểm đủ hai bước và đối chiếu ngày giờ mới. |
| FR23 | Yêu cầu đổi tự hết hạn | Thử mốc hết hạn trước khi xác nhận và xác minh lịch cũ. |
| FR24 | ADMIN duyệt/từ chối đề xuất lớp 10 | API kiểm duyệt và chặn HLV tự duyệt. Thử từ chối trên APK. |
| FR25 | ADMIN xếp trực tiếp, chọn HLV và khung giờ | API kiểm lịch đã xác nhận và thông báo tức thời cho học viên, HLV khi buổi còn cách hơn một ngày. |
| FR26 | Ca làm theo tuần, quyền xem và xử lý lịch vướng ca | API kiểm lưu ca. Thử sửa/xóa và quyền xem ca HLV trên APK. |
| FR27 | Lưới giờ PT trống, bận và ngoài ca | Thử cách tô ô theo ca, lịch trùng và nơi truy cập theo vai trò. |
| FR28 | Đóng khung giờ của HLV | Thử đóng, mở lại và xem ô bị khóa ở lưới PT. |
| FR29 | Gán lịch cố định lớp 10 và xếp PT trước | API kiểm giữ đăng ký khi buổi mới sinh và tôn trọng suất đã hủy. |
| FR30 | Mỗi gói có mã QR riêng | API kiểm hai gói cùng loại có hai mã riêng. |
| FR31 | Học viên mở QR từ thẻ gói | Kiểm QR hiển thị rõ trên điện thoại và chọn đúng gói. |
| FR32 | Quản trị viên dùng camera quét QR | Cần điện thoại Android để kiểm quyền camera và đọc mã thật. |
| FR33 | Tự chọn buổi theo gói và thời điểm đến | API kiểm PT đúng gói và hai tình huống lớp 10. Cần thêm các mốc biên BR12, nhất là lớp 30. |
| FR34 | Kiểm quyền, cửa sổ, gói và lượt trùng khi xác nhận | API kiểm gói sai, gửi lặp và xác nhận đồng thời. Bổ sung ca tài khoản khóa và gói hết hạn. |
| FR35 | Lịch sử lưu học viên, buổi, gói, thời gian, nhân viên và hình thức | API kiểm sự kiện và gói bị trừ. Kiểm hiển thị đầy đủ trên APK. |
| FR36 | ADMIN/HLV xem số lượt theo buổi | Thử sau một lượt, sau hoàn lượt và khi không có dữ liệu. |
| FR37 | Danh sách đã đến kèm dữ liệu học viên và lượt gói | Thử danh sách của ADMIN, HLV phụ trách và HLV ngoài phạm vi. |
| FR38 | Danh sách đăng ký chưa check-in | API kiểm trạng thái chờ và thay đổi sau khi chuyển buổi. |
| FR39 | Điểm danh thủ công với lý do | Thử cả ADMIN và HLV, gồm buổi thuộc và ngoài phạm vi HLV. |
| FR40 | Cập nhật lượt gói trong cùng lần ghi điểm danh | API kiểm giảm đúng một lượt, gửi lặp và hoàn lượt đúng một lần. |
| FR41 | Lịch sử theo học viên và buổi | API kiểm lọc hai kiểu. Kiểm các màn thực trên APK. |
| FR42 | Lớp 10 walk-in | API kiểm chọn buổi và trừ một lượt. Cần thêm ca hết chỗ và tới ngoài giờ cho phép. |
| FR43 | Check-in tập tự do | API kiểm không trừ lượt và từ chối lần thứ hai cùng ngày. |
| FR44 | Đánh dấu vắng | API kiểm chặn ghi vắng trước lúc kết thúc. Chạy ca sau buổi để xác nhận giữ nguyên lượt. |
| FR45 | Danh sách sắp hết lượt/hạn | Chuẩn bị gói sát ngưỡng và đối chiếu dữ liệu cần gia hạn. |
| FR46 | Danh sách lâu không tập | Thử các mốc trước, đúng và sau 14 ngày không điểm danh. |
| FR47 | Hồ sơ và hành trình học viên PT | Thử truy cập theo vai trò và theo gói PT được phân công. |
| FR48 | HLV ghi nội dung đã dạy | Thử lưu nội dung sau buổi PT và buổi nhóm. |
| FR49 | HLV đánh giá PT sau buổi | Thử mốc trước/sau giờ kết thúc và kiểm điểm hợp lệ. |
| FR50 | Học viên xem tổng kết PT | Thử từ lúc HLV lưu đánh giá tới lúc học viên xem trên APK. |
| FR51 | HLV tạo mục tiêu tháng cho học viên PT | API kiểm tạo và học viên đọc đúng mục tiêu. |
| FR52 | HLV sửa phần trăm và chọn trạng thái mục tiêu | API kiểm trạng thái được lưu, học viên đọc được và yêu cầu từ phiên bản cũ vẫn được xử lý. |
| FR53 | Lịch sử nội dung và nhận xét | Thử thứ tự bản ghi và quyền theo học viên ở cả ba vai trò. |
| FR54 | Xem tiến độ mục tiêu | API kiểm học viên đọc %. Kiểm thêm quyền ADMIN, HLV và trạng thái hoàn thành. |
| FR55 | Hộp thư nhận yêu cầu HLV đề xuất đổi PT | Thử thông báo học viên, mở yêu cầu và phản hồi. |
| FR56 | Hộp thư HLV có trạng thái lịch và thông báo thay HLV | Thử đủ đặt, duyệt, từ chối, đổi và hủy. |
| FR57 | Nhắc đánh giá PT còn chờ ở màn Lịch dạy và hộp thư | APK cần kiểm hiển thị số mục đang chờ khi HLV có buổi PT đã kết thúc. |
| FR58 | Nhắc lịch PT, lớp 10, lớp 30 và gói sắp hết | API kiểm nhắc lớp 10 theo đăng ký và lớp 30 theo gói được gán trong 24 giờ tới. |
| FR59 | ADMIN gửi thông báo chung | API kiểm học viên nhận và đánh dấu đã đọc. |
