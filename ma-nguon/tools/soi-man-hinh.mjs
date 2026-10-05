/**
 * Chụp từng màn hình của app ở đúng khung 1920×1080 của robot Nova, để soi bằng mắt
 * trước khi build APK.
 *
 *   node tools/soi-man-hinh.mjs [thu-muc-ra]
 *
 * Vì sao phải chụp thay vì mở Chrome nhìn: bố cục vỡ trên Nova gần như luôn là vỡ
 * theo CHIỀU CAO — màn robot chỉ 1080px mà nội dung xếp dọc thì nút cuối bị co về
 * height 0 và biến mất, không báo lỗi gì. Chụp đúng 1920×1080 mới thấy.
 *
 * Playwright mượn của tools/html-video (giống tools/render-video.mjs), không cài riêng.
 */
import { existsSync, mkdirSync } from 'node:fs';
import { dirname, join, resolve } from 'node:path';
import { pathToFileURL, fileURLToPath } from 'node:url';

const HERE = dirname(fileURLToPath(import.meta.url));
const APP = resolve(HERE, '..');
const PW = join(APP, '..', '..', 'tools', 'html-video', 'packages',
                'adapter-hyperframes', 'node_modules', 'playwright', 'index.mjs');
if (!existsSync(PW)) {
  console.error(`Khong thay playwright tai:\n  ${PW}\nChay: cd tools/html-video && pnpm install`);
  process.exit(1);
}
const { chromium } = await import(pathToFileURL(PW).href);

const RA = resolve(process.argv[2] || join(APP, 'demo', 'anh-soi'));
mkdirSync(RA, { recursive: true });

const TRANG = pathToFileURL(join(APP, 'demo', 'index.html')).href;

