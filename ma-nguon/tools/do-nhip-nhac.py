# -*- coding: utf-8 -*-
"""Dò phách một bài nhạc → du-lieu/vu-dao.json cho điệu múa của robot.

    python tools/do-nhip-nhac.py nhac/mua.mp3

Không dùng librosa (máy không có) — numpy thuần:
  ① độ vọt phổ (spectral flux trên log-biên-độ STFT) = "đường khởi âm"
  ② tempo = đỉnh tự tương quan của đường khởi âm, có nghiêng về 120 BPM (log-Gauss)
  ③ bám phách bằng quy hoạch động (Ellis 2007): mỗi phách chọn điểm khởi âm mạnh nhưng
     phạt khi khoảng cách lệch khỏi chu kỳ tempo
  ④ phách mạnh của ô nhịp 4/4 = pha (0..3) có tổng khởi âm cao nhất
  ⑤ năng lượng (RMS) từng phách, chuẩn hoá 0..1 → lớp web chọn động tác + biểu cảm

Robot KHÔNG tự đếm giờ: lớp web đọc audio.currentTime và bắn động tác đúng thời điểm
phách trong tệp này — nhạc chậm nạp nửa giây thì động tác chậm theo, không lệch phách.
"""
import io, json, os, subprocess, sys
import numpy as np
sys.stdout.reconfigure(encoding="utf-8")

HERE = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
tep = sys.argv[1] if len(sys.argv) > 1 else os.path.join(HERE, "nhac", "mua.mp3")
SR = 22050

raw = subprocess.run(["ffmpeg", "-v", "error", "-i", tep, "-ac", "1", "-ar", str(SR),
                      "-f", "s16le", "-"], capture_output=True, check=True).stdout
x = np.frombuffer(raw, np.int16).astype(np.float32) / 32768.0
dai = len(x) / SR

# ① đường khởi âm
N, HOP = 2048, 512
cua = np.hanning(N).astype(np.float32)
so_khung = 1 + (len(x) - N) // HOP
khung = np.lib.stride_tricks.as_strided(x, (so_khung, N), (x.strides[0] * HOP, x.strides[0]))
S = np.log1p(100 * np.abs(np.fft.rfft(khung * cua, axis=1)))
flux = np.maximum(0, np.diff(S, axis=0)).sum(axis=1)
flux = np.concatenate([[0], flux])
fps = SR / HOP
# bỏ nền trôi chậm
nen = np.convolve(flux, np.ones(int(fps)) / int(fps), mode="same")
on = np.maximum(0, flux - nen)
on /= (on.std() + 1e-9)

# ② tempo
ac = np.correlate(on, on, "full")[len(on) - 1:]
lags = np.arange(len(ac))
bpm_lag = 60 * fps / np.maximum(lags, 1)
nghieng = np.exp(-0.5 * (np.log2(bpm_lag / 120.0) / 0.9) ** 2)
lo, hi = int(60 * fps / 200), int(60 * fps / 60)
diem = ac * nghieng
lag = lo + int(np.argmax(diem[lo:hi]))
bpm = 60 * fps / lag
chu_ky = lag  # khung/phách

# ③ bám phách — quy hoạch động
alpha = 100.0
tot = on.copy(); truoc = -np.ones(len(on), int)
for i in range(len(on)):
    a, b = max(0, i - 2 * chu_ky), i - chu_ky // 2
    if b <= a: continue
    js = np.arange(a, b)
    phat = -alpha * (np.log((i - js) / chu_ky)) ** 2
    k = np.argmax(tot[js] + phat)
    tot[i] = on[i] + tot[js][k] + phat[k]
    truoc[i] = js[k]
cuoi = int(np.argmax(tot[-2 * chu_ky:])) + len(on) - 2 * chu_ky
phach = [cuoi]
while truoc[phach[-1]] >= 0: phach.append(truoc[phach[-1]])
phach = np.array(phach[::-1])
t_phach = phach / fps

# ④ phách mạnh
pha = int(np.argmax([on[phach[p::4]].sum() for p in range(4)]))

# ⑤ năng lượng từng phách
rms = np.sqrt(np.convolve(x ** 2, np.ones(HOP) / HOP, mode="same"))[::HOP][:len(on)]
nl = np.array([rms[phach[i]:phach[i + 1] if i + 1 < len(phach) else len(rms)].mean()
               for i in range(len(phach))])
nl = (nl - np.percentile(nl, 10)) / (np.percentile(nl, 95) - np.percentile(nl, 10) + 1e-9)
nl = np.clip(nl, 0, 1)
# làm mượt theo ô nhịp — đổi động tác theo từng ô, không nhảy lung tung từng phách
nl_o = np.convolve(nl, np.ones(4) / 4, mode="same")

do_ro = float(ac[lag] / ac[0])
ra = {
    "tep": os.path.basename(tep),
    "dai_giay": round(dai, 3),
    "bpm": round(float(bpm), 2),
    "do_ro_nhip": round(do_ro, 3),
    "pha_phach_manh": pha,
    "phach": [{"t": round(float(t), 3), "manh": (i - pha) % 4 == 0,
               "nang_luong": round(float(nl_o[i]), 3)} for i, t in enumerate(t_phach)],
}
dich = os.path.join(HERE, "du-lieu", "vu-dao.json")
io.open(dich, "w", encoding="utf-8").write(json.dumps(ra, ensure_ascii=False, indent=1))
kc = np.diff(t_phach)
print(f"{ra['tep']}: {dai:.1f} giây · ~{bpm:.1f} BPM · độ rõ nhịp {do_ro:.2f}")
print(f"{len(t_phach)} phách · phách đầu {t_phach[0]:.2f}s · khoảng cách TB {kc.mean()*1000:.0f} ms (lệch chuẩn {kc.std()*1000:.0f} ms)")
print(f"phách mạnh ở pha {pha} · năng lượng theo ô nhịp:")
for o in range(0, len(t_phach), 4):
    print(f"  ô {o//4+1:2d} @ {t_phach[o]:5.2f}s  " + "█" * int(round(nl_o[o] * 20)))
print("→", dich)

# Ghi thẳng vào khung-app.html — lớp web bắn động tác theo bảng này. Gọn: [giây, mạnh 0/1, năng lượng].
KHUNG = os.path.join(HERE, "khung-app.html")
k = io.open(KHUNG, encoding="utf-8").read()
A, B = "/*__BAT_DAU_VU_DAO__*/", "/*__HET_VU_DAO__*/"
if A in k and B in k:
    gon = {"tep": ra["tep"], "dai": ra["dai_giay"], "bpm": ra["bpm"], "pha": pha,
           "p": [[x["t"], 1 if x["manh"] else 0, x["nang_luong"]] for x in ra["phach"]]}
    i, j = k.index(A) + len(A), k.index(B)
    k = k[:i] + "\nconst VU_DAO = " + json.dumps(gon, ensure_ascii=False, separators=(",", ":")) + ";\n" + k[j:]
    io.open(KHUNG, "w", encoding="utf-8").write(k)
    print("→ đã ghi VU_DAO vào khung-app.html (%d phách)" % len(gon["p"]))
else:
    print("! khung-app.html chưa có mốc VU_DAO — chưa ghi")
