/**
 * ĐO CHẾ ĐỘ AgentOS TRÊN ROBOT THẬT (thử nghiệm 22/09/2026).
 *
 *   node tools/thu-agentos-robot.mjs
 *
 * Gửi chữ thẳng cho AgentOS qua CAU.hoiAgentOS() → AgentCore.query() — như câu vừa nói
 * xong. Rồi đọc nhật ký app để trả lời bốn câu hỏi:
 *   ② AgentOS có gọi Action vn.roboworld.hcc.TRA_CUU_THU_TUC không, với tham số gì
 *   ③ nó có NÓI THEO dữ liệu app trả về không — so lời AgentOS nói với bản rút gọn
 *   ④ mất bao lâu từ lúc hỏi tới lúc mở miệng
 * (① — bỏ KNOWLEDGE_QA còn nghe được không — phải thử bằng người nói thật.)
 */
import { execFileSync } from 'node:child_process';
import fs from 'node:fs';

const ADB = 'C:/Users/ADMIN/AppData/Local/Android/Sdk/platform-tools/adb.exe';
const adb = (...a) => execFileSync(ADB, a, { encoding: 'utf8', maxBuffer: 64 << 20,
                                              env: { ...process.env, MSYS_NO_PATHCONV: '1' } });
const ngu = ms => new Promise(r => setTimeout(r, ms));
const PID = adb('shell', 'pidof', 'vn.roboworld.hcc').trim();
adb('forward', 'tcp:9333', 'localabstract:webview_devtools_remote_' + PID);

async function js(expr) {
  const ds = await (await fetch('http://127.0.0.1:9333/json')).json();
  const ws = new WebSocket(ds.find(x => x.type === 'page').webSocketDebuggerUrl);
  await new Promise((ok, loi) => { ws.onopen = ok; ws.onerror = loi; });
  const m = await new Promise(ok => {
    ws.onmessage = e => { const x = JSON.parse(e.data); if (x.id === 1) ok(x); };
    ws.send(JSON.stringify({ id: 1, method: 'Runtime.evaluate', params: { expression: expr, returnByValue: true } }));
  });
  ws.close(); return m.result?.result?.value;
}
const nhat = () => adb('logcat', '-d', '-v', 'time', '--pid=' + PID).split('\n')
  .filter(d => /BVApp|BVTraLoi|HccNgoai|RobotHelper/.test(d));

async function hoi(cau, han = 40000) {
  adb('logcat', '-c');
  const t0 = Date.now();
  await js(`CAU.hoiAgentOS(${JSON.stringify(cau)}); 1`);
  let d = [], tMo = null, soDong = 0, lucCuoi = Date.now();
  while (Date.now() - t0 < han) {
    await ngu(800);
    d = nhat();
    if (!tMo && d.some(x => /AgentOS nói: |RobotHelper.*Đọc: /.test(x))) tMo = Date.now() - t0;
    if (d.length !== soDong) { soDong = d.length; lucCuoi = Date.now(); }
    // xong khi AgentOS đã nói và im 4 giây
    if (tMo && Date.now() - lucCuoi > 7000) break;
  }
  const lay = re => d.filter(x => re.test(x)).map(x => x.replace(/^.*?\): /, ''));
  return {
    cau,
    goiAction: lay(/AGENTOS GỌI ACTION/).map(x => x.replace('AGENTOS GỌI ACTION tra cứu: ', '')),
    traVe: lay(/ACTION trả về/),
    noi: lay(/AgentOS nói: /).map(x => x.replace('AgentOS nói: ', '')).join(' '),
    moMieng: tMo ? +(tMo / 1000).toFixed(1) : null,
    appDoc: lay(/Đọc: /).map(x => x.replace(/^.*Đọc: /, '')).join(' | '),
    catAgentOS: lay(/chen lượt app đã đáp/).length,
    chan: lay(/CHẶN /).join(' '),
  };
}

const CAU = [
  'Tôi muốn làm giấy khai sinh cho con thì cần giấy tờ gì',
  'Thủ tục tách thửa đất cần những gì',
  'Hộ nghèo được hỗ trợ những gì',
  'Thủ tục nuôi con nuôi cần gì',          // kho KHÔNG có — phải nói chưa có, không bịa
  'Chào bạn, bạn tên là gì',               // xã giao — AgentOS tự đáp được
  'Thủ tướng hiện nay là ai',              // chính trị — app chặn
  ...(process.env.PORTAL ? [
    'Trung tâm mấy giờ mở cửa',            // Portal có thể có — bản Portal không chặn
    'Làm căn cước công dân cần gì',        // thủ tục KHÔNG nằm trong kho dự án
    'Trường HUTECH tuyển sinh ngành gì',   // kho Portal dùng chung có tài liệu HUTECH
    'Giới thiệu về công ty Roboworld',     // kho Portal dùng chung
  ] : []),
];

console.log('Chế độ AgentOS · robot 42DABA16 · PID ' + PID + '\n');
const ra = [];
for (const c of CAU) {
  const r = await hoi(c);
  ra.push(r);
  console.log('═ ' + c);
  console.log('  gọi Action : ' + (r.goiAction.length ? r.goiAction.map(x => `"${x}"`).join(' · ') : 'KHÔNG'));
  if (r.traVe.length) console.log('  app trả    : ' + r.traVe[0].slice(0, 110));
  console.log('  mở miệng   : ' + (r.moMieng ?? '— không nói gì') + (r.moMieng ? ' giây' : ''));
  console.log('  APP đọc    : ' + (r.appDoc || '—').slice(0, 200));
  if (r.chan) console.log('  app chặn   : ' + r.chan.slice(0, 80));
  console.log('  AgentOS nói: ' + (r.noi || '(im)').slice(0, 200) + (r.catAgentOS ? '   [đã cắt AgentOS chen ' + r.catAgentOS + ' lần]' : ''));
  console.log();
  await ngu(4000);
}
fs.writeFileSync('C:/Users/ADMIN/AppData/Local/Temp/claude/d--RBW-Claude/5c8a7111-791d-4950-80d8-a9ee32ec5d7b/scratchpad/thu-agentos.json',
                 JSON.stringify(ra, null, 1), 'utf8');
