# Cookie Browser – hướng dẫn cho Claude Code trên laptop Windows

App Android (WebView) để người dùng nạp cookie **của chính tài khoản mình** vào điện thoại.
Package: `com.cookiebrowser.app`. Code Java thuần, không dùng Gradle.

## Môi trường của người dùng
- Windows 11, PowerShell 5.1.
- Android SDK: `%LOCALAPPDATA%\Android\Sdk` (adb không có trong PATH, dùng `$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe`).
- Điện thoại: Samsung Galaxy M11 (SM-M115F), Android 13, cắm USB, đã bật USB debugging.

## Cấu trúc
- `AndroidManifest.xml`, `res/drawable/ic.xml`
- `src/com/cookiebrowser/app/MainActivity.java`: giao diện, WebView, import cookie, tự nạp file đã ghi nhớ.
- `src/com/cookiebrowser/app/CookieParser.java`: đọc JSON (Cookie-Editor/iCookie/Playwright), Netscape cookies.txt, chuỗi header.
- `build.ps1`: build + ký + cài + chạy trên Windows. `build.sh` là bản cho Linux, bỏ qua trên Windows.
- `push-cookie.ps1`: đẩy cookie mới sang điện thoại và khởi động lại app.

## Quy trình chuẩn
```powershell
powershell -ExecutionPolicy Bypass -File .\build.ps1 -Install -Run
```
Script tự tìm build-tools/platform mới nhất trong SDK và JDK (PATH, JAVA_HOME, hoặc JDK đi kèm Android Studio).
Khóa ký được tạo một lần tại `%USERPROFILE%\.android\cookiebrowser.jks` và giữ cố định.
Nếu điện thoại đang có bản ký bằng khóa khác, script tự gỡ rồi cài lại (cookie trong app mất, nạp lại từ cookie.txt).

Nếu thiếu công cụ: cài build-tools/platform bằng `sdkmanager` trong `Sdk\cmdline-tools\latest\bin` (hoặc SDK Manager của Android Studio), cài JDK 17+ nếu không có javac.
Code phải giữ tương thích `-source 8` và chỉ dùng API có trong `android.jar` (minSdk 21).

## Debug
- Crash lúc mở: `build.ps1 -Run` tự in `adb logcat -b crash` và `AndroidRuntime:E`.
- Log khi đang chạy: `adb logcat --pid=$(adb shell pidof com.cookiebrowser.app)`
- Lỗi trang web hoặc cookie: app bật `WebView.setWebContentsDebuggingEnabled(true)`, nên mở Chrome trên laptop, vào `chrome://inspect`, chọn trang trong Cookie Browser, rồi xem tab Application > Cookies và Console.
- Kiểm tra file cookie trên máy: `adb shell ls -l /sdcard/Download/cookie.txt`. Không in nội dung file cookie ra log hay chat.
- Sau khi sửa code: chạy lại `build.ps1 -Install -Run` và kiểm tra cho đến khi app chạy ổn.

## Cookie
- Lần đầu trên điện thoại: mở app, nhấn giữ nút **Cookie**, chọn **Chọn file**, vào Download, chọn `cookie.txt`. Bước này phải làm tay vì Android yêu cầu người dùng tự chọn file.
- Các lần sau: `powershell -ExecutionPolicy Bypass -File .\push-cookie.ps1 <đường dẫn cookie.txt>`. App tự nạp lại khi nội dung file thay đổi.
