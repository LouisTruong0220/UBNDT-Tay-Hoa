/**
 * Thử ba lớp chặn MỚI bằng câu người dân nói thật ở quầy.
 * Bóc biểu thức từ chính MainApplication.kt — bản đang chạy trên robot.
 */
import fs from 'node:fs';

const KT = 'D:\\RBW_Claude\\11-app-nova\\hcc-trung-tam\\android\\app\\src\\main\\java\\vn\\roboworld\\hcc\\MainApplication.kt';
const src = fs.readFileSync(KT, 'utf8');

function boc(ten) {
  const i = src.indexOf(`private val ${ten} = Regex(`);
  const j = src.indexOf('RegexOption', i);
  let than = src.slice(i, j).replace(/\/\*[\s\S]*?\*\//g, '').replace(/\/\/[^\n]*/g, '');
  const manh = than.match(/"((?:[^"\\]|\\.)*)"/g) || [];
  return new RegExp(manh.map(x => x.slice(1, -1)).join(''), 'i');
}

const KHAN = boc('TU_KHAN_CAP');
const QUYET = boc('TU_XIN_QUYET_DINH');
const CHUA = boc('TU_CHUA_CO');

// [câu, lớp phải bắt: 'khan' | 'quyet' | 'chua' | '-' (phải lọt xuống tra cứu)]
const PHEP = [
  ['Có người ngất ở ghế chờ kìa',                      'khan'],
  ['Gọi cứu thương giúp tôi với',                      'khan'],
  ['Hồ sơ của tôi đến đâu rồi ạ',                      'quyet'],
  ['Tôi có được hưởng trợ cấp hộ nghèo không',         'quyet'],
  ['Chị khai hộ tôi tờ khai này được không',           'quyet'],
  ['Trường hợp của tôi thì làm thế nào',               'quyet'],
  ['Trung tâm mấy giờ đóng cửa',                       'chua'],
  ['Thứ bảy có làm việc không',                        'chua'],
  ['Cán bộ nào phụ trách đất đai',                     'chua'],
  ['Hôm nay có đông không',                            'chua'],

  // ── phải LỌT XUỐNG: đây là việc chính của robot ──
  ['Thủ tục đăng ký khai sinh cần giấy tờ gì',         '-'],
  ['Tôi muốn làm khai sinh cho con',                   '-'],
  ['Hộ nghèo được hưởng những gì',                     '-'],
  ['Chứng thực chữ ký nộp ở quầy nào',                 '-'],
  ['Hỗ trợ nạn nhân tai nạn giao thông làm sao',       '-'],
  ['Xin giấy xác nhận tình trạng hôn nhân ở đâu',      '-'],
  ['Dẫn tôi tới quầy ba',                              '-'],
  ['Đăng ký tạm trú mất bao nhiêu tiền',               '-'],  // lệ phí: để trợ lý ngoài trả lời
  ['Làm sổ đỏ bao lâu có kết quả',                     '-'],
];

let dat = 0, hong = 0;
for (const [cau, mong] of PHEP) {
  const bat = KHAN.test(cau) ? 'khan' : QUYET.test(cau) ? 'quyet' : CHUA.test(cau) ? 'chua' : '-';
  const ok = bat === mong;
  if (ok) dat++; else hong++;
  console.log(`${ok ? '✓' : '✗'} [${bat.padEnd(5)}] ${cau}${ok ? '' : `   ← phải là [${mong}]`}`);
}
console.log(`\n${hong ? `✗ ${hong} phép HỎNG / ${dat + hong}` : `✓ Cả ${dat} phép thử đạt.`}`);
process.exit(hong ? 1 : 0);
