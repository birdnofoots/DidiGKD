package li.gkd.app.didi.flow

/**
 * 默认 flow.js（规则生成器产出，随 APK 内置作兜底）。
 * 抢单三重保障（[stated] 用户要求）：
 *  ① 点一次：同一张卡**点击成功后**15秒内不再点；**点击失败允许重试**(按钮只在短暂窗口可点)
 *  ② 检查点击结果：tap 失败则 log tap_failed，不做抢中检测(防误报)
 *  ③ 抢中判定：接乘客页 AND 出发地 AND 目的地 三者都匹配才算 GRAB_OK
 */
object DefaultFlow {
    const val CODE = """// 由规则生成器生成 2026-10-28 10:54:34
function timeInfo(t){var e={day:"",hour:-1,min:-1,month:-1,dom:-1};if(!t)return e;var s=String(t);var m=s.match(/^(今天|明天|后天)[ ]*([0-9]{1,2}):([0-9]{2})/);if(m)return{day:m[1],hour:+m[2],min:+m[3],month:-1,dom:-1};m=s.match(/^([0-9]{1,2})月([0-9]{1,2})日[ ]*([0-9]{1,2}):([0-9]{2})/);if(m)return{day:"",hour:+m[3],min:+m[4],month:+m[1],dom:+m[2]};return e;}
function isToday(ti){if(ti.day==="今天")return true;if(ti.month>0){var n=new Date();return ti.month===(n.getMonth()+1)&&ti.dom===n.getDate();}return false;}
function grabCheck(card){wait(1000);var onPage=screenHas("接乘客");var mFrom=!!card.addrFrom&&screenHas(card.addrFrom);var mTo=!!card.addrTo&&screenHas(card.addrTo);if(onPage&&mFrom&&mTo){log("GRAB_OK "+card.timeText+" "+card.addrFrom+" -> "+card.addrTo);screenshot("grab_ok");}else{log("GRAB_MISS onPage="+onPage+" from="+mFrom+" to="+mTo);}}
var __g={};
onCard(function(card){
  var ti=timeInfo(card.timeText);var price=Number(card.price);var dist=Number(card.distMe);
  // 层1: 出发地屏蔽
  if(String(card.addrFrom||"").indexOf("岛")>=0||String(card.addrFrom||"").indexOf("隧道")>=0||String(card.addrFrom||"").indexOf("当前位置")>=0){var a="no_grab";screenshot(a);log("rule1 no_grab");tap(a);return;}
  // 层2: 低价 <80
  if(!isNaN(price)&&price<80){var a="no_grab";screenshot(a);log("rule2 no_grab price="+price);tap(a);return;}
  // 层3: 近单 距<20且价>100
  if(!isNaN(dist)&&!isNaN(price)&&dist<20&&price>100){var a="grab";screenshot(a);log("rule3 grab dist="+dist+" price="+price);var __k=(card.addrFrom||"")+"|"+(card.addrTo||"")+"|"+(card.timeText||"");if(__g[__k]&&(Date.now()-__g[__k])<15000){log("rule3 already_tapped");return;}if(tap(a)){__g[__k]=Date.now();grabCheck(card);}else{log("rule3 tap_failed");}return;}
  // 层4: 高价 >200
  if(!isNaN(price)&&price>200){var a="grab";screenshot(a);log("rule4 grab price="+price);var __k=(card.addrFrom||"")+"|"+(card.addrTo||"")+"|"+(card.timeText||"");if(__g[__k]&&(Date.now()-__g[__k])<15000){log("rule4 already_tapped");return;}if(tap(a)){__g[__k]=Date.now();grabCheck(card);}else{log("rule4 tap_failed");}return;}
  // 其余 pass(只记录不点)
  screenshot("pass");log("pass price="+price+" dist="+dist+" time="+card.timeText);
});"""
}
