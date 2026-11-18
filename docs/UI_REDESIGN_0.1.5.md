# Bố cục ứng dụng 0.1.5

## Quyết định theo ảnh tham chiếu

Dùng cách phân cấp và nhóm nội dung của ảnh người dùng cung cấp. Giữ bảng màu hiện tại theo vai trò, Java/XML, Remix Icon và toàn bộ API/phân quyền. Không đưa số liệu minh họa, ảnh khóa học/gói hoặc mục nghiệp vụ chưa có vào app.

## Cấu trúc dùng chung

- Header gồm biểu tượng màn, tiêu đề, mô tả và các nút thông báo/tài khoản; màn con có quay lại.
- Màn gốc có hero ngắn giới thiệu chức năng, dùng icon vector thay ảnh trang trí lớn.
- Thao tác nhanh có icon, nhãn và mô tả; bố trí theo hàng/lưới thích ứng chiều rộng.
- Danh sách dùng thẻ, icon dẫn hướng và trạng thái bằng chữ. Dữ liệu rỗng có tiêu đề/hướng dẫn riêng.
- Biểu mẫu chia nhóm có tiêu đề, trường nhập và nút chính rõ ràng. Hồ sơ có phần nhận diện riêng và khối thông tin cá nhân.
- Thanh tab giữ điều hướng theo vai trò. Mất mạng, thông báo cập nhật, camera và avatar tiếp tục dùng luồng hiện có.

## Ranh giới module

`WorkspaceUi` sở hữu widget cơ bản. `ScreenCards` sở hữu hero, nhóm, lưới thao tác, mục menu và empty state. `ScreenHeader` định nghĩa phần giới thiệu và icon cho các màn gốc. Các Pane giữ nguyên dữ liệu và callback nghiệp vụ, chỉ thay cách trình bày.

## Phạm vi nghiệm thu

Đăng nhập/đổi mật khẩu, tài khoản/avatar, quét/nhập mã/lịch sử, lịch ngày-tuần, quản lý/tài khoản/lớp, danh sách/hồ sơ, đặt lịch, thẻ tập/QR, hành trình, thông báo và các biểu mẫu/chi tiết. Kiểm tra cả ADMIN, RECEPTION, TRAINER, LEARNER, màn hẹp và cỡ chữ lớn. Dữ liệu, số liệu và thao tác xuất hiện trên UI phải được lấy từ chức năng thật.


## Kết quả kiểm tra

- Build hai APK 0.1.5 (versionCode 6), cùng chữ ký debug với bản trước để cài đè. Android lint: 0 lỗi, 11 cảnh báo hiện hữu.
- Emulator với database QA riêng: đăng nhập và tab gốc cả bốn vai trò; chọn/xem trước/lưu avatar; mở biểu mẫu đổi mật khẩu, thông tin app, đặt PT, chi tiết QR, tạo lớp 30, hồ sơ học viên; lưu thông tin cá nhân.
- Chọn ngày trên dải tuần cập nhật ngày lọc và tải lịch thật; chế độ tuần mở được. Dữ liệu minh họa chỉ thuộc database QA, không ghi vào dữ liệu demo đang phục vụ điện thoại.
- Chụp bố cục 320dp và cỡ chữ 130%, lưới chuyển thành một cột, nội dung cuộn được.
- Thử mất mạng lúc khởi động, nhập thông tin đăng nhập, trong workspace, trở về từ cài đặt và Recents: thông báo đúng, giữ phiên và dữ liệu đang nhập, kết nối lại được.
- Chưa nghiệm thu trực tiếp điện thoại thật; chưa kiểm lại từng hộp thoại và mọi luồng nghiệp vụ. Không tăng mốc hoàn thiện doanh nghiệp chỉ từ thay đổi UI.

## Ảnh giao diện thực tế

Ảnh chụp từ emulator, dùng tên/tài khoản QA và hình thử avatar; không phải ảnh khách hàng. Không có ảnh lớp/gói. Các mục dưới đây được chia theo vai trò và tình huống.

### admin

