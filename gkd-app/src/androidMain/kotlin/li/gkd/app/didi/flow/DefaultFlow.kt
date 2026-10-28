package li.gkd.app.didi.flow

/**
 * 默认 flow.js —— 滴滴车主控制流（P3）。
 * 这就是"bat 文件"：换场景只需改它（热更新，不重编译）。
 *
 * 四个关键字段可组合条件：price(价格) / distMe(距我距离) / timeText(时间) / addrFrom(出发地)
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

// 解析 timeText -> {day:'今天'|'明天'|'后天', hour, min}
// 卡片时间格式统一为 今天HH:MM / 明天HH:MM / 后天HH:MM
function timeInfo(t) {
  if (!t) return { day: '', hour: -1, min: -1 };
  var m = String(t).match(/^(今天|明天|后天)\s*(\d{1,2}):(\d{2})/);
  if (!m) return { day: '', hour: -1, min: -1 };
  return { day: m[1], hour: parseInt(m[2], 10), min: parseInt(m[3], 10) };
}

onCard(function (card) {
  // 四个关键字段
  var price = Number(card.price);        // 价格
  var dist  = Number(card.distMe);       // 距我的距离(km)
  var ti    = timeInfo(card.timeText);   // 时间: ti.day / ti.hour / ti.min
  var from  = String(card.addrFrom || ''); // 出发地

  var verdict = 'pass';
  if (card.kind !== '实时单' && !isNaN(price)) {
    // 条件1: 出发地含关键词(火车站/机场/口岸等) → 不抢
    var hitAddr = TH.skipAddr.some(function (kw) { return from.indexOf(kw) >= 0; });
    // 条件2: 当天指定时间段(如12~16点) → 不抢
    var hitHour = !!TH.skipHours && ti.day === '今天' &&
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

  // 点击
  if (verdict === 'no_grab') tap('no_grab');
  else if (verdict === 'grab') tap('grab');
});
"""
}
