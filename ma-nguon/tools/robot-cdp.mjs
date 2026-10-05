/**
 * Điều khiển WebView của app TRÊN ROBOT THẬT qua Chrome DevTools Protocol.
 *
 *   node tools/robot-cdp.mjs "<biểu thức JS>"
 *
 * Trước đó phải nối cổng (PID đổi mỗi lần app mở lại):
 *   adb forward tcp:9333 localabstract:webview_devtools_remote_<PID của vn.roboworld.hcc>
 *
 * Vì sao cần: adb chỉ chạm được màn hình và chụp ảnh — không đọc được app đang ở màn nào,
 * có bao nhiêu thẻ, chữ trên màn có sót ký hiệu Markdown không. CDP đọc thẳng DOM của
 * chính WebView đang chạy trên robot. MainActivity đã bật setWebContentsDebuggingEnabled.
 *
 * ⚠ Dùng WebSocket trần (Node ≥ 22 có sẵn), KHÔNG dùng Playwright connectOverCDP: WebView
 *   Android không hỗ trợ nhóm lệnh Browser.* mà Playwright đòi lúc nối.
 */
const CONG = process.env.CONG_CDP || '9333';
const bieuThuc = process.argv.slice(2).join(' ');
if (!bieuThuc) { console.error('Thiếu biểu thức JS'); process.exit(2); }

const ds = await (await fetch(`http://127.0.0.1:${CONG}/json`)).json();
const trang = ds.find(x => x.type === 'page');
if (!trang) { console.error('Không thấy trang WebView nào'); process.exit(2); }

const ws = new WebSocket(trang.webSocketDebuggerUrl);
await new Promise((ok, loi) => { ws.onopen = ok; ws.onerror = loi; });
const kq = await new Promise(ok => {
  ws.onmessage = e => { const m = JSON.parse(e.data); if (m.id === 1) ok(m); };
  ws.send(JSON.stringify({ id: 1, method: 'Runtime.evaluate',
    params: { expression: bieuThuc, returnByValue: true, awaitPromise: true } }));
});
ws.close();
const r = kq.result;
if (r?.exceptionDetails) { console.log('LỖI JS: ' + (r.exceptionDetails.exception?.description || r.exceptionDetails.text)); process.exit(1); }
const v = r?.result?.value;
console.log(typeof v === 'string' ? v : JSON.stringify(v, null, 1));
