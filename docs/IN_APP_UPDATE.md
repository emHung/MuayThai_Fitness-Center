# Cập nhật ngay trong ứng dụng

- AppUpdatePrompt chỉ kiểm tra release và nhắc cập nhật; UpdateActivity sở hữu màn hình tải và vòng đời thao tác cài.
- DownloadManager tiếp tục tải khi ra nền. Lưu ID theo asset; mở lại cùng asset tiếp tục theo dõi, không tải trùng.
- Màn hình hiển thị số byte/phần trăm, trạng thái chờ mạng, thử lại và hủy. Chỉ mở trình cài khi màn hình ở foreground.
- APK nằm trong thư mục riêng, chia sẻ qua FileProvider chỉ cho thư mục cập nhật. Kiểm tra SHA-256 từ release nếu có, package, phiên bản mới và chữ ký trùng ứng dụng đang cài.
- Android yêu cầu người dùng cấp quyền cài từ nguồn này và xác nhận cài. Quay lại sau cấp quyền tiếp tục bước cài; hủy trình cài không lặp tự mở.
- Không thêm thư viện tải/cài ngoài; dùng API Android và Material hiện có.

## Kiểm thử 0.1.6

Build probe mang phiên bản 0.1.4/code 5 để nhận release thật 0.1.5 từ GitHub. Emulator xác nhận tải, kiểm tra tệp, mở quyền cài nguồn này, tự tiếp tục khi quay lại và mở hộp xác nhận cài đặt. Hủy hộp thoại trở về app, không tự mở lại. Tiếp tục cài, Play Protect quét và báo an toàn; xác nhận Install rồi kiểm tra package đã lên 0.1.5. Không tắt hoặc bỏ qua Play Protect.

Chạy `tests/app-update-install.ps1 -ApkPath <probe-apk> -ExpectedVersion <release-version>` trên emulator riêng. Probe phải có chức năng mới nhưng phiên bản/code thấp hơn release hiện hành, cùng chữ ký. Bộ kiểm thử cập nhật cũ `tests/app-update.ps1` vẫn được giữ nguyên.

Ảnh: [tải](screenshots/0.1.6/download.png), [quyền](screenshots/0.1.6/install-permission.png), [xác nhận cài](screenshots/0.1.6/installer.png), [hủy cài](screenshots/0.1.6/install-cancelled.png).

Bản cũ chưa có màn tải trong app, nên cần tải/cài đè 0.1.6 một lần. Các bản tiếp theo mới dùng luồng mới. Chưa nghiệm thu điện thoại thật, mạng ngắt giữa lúc tải và mọi mã lỗi DownloadManager; DownloadManager sở hữu việc tiếp tục tải khi chờ mạng.

API tham khảo: [DownloadManager](https://developer.android.com/reference/android/app/DownloadManager), [quyền yêu cầu cài](https://developer.android.com/reference/android/content/pm/PackageManager#canRequestPackageInstalls()).
