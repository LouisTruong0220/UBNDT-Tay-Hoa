/**
 * Chạy thử TRỌN luồng dẫn đường trên bản thử (demo/thu-nghiem.html), tự động.
 *
 *   node tools/thu-luong-dan-duong.mjs
 *
 * Vì sao cần: ba màn quan trọng nhất của app chỉ xuất hiện KHI ĐANG DẪN ĐƯỜNG —
 * màn đang dẫn, màn chỉ đường, thanh robot tự về sảnh. Bấm tay thì mỗi lần đổi
 * một dòng CSS lại phải ngồi bấm lại sáu bước; để đây thì chạy một lệnh.
 *
 * Nó kiểm bằng TRẠNG THÁI THẬT của app (màn nào đang hiện, robot nói câu gì),
 * không phải chỉ chụp ảnh rồi bảo là xong.
 */
import { existsSync, mkdirSync } from 'node:fs';
import { dirname, join, resolve } from 'node:path';
import { pathToFileURL, fileURLToPath } from 'node:url';

const HERE = dirname(fileURLToPath(import.meta.url));
const APP = resolve(HERE, '..');
const PW = join(APP, '..', '..', 'tools', 'html-video', 'packages',
                'adapter-hyperframes', 'node_modules', 'playwright', 'index.mjs');
if (!existsSync(PW)) { console.error('Khong thay playwright tai:\n  ' + PW); process.exit(1); }
const { chromium } = await import(pathToFileURL(PW).href);

const RA = join(APP, 'demo', 'anh-soi', 'luong-dan-duong');
mkdirSync(RA, { recursive: true });

const b = await chromium.launch();
const p = await b.newPage({ viewport: { width: 1920, height: 1080 } });
const loi = [];
p.on('pageerror', e => loi.push('LOI JS: ' + e.message));
p.on('console', m => { if (m.type() === 'error') loi.push('console: ' + m.text()); });

await p.goto(pathToFileURL(join(APP, 'demo', 'thu-nghiem.html')).href, { waitUntil: 'load' });
await p.waitForTimeout(700);

let hong = 0;
const man = () => p.evaluate(() => (document.querySelector('.man-hinh.hien') || {}).id);
const noi = () => p.evaluate(() => (document.querySelector('#gl-noi') || {}).textContent);
const veCho = () => p.evaluate(() => document.querySelector('#ve-cho').classList.contains('hien'));

async function kiem(nhan, dieuKien, anh) {
  const ok = await dieuKien();
  console.log((ok ? '✓ ' : '✗ ') + nhan);
  if (!ok) hong++;
  if (anh) await p.screenshot({ path: join(RA, anh + '.png') });
}

/* Chờ tới khi điều kiện đúng, tối đa `han` ms.
   Dùng cái này thay vì waitForTimeout với một con số đoán: câu robot đọc dài ngắn
   khác nhau, mà đồng hồ "về chỗ" chỉ chạy SAU khi đọc xong. Hẹn giờ cứng thì bài
   kiểm lúc đạt lúc trượt tuỳ độ dài câu — trượt kiểu đó không nói lên điều gì. */
async function cho(dieuKien, han = 20000) {
  const het = Date.now() + han;
  while (Date.now() < het) {
    if (await dieuKien()) return true;
    await p.waitForTimeout(250);
  }
  return false;
}

console.log('\n── A. Khoa NGOÀI khu: dẫn ra đầu hành lang rồi chỉ đường ──');

// Rút quãng đường xuống 3 giây cho nhanh
await p.evaluate(() => { GIA_LAP.giay = 3; GIA_LAP.tatDongHo = true; });
await p.evaluate(() => { thoiCho(); });
await p.evaluate(() => { const d = DU_LIEU.dich.find(x => x.ten === 'Khoa Nhi'); moChiTiet(d.id, 0); });
await p.waitForTimeout(300);

await kiem('Màn chi tiết hiện nút dẫn đường (giả lập đã cấp CAU)',
  async () => (await p.locator('#nut-dan').count()) === 1, '01-co-nut-dan');

await p.click('#nut-dan');
await p.waitForTimeout(400);
await kiem('Bấm dẫn → sang màn ĐANG DẪN', async () => (await man()) === 'mh-dan', '02-dang-dan');
await kiem('Robot nói đúng một câu "Xin mời đi theo tôi"',
  async () => /Xin mời đi theo tôi/.test(await noi() || ''));

await p.waitForTimeout(1100);   // qua mốc 30% — có vật cản
const tin = await p.evaluate(() => document.querySelector('#dan-tin').textContent);
await kiem('Diễn biến dọc đường hiện thành CHỮ: "' + tin + '"', async () => tin.length > 0);
await kiem('Dọc đường robot KHÔNG đọc thêm câu nào (đúng quy tắc im lặng)',
  async () => { const n = await noi(); return n === '—' || /Xin mời đi theo tôi/.test(n); });

await p.waitForTimeout(2600);   // tới nơi
await kiem('Tới nơi → sang màn CHỈ ĐƯỜNG', async () => (await man()) === 'mh-chi', '03-chi-duong');
await kiem('Màn chỉ đường ghi đúng toà và tầng của Khoa Nhi (Nhà DI, tầng 2)',
  async () => await p.evaluate(() =>
    document.querySelector('#chi-toa').textContent === 'DI' &&
    document.querySelector('#chi-tang').textContent === 'Tầng 2'));
await kiem('Tới nơi robot MỚI đọc câu chỉ đường',
  async () => /Đã tới nơi/.test(await noi() || ''));
/* Robot dừng ở đầu hành lang, còn cách các toà một quãng — nên phải chỉ bằng THỨ TỰ
   toà nhà, không bằng tên. Khoa Nhi ở nhà DI = toà nhà ĐẦU TIÊN. */
await kiem('Chỉ đường bằng THỨ TỰ toà nhà, không bằng tên — DI là toà "đầu tiên"',
  async () => /toà nhà đầu tiên/i.test(await noi() || ''));
await kiem('Màn hình cũng ghi thứ tự toà nhà',
  async () => /ĐẦU TIÊN/i.test(await p.evaluate(() => document.querySelector('#chi-di').textContent)));

await kiem('Đọc xong → hiện thanh "robot về chỗ" đếm ngược',
  async () => await cho(veCho), '04-thanh-ve-cho');
