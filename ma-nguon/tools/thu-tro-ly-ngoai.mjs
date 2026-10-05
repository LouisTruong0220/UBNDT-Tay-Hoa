/**
 * BỘ THỬ BỘ LỌC TRỢ LÝ NGOÀI — chạy trên máy tính, không gọi mạng, không cần robot.
 *
 *   node tools/thu-tro-ly-ngoai.mjs
 *
 * Vì sao có file này
 * ──────────────────
 * Trợ lý ngoài (thutuc.hanhchinhso.ai.vn) là KHO CHÍNH của app. Câu của nó đi thẳng
 * ra loa cho người dân nghe, nên mọi thứ nó trả về phải qua locTraLoiNgoai() trước.
 *
 * Ngày 18/09/2026, đo trên máy chủ thật, API trả về nguyên văn:
 *     {"answer":"⚠️ Lưu ý kiểm chứng… You've hit your limit · resets 1pm…"}
 * Lỗi hạn mức của họ nằm ở ĐÚNG trường answer, không phải trường lỗi. Không chặn
 * thì robot đứng giữa sảnh hành chính công đọc to một câu tiếng Anh báo hết hạn mức.
 *
 * Các mẫu dưới đây là CÂU THẬT thu được từ máy chủ của họ, không phải câu bịa ra.
 *
 * Bộ thử bóc khối giữa __BAT_DAU_LOC_NGOAI__ và __HET_LOC_NGOAI__ trong khung-app.html
 * rồi nạp vào Node — thử đúng đoạn mã đang chạy trên robot, không chép lại dòng nào.
 */

import { readFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import { dirname, resolve } from 'node:path';
import vm from 'node:vm';

const duAn = resolve(dirname(fileURLToPath(import.meta.url)), '..');
const html = readFileSync(resolve(duAn, 'khung-app.html'), 'utf8');
const m = html.match(/__BAT_DAU_LOC_NGOAI__[\s\S]*?\*\/([\s\S]*?)\/\*__HET_LOC_NGOAI__\*\//);
if (!m) {
  console.error('Không thấy mốc __BAT_DAU_LOC_NGOAI__ / __HET_LOC_NGOAI__ trong khung-app.html.');
  process.exit(2);
}
const boi = { console };
vm.createContext(boi);
vm.runInContext(m[1], boi, { filename: 'loc-tro-ly-ngoai.js' });
const loc = boi.locTraLoiNgoai;

/* Bóc thêm BỘ DỊCH MARKDOWN — chính nó vẽ chữ lên màn hình người dân đọc.
   Trước 20/09/2026 không phép thử nào chạm tới nó, nên dấu ## và > hiện
   nguyên trên màn hình suốt mà mọi cổng kiểm vẫn xanh. */
const mMd = html.match(/__BAT_DAU_MARKDOWN__\*\/([\s\S]*?)\/\*__HET_MARKDOWN__/);
if (!mMd) {
  console.error('Không thấy mốc __BAT_DAU_MARKDOWN__ trong khung-app.html.');
  process.exit(2);
}
vm.runInContext(mMd[1], boi, { filename: 'markdown.js' });
const sangHTML = boi.doiSangHTML;

/* ── Câu THẬT thu từ máy chủ của họ ngày 18/09/2026 ── */

const CAU_HAN_MUC =
  '⚠️ **Lưu ý kiểm chứng**: Câu trả lời dưới đây CHƯA trích dẫn được căn cứ pháp lý ' +
  'cụ thể từ kho văn bản. Cán bộ vui lòng kiểm chứng trước khi sử dụng và phản hồi nếu ' +
  'phát hiện sai sót để hệ thống bổ sung căn cứ.\n\n' +
  "You've hit your limit · resets 1pm (Asia/Ho_Chi_Minh)";

const CAU_HOI_LAI_CO_FACETS =
  '**Cần làm rõ (chọn nhiều)**\nBạn muốn hỏi về trường hợp đăng ký kết hôn nào ạ?\n' +
  '[[FACETS base="Thủ tục đăng ký kết hôn" tail="cần giấy tờ gì và nộp ở đâu?" ' +
  'default="Thủ tục đăng ký kết hôn lần đầu (trong nước) cần giấy tờ gì và nộp ở đâu?"]]\n' +
  '[[GROUP mode="single" label="Tình huống của anh/chị"]]\n' +
  '- {{Kết hôn lần đầu (cả hai là công dân Việt Nam, trong nước)||lần đầu trong nước||common}}\n' +
  '- {{Có yếu tố nước ngoài (một bên là người nước ngoài)||có yếu tố nước ngoài}}\n' +
  '- {{Đăng ký lại (đã từng kết hôn, bị mất giấy chứng nhận)||đăng ký lại}}\n' +
  '- {{Một bên đã ly hôn / vợ-chồng trước đã mất||đã ly hôn}}';

const CAU_HOI_LAI_DON =
  '**Cần làm rõ**\nBạn muốn hỏi về trường hợp đăng ký khai sinh nào ạ?\n' +
  '- {{Thủ tục đăng ký khai sinh đúng hạn cần giấy tờ gì và nộp ở đâu?}}\n' +
  '- {{Thủ tục đăng ký khai sinh quá hạn cần giấy tờ gì?}}\n' +
  '- {{Thủ tục đăng ký khai sinh khi cha mẹ chưa đăng ký kết hôn cần giấy tờ gì?}}';

const CAU_TU_CHOI =
  'Xin lỗi, tôi chỉ trả lời các câu hỏi về thủ tục hành chính công cấp xã tại Đắk Lắk ' +
  'và Lâm Đồng (hộ tịch, cư trú, chứng thực, đất đai, chính sách xã hội, đăng ký kinh ' +
  'doanh hộ cá thể). Câu hỏi ngoài phạm vi này, vui lòng liên hệ bộ phận một cửa địa phương.';

const CAU_TOT =
  '**Thủ tục đăng ký khai sinh đúng hạn**\n\n' +
  'Anh/chị cần chuẩn bị: Tờ khai đăng ký khai sinh theo mẫu; Giấy chứng sinh do cơ sở ' +
  'y tế cấp; Giấy tờ tùy thân của người đi đăng ký. Nộp tại **UBND cấp xã** nơi cư trú ' +
  'của người cha hoặc người mẹ. Thời hạn giải quyết trong ngày làm việc.';

/* ── Các phép thử ── */
let hong = 0, dat = 0;

function phep(ten, dieuKien, themVao) {
  if (dieuKien) { dat++; console.log('✓ ' + ten + (themVao ? '  — ' + themVao : '')); }
  else { hong++; console.log('✗ ' + ten + (themVao ? '  — ' + themVao : '')); }
}

console.log('\n── A. Câu HỎNG phải bị vứt (quan trọng nhất) ──');
{
  const r = loc(CAU_HAN_MUC);
  phep('Lỗi hạn mức của họ KHÔNG được lọt ra loa', r.dung === false, r.vi_sao);
  phep('  …và phải nêu đúng lý do là lỗi hạ tầng',
       r.dung === false && /lỗi hạ tầng/.test(r.vi_sao || ''));
}
phep('Chuỗi rỗng', loc('').dung === false);
phep('null', loc(null).dung === false);
phep('Chỉ có khoảng trắng', loc('   \n  ').dung === false);
phep('"undefined" lọt vào câu', loc('Thủ tục undefined cần giấy tờ gì').dung === false);
phep('[object Object]', loc('Kết quả: [object Object]').dung === false);
phep('Internal Server Error', loc('Internal Server Error').dung === false);
phep('Rate limit exceeded', loc('Rate limit exceeded, try again later').dung === false);
phep('Chỉ có tiêu đề "Cần làm rõ"', loc('**Cần làm rõ**').dung === false);

console.log('\n── B. Câu TỪ CHỐI ngoài phạm vi → rơi về kho trong robot ──');
{
  const r = loc(CAU_TU_CHOI);
  phep('Câu từ chối bị vứt để rơi về kho nội bộ', r.dung === false, r.vi_sao);
}

console.log('\n── C. Câu HỎI LẠI: bóc lựa chọn, không đọc markup ──');
{
  const r = loc(CAU_HOI_LAI_CO_FACETS);
  phep('Nhận là dùng được', r.dung === true, r.vi_sao);
  phep('Bóc đúng 4 lựa chọn', r.lua_chon.length === 4, 'được ' + r.lua_chon.length);
  phep('Lấy ĐÚNG NHÃN, không lấy ô thứ hai',
       r.lua_chon[0].nhan.startsWith('Kết hôn lần đầu'), r.lua_chon[0].nhan);
  /* ⚠ Phép thử này TRƯỚC 20/09/2026 đòi gui === 'lần đầu trong nước' — tức nó canh
     đúng cái LỖI: gửi trần mảnh ghép. Ô thứ hai chỉ là MẢNH khi có khối FACETS; câu
     thật phải ghép base + mảnh + tail, đúng như dòng default của chính nguồn. */
  phep('Có FACETS: câu gửi GHÉP base + mảnh + tail',
       r.lua_chon[0].gui ===
       'Thủ tục đăng ký kết hôn lần đầu trong nước cần giấy tờ gì và nộp ở đâu?',
       r.lua_chon[0].gui);
  phep('Câu ghép mang đủ ba phần',
       r.lua_chon[0].gui.startsWith('Thủ tục đăng ký kết hôn') &&
       r.lua_chon[0].gui.includes('lần đầu trong nước') &&
       r.lua_chon[0].gui.endsWith('cần giấy tờ gì và nộp ở đâu?'));
  phep('Mọi lựa chọn FACETS đều được ghép, không sót cái nào',
       r.lua_chon.every(x => x.gui.startsWith('Thủ tục đăng ký kết hôn')),
       r.lua_chon.map(x => x.gui).join(' | ').slice(0, 120));
  phep('Lựa chọn không có ô thứ hai thì gửi chính nhãn',
       loc(CAU_HOI_LAI_DON).lua_chon[0].gui.startsWith('Thủ tục đăng ký khai sinh đúng hạn'));
  phep('KHÔNG còn [[FACETS]] trong chữ hiện lên', !/\[\[/.test(r.hien));
  phep('KHÔNG còn {{…}} trong chữ hiện lên', !/\{\{/.test(r.hien));
  phep('KHÔNG còn ** đậm của Markdown', !/\*\*/.test(r.hien));
  phep('Lời ĐỌC không chứa cả tràng lựa chọn',
       !r.doc.includes('Có yếu tố nước ngoài'), r.doc.slice(0, 60) + '…');
  phep('Lời ĐỌC có mời chọn trên màn hình', /chọn một mục trên màn hình/.test(r.doc));
  phep('Lời ĐỌC vẫn giữ câu hỏi gốc', /trường hợp đăng ký kết hôn nào/.test(r.doc));
}

console.log('\n── D. Câu TỐT phải đi qua nguyên vẹn ──');
{
  const r = loc(CAU_TOT);
  phep('Nhận là dùng được', r.dung === true, r.vi_sao);
  phep('Không có lựa chọn nào', r.lua_chon.length === 0);
  phep('Giữ được nội dung giấy tờ', /Giấy chứng sinh/.test(r.doc));
  phep('Giữ được nơi nộp', /UBND cấp xã/.test(r.doc));
  phep('Bỏ hết ** của Markdown', !/\*\*/.test(r.doc));
  phep('KHÔNG chèn thêm câu mời chọn', !/chọn một mục/.test(r.doc));
}

console.log('\n── E. Câu KHÔNG CÓ CĂN CỨ PHÁP LÝ phải bị vứt, không được đọc ──');
/* Đo 18/09/2026: máy chủ của họ có HAI đường. Khớp kho văn bản thì trả kèm citations
   trong dưới một giây. Không khớp thì nó hỏi mô hình ngôn ngữ và tự gắn dòng cảnh báo
   "CHƯA trích dẫn được căn cứ pháp lý". Robot nói với người dân về hồ sơ giấy tờ thì
   câu đó phải có căn cứ — nên đường thứ hai bị vứt, rơi về kho trong robot. */
{
  const r = loc('⚠️ **Lưu ý kiểm chứng**: Câu trả lời dưới đây CHƯA trích dẫn được căn cứ ' +
                'pháp lý cụ thể từ kho văn bản. Cán bộ vui lòng kiểm chứng trước khi sử dụng.\n\n' +
                'Thủ tục chứng thực bản sao từ bản chính nộp tại UBND cấp xã, lệ phí 2.000 đồng một trang.');
  phep('Bị vứt, KHÔNG đọc ra loa', r.dung === false, r.vi_sao);
  phep('Nêu đúng lý do', /căn cứ pháp lý/.test(r.vi_sao || ''), r.vi_sao);
}
{
  /* Nguyên văn câu máy chủ trả về lúc hết hạn mức, thu 18/09/2026 */
  const r = loc('⚠️ **Lưu ý kiểm chứng**: Câu trả lời dưới đây CHƯA trích dẫn được căn cứ pháp lý ' +
                'cụ thể từ kho văn bản.\n\n' +
                "You've hit your limit · resets Sep 24, 8am (Asia/Ho_Chi_Minh)");
  phep('Câu hết hạn mức bị vứt', r.dung === false, r.vi_sao);
}
{
  /* Câu ĐÚNG đường kho văn bản thì VẪN phải dùng được. Không có phép này thì một bộ
     lọc chặn sạch mọi thứ cũng xanh — mà như vậy là tầng trợ lý ngoài chết câm. */
  const r = loc('**Tra cứu thủ tục: Đăng ký tạm trú**\n\n**Giấy tờ cần chuẩn bị:**\n\n' +
                '1. **Tờ khai thay đổi thông tin cư trú (Mẫu CT01)** — nộp tại Công an cấp xã.');
  phep('Câu có căn cứ vẫn dùng được', r.dung === true, r.vi_sao);
  phep('Vẫn bóc được tên giấy tờ', /Tờ khai thay đổi/.test(r.doc || ''), (r.doc || '').slice(0, 60));
}

console.log('\n── F. Thử NGƯỢC: gỡ từng hàng rào ra thì phép thử phải đỏ ──');
/* Nay có HAI hàng rào độc lập chặn câu của trợ lý ngoài, nên phải thử ngược riêng
   từng cái. Gỡ một cái mà vẫn xanh nhờ cái kia thì không ai biết cái vừa gỡ đã chết. */
{
  /* ① Bảng NGOAI_HONG — câu báo hết hạn mức THUẦN, không kèm dòng cảnh báo căn cứ */
  const CHI_HAN_MUC = "You've hit your limit · resets Sep 24, 8am (Asia/Ho_Chi_Minh). " +
                      'Xin vui lòng thử lại sau khi hạn mức được đặt lại.';
  phep('Còn rào thì câu hạn mức thuần BỊ CHẶN', loc(CHI_HAN_MUC).dung === false);
  const boi2 = { console };
  vm.createContext(boi2);
  vm.runInContext(m[1].replace(/const NGOAI_HONG = \[[\s\S]*?\];/, 'const NGOAI_HONG = [];'),
                  boi2, { filename: 'loc-khong-rao-1.js' });
  phep('Gỡ bảng NGOAI_HONG thì câu đó LỌT (đúng như dự đoán)',
       boi2.locTraLoiNgoai(CHI_HAN_MUC).dung === true, 'nghĩa là hàng rào đang thật sự chặn');
}
{
  /* ② Hàng rào căn cứ pháp lý — câu CHỈ có dòng cảnh báo, không có chữ tiếng Anh nào */
  const CHI_CANH_BAO = '⚠️ **Lưu ý kiểm chứng**: Câu trả lời dưới đây CHƯA trích dẫn được ' +
                       'căn cứ pháp lý cụ thể từ kho văn bản.\n\n' +
                       'Hồ sơ gồm tờ khai và bản sao giấy tờ tuỳ thân, nộp tại UBND cấp xã.';
  phep('Còn rào thì câu không căn cứ BỊ CHẶN', loc(CHI_CANH_BAO).dung === false);
  const boi3 = { console };
  vm.createContext(boi3);
  /* Tắt điều kiện bằng cách chèn false&& — chắc hơn viết regex gỡ nguyên khối */
  vm.runInContext(m[1].replace("if(/L", "if(false&&/L"),
                  boi3, { filename: 'loc-khong-rao-2.js' });
  phep('Gỡ hàng rào căn cứ thì câu đó LỌT (đúng như dự đoán)',
       boi3.locTraLoiNgoai(CHI_CANH_BAO).dung === true, 'nghĩa là hàng rào đang thật sự chặn');
}

console.log('\n── G. Câu dài: MÀN HÌNH hiện đủ, MIỆNG nói gọn ──');
/* Đo 18/09/2026 trên máy thật: hỏi "chứng thực chữ ký" thì máy chủ trả 2.933 ký tự,
   gồm khối "Căn cứ pháp lý" liệt kê sáu nghị định kèm số trang và một đường link.
   Bản trước cho robot đọc NGUYÊN chỗ đó — gần ba phút, không ai đứng nghe hết.
   Phép này canh để không ai vô tình gỡ bước rút gọn ra. */
{
  const DAI = '**Tra cứu thủ tục: Chứng thực chữ ký**\n\n' +
    '**Giấy tờ cần chuẩn bị**\n\n' +
    '1. **Giấy tờ tùy thân** — Thẻ căn cước công dân hoặc hộ chiếu còn giá trị sử dụng, ' +
    'xuất trình bản chính để đối chiếu theo Điều 24 Nghị định 23/2015/NĐ-CP.\n' +
    '2. **Giấy tờ, văn bản cần ký** — bản chính giấy tờ mà người yêu cầu sẽ ký vào, ' +
    'không được là hợp đồng, giao dịch.\n\n---\n\n' +
    '**Các bước thực hiện**\n\n1. Nộp hồ sơ tại Bộ phận một cửa.\n' +
    '2. Cán bộ kiểm tra giấy tờ tùy thân và năng lực hành vi dân sự.\n' +
    '3. Người yêu cầu ký trước mặt người thực hiện chứng thực.\n\n' +
    '📌 **Căn cứ pháp lý**:\n' +
    'Điều 24 — Nghị định 23/2015/NĐ-CP (tr. 11-12)\n' +
    'Điều 4 khoản 2 — Thông tư 226/2016/TT-BTC (tr. 2) — mức phí 10.000 đồng\n' +
    'Mẫu tải tại https://dichvucong.gov.vn';
  const r = loc(DAI);
  phep('Câu dài vẫn dùng được', r.dung === true, r.vi_sao);
  phep('Màn hình hiện ĐỦ — dài hơn lời đọc nhiều lần',
       r.hien.length > r.doc.length * 3, r.hien.length + ' ký tự hiện / ' + r.doc.length + ' đọc');
  phep('Lời ĐỌC gọn lại dưới 400 ký tự', r.doc.length < 400, r.doc.length + ' ký tự');
  phep('Lời ĐỌC nêu đủ hai món giấy tờ',
       /Giấy tờ tùy thân/.test(r.doc) && /văn bản cần ký/.test(r.doc), r.doc);
  phep('Lời ĐỌC KHÔNG chứa số hiệu nghị định', !/Nghị định|NĐ-CP|TT-BTC/.test(r.doc));
  phep('Lời ĐỌC KHÔNG chứa đường link', !/https?:\/\//.test(r.doc));
}
{
  /* Thử NGƯỢC: câu NGẮN thì không được rút — rút nữa là mất nội dung. */
  const NGAN = 'Thủ tục này nộp tại Bộ phận một cửa Ủy ban nhân dân cấp xã, ' +
               'lệ phí mười nghìn đồng một trường hợp.';
  const r = loc(NGAN);
  phep('Câu ngắn giữ nguyên, không bị rút', r.doc.includes('mười nghìn đồng'), r.doc);
}

console.log('\n── H. Bộ dịch Markdown: không ký hiệu nào được lọt ra màn hình ──');
{
  /* Đoạn THẬT máy chủ trả về ngày 20/09/2026 cho "chứng thực chữ ký người dịch".
     Trước hôm nay không phép thử nào chạm tới hàm này, nên dấu ## và > hiện
     nguyên trên màn hình người dân suốt mà mọi cổng kiểm vẫn xanh. */
  const THAT = [
    '## Giấy tờ cần chuẩn bị',
    '**Người dịch cần xuất trình/nộp:**',
    '- **Bản dịch** — bản dịch giấy tờ, văn bản.',
    '',
    '> **Lưu ý đặc biệt:** Nếu người dịch là cộng tác viên dịch thuật.',
    '',
    '### Các bước thực hiện',
  ].join('\n');
  const h = sangHTML(THAT);
  phep('Tiêu đề ## thành thẻ <h3>, không hiện dấu #',
       /<h3>Giấy tờ cần chuẩn bị<\/h3>/.test(h) && !/#/.test(h), h.slice(0, 140));
  phep('Tiêu đề ### cũng thành <h3>', /<h3>Các bước thực hiện<\/h3>/.test(h));
  phep('Trích dẫn > thành <blockquote>', /<blockquote>/.test(h));
  phep('KHÔNG còn dấu &gt; lọt ra màn hình', !/&gt;/.test(h), h);
  phep('Đậm ** vẫn chạy', /<b>/.test(h));
  phep('Gạch đầu dòng vẫn thành <li>', /<li>/.test(h));
}
{
  /* Thử NGƯỢC: dấu # giữa câu KHÔNG được hiểu nhầm là tiêu đề. */
  const h = sangHTML('Nộp tại quầy số # 3 của một cửa.');
  phep('Dấu # giữa câu không bị hiểu nhầm là tiêu đề', !/<h3>/.test(h), h);
}
{
  /* Lời ĐỌC cũng không được mang dấu trích dẫn sang loa. */
  const r = loc('> **Lưu ý:** Hồ sơ nộp tại một cửa.');
  phep('Lời ĐỌC không còn dấu trích dẫn',
       !/^\s*>/.test(r.doc) && !/&gt;/.test(r.doc), r.doc);
}


console.log('\n' + (hong ? `✗ ${hong} phép HỎNG / ${dat + hong}` : `✓ Cả ${dat} phép thử đạt.`));
process.exit(hong ? 1 : 0);
