/**
 * BỘ THỬ CHẠY TRÊN ROBOT THẬT — gửi câu hỏi qua đúng đường Kotlin, đọc nhật ký thật.
 *
 *   node tools/thu-tren-robot.mjs
 *
 * Mỗi câu gửi qua CAU.hoiRobot() — đúng cửa mà câu nói đi vào sau khi bộ nhận dạng
 * giọng nói trả chữ (Cau.kt → TraLoi.hoi). Robot ĐỌC THÀNH TIẾNG thật. Sau mỗi câu, đọc
 * logcat của app để biết: đi lớp nào · mất bao lâu · đọc câu gì · có bị cắt nhầm không.
 *
 * Cần: robot cắm cáp, app mở từ Home, và cổng CDP đã nối (script tự nối lại theo PID).
 * Không cần nói vào micro — phần micro phải thử bằng người thật.
 */
import { execFileSync } from 'node:child_process';
import fs from 'node:fs';

const ADB = 'C:/Users/ADMIN/AppData/Local/Android/Sdk/platform-tools/adb.exe';
const adb = (...a) => execFileSync(ADB, a, { encoding: 'utf8', env: { ...process.env, MSYS_NO_PATHCONV: '1' } });
const ngu = ms => new Promise(r => setTimeout(r, ms));

const PID = adb('shell', 'pidof', 'vn.roboworld.hcc').trim();
if (!PID) { console.error('App chưa chạy trên robot'); process.exit(2); }
adb('forward', 'tcp:9333', 'localabstract:webview_devtools_remote_' + PID);

async function js(expr) {
  const ds = await (await fetch('http://127.0.0.1:9333/json')).json();
  const ws = new WebSocket(ds.find(x => x.type === 'page').webSocketDebuggerUrl);
  await new Promise((ok, loi) => { ws.onopen = ok; ws.onerror = loi; });
  const m = await new Promise(ok => {
    ws.onmessage = e => { const x = JSON.parse(e.data); if (x.id === 1) ok(x); };
    ws.send(JSON.stringify({ id: 1, method: 'Runtime.evaluate', params: { expression: expr, returnByValue: true } }));
  });
  ws.close();
  return m.result?.result?.value;
}

const TAG = /\b(BVApp|BVCau|BVTraLoi|HccNgoai|TraMang|RobotHelper)\b/;
function nhatKy() {
  return adb('logcat', '-d', '-v', 'time', '--pid=' + PID).split('\n').filter(d => TAG.test(d));
}

