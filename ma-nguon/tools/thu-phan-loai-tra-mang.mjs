/**
 * Thử BỘ PHÂN LOẠI của tầng tra mạng — câu nào được đem lên Google, câu nào không.
 *
 *   node tools/thu-phan-loai-tra-mang.mjs
 *
 * Anh Trường chốt 22/09/2026: câu ngoài kiến thức đã nạp thì tra mạng, TRỪ chính trị ·
 * thủ tục · pháp luật. Lọt một câu thủ tục ra web là robot có thể đọc sai giấy tờ cho
 * người dân từ một trang web lạ — nên bộ thử này nghiêng hẳn về canh chiều đó.
 *
 * Bóc biểu thức từ CHÍNH MainApplication.kt — bản chạy trên robot, không chép lại.
 * Dựng lại đúng logic nenTraMang() và chanChinhTriNeuCan().
 */
import fs from 'node:fs';

const KT = 'D:\\RBW_Claude\\11-app-nova\\hcc-trung-tam\\android\\app\\src\\main\\java\\vn\\roboworld\\hcc\\MainApplication.kt';
const src = fs.readFileSync(KT, 'utf8');

function boc(ten, nguon = src) {
  const i = nguon.indexOf(`private val ${ten} = Regex(`);
  if (i < 0) throw new Error('Khong thay ' + ten);
  const j = nguon.indexOf('RegexOption', i);
  const than = nguon.slice(i, j).replace(/\/\*[\s\S]*?\*\//g, '').replace(/\/\/[^\n]*/g, '');
  return new RegExp((than.match(/"((?:[^"\\]|\\.)*)"/g) || []).map(x => x.slice(1, -1)).join(''), 'i');
}

function dung(s) {
  const KHONG = boc('TU_KHONG_TRA_MANG', s), CT = boc('TU_CHINH_TRI', s);
  const CHUA = boc('TU_CHUA_CO', s), KHAN = boc('TU_KHAN_CAP', s);
  return cau => {
    if (KHONG.test(cau)) return 'thu-tuc';
    if (CT.test(cau)) return 'chinh-tri';
    if (CHUA.test(cau) || KHAN.test(cau)) return 'thu-tuc';   // việc nội bộ / khẩn: không lên web
    return 'mang';
  };
}
const loai = dung(src);

// [câu, phải ra: 'mang' (được tra) | 'thu-tuc' (không lên web) | 'chinh-tri' (từ chối)]
const PHEP = [
  // ── đời thường: PHẢI được tra ──
  ['Mai trời có mưa không',                              'mang'],
  ['Hôm nay thời tiết ở Tuy Hòa thế nào',                'mang'],
  ['Gần đây có quán ăn nào ngon không',                  'mang'],
  ['Quán cà phê phía trước có mở không',                 'mang'],   // "phía" chứa "phí"
  ['Tuần sau có sự kiện gì ở Đắk Lắk',                   'mang'],   // "sự kiện" chứa "kiện"
  ['Đi Tuy Hòa bằng xe buýt số mấy',                     'mang'],
  ['Trung tâm thương mại gần nhất ở đâu',                'mang'],   // "trung tâm" trần
  ['Tôi muốn quay lại Buôn Ma Thuột thì đi đường nào',   'mang'],   // "quay" không dấu nghĩa
  ['Giá vàng hôm nay bao nhiêu',                         'mang'],
  ['Điều kiện thời tiết mùa này có tốt để đi biển không', 'mang'],  // "điều kiện"

  // ── thủ tục / pháp luật: KHÔNG được lên web ──
  ['Làm sổ đỏ hết bao nhiêu tiền',                       'thu-tuc'],
  ['Thủ tục đăng ký tạm trú cần gì',                     'thu-tuc'],
  ['thu tuc dang ky tam tru can giay to gi',             'thu-tuc'],   // thiếu dấu kiểu ASR
  ['Kết hôn với người nước ngoài cần giấy tờ gì',        'thu-tuc'],
  ['Không đội mũ bảo hiểm bị phạt bao nhiêu',            'thu-tuc'],
  ['Luật đất đai mới có gì thay đổi',                    'thu-tuc'],
  ['Tôi muốn khởi kiện hàng xóm lấn đất',                'thu-tuc'],
  ['Nộp hồ sơ ở quầy số mấy',                            'thu-tuc'],
  ['Làm căn cước cho con mất bao lâu',                   'thu-tuc'],
  ['Trợ cấp người cao tuổi được bao nhiêu',              'thu-tuc'],
  ['Trung tâm này mấy giờ đóng cửa',                     'thu-tuc'],
  ['Biểu tình có bị phạt không',                         'thu-tuc'],   // pháp luật thắng chính trị
  ['Bạn là ai',                                          'thu-tuc'],   // chuyện của robot, không tra

  // ── chính trị: từ chối ──
  ['Thủ tướng hiện nay là ai',                           'chinh-tri'],
  ['Bạn nghĩ gì về tình hình Biển Đông',                 'chinh-tri'],
  ['Bao giờ bầu cử quốc hội',                            'chinh-tri'],
  ['Tổng thống Mỹ là ai',                                'chinh-tri'],
];

let dat = 0, hong = 0;
const phep = (ten, ok, them = '') => {
  if (ok) dat++; else hong++;
  console.log(`${ok ? '✓' : '✗'} ${ten}${them ? '  — ' + them : ''}`);
};

console.log('── Phân loại câu hỏi ──');
for (const [cau, mong] of PHEP) {
  const r = loai(cau);
  phep(`[${mong.padEnd(9)}] ${cau}`, r === mong, r === mong ? '' : 'ra ' + r);
}

// ── Thử NGƯỢC: gỡ một chốt ra thì phép thử tương ứng PHẢI đỏ ──
console.log('\n── Thử ngược: vô hiệu từng biểu thức, phép thử phải bắt được ──');
const vo = ten => src.replace(`private val ${ten} = Regex(`, `private val ${ten} = Regex("KHONGBAOGIOKHOP" +`)
                     .replace(new RegExp(`(private val ${ten} = Regex\\("KHONGBAOGIOKHOP" \\+)[\\s\\S]*?RegexOption`),
                              `$1 "", RegexOption`);
{
  const l = dung(vo('TU_KHONG_TRA_MANG'));
  phep('Gỡ TU_KHONG_TRA_MANG → câu sổ đỏ phải LỌT ra web', l('Làm sổ đỏ hết bao nhiêu tiền') === 'mang');
}
{
  const l = dung(vo('TU_CHINH_TRI'));
  phep('Gỡ TU_CHINH_TRI → câu Thủ tướng phải LỌT ra web', l('Thủ tướng hiện nay là ai') === 'mang');
}

// ── Mã Kotlin phải còn đủ ba hàng rào sau khi tra ──
console.log('\n── Hàng rào sau khi tra (đọc mã TraLoi.kt) ──');
const tl = fs.readFileSync(KT.replace('MainApplication.kt', 'TraLoi.kt'), 'utf8');
const ham = tl.slice(tl.indexOf('private fun hoiTraMang'), tl.indexOf('private fun hoiTroLyNgoai'));
{
  // Cắt ĐÚNG khối giữa hàng rào ① và ②. Không dùng [^}]*: dòng log có ${...} chứa dấu }.
  const a = ham.indexOf('r.nguon.isEmpty()'), b = ham.indexOf('dapMangCoChinhTri', a);
  phep('① KHÔNG CÓ NGUỒN THÌ KHÔNG NÓI', a > 0 && b > a && /return false/.test(ham.slice(a, b)));
}
phep('② trôi sang chính trị thì vứt', /dapMangCoChinhTri[\s\S]{0,120}return false/.test(ham));
phep('③ ra loa qua tuDoc (cửa có chốt choPhepNoi)', /MainApplication\.tuDoc\(TraMang\.rutGon/.test(ham));
phep('Tầng tra mạng đứng TRƯỚC trợ lý ngoài', tl.indexOf('nenTraMang(cau)') < tl.indexOf('hoiTroLyNgoai(cau)) return'));
phep('Không có khoá thì tắt hẳn (coKhoa)', /nenTraMang\(cau\) && TraMang\.coKhoa\(\)/.test(tl));

console.log('\n' + (hong ? `✗ ${hong} phép HỎNG / ${dat + hong}` : `✓ Cả ${dat} phép thử đạt.`));
process.exit(hong ? 1 : 0);
