# -*- coding: utf-8 -*-
"""NẠP TRƯỚC câu trả lời của trợ lý ngoài vào kho trong robot.

    python tools/nap-truoc.py              # nạp tiếp những câu chưa có
    python tools/nap-truoc.py --xem        # chỉ xem còn thiếu bao nhiêu, không gọi mạng
    python tools/nap-truoc.py --toi-da 20  # chỉ nạp 20 câu rồi dừng

VÌ SAO PHẢI NẠP TRƯỚC THAY VÌ HỎI TRỰC TIẾP
───────────────────────────────────────────
Đo trên máy chủ thật 18/09/2026, sau khi hạn mức của họ vừa reset:

    Đăng ký khai sinh đúng hạn cần giấy tờ gì?   3.171 ký tự   47,9 giây
    Đăng ký tạm trú cần giấy tờ gì?              2.718 ký tự   44,1 giây
    Chứng thực chữ ký cần giấy tờ gì?            3.087 ký tự   56,0 giây
                                        trung bình 46,3 giây

Ba con số đó nói rằng KHÔNG THỂ hỏi trực tiếp lúc người dân đang đứng trước robot:

  · 46 giây — người dân đứng đợi tối đa bảy tám giây rồi bỏ đi. Hạn chờ của app là
    9 giây, nên mọi câu sẽ quá hạn và rơi về kho trong máy. Tầng trợ lý ngoài coi như
    không tồn tại, dù hạn mức còn nguyên.
  · 3.000 ký tự — đọc thành tiếng mất ba tới bốn phút. Không ai đứng nghe hết.
  · Hạn mức của họ tính theo chu kỳ (thông báo ghi "resets 1pm") và DÙNG CHUNG với mọi
    người đang mở trang đó. Robot phục vụ cả ngày thì sớm muộn cũng vét cạn phần chung.

Nhưng NỘI DUNG thì tốt thật: có mẫu đơn, có số hiệu thông tư, có trích dẫn nguồn, và
đúng địa bàn Đắk Lắk — những thứ kho 165 thủ tục trong robot không có.

→ Vậy dùng trợ lý ngoài làm NGUỒN DỮ LIỆU, không làm kho tra cứu trực tuyến.
  Hỏi trước, ở nhà, rải ra nhiều chu kỳ. Robot đọc kho đã nạp: tức thì, không cần mạng,
  không tốn lượt gọi nào của ai.

TÔN TRỌNG HẠN MỨC CỦA HỌ
────────────────────────
Hạn mức này là của bên khác và dùng chung với người dùng thật của trang đó. Script cố ý:
  · nghỉ giữa các câu (NGHI_GIAY), không bắn liên tiếp
  · DỪNG HẲN ngay khi gặp câu báo hết hạn mức — không thử lại, không vét
  · chạy tiếp được: lần sau chỉ hỏi những câu còn thiếu
"""
import io, json, os, sys, time, unicodedata, urllib.request

try:
    sys.stdout.reconfigure(encoding="utf-8")
except Exception:
    pass

HERE = os.path.dirname(os.path.abspath(__file__))
APP = os.path.dirname(HERE)
KHO = os.path.join(APP, "du-lieu", "kho-nap-truoc.json")
DS_CAU = os.path.join(APP, "du-lieu", "cau-hoi-nap-truoc.txt")

URL = "https://thutuc.hanhchinhso.ai.vn/api/chat/stream"
UA = "RoboworldNova/1.0 (robot le tan hanh chinh cong)"

NGHI_GIAY = 3           # nghỉ giữa hai câu — đừng bắn liên tiếp vào máy chủ của người ta
HAN_CHO = 120           # câu trả lời mất ~46 giây, cho rộng tay


def chuan_hoa(s):
    """Khoá tra cứu: bỏ dấu, thường hoá, bóp khoảng trắng. Cùng lối với app."""
    s = unicodedata.normalize("NFD", (s or "").lower())
    s = "".join(c for c in s if unicodedata.category(c) != "Mn").replace("đ", "d")
    return " ".join("".join(c if c.isalnum() or c.isspace() else " " for c in s).split())


def doc_kho():
    if os.path.isfile(KHO):
        return json.load(io.open(KHO, encoding="utf-8"))
    return {"nguon": URL, "cap_nhat": "", "muc": {}}


def ghi_kho(kho):
    kho["cap_nhat"] = time.strftime("%d/%m/%Y %H:%M")
    tam = KHO + ".tam"
    with io.open(tam, "w", encoding="utf-8") as f:
        json.dump(kho, f, ensure_ascii=False, indent=1)
    os.replace(tam, KHO)        # ghi nguyên tử: mất điện giữa chừng không mất kho


