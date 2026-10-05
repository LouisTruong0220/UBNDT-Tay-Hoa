/**
 * Thử ĐIỆU MÚA — bóc thẳng bảng phách + hàm dongTacCuaPhach từ khung-app.html, mô phỏng cả bài.
 *
 *   node tools/thu-vu-dao.mjs
 *
 * Canh bốn thứ mà nhìn robot múa không bắt kịp:
 *   ① mỗi ô nhịp tổng góc xoay = 0 → robot không trôi hướng sau mỗi ô
 *   ② lệnh cùng bộ phận (thân / đầu) cách nhau đủ lâu để lệnh trước xong — theo số đo robot
 *   ③ đầu gật lên bao nhiêu thì xuống bấy nhiêu
 *   ④ mức nhẹ không lắc thân, mức mạnh có lắc — đúng thiết kế
 * Cuối có THỬ NGƯỢC: làm hỏng tính đối xứng, phép ① phải đỏ.
 */
import fs from 'node:fs';
import vm from 'node:vm';

const khung = fs.readFileSync(new URL('../khung-app.html', import.meta.url), 'utf8');
const lay = (a, b) => { const i = khung.indexOf(a), j = khung.indexOf(b, i); if (i < 0 || j < 0) throw new Error('Khong thay ' + a); return khung.slice(i, j); };
const vuDao = lay('const VU_DAO =', '/*__HET_VU_DAO__*/');
const ham = lay('function mucNangLuong', 'function nhun(');

function chay(maNguon) {
  const hop = { doiMat: [], console };
  vm.createContext(hop);
  vm.runInContext(vuDao.replace('const VU_DAO', 'var VU_DAO') +
    '\nvar mucO = 0, gocO = 18;\nvar MAT_THEO_MUC = [["a"],["b"],["c","d"]];\n' +
    'function doiMatSang(s){ doiMat.push(s); }\n' + maNguon, hop);
  const P = hop.VU_DAO.p;
  const lenh = [];
  for (let i = 0; i < P.length; i++) lenh.push({ t: P[i][0], ds: hop.dongTacCuaPhach(i), muc: hop.mucO });
  return { V: hop.VU_DAO, lenh };
}

let dat = 0, hong = 0;
const phep = (ten, ok, them = '') => { ok ? dat++ : hong++; console.log(`${ok ? '✓' : '✗'} ${ten}${them ? '  — ' + them : ''}`); };

function kiem(maNguon, im = false) {
  const { V, lenh } = chay(maNguon);
  const goc = ma => ma[0] === 'L' ? +ma.slice(1) : ma[0] === 'R' ? -ma.slice(1) : 0;
  // ① tổng góc từng ô
  const o = {}; lenh.forEach((x, i) => { const k = Math.floor((i - V.pha) / 4); o[k] = (o[k] || 0) + x.ds.reduce((s, m) => s + goc(m), 0); });
  const lech = Object.entries(o).filter(([, g]) => g !== 0);
  // ② khoảng cách giữa hai lệnh CÙNG BỘ PHẬN — theo số đo trên robot 24/09/2026:
  //    xoay 12° mất ~0,85 s; gật đầu cách 1 phách (0,46 s) là lệnh sau cắt lệnh trước.
  const THAN_TOI_THIEU = 0.88, DAU_TOI_THIEU = 0.85;
  let tThan = -9, tDau = -9, sat = [];
  lenh.forEach(x => x.ds.forEach(m => {
    if (m[0] === 'L' || m[0] === 'R') { if (x.t - tThan < THAN_TOI_THIEU) sat.push('thân@' + x.t.toFixed(2)); tThan = x.t; }
    if (m === 'U' || m === 'D') { if (x.t - tDau < DAU_TOI_THIEU) sat.push('đầu@' + x.t.toFixed(2)); tDau = x.t; }
  }));
  // ③ đầu
  const dau = lenh.reduce((s, x) => s + x.ds.filter(m => m === 'U').length - x.ds.filter(m => m === 'D').length, 0);
  if (im) return { lech, sat, dau };
  phep('① Mỗi ô nhịp tổng góc xoay = 0', !lech.length, lech.length ? lech.length + ' ô lệch' : Object.keys(o).length + ' ô');
  phep('② Lệnh cùng bộ phận cách nhau đủ để xong (thân ≥ 0,88 s · đầu ≥ 0,85 s — số đo robot)', !sat.length, sat.slice(0, 5).join(', '));
  phep('③ Đầu gật lên = gật xuống', dau === 0, 'chênh ' + dau);
  const muc = [0, 1, 2].map(m => lenh.filter(x => x.muc === m));
  const coThan = xs => xs.some(x => x.ds.some(m => m[0] === 'L' || m[0] === 'R'));
  phep('④ Mức nhẹ KHÔNG lắc thân', !coThan(muc[0]), muc[0].length + ' phách nhẹ');
  phep('④ Mức mạnh: MỌI phách đều có chuyển động (thân và đầu thay phiên)', muc[2].length > 0 && muc[2].every(x => x.ds.length), muc[2].length + ' phách mạnh');
  phep('④ Mức mạnh có lắc thân', coThan(muc[2]));
  phep('Phách đều (129 BPM ± 5%)', V.bpm > 122 && V.bpm < 136, V.bpm + ' BPM');
  const soL = lenh.reduce((s, x) => s + x.ds.length, 0);
  console.log(`   ${lenh.length} phách · ${soL} động tác · nhẹ ${muc[0].length} / vừa ${muc[1].length} / mạnh ${muc[2].length} phách`);
}

console.log('── Điệu múa trên bảng phách thật ──');
kiem(ham);

console.log('\n── Thử ngược: làm lệch một ô (trái·phải → trái·trái), phép ① phải bắt được ──');
const hong1 = ham.replace("return [[L], ['U'], [R], ['D']][k];", "return [[L], ['U'], [L], ['D']][k];");
if (hong1 === ham) throw new Error('Thu nguoc khong thay mau de sua');
const r = kiem(hong1, true);
phep('Gỡ tính đối xứng → phép ① đỏ', r.lech.length > 0, r.lech.length + ' ô lệch');

console.log('\n── Thử ngược: trả về vũ đạo cũ (lắc MỖI phách), phép ② phải bắt được ──');
const hong2 = ham.replace("return [[L], ['U'], [R], ['D']][k];", "return [[L, 'U'], [R, 'D'], [R, 'U'], [L, 'D']][k];");
const r2 = kiem(hong2, true);
phep('Lắc mỗi phách → phép ② đỏ', r2.sat.length > 0, r2.sat.length + ' lệnh sát nhau');

console.log('\n' + (hong ? `✗ ${hong} phép HỎNG / ${dat + hong}` : `✓ Cả ${dat} phép thử đạt.`));
process.exit(hong ? 1 : 0);
