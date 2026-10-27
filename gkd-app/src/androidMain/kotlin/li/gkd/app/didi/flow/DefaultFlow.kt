package li.gkd.app.didi.flow

/**
 * 默认 flow.js —— 滴滴车主控制流（P3）。
 * 这就是"bat 文件"：换场景只需改它（热更新，不重编译）。
 *
 * JS 掌控整个控制流 → 一卡一图、精准控制：
 *   onCard(card) → 决策 → screenshot(verdict) 一卡一图+记录 → tap(按钮)
 */
object DefaultFlow {
    const val CODE = """
onCard((card) => {
  // 1. 决策(移植 DidiWatch decide): 实时单恒接不点; 价<80 不抢; >200 抢单; >99且距<20 抢单
  const price = Number(card.price);
  const dist  = Number(card.distMe);
  let verdict = 'pass';
  if (card.kind !== '实时单' && !isNaN(price)) {
    if (price < 80)                                   verdict = 'no_grab';
    else if (price > 200)                             verdict = 'grab';
    else if (price > 99 && !isNaN(dist) && dist < 20) verdict = 'grab';
  }

  // 2. 一卡一图 + 记录(带决策) —— 脚本控制时机, 天然不重复
  const shot = screenshot(verdict);
  log(JSON.stringify({ price: card.price, kind: card.kind, verdict: verdict, shot: shot }));

  // 3. 点击
  if (verdict === 'no_grab') tap('no_grab');
  else if (verdict === 'grab') tap('grab');
});
"""
}