await kiem('Đồng hồ đang đếm ngược thật (số giây giảm dần)',
  async () => {
    const a = await p.evaluate(() => +document.querySelector('#vc-dem').textContent);
    await p.waitForTimeout(1600);
    const c = await p.evaluate(() => +document.querySelector('#vc-dem').textContent);
    return c < a;
  });

await p.click('#ve-cho button');   // "Về ngay"
await p.waitForTimeout(500);
await kiem('Bấm "Về ngay" → robot về sảnh, app về màn chờ',
  async () => (await man()) === 'mh-cho', '05-ve-man-cho');
await kiem('Giả lập ghi nhận robot tự đi về điểm "Sanh cho"',
  async () => await p.evaluate(() => /Sanh cho/.test(document.querySelector('#gl-log').textContent)));

console.log('\n── B. Chỗ TRONG khu lễ tân: dẫn thẳng tới nơi ──');
await p.evaluate(() => { thoiCho(); const d = DU_LIEU.dich.find(x => x.diem === 'nha-thuoc'); moChiTiet(d.id); });
await p.waitForTimeout(300);
await p.click('#nut-dan');
await kiem('Tới nơi → KHÔNG qua màn chỉ đường, về thẳng màn chi tiết',
  async () => await cho(async () => (await man()) === 'mh-ct' && !(await p.evaluate(
    () => document.querySelector('#mh-dan').classList.contains('hien')))),
  '06-trong-khu-toi-noi');

console.log('\n── B2. Toà DII và DIII phải được gọi là toà thứ hai / thứ ba ──');
for (const [khoa, iVt, mongDoi] of [['Khoa Sơ Sinh', 0, /toà nhà thứ hai/i],
                                    ['Khoa Phụ Sản', 2, /toà nhà thứ ba/i]]) {
  const noiDung = await p.evaluate(([k, i]) => {
    const d = DU_LIEU.dich.find(x => x.ten === k);
    return d.vi_tri[i].chi_duong;
  }, [khoa, iVt]);
  await kiem(khoa + ' → ' + noiDung.match(/[Tt]oà nhà (đầu tiên|thứ hai|thứ ba)/)?.[0],
    async () => mongDoi.test(noiDung));
}

/* Bốn chỗ ở nhà A · B · C · F KHÔNG nằm dọc hành lang ba toà D. Đếm thứ tự cho
   chúng là chỉ sai chỗ — mà cách đếm ấy có mặt ở ba nơi (dữ liệu, màn chỉ đường,
   PERSONA của mô hình) nên rất dễ lọt. */
console.log('\n── B3. Nhà A · B · C · F: dẫn tới TẬN NƠI, và tuyệt đối không đếm thứ tự ──');
const dsToaKhac = await p.evaluate(() =>
  DU_LIEU.dich.filter(x => x.loai === 'toa-khac' || x.loai === 'cap-cuu')
    .map(x => ({ ten: x.ten, loai: x.loai, diem: x.diem, toa: x.vi_tri[0].toa,
                 doc: x.doc, toiNoi: x.vi_tri[0].doc_toi_noi || '' })));

await kiem('Có đủ bốn chỗ mới (A · B · C · F)', async () => dsToaKhac.length === 4);
for (const d of dsToaKhac) {
  await kiem(d.ten + ' (nhà ' + d.toa + ') KHÔNG đếm thứ tự toà nhà',
    async () => !/toà nhà (đầu tiên|thứ hai|thứ ba)/i.test(d.doc + ' ' + d.toiNoi));
  await kiem(d.ten + ' có gắn điểm bản đồ riêng',
    async () => !!d.diem && d.diem !== 'truoc-hanh-lang');
}

// Đi trọn luồng thật với Khoa Răng Hàm Mặt — chỗ vừa chuyển từ DI tầng 4 sang nhà B
await p.evaluate(() => { veTrangChu(); thoiCho();
  const d = DU_LIEU.dich.find(x => x.ten === 'Khoa Răng Hàm Mặt'); moChiTiet(d.id); });
await p.waitForTimeout(300);
await kiem('Khoa Răng Hàm Mặt: màn chi tiết có nút dẫn',
  async () => (await p.locator('#nut-dan').count()) === 1, '08-rang-ham-mat');
await p.click('#nut-dan');
await kiem('Tới nơi → KHÔNG mở màn chỉ đường (robot đã đứng ngay đó)',
  async () => await cho(async () => (await man()) === 'mh-ct'), '09-rhm-toi-noi');
/* Chờ câu "Đã tới nơi" được phát hẳn rồi mới soi nội dung. Đọc ngay sau khi đổi
   màn thì lúc đạt lúc trượt tuỳ máy nhanh chậm — trượt kiểu đó không nói lên gì. */
await kiem('Robot đọc câu "Đã tới nơi" sau khi dẫn xong',
  async () => await cho(async () => /Đã tới nơi/.test(await noi() || '')));
const cauToiNoi = await noi() || '';
await kiem('Câu tới nơi nhắc đúng nhà B và số phòng D7',
  async () => /toà nhà bê/i.test(cauToiNoi) && /đê bảy/i.test(cauToiNoi));
await kiem('Câu tới nơi KHÔNG bảo "lên tầng một" (robot đang đứng ở tầng 1)',
  async () => !/lên tầng một/i.test(cauToiNoi));

console.log('\n── C. Cấp cứu: tuyệt đối không có nút dẫn ──');
await p.evaluate(() => { veTrangChu(); moNhom('cap-cuu'); });
await p.waitForTimeout(400);
await kiem('Màn cấp cứu đang hiện', async () => (await man()) === 'mh-cc');
await kiem('Màn cấp cứu KHÔNG có nút dẫn đường nào',
  async () => (await p.locator('#mh-cc #nut-dan').count()) === 0, '07-cap-cuu-khong-dan');
/* Khoa cấp cứu đổi hẳn 08/09/2026: C1 · tầng 1 · Nhà C. Khoa Hồi Sức Cấp Cứu Nội
   cũ (D23-D24, Nhà DIII) đã bỏ. Sai chỗ này là người đang nguy kịch chạy nhầm nhà. */
await kiem('Màn cấp cứu chỉ về Nhà C, phòng C1, tầng 1', async () => await p.evaluate(() =>
  document.querySelector('#cc-toa').textContent === 'C' &&
  document.querySelector('#cc-tang').textContent === '1' &&
  document.querySelector('#cc-ma').textContent.includes('C1')));
