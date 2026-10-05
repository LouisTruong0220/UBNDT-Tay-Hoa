# -*- coding: utf-8 -*-
"""Đẩy GIAO DIỆN + DỮ LIỆU lên robot — không cần build lại APK.

    python tools/day-web.py              # cáp USB
    python tools/day-web.py --ip 192.168.1.50
    python tools/day-web.py --xem        # chỉ xem trên robot đang có gì
    python tools/day-web.py --khoa "D:/duong-dan/khoa.txt"   # đưa khoá TRA MẠNG lên robot
    python tools/day-web.py --xoa-khoa   # gỡ khoá → tầng tra mạng tự tắt

KHOÁ TRA MẠNG (tầng tra Google cho câu đời thường, thêm 22/09/2026)
───────────────────────────────────
File khoá: dòng 1 là khoá (AIza… = Gemini, gsk_… = Groq), dòng 2 tuỳ chọn là tên model.
Lên robot ở files/cau-hinh/tra-mang.txt. Không có file đó thì tầng tra mạng TẮT.
Khoá không bao giờ vào mã, APK hay git; script không in khoá ra màn hình.

VÌ SAO CÓ FILE NÀY
──────────────────
`day-len-robot.ps1` là bản chép từ dự án mẫu le-tan-roboworld: nó đẩy vào
/sdcard/roboworld-letan và chỉ mang theo media. App HCC đọc giao diện ở
/sdcard/Android/data/vn.roboworld.hcc/files/ — hai đường khác hẳn nhau, nên chạy
script cũ thì robot vẫn chạy bản giao diện cũ mà không báo gì.

Viết bằng Python thay vì PowerShell để tránh hai cái bẫy đã dính thật:
  · file .ps1 có chữ Việt phải UTF-8 CÓ BOM, không thì PS 5.1 báo lỗi ở dòng vô can
  · adb in tiến độ ra STDERR, PowerShell coi đó là lỗi và giết script giữa chừng

PHẢI ĐẾM HAI BÊN
────────────────
Bài học app Sourcing, dính hai lần: script chỉ đẩy rồi in "XONG" thì đẩy hụt vẫn
trông như thành công. Ở đây so SHA-1 từng file giữa máy tính và robot.
"""
import argparse, hashlib, io, os, subprocess, sys

try:
    sys.stdout.reconfigure(encoding="utf-8")
except Exception:
    pass

HERE = os.path.dirname(os.path.abspath(__file__))
APP = os.path.dirname(HERE)
ASSETS = os.path.join(APP, "android", "app", "src", "main", "assets")

GOI = "vn.roboworld.hcc"
GOC_RB = "/sdcard/Android/data/%s/files" % GOI

# Hai file app doc tu the nho TRUOC, roi moi roi ve ban trong APK.
# ⚠ HAI THU MUC KHAC NHAU — xem MainActivity.docUuTien():
#     khung   ← files/web/khung-app.html
#     du lieu ← files/du-lieu/app-data.json
# Day ca hai vao cung mot thu muc thi SHA-1 van khop, script van bao XONG,
# ma robot van chay du lieu cu. Dung dung mot duong cho ca hai.
TEP = [
    ("khung-app.html", "web/khung-app.html"),
    ("app-data.json", "du-lieu/app-data.json"),
]

ADB = os.path.expandvars(r"%LOCALAPPDATA%\Android\Sdk\platform-tools\adb.exe")


def adb(*args, **kw):
    """Gọi adb, trả (mã thoát, stdout, stderr). Không bao giờ ném."""
    r = subprocess.run([ADB] + list(args), capture_output=True, **kw)
    ra = r.stdout.decode("utf-8", "replace").strip()
    lo = r.stderr.decode("utf-8", "replace").strip()
    return r.returncode, ra, lo


def sha1_may(duong):
    h = hashlib.sha1()
    with open(duong, "rb") as f:
        for k in iter(lambda: f.read(65536), b""):
            h.update(k)
    return h.hexdigest()


def sha1_robot(duong):
    ma, ra, _ = adb("shell", "sha1sum", duong)
    if ma != 0 or not ra or "No such file" in ra:
        return None
    return ra.split()[0]


