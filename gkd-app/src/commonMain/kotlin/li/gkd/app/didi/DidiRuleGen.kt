package li.gkd.app.didi

import io.ktor.http.ContentType
import io.ktor.server.response.respondText
import io.ktor.server.routing.Routing
import io.ktor.server.routing.get

/**
 * flow.js 规则生成器（傻瓜式分层配置）。
 * [stated] 用户：按经验分层，每层填条件+动作，自动生成已校验的 flow.js
 * 6层：①屏蔽垃圾出发地 ②屏蔽去不了的目的地 ③低价不抢 ④时间段过滤(多段)
 *     ⑤近单抢(距离<X且价格>Y) ⑥高价必抢；其余 pass
 * 矛盾检测：如低价门槛>高价门槛 → 提示不生成
 */
fun Routing.registerDidiRuleGenRoute() {
    get("/didi/rules") { call.respondText(ContentType.Text.Html) { RULE_GEN_HTML } }
}

private const val RULE_GEN_HTML = """<!DOCTYPE html>
<html><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
<title>flow.js 规则生成器</title>
<style>
*{box-sizing:border-box}
body{font-family:system-ui;margin:0;background:#0f1115;color:#e6e6e6;padding-bottom:80px}
.h{padding:14px 16px;background:#1a1d24;position:sticky;top:0;z-index:5}
.h b{font-size:17px}
.ly{background:#1a1d24;border-radius:12px;padding:12px;margin:10px 12px}
.ly h3{margin:0 0 8px;font-size:14px;color:#4d8bff;display:flex;align-items:center;gap:6px}
.ly h3 .n{background:#2b6fff;color:#fff;border-radius:50%;width:20px;height:20px;display:inline-flex;align-items:center;justify-content:center;font-size:12px}
.row{display:flex;flex-wrap:wrap;gap:6px;align-items:center;margin:6px 0}
.row label{color:#8b93a7;font-size:13px;min-width:52px}
input[type=text],input[type=number]{background:#12141a;color:#e6e6e6;border:1px solid #2a2e37;border-radius:6px;padding:6px 8px;font-size:14px}
input[type=number]{width:70px}
input[type=text]{flex:1;min-width:120px}
select{background:#12141a;color:#e6e6e6;border:1px solid #2a2e37;border-radius:6px;padding:6px;font-size:14px}
.chk{display:flex;align-items:center;gap:4px;color:#8b93a7;font-size:13px}
.btns{padding:12px;display:flex;gap:8px;flex-wrap:wrap}
.btn{background:#2b6fff;color:#fff;border:0;padding:10px 18px;border-radius:8px;font-size:15px;cursor:pointer}
.btn.g{background:#1f7a3d}.btn.y{background:#8a6d1f}.btn.r{background:#7a2330}
.msg{margin:0 12px;padding:10px;border-radius:8px;font-size:13px;line-height:1.6}
.msg.err{background:#3a1a1f;color:#ffb4c0}.msg.ok{background:#152a1c;color:#8fe0a8}.msg.hide{display:none}
textarea{width:calc(100% - 24px);margin:12px;height:38vh;background:#12141a;color:#e6e6e6;border:1px solid #2a2e37;border-radius:8px;padding:12px;font:12px/1.5 ui-monospace,Menlo,monospace;box-sizing:border-box}
.t{color:#8b93a7;font-size:12px;margin:0 12px}
</style></head>
<body>
<div class="h"><b>flow.js 规则生成器（分层，命中即停）</b></div>

<div class="ly"><h3><span class="n">1</span>屏蔽垃圾出发地（含任一关键词→不抢）</h3>
<div class="row"><label>关键词</label><input type="text" id="l1kw" value="岛,隧道,当前位置,码头"></div>
<div class="row"><label>动作</label><select id="l1act"><option value="no_grab">不抢</option><option value="grab">抢</option><option value="pass">跳过</option></select>
<label style="min-width:auto"><input type="checkbox" id="l1shot" checked>截图</label></div></div>

<div class="ly"><h3><span class="n">2</span>屏蔽去不了的目的地（含任一关键词→动作）</h3>
<div class="row"><label>关键词</label><input type="text" id="l2kw" value="机场,客运站,邮轮,口岸"></div>
<div class="row"><label>动作</label><select id="l2act"><option value="no_grab">不抢</option><option value="grab">抢</option><option value="pass">跳过</option></select>
<label style="min-width:auto"><input type="checkbox" id="l2shot" checked>截图</label></div></div>

<div class="ly"><h3><span class="n">3</span>低价不抢（价格 &lt; 阈值）</h3>
<div class="row"><label>价格&lt;</label><input type="number" id="l3p" value="80" step="1">
<select id="l3act"><option value="no_grab">不抢</option><option value="grab">抢</option><option value="pass">跳过</option></select>
<label style="min-width:auto"><input type="checkbox" id="l3shot" checked>截图</label></div></div>

<div class="ly"><h3><span class="n">4</span>时间段过滤（当天指定时段→动作，可多段）</h3>
<div class="row"><label>时段</label><input type="text" id="l4hr" value="12-16" placeholder="如 12-16 或 12-16,20-23"></div>
<div class="row"><label>动作</label><select id="l4act"><option value="no_grab">不抢</option><option value="grab">抢</option><option value="pass">跳过</option></select>
<label style="min-width:auto"><input type="checkbox" id="l4shot" checked>截图</label>
<label style="min-width:auto"><input type="checkbox" id="l4on">启用</label></div></div>

<div class="ly"><h3><span class="n">5</span>近单抢（距我 &lt; X 且 价格 &gt; Y → 抢单）</h3>
<div class="row"><label>距我&lt;</label><input type="number" id="l5d" value="20" step="1"><label>km，价格&gt;</label><input type="number" id="l5p" value="100" step="1"></div>
<div class="row"><label>动作</label><select id="l5act"><option value="grab">抢</option><option value="no_grab">不抢</option><option value="pass">跳过</option></select>
<label style="min-width:auto"><input type="checkbox" id="l5shot" checked>截图</label></div></div>

<div class="ly"><h3><span class="n">6</span>高价必抢（价格 &gt; 阈值 → 抢单）</h3>
<div class="row"><label>价格&gt;</label><input type="number" id="l6p" value="200" step="1">
<select id="l6act"><option value="grab">抢</option><option value="no_grab">不抢</option><option value="pass">跳过</option></select>
<label style="min-width:auto"><input type="checkbox" id="l6shot" checked>截图</label></div></div>

<div class="ly"><h3>抢单后抢中检测</h3>
<div class="row"><label>等待</label><input type="number" id="gwm" value="3000" step="500"><label>毫秒后检测接乘客页</label></div></div>

<div class="btns">
<button class="btn g" onclick="gen()">生成 JS</button>
<button class="btn y" onclick="valid()">校验矛盾</button>
<button class="btn" onclick="save()">保存(下一单生效)</button>
</div>
<div class="msg hide" id="msg"></div>
<p class="t">生成后自动填入下方，检查无误点「保存」。保存即热更新，下一单生效；语法错会自动回退上一版。</p>
<textarea id="out" spellcheck="false" placeholder="点「生成 JS」后在此预览"></textarea>
<script>
function V(id){var e=document.getElementById(id);return e?(e.type==='checkbox'?e.checked:e.value):'';}
function msg(t,ok){var m=document.getElementById('msg');m.className='msg '+(ok?'ok':'err');m.textContent=t;}
function hideMsg(){document.getElementById('addr'.replace('addr','msg')).className='msg hide';}
function kws(id){return String(V(id)).split(/[,，、\s]+/).filter(function(s){return s.length>0;});}
function actOf(id){return V(id);}
function shotOf(id){return V(id)?'':'';} // placeholder

function validate(){
  var errs=[],warns=[];
  var p3=Number(V('l3p')),p5=Number(V('l5p')),p6=Number(V('l6p'));
  if(!(p3>0))errs.push('第3层低价阈值必须>0');
  if(p3>=p6 && actOf('l3act')==='no_grab' && actOf('l6act')==='grab')
    warns.push('低价不抢阈值('+p3+') >= 高价必抢阈值('+p6+')，中间区间可能无规则覆盖(会 pass)');
  if(actOf('l5act')==='grab' && !(Number(V('l5d'))>0))errs.push('第5层距我阈值必须>0');
  var hr=String(V('l4hr'));
  if(V('l4on') && hr.trim()){
    var segs=hr.split(/[,，]+/);
    for(var i=0;i<segs.length;i++){
      var m=segs[i].trim().match(/^(\d{1,2})\s*-\s*(\d{1,2})/);
      if(!m){errs.push('第4层时段格式错: '+segs[i]+' (应如 12-16)');}
      else if(Number(m[1])>=Number(m[2]))errs.push('第4层时段起止倒置: '+segs[i]);
    }
  }
  return {errs:errs,warns:warns};
}

function gen(){
  var v=validate();
  if(v.errs.length){msg('❌ '+v.errs.join('；'),false);return null;}
  var warn = v.warns.length?('⚠️ '+v.warns.join('；')+'\\n'):'';
  var L=[];
  L.push('// 由规则生成器生成 '+new Date().toISOString().slice(0,19));
  L.push('function timeInfo(t){var e={day:"",hour:-1,min:-1,month:-1,dom:-1};if(!t)return e;var s=String(t);var m=s.match(/^(今天|明天|后天)\\s*(\\d{1,2}):(\\d{2})/);if(m)return{day:m[1],hour:+m[2],min:+m[3],month:-1,dom:-1};m=s.match(/^(\\d{1,2})月(\\d{1,2})日\\s*(\\d{1,2}):(\\d{2})/);if(m)return{day:"",hour:+m[3],min:+m[4],month:+m[1],dom:+m[2]};return e;}');
  L.push('function isToday(ti){if(ti.day==="今天")return true;if(ti.month>0){var n=new Date();return ti.month===(n.getMonth()+1)&&ti.dom===n.getDate();}return false;}');
  L.push('function grabCheck(card){wait('+Number(V('gwm'))+');if(screenHas("接乘客")||(card.timeText&&screenHas(card.timeText)&&card.addrTo&&screenHas(card.addrTo))){log("GRAB_OK "+card.timeText+" -> "+card.addrTo);screenshot("grab_ok");}else{log("GRAB_MISS");}}');
  L.push('onCard(function(card){');
  L.push('  var ti=timeInfo(card.timeText);var price=Number(card.price);var dist=Number(card.distMe);');
  var idx=1;
  // 层1 出发地
  var k1=kws('l1kw');
  if(k1.length && actOf('l1act')!=='pass'){
    L.push('  // 层'+idx+': 出发地屏蔽');
    L.push('  if('+k1.map(function(k){return 'String(card.addrFrom||"").indexOf("'+k+'")>=0';}).join('||')+'){var a="'+actOf('l1act')+'";'+(V('l1shot')?"screenshot(a);":"")+'log("rule'+idx+' "+a);tap(a);return;}');
    idx++;
  }
  // 层2 目的地
  var k2=kws('l2kw');
  if(k2.length && actOf('l2act')!=='pass'){
    L.push('  // 层'+idx+': 目的地屏蔽');
    L.push('  if('+k2.map(function(k){return 'String(card.addrTo||"").indexOf("'+k+'")>=0';}).join('||')+'){var a="'+actOf('l2act')+'";'+(V('l2shot')?"screenshot(a);":"")+'log("rule'+idx+' "+a);tap(a);return;}');
    idx++;
  }
  // 层3 低价
  if(p3>0 && actOf('l3act')!=='pass'){
    L.push('  // 层'+idx+': 低价 <'+p3);
    L.push('  if(!isNaN(price)&&price<'+p3+'){var a="'+actOf('l3act')+'";'+(V('l3shot')?"screenshot(a);":"")+'log("rule'+idx+' "+a+" price="+price);tap(a);return;}');
    idx++;
  }
  // 层4 时段
  if(V('l4on') && String(V('l4hr')).trim() && actOf('l4act')!=='pass'){
    var segs=String(V('l4hr')).split(/[,，]+/);
    var cond=[];
    for(var i=0;i<segs.length;i++){var m=segs[i].trim().match(/^(\d{1,2})\s*-\s*(\d{1,2})/);if(m)cond.push('(ti.hour>='+m[1]+'&&ti.hour<'+m[2]+')');}
    if(cond.length){
      L.push('  // 层'+idx+': 当天时段 '+V('l4hr'));
      L.push('  if(isToday(ti)&&(' + cond.join('||') + ')){var a="'+actOf('l4act')+'";'+(V('l4shot')?"screenshot(a);":"")+'log("rule'+idx+' "+a+" hour="+ti.hour);tap(a);return;}');
      idx++;
    }
  }
  // 层5 近单
  var d5=Number(V('l5d'));
  if(d5>0 && p5>0 && actOf('l5act')!=='pass'){
    L.push('  // 层'+idx+': 近单 距<'+d5+'且价>'+p5);
    L.push('  if(!isNaN(dist)&&!isNaN(price)&&dist<'+d5+'&&price>'+p5+'){var a="'+actOf('l5act')+'";'+(V('l5shot')?"screenshot(a);":"")+'log("rule'+idx+' "+a+" dist="+dist+" price="+price);tap(a);grabCheck(card);return;}');
    idx++;
  }
  // 层6 高价
  if(p6>0 && actOf('l6act')!=='pass'){
    L.push('  // 层'+idx+': 高价 >'+p6);
    L.push('  if(!isNaN(price)&&price>'+p6+'){var a="'+actOf('l6act')+'";'+(V('l6shot')?"screenshot(a);":"")+'log("rule'+idx+' '+p6+'+"+" price="+price);tap(a);grabCheck(card);return;}');
    idx++;
  }
  L.push('  // 其余 pass(只记录不点)');
  L.push('  screenshot("pass");log("pass price="+price+" dist="+dist+" time="+card.timeText);');
  L.push('});');
  var js=L.join('\\n');
  document.getElementById('out').value=js;
  msg((warn?'⚠️ 生成成功(有警告)':'✅ 生成成功')+'，共 '+idx+' 层规则，检查后点「保存」', !v.warns.length);
  return js;
}

function valid(){
  var v=validate();
  if(v.errs.length){msg('❌ 矛盾/错误：'+v.errs.join('；'),false);return;}
  if(v.warns.length){msg('⚠️ 警告：'+v.warns.join('；')+'（仍可生成，但请确认符合预期）',false);return;}
  msg('✅ 校验通过，无矛盾',true);
}

async function save(){
  var js=gen();
  if(!js)return;
  var r=await fetch('/api/flow_js',{method:'POST',body:js});
  var t=await r.text();
  msg('💾 已保存：'+t,true);
}
</script>
</body></html>"""