await kiem('Khoa Hồi Sức Cấp Cứu Nội cũ đã bỏ hẳn khỏi dữ liệu',
  async () => await p.evaluate(() => !DU_LIEU.dich.some(x => /Hồi [Ss]ức/.test(x.ten))));
await kiem('Không còn mã D23 / D24 trong danh mục',
  async () => await p.evaluate(() =>
    !DU_LIEU.dich.some(x => x.ma.includes('D23') || x.ma.includes('D24'))));

console.log('\n── D. Robot chưa sẵn sàng thì phải nói ra ──');
await p.evaluate(() => { GIA_LAP.lyDoChan = 'Robot chưa định vị được trên bản đồ.'; });
await p.evaluate(() => { veTrangChu(); const d = DU_LIEU.dich.find(x => x.ten === 'Khoa Mắt'); moChiTiet(d.id); });
await p.waitForTimeout(300);
await kiem('Nút dẫn bị làm mờ và hiện lý do',
  async () => await p.evaluate(() =>
    document.querySelector('#nut-dan').classList.contains('tat') &&
    document.querySelector('.ly-do') !== null), '08-chua-san-sang');
await p.evaluate(() => { GIA_LAP.lyDoChan = ''; });

console.log('\n── E. Nút mic chỉ hiện khi AI trả lời được ──');
await p.evaluate(() => { veTrangChu(); });
await p.waitForTimeout(200);
await kiem('AI đang TẮT → nút mic ẩn',
  async () => await p.evaluate(() => !document.querySelector('#mic1').classList.contains('co')));
await p.evaluate(() => { GIA_LAP.aiSanSang = true; window.baoAISanSang(true); });
await p.waitForTimeout(300);
await kiem('Bật AI → nút mic tự hiện, không phải tải lại trang',
  async () => await p.evaluate(() => document.querySelector('#mic1').classList.contains('co')),
  '09-mic-hien');

console.log('\n── F. Ba lớp chặn của bộ điều phối hội thoại ──');
/* Ba biểu thức chặn này nằm ở MainApplication.kt và được dựng lại y hệt trong
   gia-lap-robot.js. Bộ thử chạy qua bản giả lập, nên nó kiểm được ĐÚNG những câu
   phải bị chặn — thứ không thể bấm tay mà chắc chắn được. */
await p.evaluate(() => { GIA_LAP.aiSanSang = true; veTrangChu(); moChat(); });
await p.waitForTimeout(200);

/* Đọc câu robot THỰC SỰ NÓI, không đọc khung chat: khung chat chỉ nhận chữ khi đang
   ở màn Trò chuyện, mà câu quan trọng nhất (cấp cứu) lại chuyển sang màn đỏ ngay
   trước khi nói — bản đầu của bộ thử này báo trượt cả bảy phép kiểm vì lý do đó. */
async function kiemChan(cau, phaiCo, nhan) {
  await p.evaluate(() => { GIA_LAP.daNoi.length = 0; veTrangChu(); moChat(); });
  await p.evaluate(c => { document.querySelector('#chat-nhap').value = c; guiChat(); }, cau);
  await cho(async () => (await p.evaluate(() => GIA_LAP.daNoi.length)) > 0, 8000);
  const dap = (await p.evaluate(() => GIA_LAP.daNoi.join(' '))) || '';
  await kiem(nhan + '  «' + cau + '»', async () => phaiCo.test(dap));
  if (!phaiCo.test(dap)) console.log('    robot nói: ' + (dap.slice(0, 120) || '(im lặng)'));
}

await kiemChan('Có người ngất xỉu ở ngoài kia', /đi ngay, đừng chờ tôi dẫn/i, 'Cấp cứu → hô ngay, không dẫn');
await kiemChan('Tôi bị bệnh gì vậy',            /không khám bệnh và không đoán bệnh/i, 'Hỏi bệnh → từ chối');
await kiemChan('Khám ở đây hết bao nhiêu tiền', /chưa được nạp dữ liệu/i, 'Chưa có dữ liệu → nói thẳng');
await kiemChan('Bệnh viện mấy giờ mở cửa',      /chưa được nạp dữ liệu/i, 'Giờ làm việc → nói thẳng');
await kiemChan('Thời tiết hôm nay thế nào',     /chưa có trong dữ liệu|không dám đoán/i,
               'Câu vô nghĩa → KHÔNG được bịa ra một khoa');
await kiemChan('Tôi muốn đưa con đi khám',      /Khoa Nhi/i, 'Câu thật → chỉ đúng khoa');
await kiemChan('Phòng thu viện phí ở đâu',      /viện phí/i, 'Hỏi chỗ nộp tiền → KHÔNG bị chặn nhầm');

/* ⚠ TÊN KHOA BỊ CHÍNH LỚP CHẶN NUỐT (anh Trường báo 09/09/2026).
   Bệnh viện có KHOA CHẨN ĐOÁN HÌNH ẢNH từ 08/09, mà lớp chặn "hỏi bệnh" lại có từ
   khoá "chẩn đoán" đứng một mình — viết từ hồi chưa khoa nào tên như vậy. Hậu quả:
   câu tự nhiên nhất, gọi ĐÚNG TÊN KHOA, bị đáp "tôi không khám bệnh và không đoán
   bệnh được". Tra cứu vốn ra đúng khoa ở mức CHẮC (d0=202) nhưng lớp chặn nằm TRƯỚC
   nên không bao giờ tới được.
   Nay "chẩn đoán" phải đi kèm người/vật bị chẩn đoán mới tính là nhờ robot khám. */
await kiemChan('Khoa chẩn đoán hình ảnh ở đâu', /Khoa Chẩn đoán hình ảnh/i,
               'Tên khoa có chữ "chẩn đoán" → KHÔNG được bị chặn nhầm');
await kiemChan('Chẩn đoán hình ảnh ở tầng mấy', /Khoa Chẩn đoán hình ảnh/i,
               'Nói tắt tên khoa → vẫn phải trả lời');
await kiemChan('Bạn chẩn đoán giúp tôi', /không khám bệnh và không đoán bệnh/i,
               'Nhờ robot khám thì VẪN phải từ chối');