def main():
    p = argparse.ArgumentParser()
    p.add_argument("--ip", help="nối qua Wi-Fi thay vì cáp USB")
    p.add_argument("--xem", action="store_true", help="chỉ xem, không đẩy")
    p.add_argument("--khoa", help="file khoá tra mạng trên máy tính, đẩy lên thẻ nhớ robot")
    p.add_argument("--xoa-khoa", action="store_true", help="gỡ khoá tra mạng khỏi robot")
    a = p.parse_args()

    if not os.path.isfile(ADB):
        print("✗ Không thấy adb ở %s" % ADB)
        print("  Cần adb 1.0.41 của Android SDK. Bản C:\\Windows\\adb.exe là 1.0.39 của")
        print("  PUDU, lệch phiên bản với adb server nên hai bên giết tiến trình nhau.")
        return 2

    ma, ra, _ = adb("version")
    print("① adb: %s" % (ra.splitlines()[0] if ra else "?"))

    if a.ip:
        ma, ra, lo = adb("connect", "%s:5555" % a.ip)
        print("   %s" % (ra or lo))

    ma, ra, _ = adb("devices")
    may = [d.split("\t")[0] for d in ra.splitlines()[1:]
           if d.strip() and d.endswith("device")]
    if not may:
        print("\n✗ Không thấy robot nào.")
        print("  · Cáp USB cắm ở ĐẦU robot (không phải thân máy)")
        print("  · Robot phải bật 'Gỡ lỗi lâu dài' RỒI KHỞI ĐỘNG LẠI thì adb mới thấy")
        print("  · Hoặc nối Wi-Fi: python tools/day-web.py --ip <IP robot>")
        return 1
    if len(may) > 1:
        print("\n✗ Thấy %d máy: %s — rút bớt, script không đoán máy nào" % (len(may), may))
        return 1
    print("② Robot: %s" % may[0])

    # ── Xem trên robot đang có gì ──
    print("\n③ Trên robot đang có:")
    for t, duong in TEP:
        dd = "%s/%s" % (GOC_RB, duong)
        bam = sha1_robot(dd)
        if bam is None:
            print("   · %-18s CHƯA CÓ" % t)
        else:
            ma, ra, _ = adb("shell", "stat", "-c", "%s", dd)
            co = ra.strip() if ma == 0 else "?"
            print("   · %-18s %8s byte  %s…" % (t, co, bam[:12]))

    KHOA_RB = GOC_RB + "/cau-hinh/tra-mang.txt"
    co_khoa = sha1_robot(KHOA_RB) is not None
    print("   · %-18s %s" % ("khoá tra mạng", "CÓ — tầng tra mạng BẬT" if co_khoa
                                              else "không có — tầng tra mạng tắt"))

    if a.xoa_khoa:
        adb("shell", "rm", "-f", KHOA_RB)
        ok = sha1_robot(KHOA_RB) is None
        print("\n%s Gỡ khoá tra mạng" % ("✓" if ok else "✗"))
        adb("shell", "am", "force-stop", GOI)
        return 0 if ok else 1

    if a.khoa:
        if not os.path.isfile(a.khoa):
            print("\n✗ Không thấy file khoá: %s" % a.khoa); return 1
        dong = [d.strip() for d in io.open(a.khoa, encoding="utf-8").read().splitlines()
                if d.strip() and not d.strip().startswith("#")]
        if not dong or not (dong[0].startswith("AIza") or dong[0].startswith("gsk_")):
            print("\n✗ Dòng đầu file phải là khoá AIza… (Gemini) hoặc gsk_… (Groq)"); return 1
        nha = "Gemini" if dong[0].startswith("AIza") else "Groq"
        adb("shell", "mkdir", "-p", GOC_RB + "/cau-hinh")
        ma, ra, lo = adb("push", a.khoa, KHOA_RB)
        ok = ma == 0 and sha1_may(a.khoa) == sha1_robot(KHOA_RB)
        # KHÔNG in khoá — chỉ in nhà cung cấp và 4 ký tự đầu
        print("\n%s Khoá %s (%s…) %s" % ("✓" if ok else "✗", nha, dong[0][:4],
                                        "đã lên robot" if ok else "ĐẨY HỎNG"))
        adb("shell", "am", "force-stop", GOI)
        if ok: print("   Mở lại app BẰNG TAY từ màn hình chính RobotOS để nạp khoá.")
        return 0 if ok else 1

    if a.xem:
        return 0

    # ── Đẩy ──
    print("\n④ Đẩy lên robot:")
    for _, duong in TEP:
        adb("shell", "mkdir", "-p", "%s/%s" % (GOC_RB, duong.rsplit("/", 1)[0]))
    hong = 0
    for t, duong in TEP:
        nguon = os.path.join(ASSETS, t)
        if not os.path.isfile(nguon):
            print("   ✗ %-18s KHÔNG CÓ trên máy tính — chạy dung-app.py trước" % t)
            hong += 1
            continue
        dich = "%s/%s" % (GOC_RB, duong)
        ma, ra, lo = adb("push", nguon, dich)
        if ma != 0:
            print("   ✗ %-18s đẩy hỏng: %s" % (t, (lo or ra)[:60]))
            hong += 1
            continue
        # ĐẾM HAI BÊN — đẩy xong không có nghĩa là tới nơi nguyên vẹn
        bm, br = sha1_may(nguon), sha1_robot(dich)
        if bm != br:
            print("   ✗ %-18s SHA-1 LỆCH — máy %s… robot %s…"
                  % (t, bm[:12], (br or "trống")[:12]))
            hong += 1
        else:
            print("   ✓ %-18s %8d byte  %s…" % (t, os.path.getsize(nguon), bm[:12]))

    if hong:
        print("\n✗ %d file hỏng — robot đang chạy lẫn bản cũ bản mới, KHÔNG dùng được." % hong)
        return 1

    # ── Đóng app, NHƯNG KHÔNG TỰ MỞ LẠI ──
    #
    # ⚠ Script CỐ Ý không mở app hộ. Mọi cách mở từ dòng lệnh (am start, monkey)
    #   đều là phóng intent LAUNCHER, và hãng trả handleApiDisabled: app lên hình
    #   bình thường nhưng MẤT GIỌNG NÓI và mất dẫn đường. Nhìn từ ngoài giống hệt
    #   "bản mới bị lỗi", trong khi thật ra chỉ là mở sai đường.
    #   App PHẢI được mở bằng tay từ màn hình chính của RobotOS.
    print("\n⑤ Đã đóng app để nó nạp lại bản mới:")
    adb("shell", "am", "force-stop", GOI)
    print("   ✓ đã đóng")

    print("\n✓ ĐẨY XONG. Còn HAI việc phải làm TẠI MÀN HÌNH ROBOT:")
    print("   ① Mở app BẰNG TAY từ màn hình chính của RobotOS.")
    print("      ĐỪNG mở bằng adb — hãng chặn, app sẽ mất giọng nói và dẫn đường.")
    print("   ② Bấm giữ mặt biểu cảm 1,2 giây → màn quản lý. Dòng đầu phải ghi")
    print("      khung lấy từ THẺ NHỚ, không phải từ APK. Ghi APK là bản mới chưa vào.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
