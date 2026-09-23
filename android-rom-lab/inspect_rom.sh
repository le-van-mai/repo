#!/usr/bin/env bash
# =============================================================================
# inspect_rom.sh — Mổ xẻ firmware Samsung (định dạng Odin: BL/AP/CP/CSC .tar.md5)
# Dùng được cho cả ROM gốc thật lẫn ROM mẫu do build_demo_rom.sh tạo ra.
#
# Cần (Ubuntu): sudo apt install android-sdk-libsparse-utils lz4 cpio mkbootimg e2fsprogs
# Chạy:         ./inspect_rom.sh AP_G960N....tar.md5 [thư_mục_kết_quả]
#
# Không cần quyền root: file trong ảnh ext4 được lấy ra bằng debugfs, không mount.
# =============================================================================
set -euo pipefail

SRC="${1:?Cách dùng: $0 <file .tar hoặc .tar.md5> [thư_mục_kết_quả]}"
DEST="${2:-$(pwd)/unpacked_$(basename "${SRC%%.tar*}")}"

for t in tar lz4 simg2img debugfs unpack_bootimg cpio md5sum; do
  command -v "$t" >/dev/null || { echo "Thiếu công cụ: $t (xem đầu file để cài)"; exit 1; }
done
step() { printf '\n\033[1;36m==> %s\033[0m\n' "$*"; }

rm -rf "$DEST"; mkdir -p "$DEST"/{tar,images,files}

# ---------------------------------------------------------------------------
step "1. Kiểm tra MD5 (dòng cuối của file .tar.md5)"
if [[ "$SRC" == *.md5 ]]; then
  # Dòng cuối dạng "<md5>  <tên file>\n", nằm ngay sau phần đệm NUL của tar
  last_line="$(tail -n 1 "$SRC" | tr -d '\0' | grep -oE '[0-9a-f]{32}  .*$')"
  want="${last_line%% *}"
  got="$(head -c -"$(( $(printf '%s' "$last_line" | wc -c) + 1 ))" "$SRC" | md5sum | cut -d' ' -f1)"
  echo "    MD5 ghi trong file : $want"
  echo "    MD5 tính lại       : $got"
  [[ "$want" == "$got" ]] && echo "    → KHỚP, file không bị hỏng" || echo "    → KHÔNG KHỚP! File tải về bị lỗi"
else
  echo "    (file .tar thường, không có MD5)"
fi

# ---------------------------------------------------------------------------
step "2. Danh sách file bên trong tar (mỗi file ≈ một phân vùng)"
tar -tvf "$SRC" | awk '{printf "    %10s  %s\n", $3, $6}'
tar -xf "$SRC" -C "$DEST/tar"

# ---------------------------------------------------------------------------
step "3. Giải nén lz4 → sparse → ext4 thô"
is_sparse() { [[ "$(head -c4 "$1" | od -An -tx1 | tr -d ' \n')" == "3aff26ed" ]]; }
is_ext4()   { [[ "$(dd if="$1" bs=1 skip=1080 count=2 status=none | od -An -tx1 | tr -d ' \n')" == "53ef" ]]; }

shopt -s nullglob
for f in "$DEST"/tar/*; do
  [[ -f "$f" ]] || continue
  name="$(basename "$f")"
  out="$DEST/images/${name%.lz4}"
  if [[ "$name" == *.lz4 ]]; then lz4 -q -d -f "$f" "$out"; else cp "$f" "$out"; fi
  if is_sparse "$out"; then
    simg2img "$out" "$out.raw" && mv "$out.raw" "$out"
    kind="sparse → đã chuyển sang ext4 thô"
  elif is_ext4 "$out"; then kind="ext4"
  elif [[ "$(head -c8 "$out")" == "ANDROID!" ]]; then kind="Android boot image"
  else kind="nhị phân (bootloader/modem/...)"
  fi
  printf '    %-28s %8s  %s\n' "${name%.lz4}" "$(du -h "$out" | cut -f1)" "$kind"
done

# ---------------------------------------------------------------------------
step "4. Lấy file ra khỏi các ảnh ext4 (system, vendor, odm, ...)"
for img in "$DEST"/images/*.img.ext4 "$DEST"/images/*.img; do
  is_ext4 "$img" || continue
  part="$(basename "$img")"; part="${part%%.*}"
  mkdir -p "$DEST/files/$part"
  debugfs -R "rdump / $DEST/files/$part" "$img" >/dev/null 2>&1 || true
  echo "    $part: $(find "$DEST/files/$part" -type f | wc -l) file → files/$part/"
done

# ---------------------------------------------------------------------------
for img in "$DEST"/images/boot.img "$DEST"/images/recovery.img; do
  [[ -f "$img" ]] || continue
  part="$(basename "$img" .img)"
  step "5. Phân tích $part.img (header, kernel, ramdisk)"
  unpack_bootimg --boot_img "$img" --out "$DEST/files/$part" 2>/dev/null \
    | grep -E 'boot magic|header version|os version|os patch|kernel_size|ramdisk size|pagesize|command line|board' \
    | sed 's/^/    /' || true
  if [[ -f "$DEST/files/$part/ramdisk" ]]; then
    mkdir -p "$DEST/files/$part/ramdisk_files"
    ( cd "$DEST/files/$part/ramdisk_files" && \
      { gzip -dc ../ramdisk 2>/dev/null || lz4 -dc ../ramdisk 2>/dev/null || cat ../ramdisk; } \
      | cpio -id --quiet 2>/dev/null ) || true
    echo "    ramdisk: $(find "$DEST/files/$part/ramdisk_files" -type f | wc -l) file → files/$part/ramdisk_files/"
  fi
done

# ---------------------------------------------------------------------------
step "6. Thông số quan trọng trong build.prop"
mapfile -t props < <(find "$DEST/files" -name build.prop -not -path '*/ramdisk_files/*' | sort)
KEYS='ro\.(system\.)?build\.(fingerprint|id|display\.id|version\.(release|sdk|incremental|security_patch)|type|tags|PDA)|ro\.product\.(system\.|vendor\.)?(model|device|name|brand)|ro\.product\.board|ro\.board\.platform|ro\.hardware|ro\.sf\.lcd_density|ro\.product\.locale|dalvik\.vm\.heapsize|ro\.csc'
for p in "${props[@]}"; do
  echo "    --- ${p#"$DEST/files/"}"
  grep -E "^($KEYS)=" "$p" | sed 's/^/      /' || true
done
[[ ${#props[@]} -eq 0 ]] && echo "    (không tìm thấy build.prop — tar này có thể là BL/CP)"

# ---------------------------------------------------------------------------
step "7. Cấu trúc thư mục (2 cấp đầu mỗi phân vùng)"
for d in "$DEST"/files/*/; do
  echo "    [$(basename "$d")]"
  find "$d" -mindepth 1 -maxdepth 2 -not -path '*/ramdisk_files/*' -not -path '*/lost+found*' \
    | sed "s|$d||" | sort | head -40 | sed 's/^/      /'
done

echo; echo "Kết quả đầy đủ ở: $DEST"