await kiemChan('Chẩn đoán bệnh cho tôi với', /không khám bệnh và không đoán bệnh/i,
               'Nhờ robot khám — cách nói khác');
/* CĐHA là mã phòng KHÔNG theo khuôn chữ-cái-cộng-số, nên RE_MA không bắt được và nó
   rơi xuống đường chấm điểm: "cdha" hiếm thật nhưng chỉ khớp 1/2 từ có nghĩa, độ phủ
   0,50 < sàn 0,55 → KHÔNG THẤY, trong khi "phòng D3 ở chỗ nào" thì chạy ngon. */
await kiemChan('Phòng CĐHA ở chỗ nào', /Khoa Chẩn đoán hình ảnh/i,
               'Mã phòng CĐHA đi được đường MÃ như D3 · C1');

/* ═══════════════════════════════════════════════════════════════════════
   G. MICRO BẤM-MỚI-NGHE

   Chép lối đã chạy thật ở app Medinova (v1.6) và app sự kiện Tây Ninh: bấm nút
   mới thu, dứt câu một giây là tự tắt và gửi câu đi. Mục đích là robot đang trả
   lời thì không hứng tạp âm.

   Mấy phép dưới đây ĐẾM LỆNH app gọi xuống Kotlin, không nhìn ảnh chụp — lỗi
   nhóm này (mic mở lúc không nên mở) không hiện ra trong ảnh màn hình.
   ═══════════════════════════════════════════════════════════════════════ */
console.log('\n── G. Micro bấm-mới-nghe ──');

const micMo = () => p.evaluate(() => GIA_LAP.micMo);
/* Hai nút micro: #mic1 ở MÀN CHÍNH (bấm là mở màn Trò chuyện rồi bật mic luôn),
   #mic2 ở trong màn Trò chuyện. */
const nutSang = () => p.evaluate(() =>
  document.querySelector('#mic2').classList.contains('dang-nghe'));

await p.evaluate(() => { GIA_LAP.aiSanSang = true; veTrangChu(); moChat(); });
await p.waitForTimeout(300);
await kiem('Vào màn Trò chuyện: mic vẫn ĐÓNG, không tự mở',
  async () => !(await micMo()));

await p.click('#mic2');
await kiem('Bấm nút micro → mic mở (sau quãng chờ robot nín)',
  async () => await cho(async () => await micMo(), 4000));
await kiem('Nút micro sáng lên "đang nghe"', async () => await nutSang());

/* Bộ nhận dạng bắn `final` ở mỗi lần ngắt hơi. Hai vế phải gom thành MỘT câu,
   không thì robot trả lời hai lần, mà nửa đầu tra ra khoa sai. */
await p.evaluate(() => { GIA_LAP.daNoi.length = 0; GIA_LAP.khachNoi('cho tôi hỏi'); });
await p.waitForTimeout(300);
await p.evaluate(() => GIA_LAP.khachNoi('khoa nhi ở đâu'));
await kiem('Dứt câu → Kotlin tự tắt mic',
  async () => await cho(async () => !(await micMo()), 4000));
await kiem('Nút micro tự tắt sáng theo (window.baoMicTuTat)',
  async () => !(await nutSang()));
await kiem('Hai vế gom thành MỘT câu, robot chỉ trả lời một lần',
  async () => await cho(async () => {
    const ds = await p.evaluate(() => GIA_LAP.daNoi);
    return ds.length === 1 && /Khoa Nhi/i.test(ds[0]);
  }, 8000));

/* Mic đóng mà vẫn nghe được thì phải BỎ — đây chính là chốt chống tạp âm. */
await p.evaluate(() => { GIA_LAP.daNoi.length = 0; GIA_LAP.khachNoi('khoa mắt ở đâu'); });
await p.waitForTimeout(1600);
await kiem('Mic ĐÓNG mà có tiếng nói → BỎ QUA, robot không đáp',
  async () => (await p.evaluate(() => GIA_LAP.daNoi.length)) === 0);

/* Rời màn Trò chuyện là mic phải đóng — kể cả khi mới bấm, còn đang chờ mở.
   Phải quay lại màn Trò chuyện trước: câu hỏi ở trên đã làm app mở màn hỏi toà
   (Khoa Nhi có ở hai toà), nên #mic2 lúc này đang nằm ở màn khuất. */
await p.evaluate(() => { veTrangChu(); moChat(); });
await p.waitForTimeout(300);
await p.click('#mic2');
await p.evaluate(() => veTrangChu());
await p.waitForTimeout(2200);
await kiem('Bấm mic rồi thoát ngay → mic KHÔNG được mở sau lưng',
  async () => !(await micMo()));

/* Nút micro ở MÀN CHÍNH: một chạm phải vừa mở màn Trò chuyện vừa bật mic —
   đây là đường người bệnh hay dùng nhất, bắt họ chạm hai lần là mất phần lớn. */
await p.evaluate(() => veTrangChu());
await p.waitForTimeout(200);
await p.click('#mic1');
await kiem('Bấm mic ở màn chính → sang màn Trò chuyện',
  async () => await cho(async () => (await man()) === 'mh-chat', 3000));
await kiem('… và mic mở luôn, không phải chạm lần hai',
  async () => await cho(async () => await micMo(), 4000));

/* ═══════════════════════════════════════════════════════════════════════
   H. HAI LUẬT anh Trường chốt 08/09/2026

     ① Người hỏi 1 → Robot trả lời 1.
        Một lần bấm mic chỉ gửi ĐÚNG MỘT câu, dù người bệnh ngắt hơi mấy lần.
     ② Lời AI chỉ phát khi người dùng KHÔNG thao tác.
        Quay lại · Đóng · Xoá đoạn chat · bấm mic nói tiếp → cắt tiếng NGAY và
        bỏ luôn câu trả lời còn đang trên đường về.

   Mấy phép này đếm SỐ CÂU robot nói và soi lệnh gọi xuống Kotlin — lỗi nhóm này
   không bao giờ hiện ra trong ảnh chụp màn hình.
   ═══════════════════════════════════════════════════════════════════════ */
console.log('\n── H. Một lượt hỏi = một câu trả lời · thao tác là ngắt lời ──');

const vaoChat = async () => {
  await p.evaluate(() => { GIA_LAP.daNoi.length = 0; veTrangChu(); moChat(); });
  await p.waitForTimeout(250);
};

