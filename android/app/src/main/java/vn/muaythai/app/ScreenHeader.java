package vn.muaythai.app;

/** Presentation metadata only; access control remains in the workspace and API. */
final class ScreenHeader {
  record Info(int icon, String subtitle, String heroTitle, String description) {}

  private ScreenHeader() {}

  static Info of(String title) {
    return switch (title) {
      case "Quét QR check-in" -> new Info(R.drawable.ic_scan, "Điểm danh và tra cứu tại quầy",
          "Quét QR của gói tập", "Đưa mã QR vào khung, kiểm tra gói và buổi tập trước khi xác nhận.");
      case "Quản lý" -> new Info(R.drawable.ic_grid, "Tài khoản, lớp học và điểm danh",
          "Trung tâm quản lý", "Truy cập các công cụ quản lý và chăm sóc học viên của trung tâm.");
      case "Lịch toàn trung tâm", "Lịch của tôi", "Lịch dạy của tôi" -> new Info(R.drawable.ic_calendar,
          "Tra cứu buổi tập và lịch đã được sắp xếp", "Lịch học & hoạt động",
          "Chọn ngày hoặc tuần để xem lịch và các thay đổi mới nhất.");
      case "Đặt lịch" -> new Info(R.drawable.ic_book, "PT cá nhân và lớp nhóm",
          "Đặt lịch tập luyện", "Đặt buổi với HLV phụ trách hoặc chọn buổi lớp nhóm phù hợp.");
      case "Thẻ tập của tôi" -> new Info(R.drawable.ic_qr, "Gói tập và quyền lợi của bạn",
          "Thẻ tập luyện", "Xem thời hạn, số lượt và mã QR của gói đang sử dụng.");
      case "Hành trình" -> new Info(R.drawable.ic_trend, "Mục tiêu và đánh giá tập luyện",
          "Hành trình của bạn", "Theo dõi mục tiêu tháng và các nhận xét từ huấn luyện viên.");
      case "Học viên", "Học viên PT" -> new Info(R.drawable.ic_group, "Danh sách và hồ sơ học viên",
          "Kết nối với học viên", "Tìm kiếm hồ sơ và mở các thông tin được phân quyền.");
      case "Chờ xác nhận" -> new Info(R.drawable.ic_inbox, "Các yêu cầu cần bạn xử lý",
          "Yêu cầu tập luyện", "Xem thông tin trước khi duyệt hoặc phản hồi yêu cầu của học viên.");
      case "Tài khoản của tôi" -> new Info(R.drawable.ic_user, "Hồ sơ và tùy chọn tài khoản", "", "");
      case "Thông báo" -> new Info(R.drawable.ic_inbox, "Thông tin và cập nhật hệ thống", "", "");
      case "Thông tin ứng dụng" -> new Info(R.drawable.ic_book, "Phiên bản và cập nhật", "", "");
      case "Lớp nhóm" -> new Info(R.drawable.ic_group, "Danh sách lớp 10 và lớp 30", "", "");
      case "Huấn luyện viên" -> new Info(R.drawable.ic_book, "Danh sách và hồ sơ huấn luyện viên", "", "");
      case "Lễ tân" -> new Info(R.drawable.ic_user, "Quản lý tài khoản tại quầy", "", "");
      case "Đổi mật khẩu" -> new Info(R.drawable.ic_key, "Bảo vệ tài khoản của bạn", "", "");
      case "Lịch sử điểm danh" -> new Info(R.drawable.ic_calendar, "Tra cứu các lượt tập đã ghi nhận", "", "");
      default -> new Info(R.drawable.ic_book, "Thông tin chi tiết và thao tác", "", "");
    };
  }
}
