# Android ROM Lab: tìm hiểu cấu trúc ROM Samsung Galaxy S9 (SM-G960N)

Thư mục này giúp bạn **hiểu ROM được cấu tạo thế nào** bằng cách tự build một ROM mẫu,
rồi mổ xẻ lại nó (hoặc mổ xẻ ROM gốc thật).

| File | Chức năng |
|---|---|
| `build_demo_rom.sh` | Build ROM mẫu theo đúng định dạng firmware Samsung (Odin) và dạng ZIP (TWRP) |
| `inspect_rom.sh` | Giải nén từng lớp của file `AP_...tar.md5` (ROM mẫu hoặc ROM gốc thật) và in ra các thông số |

> ⚠️ **ROM mẫu chứa kernel GIẢ, KHÔNG KHỞI ĐỘNG ĐƯỢC. Không flash file trong `out/` vào điện thoại.**
> Tên file đã được gắn `_DEMO_KHONG_FLASH` để nhắc bạn điều này.

## Chạy thử (Ubuntu 22.04 / 24.04)

```bash
sudo apt install e2fsprogs android-sdk-libsparse-utils lz4 cpio mkbootimg zip
cd android-rom-lab
./build_demo_rom.sh                                   # build ROM mẫu vào out/
./inspect_rom.sh out/odin/AP_*.tar.md5 out/unpacked   # mổ xẻ lại ROM vừa build
```

Mổ xẻ ROM gốc thật (sau khi tải firmware SM-G960N về máy):

```bash
./inspect_rom.sh ~/Downloads/AP_G960NKSU....tar.md5
```

Firmware thật nặng khoảng 4–5 GB, và khi giải nén cần thêm khoảng 15 GB trống.

---

## 1. ROM là gì? Có hai dạng

| | **Firmware gốc Samsung (Odin)** | **ROM ZIP (TWRP): LineageOS, ROM cook** |
|---|---|---|
| Định dạng | 4 file `BL`, `AP`, `CP`, `CSC` (`.tar.md5`) | 1 file `.zip` |
| Công cụ flash | Odin (Windows) hoặc Heimdall (Linux) ở chế độ Download | Recovery TWRP |
| Ghi vào | Cả bootloader, modem, mọi phân vùng | Thường chỉ `system`, `vendor`, `boot` |
| Cách build | Samsung tự build, ký số | Build từ mã nguồn (AOSP) hoặc sửa ROM gốc |

Script build ra **cả hai dạng** để bạn so sánh.

## 2. Bốn file firmware Samsung

Dưới đây là các file **thường gặp** trong firmware S9. Chạy `inspect_rom.sh` trên firmware thật để xem danh sách chính xác.

| File | Viết tắt | Bên trong | Vai trò |
|---|---|---|---|
| `BL_...` | **B**oot**L**oader | `sboot.bin`, `cm.bin`, `up_param.bin`, `keystorage.bin` | Chương trình chạy đầu tiên khi bật máy, kiểm tra chữ ký. **Flash sai BL có thể làm chết máy hoàn toàn.** |
| `AP_...` | **A**pplication **P**rocessor | `boot.img`, `recovery.img`, `system.img.ext4`, `vendor.img.ext4`, `userdata.img.ext4`… | Toàn bộ Android. **Đây là phần bạn mod nhiều nhất.** |
| `CP_...` | **C**ommunication **P**rocessor | `modem.bin` | Firmware modem: sóng điện thoại, 4G, gọi điện |
| `CSC_...` / `HOME_CSC_...` | **C**onsumer **S**oftware **C**ustomization | `cache.img`, `odm.img`, `hidden.img`, file `.pit` | Cấu hình theo vùng và nhà mạng (KOO, SKC, KTC, LUC). File `.pit` là bảng phân vùng, nên bản `CSC` sẽ xóa dữ liệu còn `HOME_CSC` giữ lại dữ liệu. |

## 3. Các lớp đóng gói (từ ngoài vào trong)

```
AP_G960N....tar.md5          ← file tar + 1 dòng MD5 ở cuối (Odin kiểm tra file tải về có bị hỏng không)
 └─ system.img.ext4.lz4      ← nén lz4 cho nhẹ
     └─ system.img.ext4      ← "sparse image": bỏ các block trống (64 MB → 216 KB trong ROM mẫu)
         └─ ext4 thô         ← ảnh phân vùng thật, đúng từng byte sẽ ghi vào bộ nhớ máy
             └─ /system/build.prop, /system/app/..., /system/framework/...
```