/* ① Nói ba vế rời rạc trong CÙNG một lần bấm mic → vẫn chỉ một câu trả lời. */
await vaoChat();
await p.click('#mic2');
await cho(async () => await p.evaluate(() => GIA_LAP.micMo), 4000);
await p.evaluate(() => GIA_LAP.khachNoi('cho tôi hỏi'));
await p.waitForTimeout(400);
await p.evaluate(() => GIA_LAP.khachNoi('cái khoa'));
await p.waitForTimeout(400);
await p.evaluate(() => GIA_LAP.khachNoi('mắt ở đâu'));
await kiem('Ba vế rời rạc trong MỘT lần bấm mic → gom thành một câu',
  async () => await cho(async () =>
    (await p.evaluate(() => GIA_LAP.daNoi.length)) > 0, 9000));
await p.waitForTimeout(1200);          // để câu thứ hai (nếu có) kịp lọt vào
await kiem('… và robot chỉ trả lời ĐÚNG MỘT lần',
  async () => (await p.evaluate(() => GIA_LAP.daNoi.length)) === 1);

/* Ép mic mở lại rồi nói thêm — mô phỏng bộ nhận dạng bắn vế muộn SAU khi câu đã
   đi. Không có chốt "một phiên một câu" thì đây chính là chỗ đẻ ra câu trả lời
   thứ hai. Ép tay vì đường bình thường đã tắt mic, không lộ ra lỗi này. */
await p.evaluate(() => { GIA_LAP.micMo = true; GIA_LAP.khachNoi('còn khoa nhi nữa'); });
await p.waitForTimeout(2600);
await kiem('Vế nghe được MUỘN sau khi câu đã gửi → KHÔNG trả lời lần hai',
  async () => (await p.evaluate(() => GIA_LAP.daNoi.length)) === 1);
await p.evaluate(() => { GIA_LAP.micMo = false; });

/* ② Bấm Xoá đoạn chat giữa lúc robot đang đọc → phải cắt tiếng ngay. */
await vaoChat();
await p.evaluate(() => { doc('Đây là một câu rất dài để robot còn đang đọc dở khi ta bấm nút xoá đoạn chat.'); });
await p.waitForTimeout(300);
await kiem('Dựng cảnh: robot đang đọc dở',
  async () => (await p.evaluate(() => !!GIA_LAP.dangDoc)));
await p.click('.cd-nut');              // nút "Xoá đoạn chat"
await kiem('Bấm Xoá đoạn chat → cắt tiếng NGAY',
  async () => (await p.evaluate(() => !GIA_LAP.dangDoc)));

/* Bấm Đóng giữa lúc đang đọc cũng phải cắt. */
await vaoChat();
await p.evaluate(() => { doc('Câu dài thứ hai, lần này ta bấm nút Đóng để rời màn trò chuyện.'); });
await p.waitForTimeout(300);
await p.evaluate(() => dongChat());
await kiem('Bấm Đóng → cắt tiếng NGAY',
  async () => (await p.evaluate(() => !GIA_LAP.dangDoc)));

/* Câu trả lời VỀ MUỘN sau khi người bệnh đã thao tác → phải câm hẳn.
   Đây là lỗ mà "cắt tiếng" một mình KHÔNG bịt được: lúc họ bấm Đóng thì câu
   chưa phát, không có gì để cắt; mấy giây sau nó mới về và cất lên. */
await vaoChat();
await p.evaluate(() => { GIA_LAP.daNoi.length = 0;
  document.querySelector('#chat-nhap').value = 'khoa mắt ở đâu'; guiChat(); });
await p.waitForTimeout(200);           // câu đang trên đường về (giả lập trễ 900 ms)
await p.evaluate(() => dongChat());    // người bệnh bỏ đi giữa chừng
await p.waitForTimeout(2500);
await kiem('Câu trả lời VỀ MUỘN sau khi đã bấm Đóng → robot câm',
  async () => (await p.evaluate(() => GIA_LAP.daNoi.length)) === 0);

/* Bấm micro nói tiếp giữa lúc robot đang trả lời cũng phải cắt. */
await vaoChat();
await p.evaluate(() => { doc('Câu dài thứ ba, người bệnh sẽ bấm micro để hỏi tiếp giữa chừng.'); });
await p.waitForTimeout(300);
await p.click('#mic2');
await kiem('Bấm micro nói tiếp → cắt lời robot NGAY',
  async () => (await p.evaluate(() => !GIA_LAP.dangDoc)));
await p.evaluate(() => dongMic());

/* Nhưng app TỰ chuyển màn (tra cứu chắc → mở trang chỉ đường) thì KHÔNG được
   tính là người bệnh thao tác — không thì robot mở đúng trang rồi đứng câm. */
await vaoChat();
await p.evaluate(() => { GIA_LAP.daNoi.length = 0; });
await p.click('#mic2');
await cho(async () => await p.evaluate(() => GIA_LAP.micMo), 4000);
await p.evaluate(() => GIA_LAP.khachNoi('khoa mắt ở đâu'));
await kiem('App tự mở trang chi tiết → vẫn đọc câu trả lời, không bị bỏ',
  async () => await cho(async () =>
    (await p.evaluate(() => GIA_LAP.daNoi.length)) > 0, 9000), '10-ai-mo-trang');

/* ③ Dải hướng dẫn phải đổi chữ theo trạng thái mic — người bệnh đọc dải này
      để biết phải làm gì. */
await vaoChat();
const chuHD = () => p.evaluate(() => document.querySelector('#hd-chu').textContent);
await kiem('Dải hướng dẫn mời bấm micro khi mic đang đóng',
  async () => /bấm nút micro/i.test(await chuHD()));
await p.click('#mic2');
await cho(async () => await p.evaluate(() => GIA_LAP.micMo), 4000);
await kiem('Mic mở → dải đổi sang "Tôi đang nghe"',
  async () => /đang nghe/i.test(await chuHD()), '11-chat-dang-nghe');
await kiem('Nút micro cũng đổi nhãn thành "Đang nghe…"',
  async () => /đang nghe/i.test(await p.evaluate(() =>
    document.querySelector('#nm-chu').textContent)));
await p.evaluate(() => dongMic());