- [calendar](screenshots/0.1.5/admin/calendar.png)
- [class30-form](screenshots/0.1.5/admin/class30-form.png)
- [learner-detail](screenshots/0.1.5/admin/learner-detail.png)
- [learners](screenshots/0.1.5/admin/learners.png)
- [login](screenshots/0.1.5/admin/login.png)
- [management-320dp-font130](screenshots/0.1.5/admin/management-320dp-font130.png)
- [management](screenshots/0.1.5/admin/management.png)
- [profile-320dp-font130](screenshots/0.1.5/admin/profile-320dp-font130.png)
- [profile-after-save](screenshots/0.1.5/admin/profile-after-save.png)
- [profile-image-preview](screenshots/0.1.5/admin/profile-image-preview.png)
- [profile-image-saved](screenshots/0.1.5/admin/profile-image-saved.png)
- [profile](screenshots/0.1.5/admin/profile.png)
- [scan](screenshots/0.1.5/admin/scan.png)

### reception

- [calendar](screenshots/0.1.5/reception/calendar.png)
- [learners](screenshots/0.1.5/reception/learners.png)
- [profile-image-preview](screenshots/0.1.5/reception/profile-image-preview.png)
- [profile-image-saved](screenshots/0.1.5/reception/profile-image-saved.png)
- [profile](screenshots/0.1.5/reception/profile.png)
- [scan](screenshots/0.1.5/reception/scan.png)

### trainer

- [profile-image-preview](screenshots/0.1.5/trainer/profile-image-preview.png)
- [profile-image-saved](screenshots/0.1.5/trainer/profile-image-saved.png)
- [profile](screenshots/0.1.5/trainer/profile.png)
- [pt-learners](screenshots/0.1.5/trainer/pt-learners.png)
- [requests](screenshots/0.1.5/trainer/requests.png)
- [teaching-calendar](screenshots/0.1.5/trainer/teaching-calendar.png)

### learner

- [app-information](screenshots/0.1.5/learner/app-information.png)
- [booking](screenshots/0.1.5/learner/booking.png)
- [calendar-selected-week](screenshots/0.1.5/learner/calendar-selected-week.png)
- [calendar-week-results](screenshots/0.1.5/learner/calendar-week-results.png)
- [calendar](screenshots/0.1.5/learner/calendar.png)
- [journey](screenshots/0.1.5/learner/journey.png)
- [package-picker](screenshots/0.1.5/learner/package-picker.png)
- [package-qr-detail](screenshots/0.1.5/learner/package-qr-detail.png)
- [password-form](screenshots/0.1.5/learner/password-form.png)
- [profile-image-preview](screenshots/0.1.5/learner/profile-image-preview.png)
- [profile-image-saved](screenshots/0.1.5/learner/profile-image-saved.png)
- [profile-options](screenshots/0.1.5/learner/profile-options.png)
- [profile](screenshots/0.1.5/learner/profile.png)
- [pt-booking-form](screenshots/0.1.5/learner/pt-booking-form.png)
- [qr-cards](screenshots/0.1.5/learner/qr-cards.png)

### network

- [login-offline](screenshots/0.1.5/network/login-offline.png)
- [login-online](screenshots/0.1.5/network/login-online.png)
- [workspace-offline](screenshots/0.1.5/network/workspace-offline.png)
- [workspace-online](screenshots/0.1.5/network/workspace-online.png)

## Chạy lại kiểm thử

Dùng stack Docker QA riêng cổng 8180. Chạy tests/ui-fixtures.mjs với MUAYTHAI_UI_FIXTURE_OUT trỏ vào android/app/build/, đặt course.jpg cạnh tệp fixture. APK kiểm thử phải chứa endpoint emulator cổng 8180. Chạy tests/ui-redesign.ps1 với FixturePath, ApkPath và OutputDirectory; tiếp theo tests/connectivity.ps1 với cùng APK. Không dùng fixture/avatar test trên stack demo cổng 8080. Fixture chứa mật khẩu/token và luôn để trong thư mục build bị Git bỏ qua.