def hoi_mot_cau(q):
    """Trả về (answer, citations, giây). Ném lỗi nếu gọi hỏng."""
    than = json.dumps({"query": q, "language": "vi"}).encode("utf-8")
    rq = urllib.request.Request(URL, data=than, method="POST", headers={
        "Content-Type": "application/json; charset=utf-8",
        "Accept": "text/event-stream",
        "User-Agent": UA,
    })
    t0 = time.time()
    ten, ans, cit = "", None, []
    with urllib.request.urlopen(rq, timeout=HAN_CHO) as r:
        for dong in r:
            d = dong.decode("utf-8", "replace").rstrip("\n")
            if d.startswith("event:"):
                ten = d[6:].strip()
            elif d.startswith("data:") and ten == "answer":
                try:
                    o = json.loads(d[5:].strip())
                    ans = o.get("answer") or None
                    cit = o.get("citations") or []
                except Exception:
                    pass
            elif not d.strip():
                ten = ""
    return ans, cit, time.time() - t0


DAU_HIEU_HONG = ("hit your limit", "rate limit", "quota",
                 "internal server error", "something went wrong")


def cau_hong(ans):
    t = (ans or "").lower()
    return any(x in t for x in DAU_HIEU_HONG)


def doc_danh_sach():
    if not os.path.isfile(DS_CAU):
        print("✗ Không thấy %s" % DS_CAU)
        print("  File này là danh sách câu hỏi cần nạp, mỗi dòng một câu.")
        raise SystemExit(2)
    ra = []
    for dong in io.open(DS_CAU, encoding="utf-8"):
        d = dong.strip()
        if d and not d.startswith("#"):
            ra.append(d)
    return ra


def main():
    chi_xem = "--xem" in sys.argv
    toi_da = None
    if "--toi-da" in sys.argv:
        toi_da = int(sys.argv[sys.argv.index("--toi-da") + 1])

    ds = doc_danh_sach()
    kho = doc_kho()
    co = kho["muc"]

    thieu = [q for q in ds if chuan_hoa(q) not in co]
    print("Kho nạp trước: %s" % KHO)
    print("  đã có %d / %d câu · còn thiếu %d" % (len(ds) - len(thieu), len(ds), len(thieu)))
    if kho.get("cap_nhat"):
        print("  cập nhật gần nhất: %s" % kho["cap_nhat"])

    if chi_xem or not thieu:
        if not thieu:
            print("\n✓ Đã nạp đủ.")
        return

    if toi_da:
        thieu = thieu[:toi_da]
    print("\nSẽ hỏi %d câu, mỗi câu mất khoảng 46 giây → ước tính %d phút.\n"
          % (len(thieu), (len(thieu) * (46 + NGHI_GIAY)) // 60 + 1))

    xong = 0
    for i, q in enumerate(thieu, 1):
        try:
            ans, cit, giay = hoi_mot_cau(q)
        except Exception as e:
            print("  [%d/%d] ✗ %s — lỗi gọi: %s" % (i, len(thieu), q[:50], str(e)[:60]))
            continue

        if not ans:
            print("  [%d/%d] ✗ %s — máy chủ không trả câu nào" % (i, len(thieu), q[:50]))
            continue

        if cau_hong(ans):
            # ⚠ DỪNG HẲN. Hạn mức là của bên khác và dùng chung với người dùng thật của
            #   họ — gặp tường thì lùi, không thử lại, không vét.
            print("\n⚠ Máy chủ báo HẾT HẠN MỨC. Dừng tại đây, không thử thêm.")
            print("  Đã nạp được %d câu trong lượt này." % xong)
            print("  Chạy lại script sau khi hạn mức của họ hồi (thông báo ghi 'resets 1pm').")
            break

        co[chuan_hoa(q)] = {
            "hoi": q,
            "dap": ans,
            "nguon": [{"ten": c.get("docName", ""), "muc": c.get("section", "")}
                      for c in (cit or [])][:4],
            "luc": time.strftime("%Y-%m-%d %H:%M"),
        }
        xong += 1
        ghi_kho(kho)        # ghi ngay sau MỖI câu — mất mạng giữa chừng không mất công
        print("  [%d/%d] ✓ %-52s %5d ký tự %5.1fs %s"
              % (i, len(thieu), q[:50], len(ans), giay,
                 ("· %d nguồn" % len(cit)) if cit else ""))
        if i < len(thieu):
            time.sleep(NGHI_GIAY)

    print("\n→ %s" % KHO)
    print("  tổng cộng %d câu trong kho" % len(co))
    if xong:
        print("\nBước tiếp: python dung-app.py  (nhúng kho vào app)")


if __name__ == "__main__":
    main()