/* Mỗi bước: [ten file, mo ta, ham chay trong trang] */
const BUOC = [
  ['01-man-cho',    'Man cho — ten trung tam + moi cham',        null],
  ['02-man-chinh',  'Man chinh: 5 o chuc nang + nut ve man cho', () => batDau()],
  ['02b-tra-cuu',   'Tra cuu: linh vuc + the nhanh, KHONG o hoi AI', () => { batDau(); ve('mh-tra-cuu'); }],
  ['03-linh-vuc',   'Muc con cua mot linh vuc',                  () => {
      batDau(); moLinhVuc(window.DU_LIEU.linh_vuc[0].ma); }],
  ['04-linh-vuc-2', 'Linh vuc nhieu muc nhat',                   () => {
      const lv = window.DU_LIEU.linh_vuc.slice().sort((a,b)=>b.muc.length-a.muc.length)[0];
      batDau(); moLinhVuc(lv.ma); }],
  /* Chon muc CO NOI DUNG THAT: bo cac muc tra ve danh sach lua chon ({{...}}),
     khong thi man nay soi nham sang man "Can lam ro" — dung loi 20/09/2026. */
  ['05-cau-tra-loi','Cau tra loi day du — da nap san',           () => {
      const ds = Object.values(window.DU_LIEU.nap_truoc)
          .filter(m => !/\{\{/.test(m.dap)).sort((a,b) => a.dap.length - b.dap.length);
      batDau(); hoiRoiHien(ds[Math.floor(ds.length/2)].hoi, 'Kết quả tra cứu'); }],
  ['05b-tra-loi-dai','Cau tra loi DAI NHAT — de tran khung nhat', () => {
      const ds = Object.values(window.DU_LIEU.nap_truoc)
          .filter(m => !/\{\{/.test(m.dap)).sort((a,b) => b.dap.length - a.dap.length);
      batDau(); hoiRoiHien(ds[0].hoi, 'Kết quả tra cứu'); }],
  /* Câu trả lời từ TẦNG TRA MẠNG (22/09/2026): nguồn là trang web, KHÔNG được hiện
     dưới tiêu đề "Căn cứ pháp lý" — người dân sẽ tưởng đó là văn bản luật. */
  ['05c-tra-mang', 'Tra mang — nguon web, khong phai can cu phap ly', () => {
      batDau(); hoiRoiHien('thoi tiet ngay mai o tuy hoa ' + Date.now(), 'Kết quả tra cứu');
      window.hienBanDayDu('**Thông tin tra trên Internet** — chỉ để tham khảo, không phải thông tin của Trung tâm.\n\n' +
        'Ngày mai ở Tuy Hòa trời nhiều mây, có mưa rào rải rác, nhiệt độ từ 24 đến 30 độ. Anh chị nên mang theo áo mưa khi ra ngoài.',
        JSON.stringify([{title:'Trung tâm Dự báo KTTV Quốc gia', url:'https://nchmf.gov.vn', loai:'web'},
                        {title:'Thời tiết Phú Yên', url:'', loai:'web'}])); }],
  ['06-dang-cho',   'Dang hoi nguon — vong quay',                () => {
      batDau(); hoiRoiHien('cau chua nap bao gio ' + Date.now(), 'Tra cuu'); }],

  /* ── Man TRO CHUYEN: mat robot · MOT loi thoai · MOT nut mic ── */
  ['07-ai-nghi',    'AI trang thai NGHI',                        () => {
      batDau(); window.baoAISanSang(true); ve('mh-chat'); }],
  ['08-ai-nghe',    'AI dang NGHE — thanh mic doi mau',          () => {
      batDau(); window.baoAISanSang(true); ve('mh-chat');
      dangNgheMic = true; datTrangThai('nghe'); }],
  ['09-ai-nguoi-noi','Nguoi vua noi — chu nghieng, nhat mau',    () => {
      batDau(); window.baoAISanSang(true); ve('mh-chat');
      window.nhanLoiNoi('nguoi', 'Toi muon lam giay khai sinh cho con'); }],
  ['10-ai-tra-loi', 'Robot tra loi NGAN — chu phai to nhat',     () => {
      batDau(); window.baoAISanSang(true); ve('mh-chat');
      window.nhanLoiNoi('robot', 'Da vang a.'); }],
  ['11-ai-tra-dai', 'Robot tra loi DAI — co chu tu co, KHONG cuon', () => {
      batDau(); window.baoAISanSang(true); ve('mh-chat');
      window.nhanLoiNoi('robot', 'Thu tuc dang ky khai sinh dung han.\n' +
        'To khai dang ky khai sinh\nGiay chung sinh\nGiay to tuy than cua nguoi di dang ky\n' +
        'Giay chung nhan ket hon cua cha me neu co\nSo ho khau hoac giay xac nhan cu tru\n' +
        'Van ban uy quyen neu nho nguoi khac di thay'); }],
  ['12-ai-lua-chon','Nguon hoi lai — hien nut bam',              () => {
      batDau(); window.baoAISanSang(true); ve('mh-chat');
      window.nhanLoiNoi('robot', 'Anh chi muon hoi ve thu tuc dat dai nao a?');
      window.hienLuaChonNgoai(JSON.stringify([
        {nhan:'Tach thua dat can giay to gi?', gui:'Thu tuc tach thua dat can giay to gi?'},
        {nhan:'Sang ten so do gom nhung buoc nao?', gui:'Thu tuc dang ky bien dong dat dai'},
        {nhan:'Cap doi Giay chung nhan can giay to gi?', gui:'Thu tuc cap doi GCN'}])); }],
  ['13-ai-chua-thong','AI chua thong — nut mic phai bao dang ket noi', () => {
      batDau(); window.baoAISanSang(false); ve('mh-chat'); }],
  ['14-ai-khong-nghe','Mic mo 15 giay khong nghe duoc gi',       () => {
      batDau(); window.baoAISanSang(true); ve('mh-chat');
      window.baoMicKhongNgheDuoc(); }],

  /* ── Giải trí · Thông tin (24/09/2026) ── */
  /* Dữ liệu THẬT (Thông báo 16/TB-PVHCC) — dung-app.py rải demo/thong-tin/thong-tin.js. */
  ['26-tt-can-bo-that', 'Thong tin — chi 5 nguoi CO ANH, bam the dau', () => new Promise((xong, loi) => {
      const s = document.createElement('script'); s.src = 'thong-tin/thong-tin.js';
      s.onerror = () => loi(new Error('Thieu demo/thong-tin/thong-tin.js — chay dung-app.py'));
      s.onload = () => {
        window.THONG_TIN = window.THONG_TIN_THAT; window.THU_MUC_THONG_TIN = 'thong-tin/';
        batDau(); veThongTin(); ve('mh-tt'); moTabTT('cb');
        setTimeout(() => {
          const the = [...document.querySelectorAll('#tt-noi-dung .the-can-bo')];
          const hong = [...document.querySelectorAll('.the-can-bo img')].filter(i => !i.naturalWidth).length;
          if(hong) return loi(new Error(hong + ' anh can bo KHONG nap duoc'));
          if(the.length !== 5) return loi(new Error('Phai hien DUNG 5 nguoi co anh, dang hien ' + the.length));
          the[0].click();
          if(!the[0].classList.contains('dang-doc')) return loi(new Error('Bam the khong danh dau dang doc'));
          xong();
        }, 400);
      };
      document.head.appendChild(s); })],
  ['16-giai-tri',   'Giai tri: nhay mua + do vui',               () => { batDau(); ve('mh-giai-tri'); }],
  /* ── Di chuyển (29/09/2026): trạm sạc có mật khẩu · du hành · nhãn trên màn chờ ── */
  ['16b-tram-sac',  'Ve tram sac — ban phim mat khau',          () => { batDau(); moManSac(); }],
  ['16c-sac-sai',   'Ve tram sac — go 3 so, o mat khau to 3 cham', () => { batDau(); moManSac(); goSo('1'); goSo('2'); goSo('3'); }],
  ['16d-sac-dang-ve','Ve tram sac — mat khau dung, dang ve',    () => { batDau(); moManSac(); ['4','0','3','0'].forEach(goSo);
      window.baoDiChuyen('VE_SAC', 'bat-dau', '', 55, false); }],
  ['16e-du-hanh',   'Du hanh — man xac nhan',                    () => { batDau(); moManDuHanh(); }],
  ['16f-cho-du-hanh','Man cho khi dang du hanh — nhan do',       () => { batDau(); moManDuHanh(); batDauDuHanh();
      window.baoDiChuyen('DU_HANH', 'dang-di', 'Diem 2', 70, false);
      if(manDang !== 'mh-cho') throw new Error('Bat dau du hanh KHONG ve man cho: ' + manDang);
      if(document.getElementById('nhan-di').hidden) throw new Error('Nhan "dang du hanh" KHONG hien'); }],
  ['17-nhay-chuan-bi','Nhay mua — nhac nguoi dan lui lai',       () => { batDau(); vaoManMua(); }],
  ['18-nhay-dang',  'Nhay mua — dang nhay, chi con nut Dung',     () => { batDau(); vaoManMua(); batDauNhay(); }],
  /* Nút Dừng bị video che (24/09/2026): bộ đổi mặt gán z-index cho video; thiếu tầng riêng
     là video đè lên nút. Ép đúng trạng thái đó — video lớp 2 hiện, z-index 2, mặt KHÔNG
     nhún — rồi hỏi trình duyệt phần tử trên cùng tại tâm nút là gì. */
  ['18b-nut-dung-tren-cung', 'Nhay mua — nut Dung khong bi video che', () => {
      const v2 = document.getElementById('clip-mua-2'), v1 = document.getElementById('clip-mua');
      v2.classList.remove('an'); v2.style.zIndex = 2; v1.style.zIndex = 1;
      /* Tắt hiệu ứng co giãn TRƯỚC khi đo: đang co về sau cú nhún (0,09 s) thì khung vẫn còn
         tầng riêng và nút nằm trên — đo lúc đó là phép xanh mà không canh gì (đã dính 24/09). */
      const lm = document.getElementById('lop-mat');
      lm.style.transition = 'none'; lm.classList.remove('nhun', 'nhun-nhe'); void lm.offsetWidth;
      const b = document.querySelector('#mua-dang button'), r = b.getBoundingClientRect();
      const tren = document.elementFromPoint(r.left + r.width / 2, r.top + r.height / 2);
      if (tren !== b && !b.contains(tren)) console.error('NUT DUNG BI CHE boi: ' + (tren && (tren.id || tren.tagName)));
      dungNhay(false, false);
      const b2 = document.querySelector('#mua-chuan-bi .nut-lon:last-child'), r2 = b2.getBoundingClientRect();
      const tren2 = document.elementFromPoint(r2.left + r2.width / 2, r2.top + r2.height / 2);
      if (tren2 !== b2 && !b2.contains(tren2)) console.error('NUT BAT DAU BI CHE boi: ' + (tren2 && (tren2.id || tren2.tagName)));
      lm.style.transition = ''; batDauNhay(); }],
  ['19-do-cau',     'Do vui — cau hoi + 4 dap an',               () => { dungNhay(false, false); batDau(); batDauDoVui(); }],
  ['20-do-da-chon', 'Do vui — da chon, to dap an dung',          () => {
      const b = document.querySelector('#luoi-dap-do button'); b.click(); }],
  ['21-do-ket-qua', 'Do vui — ket qua cuoi luot',                () => {
      for (let i = 0; i < 6; i++) { const n = document.querySelector('#luoi-dap-do button'); if (n) n.click(); cauDoTiep(); } }],
  ['22-tt-trong',   'Thong tin — CHUA CO TEP: phai noi dang cap nhat', () => {
      window.THONG_TIN = null; batDau(); veThongTin(); ve('mh-tt'); }],
  ['23-tt-gt-trong','Thong tin — tab gioi thieu, chua co tep',   () => { moTabTT('gt'); }],
  /* Dữ liệu THỬ chỉ để soi bố cục — tên là nhãn giả, không phải người thật. */
  ['24-tt-can-bo',  'Thong tin — 6 can bo (du lieu thu)',        () => {
      window.THU_MUC_THONG_TIN = './';
      window.THONG_TIN = { ten_don_vi: 'Trung tâm thử', can_bo: [1,2,3,4,5,6,7,8,9,10,11,12].map(i => ({
        ten: 'Cán bộ thử số ' + i, chuc_vu: 'Chức vụ thử', mo_ta: 'Dòng mô tả thử cho bố cục',
        anh: i % 2 ? 'bieu-cam/emoji_default.png' : 'khong-co-anh.jpg' })),
        gioi_thieu: { tieu_de: 'Tiêu đề thử', doan: ['Đoạn thử thứ nhất để soi bố cục.', 'Đoạn thử thứ hai.'],
          media: [{loai:'anh', tep:'bieu-cam/emoji_default.png'}, {loai:'video', tep:'bieu-cam/emoji_happy.mp4'},
                  {loai:'anh', tep:'bieu-cam/emoji_default.png'}] } };
      batDau(); veThongTin(); moTabTT('cb'); ve('mh-tt'); }],
  ['25-tt-gioi-thieu','Thong tin — gioi thieu co anh + video (du lieu thu)', () => { moTabTT('gt'); }],

  /* ── Man quan ly, giau sau bam giu mat robot 1,2 giay ── */
  ['15-quan-ly',    'Man quan ly kho — bam giu mat robot',       () => {
      batDau(); ve('mh-chat'); moManKho(); }],
];


const trinhDuyet = await chromium.launch();
/* Chụp ở 1920×1080 CSS px vì app BỐ CỤC ở khung đó: khung-app.html khai
   meta viewport width=1920, còn MainActivity bật useWideViewPort +
   loadWithOverviewMode để WebView thu nhỏ cả trang cho vừa màn 548×308 thật.
   ⚠ Đo trên máy 18/09/2026: robot cho khung 548×308 CSS px, dpr 3,5. Nếu ai đó
   đổi meta viewport sang device-width thì bố cục chuyển sang khung đó, và bộ soi
   này phải đổi theo — không thì ảnh đẹp mà máy thật vỡ. */
const CAO_CSS = 1080;
const trang = await trinhDuyet.newPage({ viewport: { width: 1920, height: CAO_CSS } });

const loi = [];
trang.on('pageerror', e => loi.push('  ✗ LOI JS: ' + e.message));
trang.on('console', m => { if (m.type() === 'error') loi.push('  ✗ console: ' + m.text()); });

await trang.goto(TRANG, { waitUntil: 'load' });
await trang.waitForTimeout(700);

for (const [ten, mo, ham] of BUOC) {
  if (ham) { await trang.evaluate(ham); await trang.waitForTimeout(450); }
  await trang.screenshot({ path: join(RA, ten + '.png') });

  /* Tu kiem ngay tai cho: co phan tu nao tran ra ngoai khung 1080, hay bi co ve 0 khong */
  const bao = await trang.evaluate((CAO_CSS) => {
    const hien = document.querySelector('.man-hinh.hien');
    const r = [];
    if (!hien) return ['KHONG co man hinh nao dang hien'];
    /* Đo CUỘN ĐƯỢC THẬT hay không, chứ không chỉ so scrollHeight.
       Phần tử ẩn bằng transform (pop-up gợi ý trượt xuống dưới) vẫn làm scrollHeight
       lớn hơn 1080, nhưng html đã overflow:hidden nên người dân không vuốt được gì —
       báo lỗi ở đó là báo nhầm, mà bộ soi kêu nhầm vài lần là không ai đọc nó nữa.
       Thứ thật sự hỏng là khi trang CUỘN ĐƯỢC: giao diện nhúc nhích dưới tay người dân. */
    const h = document.documentElement;
    h.scrollTop = 50;
    const cuonDuoc = h.scrollTop > 0;
    h.scrollTop = 0;
    if (cuonDuoc)
      r.push('TRANG CUON DUOC (' + h.scrollHeight + 'px > ' + CAO_CSS + 'px) — giao dien se nhuc nhich');
    /* Bốn thứ app CỐ Ý ẩn: nút mic (ẩn khi Agent SDK chưa bind được), nút trò
       chuyện, băng "khám lần đầu" và dải "tìm theo mã phòng" (cả hai nhường chỗ
       khi đang xem danh sách kết quả). Bỏ qua, không thì bộ soi kêu ở mọi màn.

       ⚠ Chỉ thêm vào danh sách này khi chắc chắn phần tử ĐƯỢC PHÉP ẩn ở màn đó.
       Mỗi cái thêm vào là một chỗ bộ soi thôi canh — nút "Nghe robot đọc lại" từng
       biến mất 0×0 vì trùng tên class, và chính phép kiểm này bắt được. */
    const CO_Y_AN = '.nut-mic, .nut-chat, #bang-moi, #bang-ma-phong, ' +
      /* Nút "Xem lại số của tôi": chỉ hiện sau khi người dân đã lấy số trong lượt
         này. Chức năng lấy số hiện chưa bật (anh Trường chưa chọn) nên nút luôn ẩn. */
      '#nut-xem-so, #nut-tiep-do';   /* nút Câu tiếp: chỉ hiện sau khi đã chọn đáp án */
    hien.querySelectorAll('button, .the-nhom, .muc, .nut-to, .toa-nut').forEach(e => {
      if (e.closest(CO_Y_AN)) return;
      const ten = (e.textContent || '').trim().replace(/\s+/g, ' ').slice(0, 34);
      /* Tổ tiên bị ẩn (lưới nhóm đang nhường chỗ cho danh sách kết quả) là chuyện
         bình thường — chỉ báo khi CHÍNH nút bị ẩn còn khung chứa nó vẫn hiện. */
      let cha = e.parentElement, choAn = false;
      while (cha && cha !== hien) {
        if (getComputedStyle(cha).display === 'none') { choAn = true; break; }
        cha = cha.parentElement;
      }
      if (choAn) return;
      const b = e.getBoundingClientRect();
      /* Bắt trường hợp 0×0: nút vẫn nằm trong DOM nhưng bị một luật CSS khác đè
         display:none. Bản đầu của bộ soi chỉ kiểm chiều cao, nên nút "Nghe robot
         đọc lại" mất tăm mà vẫn báo PASS. */
      if (b.width === 0 && b.height === 0)
        r.push('NUT KHONG DUOC VE (0x0, nhiều khả năng bị display:none): "' + ten + '"');
      else if (b.height < 8)
        r.push('NUT BI CO VE 0: "' + ten + '"');
      /* Nút nằm trong một khung CUỘN ĐƯỢC thì lọt khỏi màn là chuyện bình thường —
         danh sách kết quả tìm kiếm dài hơn màn hình là đúng thiết kế. Chỉ báo khi
         nút lọt ra ngoài mà KHÔNG có khung cuộn nào chứa nó. */
      let trongKhungCuon = false;
      let oCha = e.parentElement;
      while (oCha && oCha !== hien) {
        const ov = getComputedStyle(oCha).overflowY;
        if ((ov === 'auto' || ov === 'scroll') && oCha.scrollHeight > oCha.clientHeight + 2) {
          trongKhungCuon = true; break;
        }
        oCha = oCha.parentElement;
      }
      if (!trongKhungCuon && b.bottom > innerHeight + 2)
        r.push('NUT LOT KHOI KHUNG: "' + ten + '"');
    });
    return r;
  });
  const co = bao.length || loi.length;
  console.log((co ? '✗ ' : '✓ ') + ten + '  —  ' + mo);
  bao.forEach(x => console.log('  ✗ ' + x));
  loi.splice(0).forEach(x => console.log(x));
}

await trinhDuyet.close();
console.log('\nAnh o: ' + RA);
