# Cookie Browser

Trình duyệt WebView tối giản để nạp cookie của chính tài khoản bạn (JSON, Netscape cookies.txt, chuỗi header).

- `CookieBrowser.apk`: bản đã build sẵn (minSdk 21, targetSdk 34).
- Nhấn nút **Cookie** để dán hoặc chọn file cookie.
- Nhấn giữ nút **Cookie** để chọn file `cookie.txt` sẽ được tự nạp mỗi lần mở app (chỉ nạp lại khi nội dung file thay đổi).

Build lại: `bash build.sh` (cần aapt, dx, zipalign, android.jar API 23), sau đó ký bằng apksigner.
