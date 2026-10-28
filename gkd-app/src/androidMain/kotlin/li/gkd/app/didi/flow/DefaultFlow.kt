package li.gkd.app.didi.flow

/**
 * 默认 flow.js —— 滴滴车主控制流（P3）。
 * 四个关键字段可组合条件：price/distMe/timeText/addrFrom
 * timeInfo 支持四种时间格式(今天/明天/后天HH:MM + MM月DD日HH:MM)
 * 常量集中在 TH，网页编辑器 http://<ip>:<port>/didi/flow 可改，保存后下一单生效。
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
  var price = Number(card.price);          // 价格
  var dist  = Number(card.distMe);         // 距我的距离(km)
  var ti    = timeInfo(card.timeText);     // 时间
  var from  = String(card.addrFrom || ''); // 出发地

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

  var shot = screenshot(verdict);
  log(JSON.stringify({ price: card.price, distMe: card.distMe, time: card.timeText,
                       kind: card.kind, verdict: verdict, shot: shot }));

  if (verdict === 'no_grab') tap('no_grab');
  else if (verdict === 'grab') tap('grab');
});
"""
}
