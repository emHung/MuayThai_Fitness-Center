package vn.muaythai.app;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.os.Build;
import java.io.File;
import java.io.FileInputStream;
import java.security.MessageDigest;
import java.util.Arrays;

/** Reject unrelated, downgraded or differently signed packages before handing off to Android. */
final class UpdatePackage {
  private UpdatePackage() {}

  @SuppressWarnings("deprecation")
  static void verify(Context context, File file, String version, String expectedDigest) throws Exception {
    if (!file.isFile() || file.length() == 0) throw new SecurityException("Empty APK");
    if (!expectedDigest.isEmpty()) {
      if (!expectedDigest.matches("sha256:[a-fA-F0-9]{64}")) throw new SecurityException("Unsupported digest");
      var hash = MessageDigest.getInstance("SHA-256");
      try (var stream = new FileInputStream(file)) {
        byte[] buffer = new byte[65536];
        int length;
        while ((length = stream.read(buffer)) != -1) hash.update(buffer, 0, length);
      }
      var hex = new StringBuilder();
      for (byte value : hash.digest()) hex.append(String.format(java.util.Locale.ROOT, "%02x", value & 255));
      if (!expectedDigest.substring(7).equalsIgnoreCase(hex.toString())) throw new SecurityException("Digest mismatch");
    }
    var manager = context.getPackageManager();
    int flags = Build.VERSION.SDK_INT >= 28 ? PackageManager.GET_SIGNING_CERTIFICATES : PackageManager.GET_SIGNATURES;
    PackageInfo apk = manager.getPackageArchiveInfo(file.getAbsolutePath(), flags);
    PackageInfo installed = manager.getPackageInfo(context.getPackageName(), flags);
    if (apk == null || !context.getPackageName().equals(apk.packageName) || !version.equals(apk.versionName)
        || !AppUpdatePrompt.newerThan(apk.versionName, installed.versionName)
        || (Build.VERSION.SDK_INT >= 28 ? apk.getLongVersionCode() <= installed.getLongVersionCode()
            : apk.versionCode <= installed.versionCode)) throw new SecurityException("Unexpected package or version");
    Signature[] candidate = Build.VERSION.SDK_INT >= 28 ? apk.signingInfo.getApkContentsSigners() : apk.signatures;
    Signature[] current = Build.VERSION.SDK_INT >= 28 ? installed.signingInfo.getApkContentsSigners() : installed.signatures;
    if (candidate == null || current == null || candidate.length == 0 || candidate.length != current.length
        || !Arrays.asList(candidate).containsAll(Arrays.asList(current))) throw new SecurityException("Signer mismatch");
  }
}
