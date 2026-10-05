# -*- coding: utf-8 -*-
"""Đối chiếu BA LỚP CHẶN giữa Kotlin (bản chạy trên robot) và bản giả lập trên máy tính.

    python tools/doi-chieu-lop-chan.py

⚠ VÌ SAO CẦN: ba biểu thức chặn — khẩn cấp · xin quyết định thay cán bộ · chưa có dữ liệu — nằm ở HAI nơi:
  MainApplication.kt và gia-lap-robot.js. Sửa một bên mà quên bên kia thì bản thử trên
  máy tính nói KHÁC robot thật, mà đó đúng là kiểu sai làm người ta yên tâm nhầm rồi
  mang máy ra bệnh viện mới vỡ. README dặn "sửa một bên phải sửa cả hai" từ lâu, nhưng
  dặn bằng lời thì không ai kiểm được — script này kiểm bằng máy.

  09/09/2026: sửa lớp "hỏi bệnh" (bỏ "chẩn đoán" đứng một mình, vì bệnh viện có hẳn
  Khoa Chẩn đoán hình ảnh) là phải sửa đúng hai chỗ. Đây là cổng canh việc đó.

Trả mã thoát 1 nếu hai bên lệch nhau.
"""
import io, os, re, sys

try:
    sys.stdout.reconfigure(encoding="utf-8")
except Exception:
    pass

HERE = os.path.dirname(os.path.abspath(__file__))
APP = os.path.dirname(HERE)
KT = os.path.join(APP, "android", "app", "src", "main", "java", "vn", "roboworld", "hcc",
                  "MainApplication.kt")
JS = os.path.join(APP, "gia-lap-robot.js")

TEN = ["TU_KHAN_CAP", "TU_XIN_QUYET_DINH", "TU_CHUA_CO",
       "TU_KHONG_TRA_MANG", "TU_CHINH_TRI"]   # hai cái sau: tầng tra mạng 22/09/2026

kt_nguon = io.open(KT, encoding="utf-8").read()
js_nguon = io.open(JS, encoding="utf-8").read()


def boc_kotlin(ten):
    """Ghép các mẩu chuỗi trong Regex("..." + "..." + ...), bỏ chú thích."""
    i = kt_nguon.index("private val %s = Regex(" % ten)
    j = kt_nguon.index("RegexOption", i)
    than = kt_nguon[i:j]
    than = re.sub(r"/\*.*?\*/", " ", than, flags=re.S)   # chú thích khối
    than = re.sub(r"//[^\n]*", " ", than)                 # chú thích dòng
    # Mẩu chuỗi = phần nằm giữa cặp dấu nháy kép; tách bằng cách đếm dấu nháy
    manh = than.split('"')
    return "".join(manh[k] for k in range(1, len(manh), 2))


def boc_js(ten):
    i = js_nguon.index("var %s = /" % ten)
    dau = js_nguon.index("/", i + len("var %s = " % ten)) + 1
    cuoi = js_nguon.index("/i;", dau)
    return js_nguon[dau:cuoi]


hong = 0
for ten in TEN:
    a = boc_kotlin(ten)
    b = boc_js(ten)
    if a == b:
        print("✓ %-14s khớp từng ký tự (%d ký tự)" % (ten, len(a)))
        continue
    hong += 1
    print("✗ %s LỆCH giữa Kotlin và giả lập" % ten)
    # So theo từng nhánh cho dễ nhìn — biểu thức dài, nhìn nguyên chuỗi không ra
    ka = [x.strip() for x in a.split("|")]
    jb = [x.strip() for x in b.split("|")]
    chi_kt = [x for x in ka if x not in jb]
    chi_js = [x for x in jb if x not in ka]
    if chi_kt:
        print("   chỉ có bên KOTLIN : %s" % " | ".join(chi_kt))
    if chi_js:
        print("   chỉ có bên GIẢ LẬP: %s" % " | ".join(chi_js))
    if not chi_kt and not chi_js:
        print("   cùng tập nhánh nhưng khác thứ tự hoặc khoảng trắng")

print()
if hong:
    print("✗ %d lớp chặn lệch nhau — sửa cho khớp rồi chạy lại." % hong)
    print("  Kotlin : %s" % KT)
    print("  Giả lập: %s" % JS)
    raise SystemExit(1)
print("✓ Cả %d lớp chặn khớp nhau giữa robot thật và bản thử trên máy tính." % len(TEN))
