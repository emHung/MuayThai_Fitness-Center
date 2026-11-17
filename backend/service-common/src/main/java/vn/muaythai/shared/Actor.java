package vn.muaythai.shared;

public record Actor(long id, String role, boolean canAdjustCheckins) {
  public boolean is(String value) {
    return role.equals(value);
  }

  public boolean canCheckInAtDesk() {
    return is("ADMIN") || is("RECEPTION");
  }

  public void require(String value) {
    if (!is(value)) {
      throw Problem.forbidden();
    }
  }

  public void require(boolean allowed) {
    if (!allowed) {
      throw Problem.forbidden();
    }
  }
}
