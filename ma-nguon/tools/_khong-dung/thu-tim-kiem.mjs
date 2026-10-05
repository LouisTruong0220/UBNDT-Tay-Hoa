/**
 * BỘ THỬ TRA CỨU THỦ TỤC — chạy trên máy tính, không cần robot.
 *
 *   node tools/tthc/thu-tim-kiem.mjs
 *   node tools/tthc/thu-tim-kiem.mjs --chi-tiet     # in cả điểm và độ phủ từng câu
 *
 * Vì sao có file này: ngày 11/08/2026 robot trả lời "đăng ký kinh doanh" bằng thủ tục
 * CẤP GIẤY PHÉP SẢN XUẤT RƯỢU, và đọc to lên. Nguyên nhân là bộ tìm kiếm cắt kết quả
 * bằng ngưỡng tương đối (`kq[0].d * 0.42`) nên không bao giờ biết nói "tôi không biết".
 * Sau khi thêm ngưỡng tuyệt đối + độ phủ từ, phải có chỗ chỉnh ba con số đó BẰNG SỐ
 * chứ không phải bằng cảm giác. Đây là chỗ đó.
 *
 * Bộ thử chạy trên CHÍNH đoạn mã đang chạy trên robot: nó bóc khối giữa hai mốc
 * __BAT_DAU_BO_TIM__ và __HET_BO_TIM__ trong khung-app.html rồi nạp vào Node. Không
 * chép lại dòng nào, nên không có chuyện bản thử và bản chạy thật lệch nhau.
 */

import { readFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import { dirname, resolve } from 'node:path';
import vm from 'node:vm';

/* Bộ thử nằm ngay trong dự án: đọc khung-app.html và app-data.json của CHÍNH app này,
   không trỏ sang app khác. Trỏ sang app khác là thử một bản mã không ai chạy. */
const duAn = resolve(dirname(fileURLToPath(import.meta.url)), '..');

/* ── Bóc bộ tìm kiếm ra khỏi khung-app.html ── */
const html = readFileSync(resolve(duAn, 'khung-app.html'), 'utf8');
const m = html.match(/\/\*__BAT_DAU_BO_TIM__[\s\S]*?\*\/([\s\S]*?)\/\*__HET_BO_TIM__\*\//);
if (!m) {
  console.error('Không tìm thấy mốc __BAT_DAU_BO_TIM__ / __HET_BO_TIM__ trong khung-app.html.');
  console.error('Ai đó đã xoá hoặc đổi tên hai mốc này — xem chú thích ngay trên chúng.');
  process.exit(2);
}

const D = JSON.parse(readFileSync(resolve(duAn, 'du-lieu/app-data.json'), 'utf8'));
const boi = { D, TT: D.thu_tuc, console };
vm.createContext(boi);
vm.runInContext(m[1] + '\ndungChiMuc();', boi, { filename: 'bo-tim-kiem.js' });

/* ══════════════════════════════════════════════════════════════════════
   40 CÂU THỬ — viết theo đúng lời người dân nói ở quầy, không phải tên
   thủ tục trong văn bản.

   Cột mong đợi:
     'ngoai-tham-quyen' — cấp xã không làm việc này, phải nói thẳng
     'khong-thay'       — kho chưa có, phải nói thẳng
     'chac'             — được phép đọc to
     'chac|chua-chac'   — chấp nhận cả hai; miễn là ứng viên đầu đúng
   Cột `phai_co`: một cụm BẮT BUỘC có trong tên thủ tục đứng đầu.
   Cột `cam`: cụm TUYỆT ĐỐI không được xuất hiện trong tên thủ tục đứng đầu.
   Cột `trong_top3` (tuỳ chọn): cụm phải xuất hiện ở MỘT TRONG BA ứng viên. Dùng cho
     câu mà xếp hạng số 1 chưa chắc đúng nhưng robot chỉ hỏi lại chứ không khẳng
     định — lúc đó điều quan trọng là câu trả lời đúng CÓ trên màn hình.
   ══════════════════════════════════════════════════════════════════════ */
const THU = [
  // ── Việc cấp xã KHÔNG tiếp nhận: đã đếm 0/165, phải nói thẳng ──
  ['tôi muốn đăng ký kinh doanh',            'ngoai-tham-quyen', '', 'rượu'],
  ['đăng ký hộ kinh doanh cá thể',           'ngoai-tham-quyen', '', 'rượu'],
  ['xin giấy phép kinh doanh',               'ngoai-tham-quyen', '', 'rượu'],
  ['tôi muốn thành lập doanh nghiệp',        'ngoai-tham-quyen', '', ''],
  ['đăng ký mã số thuế',                     'ngoai-tham-quyen', '', ''],

  // ── Kho chưa nạp / hỏi vu vơ: không được vơ đại một thủ tục ──
  ['hôm nay trời thế nào',                   'khong-thay', '', ''],
  ['bao giờ thì hết giờ làm việc',           'khong-thay', '', ''],
  ['tôi cần',                                'khong-thay', '', ''],
  ['cho tôi hỏi một chút',                   'khong-thay', '', ''],
  ['quán ăn ngon gần đây',                   'khong-thay', '', ''],

  // ── Đất đai ──
  ['sổ đỏ nhà tôi ghi sai tên',              'chac|chua-chac', 'chính',  'rượu'],
  ['tôi muốn làm sổ đỏ',                     'chac|chua-chac', 'đất',    'rượu'],
  ['xin cấp giấy chứng nhận quyền sử dụng đất', 'chac|chua-chac', 'đất',  ''],
  ['đính chính giấy chứng nhận',             'chac',           'chính',  ''],
  ['tôi muốn xin phép xây nhà',              'chac|chua-chac', 'xây',    ''],

  // ── Trợ cấp, hộ nghèo ──
  ['tôi muốn xin hộ nghèo',                  'chac|chua-chac', 'nghèo',  ''],
  ['xin công nhận hộ cận nghèo',             'chac|chua-chac', 'nghèo',  ''],
  ['xin trợ cấp xã hội hàng tháng',          'chac|chua-chac', 'trợ cấp',''],
  ['nhà tôi có người mất, xin hỗ trợ mai táng', 'chac|chua-chac', 'táng', ''],

  // ── Kinh doanh có điều kiện — kho CÓ, phải ra đúng ──
  ['tôi muốn xin giấy phép bán lẻ rượu',     'chac',           'rượu',   ''],
  ['nấu rượu thủ công để bán',               'chac|chua-chac', 'rượu',   ''],
  ['mở cửa hàng bán bình gas',               'chac|chua-chac', 'LPG',    'rượu'],

  // ── Trẻ em, con nuôi ──
  ['tôi muốn nhận con nuôi',                 'chac|chua-chac', 'con nuôi',''],
  ['đăng ký nhận chăm sóc thay thế trẻ em',  'chac|chua-chac', 'trẻ em', ''],

  // ── Học hành ──
  ['xin cho con vào trường mầm non',         'chac|chua-chac', 'mầm non',''],
  ['thành lập trường mẫu giáo tư thục',      'chac|chua-chac', 'mầm non',''],

  // ── Nông nghiệp, môi trường ──
  // Thủ tục CÓ trong kho nhưng mang tên "Đăng ký khai thác, sử dụng nước dưới đất"
  // — không có chữ "giếng" nào. Đây là ca thử bảng đồng nghĩa trỏ đúng chữ.
  ['tôi muốn khoan giếng lấy nước',          'chac|chua-chac', 'nước dưới đất', ''],
  // Cả kho chỉ có đúng 1 thủ tục nhắc "cây": chuyển đổi cơ cấu cây trồng trên đất
  // trồng lúa — không phải xin chặt cây. Phải nói không biết.
  ['xin chặt cây trong vườn nhà',            'khong-thay',     '',       ''],
  /* Ca khó đã đo kỹ và CHẤP NHẬN như hiện tại: "nuôi tôm" đấu với "nuôi con nuôi".
     Chữ "tôm" không xuất hiện trong kho, còn "đăng ký" thì có ở 36/165 thủ tục và
     "thuỷ" ở 40/165 — không có ngưỡng độ hiếm nào tách được hai bên. Xếp hạng số 1
     vẫn là con nuôi, nhưng mức chỉ là 'chua-chac' nên robot HỎI LẠI chứ không khẳng
     định, và 5 thủ tục thuỷ sản nằm ngay dưới, hiện đủ trên màn hình để người dân
     chọn. Ép cho bằng được câu này sẽ phải vặn ngưỡng tới mức hỏng các câu khác. */
  ['đăng ký nuôi tôm',                       'chua-chac',      '',       'rượu', 'thủy sản'],

  // ── Việc làm ──
  // Không có thủ tục nào tên chứa "nước ngoài" cho người đi lao động. Thứ đúng là
  // "Đăng ký hợp đồng lao động trực tiếp giao kết" — đúng việc, chỉ khác tên gọi.
  ['tôi muốn đi làm việc ở nước ngoài',      'chac|chua-chac', 'lao động',''],

  // ── Khiếu nại, tôn giáo, giao thông ──
  ['tôi muốn gửi đơn khiếu nại',             'chac|chua-chac', 'khiếu nại',''],
  ['tôi muốn tố cáo cán bộ',                 'chac|chua-chac', 'tố cáo', ''],
  ['đăng ký sinh hoạt tôn giáo tập trung',   'chac|chua-chac', 'tôn giáo',''],
  // Kho chỉ có "phương tiện hoạt động vui chơi, giải trí dưới nước" — không phải
  // thuyền đánh cá. Chấp nhận cả hai, miễn là KHÔNG lôi con nuôi hay rượu vào.
  ['đăng ký thuyền của tôi',                 'khong-thay|chua-chac', '', 'nuôi'],

  // ── Câu nghe sót chữ (ASR chỉ đúng trọn câu 43%) ──
  ['hộ nghèo',                               'chac|chua-chac', 'nghèo',  ''],
  ['sổ đỏ',                                  'chac|chua-chac', 'đất',    'rượu'],
  ['bán lẻ rượu',                            'chac',           'rượu',   ''],
  ['mai táng',                               'chac|chua-chac', 'táng',   ''],
  ['khiếu nại',                              'chac|chua-chac', 'khiếu nại',''],
  ['con nuôi',                               'chac|chua-chac', 'con nuôi',''],
];

/* ── Chạy ── */
const chiTiet = process.argv.includes('--chi-tiet');
let dat = 0;
const hong = [];

for (const [cau, mong, phaiCo, cam, trongTop3] of THU) {
  const r = boi.traCuu(cau, 3);
  const dau = r.ds[0];
  const ten = dau ? dau.ten : '';
  const mucOk = mong.split('|').includes(r.muc);

  let loi = '';
  if (!mucOk) {
    loi = `mức '${r.muc}', mong '${mong}'`;
  } else if (r.muc === 'chac' || r.muc === 'chua-chac') {
    if (phaiCo && !ten.toLowerCase().includes(phaiCo.toLowerCase())) {
      loi = `thủ tục đầu thiếu cụm '${phaiCo}'`;
    } else if (cam && ten.toLowerCase().includes(cam.toLowerCase())) {
      loi = `thủ tục đầu chứa cụm CẤM '${cam}'`;
    } else if (trongTop3 &&
               !r.ds.some(t => t.ten.toLowerCase().includes(trongTop3.toLowerCase()))) {
      loi = `cả 3 ứng viên đều không có cụm '${trongTop3}'`;
    }
  }

  if (loi) hong.push({ cau, loi, ten, r }); else dat++;

  if (chiTiet) {
    const dd = `d0=${r.d0.toFixed(0)} d1=${r.d1.toFixed(0)} phủ=${(r.phu * 100).toFixed(0)}%`;
    console.log(`${loi ? 'HỎNG' : ' ok '}  ${cau.padEnd(44)} ${r.muc.padEnd(17)} ${dd.padEnd(28)} ${ten.slice(0, 60)}`);
  }
}

console.log(`\n${dat}/${THU.length} câu đạt.`);
if (hong.length) {
  console.log('\nCác câu hỏng:');
  for (const h of hong) {
    console.log(`  · "${h.cau}"`);
    console.log(`      ${h.loi}`);
    console.log(`      d0=${h.r.d0.toFixed(0)} d1=${h.r.d1.toFixed(0)} phủ=${(h.r.phu * 100).toFixed(0)}%` +
                (h.ten ? ` → ${h.ten.slice(0, 70)}` : ' → (không có kết quả)'));
  }
  console.log('\nBa con số để chỉnh: TIN_CHAC và TIN_SAN trong khung-app.html.');
  console.log('Chỉnh xong chạy lại file này — đừng chỉnh bằng cảm giác.');
}
process.exit(hong.length ? 1 : 0);
