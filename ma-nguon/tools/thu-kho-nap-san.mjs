/**
 * Thử TRA KHO NẠP SẴN với câu người dân NÓI THẬT — không lặp đúng từng chữ như trong kho.
 *
 *   node tools/thu-kho-nap-san.mjs
 *
 * Sinh ra từ buổi thử máy thật 22/09/2026: anh Trường nói "Thủ tục chấm dứt hoạt động
 * kinh doanh cần giấy tờ gì?", kho lưu "…hoạt động HỘ kinh doanh…". Tra theo chuỗi trượt,
 * robot chờ nguồn 9 giây rồi đáp "chưa có" cho thủ tục đang nằm sẵn trong máy.
 *
 * Chạy bằng KHO THẬT (du-lieu/app-data.json) và bóc hàm từ CHÍNH khung-app.html.
 * Phần quan trọng không kém là các câu PHẢI TRƯỢT: khớp nhầm sang thủ tục khác là robot
 * đọc nguyên một thủ tục sai — tệ hơn nói "chưa có".
 */
import fs from 'node:fs';
import vm from 'node:vm';

const APP = 'D:/RBW_Claude/11-app-nova/hcc-trung-tam/';
const html = fs.readFileSync(APP + 'khung-app.html', 'utf8');
const D = JSON.parse(fs.readFileSync(APP + 'du-lieu/app-data.json', 'utf8'));

// khongDau nằm NGOÀI mốc bóc — bóc riêng
const mKd = html.match(/function khongDau\(s\)\{[\s\S]*?\n\}/);
const mLoc = html.match(/__BAT_DAU_LOC_NGOAI__[\s\S]*?\*\/([\s\S]*?)\/\*__HET_LOC_NGOAI__\*\//);
if (!mKd || !mLoc) { console.error('Không bóc được hàm'); process.exit(2); }
const boi = { console, D };
vm.createContext(boi);
vm.runInContext(mKd[0] + '\n' + mLoc[1] + '\n;globalThis.tra = traKhoNapTruoc;', boi);
const tra = boi.tra;

const tenCua = r => (r.hien.match(/Tra cứu thủ tục:\s*([^\n*]+)/i) || [, (r.hien.split('\n')[0] || '')])[1].trim();

// [câu nói thật, chữ PHẢI có trong tên thủ tục khớp được | null = PHẢI TRƯỢT]
const PHEP = [
  // ── câu anh Trường nói trên máy thật 22/09 ──
  ['Thủ tục chấm dứt hoạt động kinh doanh cần giấy tờ gì?', 'Chấm dứt'],
  // ── lệch chữ kiểu người nói tự nhiên ──
  ['Tạm ngừng kinh doanh hộ kinh doanh cá thể cần gì',      'ạm ngừng'],
  ['Đăng ký khai tử cho người nhà cần giấy tờ gì',           'hai tử'],
  ['thủ tục tách thửa đất',                                  'ách thửa'],
  ['Chứng thực chữ ký người dịch làm thế nào',               'người dịch'],

  // ── PHẢI TRƯỢT: khớp nhầm là đọc sai thủ tục ──
  ['Đăng ký thủ tục có khó không?',                          null],
  // câu cụt một mảng → ĐÚNG là ra menu hỏi lại của chính mảng đó, không phải một thủ tục
  ['Kinh doanh',                                             'MENU:kinh doanh'],
  ['Tôi muốn hỏi về đất',                                    null],
  ['Làm giấy tờ gì ở đây',                                   null],
  ['Nuôi con nuôi cần giấy tờ gì',                           null],   // kho KHÔNG có
];

let dat = 0, hong = 0;
for (const [cau, mong] of PHEP) {
  const r = tra(cau);
  let ok, ghi;
  if (typeof mong === 'string' && mong.startsWith('MENU:')) {
    ok = r.dung && /Cần làm rõ/.test(r.hien) && r.hien.toLowerCase().includes(mong.slice(5));
    ghi = r.dung ? (ok ? 'menu hỏi lại đúng mảng' : 'SAI: ' + tenCua(r).slice(0, 50)) : 'KHÔNG KHỚP';
  } else if (mong === null) { ok = !r.dung; ghi = r.dung ? 'KHỚP NHẦM → ' + tenCua(r).slice(0, 50) : 'trượt đúng'; }
  else { ok = r.dung && tenCua(r).includes(mong); ghi = r.dung ? tenCua(r).slice(0, 60) : 'KHÔNG KHỚP'; }
  if (ok) dat++; else hong++;
  console.log(`${ok ? '✓' : '✗'} ${cau.padEnd(58)} → ${ghi}`);
}

// ── Mục kho là MENU hỏi lại: lời đọc không được mang ký hiệu, và phải có nút bấm ──
// Đo trên robot 22/09/2026: robot đọc nguyên "}}" và "-" ra loa, màn không có nút.
{
  const r = tra('Tôi mới sinh con, làm giấy khai sinh cần gì?');
  const sach = r.dung && !/[{}]|\[\[|^\s*-/m.test(r.doc);
  const nut = r.dung && Array.isArray(r.lua_chon) && r.lua_chon.length >= 2;
  for (const [ten, ok, ghi] of [
    ['Menu từ kho: lời ĐỌC không còn ký hiệu {{ }} [[ ]] -', sach, r.doc?.slice(0, 70)],
    ['Menu từ kho: trả kèm NÚT lựa chọn', nut, (r.lua_chon || []).length + ' nút'],
  ]) { if (ok) dat++; else hong++; console.log(`${ok ? '✓' : '✗'} ${ten}  — ${ghi}`); }
}

// Thử NGƯỢC: gỡ bước 3 thì câu thật của anh Trường phải trượt lại
const khongB3 = mLoc[1].replace('if(nhat && diemNhat - diemNhi >= 0.15) muc = kho[nhat];', '');
const b2 = { console, D }; vm.createContext(b2);
vm.runInContext(mKd[0] + '\n' + khongB3 + '\n;globalThis.tra = traKhoNapTruoc;', b2);
const lai = !b2.tra('Thủ tục chấm dứt hoạt động kinh doanh cần giấy tờ gì?').dung;
if (lai) dat++; else hong++;
console.log(`${lai ? '✓' : '✗'} THỬ NGƯỢC: gỡ bước 3 thì câu thật phải trượt lại`);

console.log('\n' + (hong ? `✗ ${hong} phép HỎNG / ${dat + hong}` : `✓ Cả ${dat} phép thử đạt.`));
process.exit(hong ? 1 : 0);