/* ═══════════════════════════════════════════════════════════════════════
   I. HỎI BẰNG MIỆNG MÀ RA LỆNH DẪN ĐƯỜNG THÌ ROBOT PHẢI ĐI THẬT

   Lỗi anh Trường báo 09/09/2026: *"AI nhận lệnh dẫn đường rồi mà robot chỉ phản
   hồi chỉ đường thôi, không thực hiện việc dẫn đường."* Đo lại đúng vậy —
   goiYTuRobot chỉ gọi moChiTiet, không có một đường nào tới CAU.danDuongToiDiem.
   Sáu câu hỏi, không lệnh dẫn nào được phát.

   ⚠ Nhóm phép thử này ĐẾM LỆNH GỌI XUỐNG KOTLIN (GIA_LAP.daDan), không nhìn màn
     hình. Lúc lỗi thì màn chỉ đường vẫn mở, nút vẫn sáng, chữ vẫn to — ảnh chụp
     giống hệt lúc chạy đúng, chỉ khác mỗi chuyện bánh xe không quay.

   Hai nhóm câu, và ranh giới giữa chúng mới là thứ đáng canh:
     · CÂU LỆNH ("dẫn tôi tới…") → phải phát lệnh đi.
     · CÂU HỎI  ("… ở đâu")      → mở màn, đọc vị trí, ĐỨNG YÊN. Sảnh chỉ có một
       con robot; để nó rời chỗ mỗi lần có người hỏi thì cả buổi không ai gặp được.
   ═══════════════════════════════════════════════════════════════════════ */
console.log('\n── I. Ra lệnh bằng miệng thì robot phải lăn bánh ──');

/** Nói một câu qua đúng cửa Kotlin dùng, rồi đếm lệnh dẫn đường đã phát. */
async function kiemDan(cau, phaiDi, nhan) {
  await p.evaluate(() => {
    GIA_LAP.daDan.length = 0; GIA_LAP.daNoi.length = 0;
    if (window.dungDanDuongNgay) { try { dungDanDuongNgay(); } catch (e) {} }
    veTrangChu(); moChat();
  });
  await p.waitForTimeout(150);
  await p.evaluate(c => { document.querySelector('#chat-nhap').value = c; guiChat(); }, cau);
  await cho(async () => (await p.evaluate(() => GIA_LAP.daNoi.length)) > 0, 8000);
  await p.waitForTimeout(300);
  const daDan = await p.evaluate(() => GIA_LAP.daDan.slice());
  const diThat = daDan.length > 0;
  await kiem(nhan + '  «' + cau + '»', async () => diThat === phaiDi);
  if (diThat !== phaiDi) {
    console.log('    lệnh dẫn đã phát: ' + (daDan.join(', ') || '(không có lệnh nào)'));
    console.log('    màn đang hiện  : ' + (await man()));
  }
}

/* ① Câu LỆNH — phải đi, và phải đi tới ĐÚNG điểm bản đồ. */
await kiemDan('dẫn tôi tới khoa mắt',            true,  'Lệnh dẫn tới khoa trong toà D → đi ra đầu hành lang');
await kiemDan('đưa tôi tới phòng thu viện phí',  true,  'Lệnh dẫn tới tiện ích trong khu → đi thẳng');
await kiemDan('dắt tôi ra chỗ chụp x quang',     true,  'Lệnh dẫn tới nhà F → đi tới tận nơi');
await kiemDan('dẫn tôi ra nhà vệ sinh',          true,  'Lệnh dẫn — cách nói khác');

/* ② Câu HỎI — tuyệt đối không được tự lăn bánh. */
await kiemDan('khoa mắt ở đâu',                  false, 'Chỉ HỎI thì robot đứng yên');
await kiemDan('nhà thuốc bệnh viện ở đâu',       false, 'Chỉ HỎI thì robot đứng yên');
await kiemDan('tôi muốn đưa con đi khám',        false, '"đưa con đi khám" là kể việc, KHÔNG phải lệnh');
await kiemDan('cho tôi hỏi hướng dẫn làm thủ tục', false, '"hướng dẫn" chứa chữ "dẫn" nhưng không phải lệnh');

/* ③ HAI CHỐT AN TOÀN — quan trọng hơn cả nhóm ①. */
await kiemDan('dẫn tôi tới khoa cấp cứu',        false,
  '⚠ CẤP CỨU: dù RA LỆNH cũng TUYỆT ĐỐI KHÔNG DẪN (robot chậm hơn người chạy)');
/* ⚠ Câu trên do LỚP CHẶN CẤP CỨU giữ — nó có chữ "cấp cứu" nên bị bắt ngay ở lớp 1,
   không bao giờ đi tới goiYTuRobot. Câu dưới mới là câu ĐI LỌT qua lớp đó: đọc mã
   phòng C1 thì không có chữ nào khớp TU_CAP_CUU, tra cứu ra Khoa Cấp cứu bằng đường
   MÃ PHÒNG, và lúc ấy thứ duy nhất chặn robot lăn bánh là chốt manDang === 'mh-ct'
   trong goiYTuRobot. Thiếu dòng này thì gỡ chốt đó ra bộ thử vẫn xanh. */
await kiemDan('dẫn tôi tới phòng C1',            false,
  '⚠ CẤP CỨU đi lối MÃ PHÒNG — lọt lớp chặn cấp cứu, chốt màn hình phải giữ');
await kiemDan('dẫn tôi tới khoa nhi',            false,
  '⚠ Khoa ở NHIỀU TOÀ: phải hỏi lại toà nào trước, chưa biết đi đâu thì chưa đi');

/* ④ Nhận dạng ý định — kiểm THẲNG hàm xinDanDuong(), không đi vòng qua tra cứu.
   Vì sao phải kiểm riêng: mấy câu chứa "hướng dẫn" đều tra cứu không ra đích nào,
   nên chúng dừng lại từ trước, không bao giờ chạm tới nhánh dẫn đường. Kiểm qua
   đường kia thì phép thử xanh mà chẳng canh gì cả — bỏ hẳn bước loại cụm "hướng
   dẫn" ra, cả nhóm vẫn xanh. Đã thử ngược đúng như vậy 09/09/2026. */
