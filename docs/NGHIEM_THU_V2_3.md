# Đối chiếu 59 yêu cầu v2.3

Bảng phân biệt **kiểm chứng nghiệp vụ ở lớp hệ thống** với **nghiệm thu thiết bị**. Không diễn giải kết quả API thành đã thử mọi thao tác trên mọi điện thoại.

- **A:** [acceptance.mjs](../tests/acceptance.mjs), bộ hồi quy nghiệp vụ/SSE/phục hồi dịch vụ.
- **H:** [handoff.mjs](../tests/handoff.mjs), các ca sức chứa, quyền, mốc thời gian và nghiệp vụ bổ sung.
- **UI:** bằng chứng emulator trong [UI_REDESIGN_0.1.5.md](UI_REDESIGN_0.1.5.md) và [IN_APP_UPDATE.md](IN_APP_UPDATE.md); không phải chụp lại toàn bộ giao diện ở lượt này.
- **Thiết bị:** phần được người sử dụng xác nhận trên điện thoại thật.

Bộ A, H, kiểm thử ảnh và production smoke đã chạy đạt trên môi trường thử riêng. Thời điểm hết hạn và mốc thời gian trong H được chuẩn bị bằng dữ liệu QA; không thay đổi đồng hồ hoặc dữ liệu máy chủ đang sử dụng. Còn chốt đối chiếu lượt, đồng bộ thiết bị và cài bộ release trong [PROGRESS.md](../PROGRESS.md).