| Lớp | Đóng gói (khi build) | Giải nén (khi mổ xẻ) |
|---|---|---|
| tar.md5 | `tar -H ustar -cf` rồi `md5sum -t >> file` | `tar -xf` |
| lz4 | `lz4 -B6 --content-size` | `lz4 -d` |
| sparse | `img2simg` | `simg2img` |
| ext4 | `mkfs.ext4 -d <thư_mục>` | `debugfs rdump` hoặc `sudo mount -o loop,ro` |

## 4. Các phân vùng chính

| Phân vùng | Mount vào | Chứa gì | Mod thường gặp |
|---|---|---|---|
| **boot** | (không mount) | kernel + ramdisk + cmdline | Root (Magisk vá file này), đổi kernel |
| **recovery** | (không mount) | Chế độ khôi phục | Thay bằng TWRP |
| **system** | `/` (system-as-root) | Android framework, app hệ thống, `build.prop` | Gỡ app rác, sửa `build.prop`, đổi font |
| **vendor** | `/vendor` | Driver, thư viện phần cứng Exynos, `fstab` | Hiếm khi sửa (dễ lỗi phần cứng) |
| **odm** | `/odm` | Cấu hình riêng theo vùng | |
| **userdata** | `/data` | App và dữ liệu người dùng | |
| **efs** | `/mnt/vendor/efs` | **IMEI, địa chỉ MAC, số serial** | ⚠️ **Không bao giờ xóa. Nên sao lưu trước khi mod.** |
| **cache** | `/cache` | File tạm, gói cập nhật OTA | |

Bảng phân vùng mẫu nằm trong file `vendor/etc/fstab.samsungexynos9810` của ROM mẫu.

## 5. `boot.img`: thứ bootloader nạp đầu tiên

```
┌──────────────────────┐
│ header "ANDROID!"    │ ← địa chỉ nạp, kích thước từng phần, cmdline, os_version
├──────────────────────┤
│ kernel (Image)       │ ← nhân Linux (bản thật khoảng 20 MB; ROM mẫu dùng file giả)
├──────────────────────┤
│ ramdisk (cpio.gz)    │ ← init.rc, prop.default: tiến trình init đọc đầu tiên
├──────────────────────┤
│ "SEANDROIDENFORCE"   │ ← dấu hiệu Samsung đặt ở cuối ảnh đã ký
└──────────────────────┘
```

| Tham số mkbootimg | Giá trị (S9) | Ý nghĩa |
|---|---|---|
| `--header_version` | 1 | Phiên bản cấu trúc header |
| `--base` | `0x10000000` | Địa chỉ gốc trong RAM |
| `--kernel_offset` | `0x00008000` | Kernel nạp tại `base + offset` |
| `--ramdisk_offset` | `0x01000000` | Ramdisk nạp tại `base + offset` |
| `--pagesize` | 2048 | Kích thước trang; mỗi phần được căn chỉnh theo giá trị này |
| `--cmdline` | `androidboot.hardware=samsungexynos9810 ...` | Tham số truyền cho kernel. Ví dụ `androidboot.selinux=permissive` sẽ tắt SELinux. |

Các giá trị trên lấy từ device tree `starlte` (TWRP/LineageOS), chỉ mang tính tham khảo. Muốn biết giá trị thật, hãy chạy `inspect_rom.sh` trên firmware gốc: bước 5 sẽ in ra header.

## 6. `build.prop`: bảng thông số của ROM

Mỗi dòng có dạng `khóa=giá_trị`. Các khóa bắt đầu bằng `ro.` là chỉ đọc: chúng được nạp lúc khởi động và không đổi được khi máy đang chạy.

### Nhận dạng ROM và máy

| Khóa | Ví dụ | Ý nghĩa |
|---|---|---|
| `ro.product.model` / `ro.product.system.model` | `SM-G960N` | Tên model. App và Play Store dùng giá trị này để nhận dạng máy. |
| `ro.product.device` | `starlte` | Mã máy (codename). ROM ZIP kiểm tra khóa này để tránh flash nhầm máy. |
| `ro.product.name` | `starlteks` | Tên sản phẩm: `starlte` + `ks` (Korea) |
| `ro.board.platform` | `exynos5` | Nền tảng chip, dùng để chọn driver |
| `ro.build.fingerprint` | `samsung/starlteks/starlte:10/QP1A.190711.020/G960NKSU...:user/release-keys` | "Dấu vân tay" của ROM. Google Play và SafetyNet kiểm tra khóa này. |