const yDinh = cau => p.evaluate(c => xinDanDuong(c), cau);
for (const [cau, laLenh] of [
  ['dẫn tôi tới khoa mắt',              true],
  ['dắt tôi ra ngoài kia',              true],
  ['đưa tôi đi',                        true],
  ['đưa mẹ tôi tới khoa tim mạch',      true],
  ['robot dẫn giúp tôi nhé',            true],
  ['hướng dẫn tôi cách lấy số',         false],
  ['cho tôi xin tờ hướng dẫn khám bệnh', false],
  ['tôi muốn đưa con đi khám',          false],
  ['khoa mắt ở đâu',                    false],
  ['tôi cần chụp x quang',              false],
]) {
  await kiem('Nhận ra ' + (laLenh ? 'LỆNH DẪN  ' : 'câu thường') + '  «' + cau + '»',
    async () => (await yDinh(cau)) === laLenh);
}

/* ═══════════════════════════════════════════════════════════════════════
   J. TÊN ĐIỂM BẢN ĐỒ LỆCH CHÍNH TẢ

   Anh Trường báo 09/09/2026: mọi khoa dẫn đường được, RIÊNG Khoa Chẩn đoán hình
   ảnh thì không. Đó là điểm DUY NHẤT đã có sẵn trên bản đồ từ trước — ba điểm kia
   bệnh viện mới đặt theo đúng tên app đưa ra. Bản đồ ghi "Khoa chuẩn đoán hình
   ảnh" ("chuẩn đoán" là lỗi chính tả rất phổ biến của "chẩn đoán"), app hỏi
   "Khoa chan doan hinh anh".

   ⚠ Bộ dò mềm chỉ bỏ DẤU và hạ chữ thường — nó KHÔNG sửa CHÍNH TẢ:
        "khoa chuan doan hinh anh"  ≠  "khoa chan doan hinh anh"
     Robot ném ERROR_DESTINATION_NOT_EXIST rồi đứng im. Nhìn từ ngoài y hệt máy
     hỏng, mà lại chỉ hỏng đúng một khoa nên rất khó lần ra.

   Nhóm phép thử này dựng lại BẢN ĐỒ THẬT ngoài bệnh viện rồi bắt app đi. Nó là
   thứ duy nhất trong cả bộ kiểm được lớp khớp tên điểm — trước đây giả lập bỏ hẳn
   bước dò tên, nên loại lỗi này không bao giờ lộ ra trên máy tính.
   ═══════════════════════════════════════════════════════════════════════ */
console.log('\n── J. Tên điểm trên bản đồ lệch chính tả ──');

/* Bản đồ y như ngoài hiện trường: CĐHA viết "chuẩn đoán", cấp cứu còn nguyên dấu. */
const BAN_DO_THAT = ['Tiep don', 'Vien phi', 'Nha thuoc', 'Can tin', 'Nha ve sinh',
  'Truoc hanh lang', 'Sanh cho', 'Tram sac', 'Khoa cấp cứu',
  'Khu Tham do chuc nang', 'Khoa Rang Ham Mat', 'Khoa chuẩn đoán hình ảnh'];

/** Đặt bản đồ giả lập rồi bắt app dẫn tới một điểm; trả true nếu robot lăn bánh. */
async function thuDiemTrenBanDo(tenAppHoi, banDo) {
  return p.evaluate(([ten, ds]) => {
    GIA_LAP.diemTrenBanDo = ds;
    GIA_LAP.daNoi.length = 0;
    CAU.danDuongToiDiem(ten);
    const im = GIA_LAP.daNoi.join(' ').indexOf('chưa có điểm tên') >= 0;
    CAU.dungDanDuong();
    return !im;
  }, [tenAppHoi, banDo]);
}

/* Nạp bảng bí danh đúng cách app làm lúc khởi động. */
await p.evaluate(() => {
  const bd = {};
  for (const k in DU_LIEU.diem) {
    const d = DU_LIEU.diem[k];
    if (d.bi_danh && d.bi_danh.length) bd[d.diem_ban_do] = d.bi_danh;
  }
  CAU.datBiDanhDiem(JSON.stringify(bd));
});

await kiem('Bản đồ ghi "Khoa chuẩn đoán hình ảnh" → BÍ DANH cứu, robot vẫn đi',
  async () => await thuDiemTrenBanDo('Khoa chan doan hinh anh', BAN_DO_THAT));
await kiem('Bản đồ ghi "Khoa cấp cứu" (còn dấu) → dò mềm lo được',
  async () => await thuDiemTrenBanDo('Khoa cap cuu', BAN_DO_THAT));
await kiem('Điểm đặt đúng tên thì đương nhiên chạy',
  async () => await thuDiemTrenBanDo('Khoa Rang Ham Mat', BAN_DO_THAT));
await kiem('Bản đồ ghi thiếu chữ "Khoa" → bí danh vẫn nhận',
  async () => await thuDiemTrenBanDo('Khoa chan doan hinh anh',
    BAN_DO_THAT.map(x => x === 'Khoa chuẩn đoán hình ảnh' ? 'Chan doan hinh anh' : x)));

/* ⚠ Chốt ngược: bản đồ THIẾU HẲN điểm thì phải báo lỗi, TUYỆT ĐỐI không im lặng
   coi như thành công. Không có phép này thì một hàm dò tên luôn trả "khớp" cũng
   làm ba phép trên xanh. */
await kiem('Bản đồ KHÔNG có điểm nào giống → phải báo lỗi, không được lờ đi',
  async () => !(await thuDiemTrenBanDo('Khoa chan doan hinh anh',
    BAN_DO_THAT.filter(x => x !== 'Khoa chuẩn đoán hình ảnh'))));

/* ── Đặt tên điểm CÓ DẤU tiếng Việt — anh Trường hỏi 09/09/2026 ──────────
   Bệnh viện đặt tên điểm có dấu ("Khoa Chẩn đoán hình ảnh") trong khi app khai
   tên không dấu. Quét cả mười hai điểm để chốt: dấu KHÔNG phải vấn đề, bộ dò mềm
   bỏ dấu trước khi so. Đây cũng là bằng chứng khoanh vùng — nếu dấu là nguyên
   nhân thì cả bốn chỗ mới đều hỏng, chứ không riêng một khoa. */
