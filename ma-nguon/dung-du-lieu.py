# -*- coding: utf-8 -*-
"""SINH du-lieu/app-data.json — nguồn DUY NHẤT là thutuc.hanhchinhso.ai.vn.

    python tools/tai-nguon.py      ← tải về trước (cấu hình · menu · kho câu trả lời)
    python dung-du-lieu.py         ← rồi chạy cái này
    python dung-app.py             ← rồi ghép vào giao diện

ANH TRƯỜNG CHỐT 18/09/2026
──────────────────────────
App **chỉ dùng dữ liệu và nguồn thông tin của thutuc.hanhchinhso.ai.vn**, và giao diện
chính lấy theo giao diện của chính trang đó.

Kho 165 thủ tục niêm yết Quảng Ninh đã bị BỎ HẲN. Lý do không chỉ là "sếp bảo thế":
kho đó ghi số quầy, lệ phí và mã QR của một tỉnh khác, mà robot lại đứng ở Đắk Lắk.
Hai nguồn sự thật mâu thuẫn trong cùng một máy thì sớm muộn robot cũng đọc nhầm cái
sai — và không ai biết nó lấy từ đâu.

CẤU TRÚC SINH RA
────────────────
  don_vi · tinh · cap_nhat      — chỗ robot đứng (do Roboworld khai, không phải TTHC)
  nguon                         — ghi rõ lấy từ đâu, hiện trên màn hình
  thuong_hieu                   — tên · khẩu hiệu · BẢNG MÀU của họ
  linh_vuc[]                    — thẻ lớn màn chính, mỗi thẻ kèm mục con CỦA HỌ
  the_nhanh[]                   — quickAccess của họ
  vi_du[]                       — exampleQuestions của họ
  chua_phu[]                    — mảng họ chưa phủ, nói thẳng thay vì thẻ rỗng
  can_bo_sung[]                 — coverageMap.needed của họ, cho màn quản lý
  van_ban[]                     — coverageMap.have: 11 văn bản họ đã lập chỉ mục
  cach_dung[]                   — onboarding.steps của họ
  canh_bao                      — previewBanner của họ, NGUYÊN VĂN
  nap_truoc{}                   — câu trả lời đã nạp sẵn, khoá đã chuẩn hoá
"""
import io, json, os, sys, unicodedata

try:
    sys.stdout.reconfigure(encoding="utf-8")
except Exception:
    pass

HERE = os.path.dirname(os.path.abspath(__file__))
DU_LIEU = os.path.join(HERE, "du-lieu")

# ── Chỗ robot đứng. Đây là thứ DUY NHẤT không lấy từ máy chủ của họ, vì họ không
#    biết robot đặt ở đâu. Sửa hai dòng này khi đổi đơn vị sử dụng. ──
DON_VI = "Trung tâm Phục vụ Hành chính công xã Tây Hòa"
TINH = "Đắk Lắk"

F_CAU_HINH = os.path.join(DU_LIEU, "nguon-cau-hinh.json")
F_MENU = os.path.join(DU_LIEU, "nguon-menu.json")
F_KHO = os.path.join(DU_LIEU, "nguon-kho.json")
RA = os.path.join(DU_LIEU, "app-data.json")


def chuan_hoa(s):
    """PHẢI khớp khoaNapTruoc() bên khung-app.html và chuan_hoa() bên tai-nguon.py."""
    s = unicodedata.normalize("NFD", (s or "").lower())
    s = "".join(c for c in s if unicodedata.category(c) != "Mn").replace("đ", "d")
    return " ".join("".join(c if c.isalnum() or c.isspace() else " " for c in s).split())


def doc(duong, ten):
    if not os.path.isfile(duong):
        print("✗ Chưa có %s" % duong)
        print("  Chạy trước:  python tools/tai-nguon.py")
        raise SystemExit(2)
    return json.load(io.open(duong, encoding="utf-8"))


