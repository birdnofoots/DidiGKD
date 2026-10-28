package li.gkd.app.didi.flow

/**
 * 默认 flow.js —— 滴滴车主控制流（P3）。
 * 四字段条件(price/distMe/timeText/addrFrom) + 抢单抢中检测(grab_ok)。
 * 新原语: wait(ms)/readText(vid)/screenHas(text)。
 * timeInfo 支持四种时间格式(今天/明天/后天HH:MM + MM月DD日HH:MM)。
 */
object DefaultFlow {
    const val CODE = """// ===== 可调阈值(按需改, 网页保存后下一单生效) =====
var TH = {
  minPrice:   80,            // 低于此价 → no_grab 不抢
  grabPrice:  200,           // 高于此价 → grab 抢单
  grabPrice2: 99,            // 此价以上 且 distMe < grabDist2 → grab
  grabDist2:  20,            // 上面的距离条件(km)
  // 当天这个时间段不抢 (如 [12,16] = 12:00~16:00); 设 null 关闭
  skipHours:  [12, 16],
  // 出发地含任一关键词 → no_grab 不抢; 设 [] 关闭
  skipAddr:   ['北站', '机场', '口岸'],
  // 抢单后等几秒检测是否抢中(进入接乘客页)
  grabWaitMs: 3000,
};

// 解析 timeText -> {day, hour, min, month, dom}
// 支持四种格式: 今天HH:MM / 明天HH:MM / 后天HH:MM / MM月DD日HH:MM(远期单,如 10月29日16:40)
function timeInfo(t) {
  var e = { day: '', hour: -1, min: -1, month: -1, dom: -1 };
  if (!t) return e;
  var s = String(t);
  var m = s.match(/^(今天|明天|后天)\s*(\d{1,2}):(\d{2})/);
  if (m) return { day: m[1], hour: +m[2], min: +m[3], month: -1, dom: -1 };
  m = s.match(/^(\d{1,2})月(\d{1,2})日\s*(\d{1,2}):(\d{2})/);
  if (m) return { day: '', hour: +m[3], min: +m[4], month: +m[1], dom: +m[2] };
  return e;
}

// 是否"今天"的单: 今天格式 或 月日=今天
function isToday(ti) {
  if (ti.day === '今天') return true;
  if (ti.month > 0) { var n = new Date(); return ti.month === (n.getMonth() + 1) && ti.dom === n.getDate(); }
  return false;
}

onCard(function (card) {
  var price = Number(card.price);
  var dist  = Number(card.distMe);
  var ti    = timeInfo(card.timeText);
  var from  = String(card.addrFrom || '');

  var verdict = 'pass';
  if (card.kind !== '实时单' && !isNaN(price)) {
    var hitAddr = TH.skipAddr.some(function (kw) { return from.indexOf(kw) >= 0; });
    var hitHour = !!TH.skipHours && isToday(ti) &&
                  ti.hour >= TH.skipHours[0] && ti.hour < TH.skipHours[1];

    if (price < TH.minPrice)                     verdict = 'no_grab';
    else if (hitAddr)                            verdict = 'no_grab';
    else if (hitHour)                            verdict = 'no_grab';
    else if (price > TH.grabPrice)               verdict = 'grab';
    else if (price > TH.grabPrice2 && !isNaN(dist) && dist < TH.grabDist2) verdict = 'grab';
  }

  // 一卡一图 + 记录
  var shot = screenshot(verdict);
  log(JSON.stringify({ price: card.price, distMe: card.distMe, time: card.timeText,
                       kind: card.kind, verdict: verdict, shot: shot }));

  // 点击 + 抢单抢中检测
  if (verdict === 'no_grab') {
    tap('no_grab');
  } else if (verdict === 'grab') {
    var ok = tap('grab');
    if (ok) {
      // 抢单后等几秒, 检测是否进入接乘客页(抢中)
      wait(TH.grabWaitMs);
      var onPickup = screenHas('接乘客');
      // 二次确认: 接乘客页通常仍显示 时间/目的地, 与卡片匹配则更确定
      var matchTime = !!card.timeText && screenHas(card.timeText);
      var matchTo   = !!card.addrTo   && screenHas(card.addrTo);
      if (onPickup || (matchTime && matchTo)) {
        log('GRAB_OK time=' + card.timeText + ' from=' + card.addrFrom + ' to=' + card.addrTo);
        screenshot('grab_ok');   // 抢中截图(绕过15秒去重, 存为 grab_ok 状态)
      } else {
        log('GRAB_MISS no-pickup-page-after-' + TH.grabWaitMs + 'ms');
      }
    }
  }
});
"""
}
