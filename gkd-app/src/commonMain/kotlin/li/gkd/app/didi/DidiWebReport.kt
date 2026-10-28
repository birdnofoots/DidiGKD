package li.gkd.app.didi

import io.ktor.http.ContentType
import io.ktor.server.response.respondFile
import io.ktor.server.response.respondText
import io.ktor.server.routing.Routing
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.request.receiveText
import java.io.File

/**
 * DidiGKD 网页报告 + flow.js 远程定制（挂到 GKD 自带 HTTP 服务上）。
 *
 * 端点：
 *   GET  /didi/report          网页报告(自包含 HTML, 浏览器直接看每单截图+决策)
 *   GET  /api/didi_records     JSON 记录列表
 *   GET  /api/didi_shot?name=x 截图文件(captures/下)
 *   GET  /api/flow_js          读 flow.js 内容
 *   POST /api/flow_js          写 flow.js(热更新, 下一单生效)
 *
 * [stated] 用户: 报告挂网页比 App 内看方便; flow.js 要能方便定制工作流
 */
fun Routing.registerDidiWebRoutes() {
    // 网页报告
    get("/didi/report") {
        call.respondText(ContentType.Text.Html) { DIDI_REPORT_HTML }
    }
    // 记录 JSON
    get("/api/didi_records") {
        val sb = StringBuilder("[")
        var first = true
        for (r in DidiReportData.load()) {
            if (!first) sb.append(",")
            first = false
            sb.append("""{"ts":"${r.ts}","price":"${r.price}","kind":"${r.kind}","verdict":"${r.verdict}","shot":"${r.shot}","addrTo":"${r.addrTo}"}""")
        }
        sb.append("]")
        call.respondText(ContentType.Application.Json) { sb.toString() }
    }
    // 截图文件
    get("/api/didi_shot") {
        val name = call.request.queryParameters["name"] ?: ""
        // 防目录穿越
        if (name.contains("/") || name.contains("..") || name.isEmpty()) {
            call.respondText("bad name") { ContentType.Text.Plain }
            return@get
        }
        val f = File(DidiReportData.capturesDir, name)
        if (f.exists()) call.respondFile(f)
        else call.respondText("not found") { ContentType.Text.Plain }
    }
    // flow.js 网页编辑器
    get("/didi/flow") {
        call.respondText(ContentType.Text.Html) { FLOW_EDITOR_HTML }
    }
    // 读 flow.js
    get("/api/flow_js") {
        val f = DidiReportData.flowJsFile
        val txt = if (f.exists()) f.readText() else "// flow.js 尚未生成(等第一单触发后自动生成)"
        call.respondText(ContentType.Text.Plain) { txt }
    }
    // 写 flow.js(热更新)
    post("/api/flow_js") {
        val body = call.receiveText()
        val f = DidiReportData.flowJsFile
        f.parentFile?.mkdirs()
        f.writeText(body)
        call.respondText(ContentType.Text.Plain) { "saved ${body.length} bytes, 下一单生效(语法错会自动回退上一版)" }
    }
}

private const val DIDI_REPORT_HTML = """<!DOCTYPE html>
<html><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
<title>DidiGKD 决策报告</title>
<style>
body{font-family:system-ui;margin:0;background:#0f1115;color:#e6e6e6}
.h{padding:14px 16px;background:#1a1d24;position:sticky;top:0;display:flex;justify-content:space-between;align-items:center}
.h b{font-size:17px}.h button{background:#2b6fff;color:#fff;border:0;padding:8px 14px;border-radius:8px;font-size:14px}
.c{padding:12px;display:flex;flex-direction:column;gap:10px}
.card{background:#1a1d24;border-radius:12px;padding:10px;display:flex;gap:12px;align-items:center}
.card img{width:64px;height:64px;object-fit:cover;border-radius:8px;background:#2a2e37;flex:none}
.meta{flex:1;min-width:0}
.ts{color:#8b93a7;font-size:12px}.kind{color:#4d8bff;font-size:12px;margin-left:8px}
.price{font-size:18px;font-weight:700;margin:2px 0}
.grab{color:#4d8bff}.no_grab{color:#c58bff}.pass{color:#8b93a7}
.to{color:#8b93a7;font-size:13px;white-space:nowrap;overflow:hidden;text-overflow:ellipsis}
.n{color:#8b93a7;text-align:center;padding:40px}
</style></head>
<body>
<div class="h"><b>DidiGKD 决策报告</b><button onclick="load()">刷新</button></div>
<div class="c" id="c"><div class="n">加载中...</div></div>
<script>
async function load(){
  const c=document.getElementById('c');
  let rs; try{rs=await (await fetch('/api/didi_records')).json();}catch(e){c.innerHTML='<div class=n>获取失败: '+e+'</div>';return;}
  if(!rs.length){c.innerHTML='<div class=n>暂无记录<br>(等弹单后自动生成)</div>';return;}
  c.innerHTML = rs.map(r=>{
    const vc = r.verdict==='grab'?'grab':(r.verdict==='no_grab'?'no_grab':'pass');
    const img = r.shot ? `<img src="/api/didi_shot?name=${encodeURIComponent(r.shot)}" onerror="this.style.visibility='hidden'">` : '';
    return `<div class="card">${img}<div class="meta">
      <div><span class="ts">${r.ts}</span><span class="kind">${r.kind}</span></div>
      <div class="price ${vc}">¥${r.price} · ${r.verdict||'-'}</div>
      <div class="to">${r.addrTo||''}</div></div></div>`;
  }).join('');
}
load();
</script>
</body></html>"""

private const val FLOW_EDITOR_HTML = """<!DOCTYPE html>
<html><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
<title>flow.js 定制</title>
<style>
body{font-family:system-ui;margin:0;background:#0f1115;color:#e6e6e6}
.h{padding:12px 16px;background:#1a1d24;display:flex;justify-content:space-between;align-items:center}
.h b{font-size:16px}
button{background:#2b6fff;color:#fff;border:0;padding:8px 14px;border-radius:8px;font-size:14px;margin-left:8px}
textarea{width:calc(100% - 24px);margin:12px;height:60vh;background:#12141a;color:#e6e6e6;border:1px solid #2a2e37;border-radius:8px;padding:12px;font:13px/1.5 ui-monospace,Menlo,monospace;box-sizing:border-box}
.tip{margin:0 12px;color:#8b93a7;font-size:13px;line-height:1.6}
#msg{color:#4d8bff}
</style></head>
<body>
<div class="h"><b>flow.js 工作流定制</b><span><span id="msg"></span><button onclick="load()">重新载入</button><button onclick="save()">保存(下一单生效)</button></span></div>
<p class="tip">原语：<b>onCard(card)</b> 收到卡片入口；<b>card</b> 有 price/distMe/kind/addrTo 等字段；<b>tap("no_grab"|"grab")</b> 点击；<b>screenshot(verdict)</b> 截图+存档；<b>log(msg)</b> 日志。<br>
保存后下一单自动热加载；语法写错会自动回退上一版，不会影响抢单。</p>
<textarea id="t" spellcheck="false"></textarea>
<script>
async function load(){const t=document.getElementById('t');t.value=await (await fetch('/api/flow_js')).text();document.getElementById('msg').textContent='';}
async function save(){const t=document.getElementById('t');const r=await fetch('/api/flow_js',{method:'POST',body:t.value});document.getElementById('msg').textContent=await r.text();}
load();
</script></body></html>"""