/** Gửi một câu, chờ robot mở miệng (hoặc quá hạn), trả tóm tắt. */
async function hoi(cau, hanMs = 25000) {
  adb('logcat', '-c');
  const t0 = Date.now();
  await js(`CAU.nguoiDungThaoTac && CAU.nguoiDungThaoTac(); CAU.hoiRobot(${JSON.stringify(cau)}); 1`);
  let dong = [];
  while (Date.now() - t0 < hanMs) {
    await ngu(700);
    dong = nhatKy();
    if (dong.some(d => /RobotHelper.*Đọc: /.test(d))) break;
  }
  await ngu(2500);                              // để luồng chữ TTS kịp bắn về
  dong = nhatKy();
  const doc = (dong.find(d => /Đọc: /.test(d)) || '').replace(/^.*Đọc: /, '');
  const giay = (Date.now() - t0 - 2500) / 1000;
  const co = re => dong.some(d => re.test(d));
  /* Lớp kho nạp sẵn KHÔNG in log riêng (chỉ ghi file nhật ký trên thẻ nhớ) — nhận bằng
     loại trừ: có đọc, mà không qua chặn, không ra mạng, không hỏi mô hình. */
  const lop =
    co(/CHẶN KHẨN CẤP/) ? 'chặn khẩn cấp' :
    co(/CHẶN XIN QUYẾT ĐỊNH/) ? 'chặn xin quyết định' :
    co(/CHẶN CHÍNH TRỊ/) ? 'chặn chính trị' :
    co(/CHẶN CHƯA CÓ/) ? 'chặn chưa có dữ liệu' :
    co(/LLM chọn mục: #/) ? 'LLM chọn → kho' :
    co(/Tra mạng/) ? 'tra mạng' :
    co(/Trợ lý ngoài trả lời/) && !co(/hoiMoHinh: status/) ? 'nguồn (mạng)' :
    co(/hoiMoHinh: status/) ? 'mô hình → chưa có' :
    co(/Đọc: /) ? 'kho nạp sẵn' : '(không đọc gì)';
  return {
    cau, giay: +giay.toFixed(1), lop, doc: doc.slice(0, 90),
    catNham: dong.filter(d => /AgentOS tự trả lời/.test(d)).length,
    ngoai: (dong.find(d => /Trợ lý ngoài (trả lời|hỏng)/.test(d)) || '').replace(/^.*Trợ lý ngoài /, ''),
    chon: (dong.find(d => /LLM chọn mục:/.test(d)) || '').replace(/^.*LLM chọn mục: /, ''),
    tho: dong,
  };
}

// [câu, mong đợi — để người đọc bảng biết đúng sai]
const TU_NHIEN = [
  // câu KỂ HOÀN CẢNH, không gọi tên thủ tục — đúng kiểu người lớn tuổi nói với robot
  ['Bố tôi vừa mất thì gia đình phải làm giấy tờ gì',       'khai tử'],
  ['Tôi muốn chia mảnh đất ra làm hai cho hai đứa con',      'tách thửa'],
  ['Tôi định mở tiệm tạp hóa nhỏ trước nhà',                 'thành lập hộ kinh doanh'],
  ['Nhà tôi khó khăn quá có được nhà nước hỗ trợ gì không',  'hộ nghèo / trợ cấp'],
  ['Con gái tôi muốn lấy chồng người Hàn Quốc',              'kết hôn có yếu tố nước ngoài'],
  ['Giấy khai sinh của tôi ghi sai tên mẹ',                  'cải chính hộ tịch'],
  ['Tôi mới chuyển về đây ở hẳn thì phải báo ai',            'thường trú'],
  // PHẢI KHÔNG chọn mục nào
  ['Mai trời có mưa không',                                  'KHÔNG chọn mục'],
  ['Nuôi chó trong nhà có phải đăng ký không',               'KHÔNG chọn mục'],
];
const CAU = process.env.TU_NHIEN ? TU_NHIEN : process.env.MAT_MANG ? [
  ['Thủ tục tách thửa đất cần giấy tờ gì?',  'MẤT MẠNG — kho nạp sẵn vẫn phải trả lời'],
  ['người có công',                          'MẤT MẠNG — kho nạp sẵn'],
  ['Thủ tục nuôi con nuôi cần gì',           'MẤT MẠNG — ngoài kho, phải nói chưa có, không treo'],
] : [
  ['Thủ tục chấm dứt hoạt động kinh doanh cần giấy tờ gì?', 'kho nạp sẵn — câu lệch 1 chữ, bản cũ trượt'],
  ['Tư vấn thủ tục đăng ký kinh doanh',                     'nguồn trả menu — KHÔNG được cắt nhầm'],
  ['hộ nghèo',                                              'kho nạp sẵn'],
  ['Tôi mới sinh con, làm giấy khai sinh cần gì?',          'kho nạp sẵn'],
  ['Có người ngất ở ghế chờ kìa',                           'chặn khẩn cấp'],
  ['Hồ sơ của tôi đến đâu rồi ạ',                           'chặn xin quyết định'],
  ['Thủ tướng hiện nay là ai',                              'chặn chính trị'],
  ['Trung tâm mấy giờ đóng cửa',                            'chặn chưa có dữ liệu'],
  ['Mai trời có mưa không',                                 'đời thường — chưa có khoá tra mạng'],
];

const ra = [];
console.log('Robot 42DABA16 · PID ' + PID + ' · ' + new Date().toLocaleTimeString('vi-VN') + '\n');
for (const [cau, mong] of CAU) {
  const r = await hoi(cau);
  r.mong = mong;
  ra.push(r);
  console.log(`${r.catNham ? '✗' : '·'} ${r.giay.toFixed(1).padStart(5)}s  ${r.lop.padEnd(18)} ${cau}`);
  console.log(`         mong: ${mong}`);
  console.log(`         đọc : ${r.doc || '(không đọc gì)'}`);
  if (r.chon) console.log(`         LLM : ${r.chon}`);
  if (r.ngoai) console.log(`         nguồn: ${r.ngoai}`);
  if (r.catNham) console.log(`         ✗ CẮT NHẦM ${r.catNham} lần`);
  await ngu(2000);
}
fs.writeFileSync('C:/Users/ADMIN/AppData/Local/Temp/claude/d--RBW-Claude/5c8a7111-791d-4950-80d8-a9ee32ec5d7b/scratchpad/thu-tren-robot.json',
                 JSON.stringify(ra, null, 1), 'utf8');
const tong = ra.reduce((s, x) => s + x.catNham, 0);
console.log(`\nCắt nhầm tiếng của app: ${tong} lần ${tong ? '✗' : '✓'}`);