const BAN_DO_CO_DAU = {
  'Tiep don': 'Tiếp đón', 'Vien phi': 'Viện phí', 'Nha thuoc': 'Nhà thuốc',
  'Can tin': 'Căn tin', 'Nha ve sinh': 'Nhà vệ sinh', 'Truoc hanh lang': 'Trước hành lang',
  'Sanh cho': 'Sảnh chờ', 'Tram sac': 'Trạm sạc', 'Khoa cap cuu': 'Khoa Cấp cứu',
  'Khu Tham do chuc nang': 'Khu Thăm dò chức năng',
  'Khoa Rang Ham Mat': 'Khoa Răng Hàm Mặt',
  'Khoa chan doan hinh anh': 'Khoa Chẩn đoán hình ảnh',
};
const dsCoDau = Object.values(BAN_DO_CO_DAU);
let dauHong = [];
for (const [appHoi, tren] of Object.entries(BAN_DO_CO_DAU)) {
  if (!(await thuDiemTrenBanDo(appHoi, dsCoDau))) dauHong.push(tren);
}
await kiem('Cả 12 điểm đặt tên CÓ DẤU đều dẫn được (dấu không phải vấn đề)',
  async () => dauHong.length === 0);
if (dauHong.length) console.log('    điểm không khớp: ' + dauHong.join(' · '));

/* Trả bản đồ về mặc định cho phần sau của bộ thử. */
await p.evaluate(() => { GIA_LAP.diemTrenBanDo = null; });
await p.evaluate(() => { GIA_LAP.diemTrenBanDo = ['Tiep don','Vien phi','Nha thuoc','Can tin',
  'Nha ve sinh','Truoc hanh lang','Sanh cho','Tram sac','Khoa cap cuu',
  'Khu Tham do chuc nang','Khoa Rang Ham Mat','Khoa chan doan hinh anh']; });

/* ═══════════════════════════════════════════════════════════════════════
   K. MIC CHẾT TRÊN MÁY THẬT — anh Trường báo 11/09/2026

   "Vào app thì mất nút mic, đợi một lúc sau mới hiện, bấm vào nói thì robot không
   nhận." Máy ở bệnh viện, không cắm được cáp — nên app phải tự khai ra nguyên nhân.
   Nhóm này canh hai thứ: mic hết giờ mà không nghe được chữ nào thì PHẢI nói thẳng
   ra (bản trước tắt im lặng), và bảng tự chẩn đoán chỉ đúng từng kiểu hỏng.
   ═══════════════════════════════════════════════════════════════════════ */
console.log('\n── K. Mic mở mà không nghe được · bảng tự chẩn đoán ──');
await p.evaluate(() => { GIA_LAP.aiSanSang = true; veTrangChu(); moChat(); });
await p.waitForTimeout(250);
await p.evaluate(() => window.baoMicKhongNgheDuoc());
await kiem('Mic hết giờ mà KHÔNG nghe được chữ nào → dải hướng dẫn nói thẳng ra',
  async () => /chưa nghe được/i.test(await p.evaluate(() =>
    document.querySelector('#hd-chu').textContent)), '12-mic-khong-nghe');
await kiem('… và dải chuyển sang màu cảnh báo',
  async () => await p.evaluate(() =>
    document.querySelector('#chat-huong-dan').classList.contains('khong')));

const bangHien = () => p.evaluate(() =>
  getComputedStyle(document.querySelector('#phu-chan-doan')).display !== 'none');
async function chanDoan(tt) {
  await p.evaluate(o => { Object.assign(GIA_LAP, o); moChanDoan(); }, tt);
  return p.evaluate(() => document.querySelector('#chan-doan').textContent);
}

/* Bấm một cái KHÔNG được mở — người bệnh chạm nhầm tiêu đề là chuyện thường. */
await p.dispatchEvent('#mh-chat .cd-ten', 'mousedown');
await p.waitForTimeout(300);
await p.dispatchEvent('#mh-chat .cd-ten', 'mouseup');
await p.waitForTimeout(1300);
await kiem('Chạm nhanh tiêu đề thì bảng chẩn đoán KHÔNG mở', async () => !(await bangHien()));
await p.dispatchEvent('#mh-chat .cd-ten', 'mousedown');
await p.waitForTimeout(1500);
await kiem('Bấm GIỮ tiêu đề 1,2 giây thì bảng chẩn đoán mở', async () => await bangHien());
await p.dispatchEvent('#mh-chat .cd-ten', 'mouseup');

let cd = await chanDoan({ mang: '4g', micHeThong: 1, aiSanSang: true, loiAI: '' });
await p.screenshot({ path: join(RA, '13-chan-doan.png') });
await kiem('Robot chạy 4G → bảng bảo nối lại Wi-Fi', async () => /4G/.test(cd) && /Nối lại Wi-Fi/.test(cd));
cd = await chanDoan({ mang: 'mat', micHeThong: 1, aiSanSang: false, loiAI: '' });
await kiem('Mất mạng → bảng nói thẳng MẤT MẠNG', async () => /MẤT MẠNG/.test(cd));
cd = await chanDoan({ mang: 'wifi', micHeThong: 0, aiSanSang: true, loiAI: '' });
await kiem('Micro của máy TẮT → bảng chỉ đúng lệnh bật lại', async () => /microphone 1/.test(cd));
cd = await chanDoan({ mang: 'wifi', micHeThong: 1, aiSanSang: false, loiAI: 'status=2 · lỗi=quota' });
await kiem('AI chưa thông → bảng in NGUYÊN VĂN lỗi của máy chủ', async () => /status=2 · lỗi=quota/.test(cd));
cd = await chanDoan({ mang: 'wifi', micHeThong: 1, aiSanSang: true, loiAI: '' });
await kiem('Mạng ổn, AI thông mà chưa nghe câu nào → nhắc đứng trước camera + mở từ RobotOS',
  async () => /camera/i.test(cd) && /RobotOS/.test(cd));
await p.evaluate(() => { veTrangChu(); });
await kiem('Đổi màn thì bảng chẩn đoán tự đóng, không đè lên màn khác',
  async () => !(await bangHien()));
await p.evaluate(() => { Object.assign(GIA_LAP, { mang: 'wifi', micHeThong: 1, loiAI: '' }); });

if (loi.length) { console.log('\n✗ Lỗi JS bắt được:'); loi.forEach(x => console.log('   ' + x)); hong += loi.length; }
console.log('\n' + (hong ? '✗ ' + hong + ' phép kiểm KHÔNG đạt' : '✓ Tất cả phép kiểm đều đạt'));
console.log('Ảnh: ' + RA);
await b.close();
process.exit(hong ? 1 : 0);