def main():
    ch = doc(F_CAU_HINH, "cấu hình")
    menu = doc(F_MENU, "menu")
    kho = doc(F_KHO, "kho") if os.path.isfile(F_KHO) else {"muc": {}}

    brand = ch.get("brand", {})
    mau = brand.get("accentPalette", {})

    # ── Lĩnh vực: thẻ lớn + mục con, kèm cờ ĐÃ NẠP cho từng mục ──
    linh_vuc = []
    for ma, v in menu.get("linh_vuc", {}).items():
        muc = []
        for m in v.get("muc", []):
            k = chuan_hoa(m["hoi"])
            muc.append({
                "nhan": m["nhan"],
                "hoi": m["hoi"],
                "san": k in kho.get("muc", {}),    # đã nạp sẵn → trả lời tức thì
            })
        linh_vuc.append({
            "ma": ma, "ten": v["ten"], "phu": v.get("phu", ""),
            "icon": v.get("icon", ""), "hoi": v.get("cau_moi", ""),
            "muc": muc,
        })

    # ── Kho nạp trước: khoá đã chuẩn hoá, giữ nguyên chữ của họ ──
    nap_truoc = {}
    for k, m in kho.get("muc", {}).items():
        nap_truoc[k] = {"hoi": m.get("hoi", ""), "dap": m.get("dap", ""),
                        "nguon": m.get("nguon", [])}

    cv = ch.get("coverageMap", {})
    d = {
        "don_vi": DON_VI,
        "tinh": TINH,
        "cap_nhat": ch.get("_tai_luc", ""),
        "nguon": "thutuc.hanhchinhso.ai.vn — Trợ Lý Hành Chính",
        "nguon_url": "https://thutuc.hanhchinhso.ai.vn",

        "thuong_hieu": {
            "ten": brand.get("name", ""),
            "khau_hieu": brand.get("tagline", ""),
            "mo_ta": brand.get("description", ""),
            "mau_chinh": mau.get("primary", "#C8102E"),
            "mau_phu": mau.get("secondary", "#FFCD00"),
            "mau_nen": mau.get("surface", "#FEF2F2"),
        },

        "linh_vuc": linh_vuc,
        "the_nhanh": [{"nhan": q.get("label", ""), "phu": q.get("subtitle", ""),
                       "icon": q.get("icon", ""), "hoi": q.get("query", "")}
                      for q in ch.get("quickAccess", [])],
        "vi_du": [{"nhan": e.get("label", ""), "icon": e.get("icon", ""),
                   "cau": e.get("questions", [])}
                  for e in ch.get("exampleQuestions", [])
                  if "demo" not in (e.get("label", "") or "").lower()],

        "chua_phu": [{"ten": t, "phu": p, "vi_sao": v} for t, p, v in CHUA_PHU],
        "can_bo_sung": [{"ten": x.get("shortLabel", ""), "vi_sao": x.get("rationale", "")}
                        for x in cv.get("needed", [])],
        "van_ban": [{"ma": x.get("shortLabel", ""), "phu": x.get("subtitle", ""),
                     "ten": x.get("fullTitle", "")} for x in cv.get("have", [])],
        "cho_ai": cv.get("spocLabel", ""),
        "cho_den": cv.get("spocEta", ""),

        "cach_dung": ch.get("onboarding", {}).get("steps", []),
        "canh_bao": ch.get("previewBanner", {}).get("vi", ""),

        "nap_truoc": nap_truoc,
    }

    # ══════════════ CỔNG CHẤT LƯỢNG ══════════════
    # Sinh ra file hỏng còn tệ hơn không sinh: app vẫn chạy, chỉ nội dung sai, mà
    # không tầng nào báo. Thà dừng ở đây.
    loi = []
    nhac = []   # cảnh báo MỀM: in ra nhưng không chặn sinh file
    if not d["thuong_hieu"]["ten"]:
        loi.append("cấu hình không có tên thương hiệu — tải lại /api/config")
    if not linh_vuc:
        loi.append("menu rỗng — chạy python tools/tai-nguon.py --chi-menu")
    for lv in linh_vuc:
        if not lv["muc"]:
            loi.append("lĩnh vực '%s' không có mục con nào" % lv["ten"])
        if len(lv["muc"]) > 6:
            loi.append("lĩnh vực '%s' có %d mục — màn robot chỉ vừa 6"
                       % (lv["ten"], len(lv["muc"])))
    # Dòng cảnh báo vẫn được LẤY VỀ và giữ trong app-data.json, nhưng thôi bắt buộc
    # phải hiện: anh Trường quyết 20/09/2026 gỡ nó khỏi màn hình robot.
    # Vẫn cảnh báo nhẹ nếu nguồn ngừng trả trường này — đó là tín hiệu họ đổi API.
    if not d["canh_bao"]:
        print("⚠ nguồn không còn trả previewBanner — kiểm lại /api/config")
    # ── Cổng MỀM: dòng phụ có hứa thứ menu không có không? ──
    #
    # Phát hiện 20/09/2026: thẻ "Hộ tịch khác" ghi phụ đề "Kết hôn · cải chính · khai tử"
    # và thẻ "Cư trú" ghi "Tạm trú · tạm vắng · thường trú", trong khi menu KHÔNG có mục
    # khai tử lẫn thường trú nào. Phụ đề là chữ tự viết, không lấy từ nguồn, nên không
    # tầng nào canh. Người dân đọc phụ đề rồi bấm vào, không thấy thứ vừa đọc.
    #
    # CỐ Ý để mềm: phụ đề viết theo tiếng người dân nên có khi đúng ý mà khác chữ
    # ("khai muộn" ↔ "đăng ký khai sinh quá hạn"). Máy không phán được chuyện đó — nó chỉ
    # nêu chỗ đáng ngờ để người viết tự rà.
    # Cụm ĐÃ RÀ TAY: cố ý khác chữ vì đó là tiếng người dân, không phải tên thủ tục
    # chính thức. Ghi kèm lý do để lần sau khỏi rà lại. Cổng vẫn canh mọi cụm khác.
    DA_RA = {
        "Cho con mới sinh": "tiếng dân của 'khai sinh đúng hạn'",
        "khai muộn":        "tiếng dân của 'khai sinh quá hạn'",
        "Sao y":            "tiếng dân của 'chứng thực bản sao từ bản chính'",
    }
    for lv in linh_vuc:
        nhan_gop = chuan_hoa(" | ".join(m.get("nhan", "") for m in lv["muc"]))
        for cum in [x.strip() for x in (lv.get("phu") or "").split("·")]:
            if cum and cum not in DA_RA and chuan_hoa(cum) not in nhan_gop:
                nhac.append("thẻ '%s' hứa \"%s\" ở dòng phụ — không mục con nào nhắc tới"
                            % (lv["ten"], cum))

    if loi:
        print("✗ DỪNG, không sinh file:")
        for x in loi:
            print("   · " + x)
        raise SystemExit(1)
    if nhac:
        print("⚠ Nên rà lại (không chặn sinh file):")
        for x in nhac:
            print("   · " + x)

    io.open(RA, "w", encoding="utf-8").write(json.dumps(d, ensure_ascii=False, indent=1))

    tong_muc = sum(len(v["muc"]) for v in linh_vuc)
    san = sum(1 for v in linh_vuc for m in v["muc"] if m["san"])
    print("✓ Nguồn: %s" % d["nguon"])
    print("✓ Màu:   %s · %s · %s" % (d["thuong_hieu"]["mau_chinh"],
                                     d["thuong_hieu"]["mau_phu"],
                                     d["thuong_hieu"]["mau_nen"]))
    print("✓ Menu:  %d lĩnh vực · %d mục con (%d đã nạp sẵn, %d còn hỏi trực tiếp)"
          % (len(linh_vuc), tong_muc, san, tong_muc - san))
    print("✓ Thêm:  %d thẻ nhanh · %d nhóm ví dụ · %d văn bản căn cứ"
          % (len(d["the_nhanh"]), len(d["vi_du"]), len(d["van_ban"])))
    if d["chua_phu"]:
        print("⚠ Mảng họ CHƯA phủ (app nói thẳng, không dựng thẻ):")
        for x in d["chua_phu"]:
            print("   · %s — %s" % (x["ten"], x["vi_sao"]))
    print("→ %s (%.2f MB)" % (RA, os.path.getsize(RA) / 1048576))


# Nhập bảng mảng chưa phủ từ script tải nguồn — một chỗ khai duy nhất
sys.path.insert(0, os.path.join(HERE, "tools"))
try:
    from importlib import import_module
    CHUA_PHU = import_module("tai-nguon".replace("-", "_")).CHUA_PHU
except Exception:
    # tên file có dấu gạch nối nên import thường không chạy — đọc thẳng bằng tay
    import re as _re
    _src = io.open(os.path.join(HERE, "tools", "tai-nguon.py"), encoding="utf-8").read()
    _m = _re.search(r"CHUA_PHU = \[(.*?)\n\]", _src, _re.S)
    CHUA_PHU = eval("[" + _m.group(1) + "]") if _m else []

if __name__ == "__main__":
    main()