| Mã | Phần triển khai | Kiểm chứng và giới hạn |
| --- | --- | --- |
| FR01 | Phiên đăng nhập, đăng xuất, đổi mật khẩu tạm | A/H: đăng nhập, mật khẩu tạm, đăng xuất và thu hồi phiên. |
| FR02 | Phân quyền theo vai trò và theo đối tượng | A/H: quyền lễ tân, hồ sơ, lịch sử, đánh giá và mục tiêu theo đối tượng. |
| FR03 | Hồ sơ học viên, khóa/mở khóa, mã hội viên | A/H: tạo mã hội viên, lưu hồ sơ, khóa/mở và phiên sau khi khóa. |
| FR04 | Hồ sơ HLV, mật khẩu tạm, ca làm, khóa/mở khóa, xóa | A: tạo/xóa HLV, dữ liệu tham chiếu và tranh chấp xóa với gán ca. |
| FR05 | Cấp gói, chọn HLV/lớp, hạn, lượt và gia hạn qua gói mới | A/H: cấp nhiều loại gói, mã riêng, đọc hạn/lượt và đổi HLV. |
| FR06 | Tìm theo từ khóa trong danh bạ, lớp và lịch ngày/tuần | A: tìm danh bạ, lớp và buổi; UI: bộ lọc lịch đã được thử trên emulator. |
| FR07 | Sửa thông tin cá nhân và đổi mật khẩu | H/UI: lưu thông tin cá nhân và kiểm dữ liệu; đổi mật khẩu theo API. |
| FR08 | Lớp 30 lặp hàng tuần và sinh lịch | A/H: sinh lịch lớp 30, sửa quy tắc và dừng lịch tương lai. |
| FR09 | HLV đề xuất lớp 10 theo thứ/giờ | A/H: HLV đề xuất lớp 10, chờ duyệt, mở lịch hoặc từ chối. |
| FR10 | Khung PT theo ca, xếp trực tiếp, đề xuất đổi lịch | A/H: xếp PT trực tiếp, khung khả dụng và đề xuất đổi giờ. |
| FR11 | Chặn trùng HLV và học viên theo khoảng giờ | A/H: chặn lịch trùng, học viên tranh cùng giờ qua hai HLV, HLV thay thế đang bận. |
| FR12 | Lớp 10 giới hạn theo đăng ký. Lớp 30 giới hạn theo lượt ghi | H: tranh suất cuối lớp 10/lớp 30; một yêu cầu thành công, một bị chặn, không mất lượt sai. |
| FR13 | Học viên chọn khung PT. HLV duyệt/từ chối | A/H: học viên gửi, HLV duyệt/từ chối và khung giờ được giải phóng khi từ chối. |
| FR14 | HLV gửi đề xuất đổi giờ PT | H: HLV gửi đề xuất, đúng học viên nhận và phản hồi. |
| FR15 | Đồng ý mới cập nhật lịch cũ | A/H: chỉ cập nhật khi chấp nhận; từ chối/hết hạn giữ nguyên lịch. |
| FR16 | Chuyển đăng ký lớp 10 sang buổi khác | A: chuyển đăng ký lớp 10, đối chiếu danh sách cả hai buổi. |
| FR17 | Lịch lớp 30 của học viên theo lớp được gán | H: lịch theo lớp/gói, gói của lớp khác bị chặn khi điểm danh. |
| FR18 | ADMIN quản lý lịch lớp 30 và phân công HLV | A/H: tạo/sửa/dừng lớp 30, thay HLV buổi, kiểm quyền và thông báo hai phía. |
| FR19 | Màn lịch lắng nghe cập nhật SSE và tải dữ liệu theo quyền | A: SSE đồng thời ba phiên vai trò đạt; còn xác nhận cập nhật trên nhiều thiết bị sử dụng. |
| FR20 | Lịch ngày và tuần | UI: chọn ngày/dải tuần và chế độ tuần đã thử trên emulator; A kiểm dữ liệu lịch. |
| FR21 | Hủy PT/lớp 10 theo hạn và hủy buổi có lý do | A/H: mốc hủy PT 12 giờ, lớp 10 3 giờ và hủy cạnh tranh điểm danh. |
| FR22 | Học viên đề xuất và HLV xác nhận đổi PT | A: học viên đề xuất, HLV đồng ý và đối chiếu giờ trước/sau. |
| FR23 | Yêu cầu đổi tự hết hạn | H: yêu cầu quá hạn chuyển EXPIRED, không chấp nhận lại và không đổi lịch cũ. |
| FR24 | ADMIN duyệt/từ chối đề xuất lớp 10 | A/H: duyệt/từ chối đề xuất lớp 10; chặn HLV tự duyệt. |
| FR25 | ADMIN xếp trực tiếp, chọn HLV và khung giờ | A: xếp trực tiếp, chọn HLV, khung giờ và thông báo cho người liên quan. |
| FR26 | Ca làm theo tuần, quyền xem và xử lý lịch vướng ca | A/H: lưu/xem ca theo quyền; kiểm giờ đóng có lịch đang đặt. |
| FR27 | Lưới giờ PT trống, bận và ngoài ca | H/UI: dữ liệu ô giờ trống/bận/đóng/ngoài ca; lưới PT đã mở trên emulator. |
| FR28 | Đóng khung giờ của HLV | A/H: đóng/mở giờ, không đặt vào giờ đóng và không đóng đè buổi đã đặt. |
| FR29 | Gán lịch cố định lớp 10 và xếp PT trước | A: tự giữ chỗ khi sinh buổi lớp 10 mới, không khôi phục suất đã hủy. |
| FR30 | Mỗi gói có mã QR riêng | A: hai gói cùng loại có mã riêng, giữ mã khi đổi HLV. |
| FR31 | Học viên mở QR từ thẻ gói | UI: mở QR từng gói; người sử dụng xác nhận đã quét mã và xác nhận điểm danh thành công. |
| FR32 | Quản trị viên dùng camera quét QR | Thiết bị: người sử dụng đã xác nhận quét QR và xác nhận điểm danh thành công. |
| FR33 | Tự chọn buổi theo gói và thời điểm đến | A/H: chọn buổi theo gói, cửa sổ điểm danh, buổi sai và walk-in ngoài mốc bị chặn. |
| FR34 | Kiểm quyền, cửa sổ, gói và lượt trùng khi xác nhận | A/H: gói sai/hết hạn/hết lượt, tài khoản khóa, lần gửi trùng và sức chứa. |
| FR35 | Lịch sử lưu học viên, buổi, gói, thời gian, nhân viên và hình thức | A/H: kiểm bản ghi, học viên/buổi/gói/người ghi/hình thức và phạm vi xem. |
| FR36 | ADMIN/HLV xem số lượt theo buổi | A/H: danh sách theo buổi và trạng thái trước/sau hoàn lượt. |
| FR37 | Danh sách đã đến kèm dữ liệu học viên và lượt gói | H: danh sách người đã đến, số lượt và HLV ngoài phạm vi bị chặn. |
| FR38 | Danh sách đăng ký chưa check-in | A: danh sách chờ thay đổi đúng khi chuyển/hủy đăng ký. |
| FR39 | Điểm danh thủ công với lý do | H: thủ công bởi HLV đúng quyền, bắt buộc lý do; lễ tân/HLV khác không được dùng luồng này. |
| FR40 | Cập nhật lượt gói trong cùng lần ghi điểm danh | A/H: giảm/hoàn đúng một lượt, không ghi đôi khi đồng thời; còn đối chiếu lượt trên điện thoại sử dụng. |
| FR41 | Lịch sử theo học viên và buổi | A/H: lọc lịch sử và quyền admin/HLV/học viên/lễ tân. |
| FR42 | Lớp 10 walk-in | A/H: walk-in lớp 10, đủ chỗ và muộn quá cửa sổ; không trừ lượt khi bị chặn. |
| FR43 | Check-in tập tự do | A: tập tự do không trừ lượt, chặn lượt thứ hai cùng ngày. |
| FR44 | Đánh dấu vắng | A/H: chặn ghi vắng trước kết thúc; sau buổi ghi ABSENT, không mất lượt. |
| FR45 | Danh sách sắp hết lượt/hạn | H: ngưỡng 3/4 lượt, hạn gần hết và thông báo gói đúng tài khoản. |
| FR46 | Danh sách lâu không tập | H: danh sách lâu chưa tập trước/sau mốc 14 ngày và sau khi đã điểm danh. |
| FR47 | Hồ sơ và hành trình học viên PT | H: lịch sử/đánh giá theo học viên PT, chặn HLV hoặc học viên không liên quan. |
| FR48 | HLV ghi nội dung đã dạy | H: lưu nội dung buổi, đọc lại và lịch sử. |
| FR49 | HLV đánh giá PT sau buổi | H: chỉ sau khi kết thúc, kiểm rating và HLV phụ trách. |
| FR50 | Học viên xem tổng kết PT | H: học viên đọc tổng kết đã lưu, học viên khác không đọc được. |
| FR51 | HLV tạo mục tiêu tháng cho học viên PT | A/H: HLV được phân công tạo mục tiêu và học viên đọc đúng. |
| FR52 | HLV sửa phần trăm và chọn trạng thái mục tiêu | A/H: phần trăm/trạng thái, trường hợp đạt 100% và tương thích yêu cầu cũ. |
| FR53 | Lịch sử nội dung và nhận xét | H: nhiều bản ghi có thứ tự thời gian và kiểm quyền xem. |
| FR54 | Xem tiến độ mục tiêu | A/H: học viên đọc tiến độ, admin/HLV theo quyền, trạng thái hoàn thành. |
| FR55 | Hộp thư nhận yêu cầu HLV đề xuất đổi PT | H: đề xuất của HLV đến đúng hộp thư học viên và phản hồi. |
| FR56 | Hộp thư HLV có trạng thái lịch và thông báo thay HLV | A/H: trạng thái PT và thông báo thay HLV cho cả HLV cũ/mới. |
| FR57 | Nhắc đánh giá PT còn chờ ở màn Lịch dạy và hộp thư | UI/A: lời nhắc đánh giá được đối chiếu ở màn lịch/hộp thư trong bộ thử trước. |
| FR58 | Nhắc lịch PT, lớp 10, lớp 30 và gói sắp hết | A/H: nhắc PT/lớp theo đăng ký/gói; thông báo gói sắp hết theo ngày hoặc lượt. |
| FR59 | ADMIN gửi thông báo chung | A: thông báo chung đến học viên, đánh dấu đã đọc. |