### Phiên bản

| Khóa | Ví dụ | Ý nghĩa |
|---|---|---|
| `ro.build.version.release` | `10` | Phiên bản Android |
| `ro.build.version.sdk` | `29` | API level. App dùng số này để biết tính năng nào có sẵn. |
| `ro.build.id` | `QP1A.190711.020` | Mã nhánh mã nguồn Google. `Q` = Android 10. |
| `ro.build.PDA` / `ro.build.version.incremental` | `G960NKSU9...` | Phiên bản firmware Samsung (xem cách đọc bên dưới) |
| `ro.build.version.security_patch` | `2021-11-01` | Mức bản vá bảo mật |
| `ro.build.type` | `user` | `user` là bản phát hành, `userdebug` hoặc `eng` là bản cho nhà phát triển |
| `ro.build.tags` | `release-keys` | Được ký bằng khóa chính thức. `test-keys` nghĩa là ROM tự build. |

**Cách đọc mã firmware Samsung**, ví dụ `G960N KS U 9 F U K 1`:

| Phần | Ví dụ | Ý nghĩa |
|---|---|---|
| Model | `G960N` | Mã model |
| Vùng | `KS` | K = Hàn Quốc |
| Loại bản cập nhật | `U` | U = cập nhật thường, S = chỉ vá bảo mật |
| Bootloader | `9` | **Phiên bản bootloader. Không thể hạ xuống firmware có số nhỏ hơn.** |
| Hệ điều hành | `F` | Chữ cái tăng dần theo mỗi lần lên Android |
| Năm | `U` | R = 2018, S = 2019, T = 2020, U = 2021… |
| Tháng | `K` | A = tháng 1 … L = tháng 12 |
| Số bản build | `1` | Số thứ tự bản build trong tháng |

### Thông số hay chỉnh khi mod

| Khóa | Ý nghĩa | Lưu ý |
|---|---|---|
| `ro.sf.lcd_density` | Mật độ điểm ảnh (DPI). Giảm số này thì chữ và giao diện nhỏ lại. | Có thể làm hỏng giao diện một số app |
| `dalvik.vm.heapsize` | Bộ nhớ tối đa cho mỗi app | |
| `ro.product.locale` | Ngôn ngữ mặc định (`ko-KR`, `vi-VN`) | |
| `persist.sys.timezone` | Múi giờ mặc định (`Asia/Seoul`, `Asia/Ho_Chi_Minh`) | |
| `ro.config.ringtone` | Nhạc chuông mặc định | File phải có trong `system/media/audio/ringtones` |

## 7. Thử nghiệm: đổi thông số rồi build lại

1. Mở `build_demo_rom.sh` và sửa khối **"Thông số của ROM"** ở đầu file, ví dụ:
   `ANDROID_VER="11"`, `SDK_VER="30"` hoặc `PDA="G960NKSU9TEST2"`.
2. Sửa các dòng trong `build.prop` (bước 1 của script), ví dụ `ro.product.locale=vi-VN`.
3. Chạy `./build_demo_rom.sh`, rồi `./inspect_rom.sh out/odin/AP_*.tar.md5 out/unpacked`.
4. Bạn sẽ thấy thay đổi xuất hiện trong fingerprint, trong header của `boot.img` và trong `build.prop`.

## 8. Vì sao ROM mẫu không flash được?

- **Kernel là file chữ**, không phải nhân Linux thật, nên máy không khởi động được.
- **Chưa ký số:** khi bootloader đang khóa, Samsung chỉ chấp nhận ảnh có chữ ký của Samsung.
- **Thiếu driver thật** trong `vendor`.

Muốn mod ROM thật, bạn làm theo quy trình: mổ xẻ ROM gốc bằng `inspect_rom.sh` → sửa file →
đóng gói lại theo các lệnh ở mục 3 → mở khóa bootloader → tắt dm-verity (hoặc dùng Magisk) → flash.
Hãy luôn **sao lưu EFS** và giữ sẵn một bản ROM gốc để cứu máy khi bị bootloop.
