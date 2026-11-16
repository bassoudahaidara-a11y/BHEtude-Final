package com.bassouda.etude

import android.annotation.SuppressLint
import android.graphics.Color
import android.view.View
import android.view.ViewGroup
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.widget.FrameLayout

/** Convertit le texte de l'IA (Markdown + LaTeX) en page HTML : vrais tableaux, vraies fractions, bulles de discussion. */
object Md {
    private val CSS = "body{margin:0;padding:4px 6px;color:#EAF0FF;font:16px/1.55 sans-serif;background:transparent;word-wrap:break-word}" +
        "h3{color:#00D4FF;font-size:19px;margin:14px 0 6px}h4{color:#8FB8FF;font-size:17px;margin:12px 0 4px}" +
        "p{margin:6px 0}ul,ol{margin:6px 0;padding-left:22px}li{margin:3px 0}b{color:#fff}" +
        "hr{border:0;border-top:1px solid #2A3C70;margin:12px 0}" +
        "code{background:#0B1A40;padding:1px 6px;border-radius:6px}" +
        ".t{overflow-x:auto;margin:10px 0;border-radius:12px;border:1px solid #34497F}" +
        "table{border-collapse:collapse;width:100%;min-width:max-content}" +
        "th{background:#1F4FA8;color:#fff;padding:8px 12px;text-align:left;border-bottom:2px solid #00D4FF}" +
        "td{padding:8px 12px;border-top:1px solid #2A3C70}tr:nth-child(even) td{background:#0F1F4D}" +
        ".m{overflow-x:auto;margin:8px 0;padding:6px 0}" +
        ".c{display:flex;flex-direction:column}" +
        ".b{max-width:90%;padding:9px 13px;border-radius:18px;margin:6px 0}" +
        ".me{align-self:flex-end;background:#2F80ED;border-bottom-right-radius:5px}" +
        ".ot{align-self:flex-start;background:#1B2E5E;border-bottom-left-radius:5px}" +
        ".n{font-size:12px;color:#00D4FF;font-weight:bold;margin-bottom:2px}" +
        ".katex{font-size:1.1em}.katex-display{margin:8px 0}"

    private fun esc(s: String): String = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")

    private fun inline(s: String): String {
        var t = esc(s)
        t = Regex("\\*\\*(.+?)\\*\\*").replace(t) { "<b>" + it.groupValues[1] + "</b>" }
        t = Regex("`([^`]+)`").replace(t) { "<code>" + it.groupValues[1] + "</code>" }
        return t
    }

    fun html(md: String): String {
        val lines = md.replace("\r", "").split("\n")
        val sb = StringBuilder()
        var list = ""
        fun close() { if (list.isNotEmpty()) { sb.append("</").append(list).append(">"); list = "" } }
        var i = 0
        while (i < lines.size) {
            val ln = lines[i].trim()
            if (ln.isEmpty()) { close(); i++; continue }
            if (ln.startsWith("\$\$") && !(ln.length > 3 && ln.endsWith("\$\$"))) {
                close()
                val b = StringBuilder(ln)
                i++
                while (i < lines.size && !lines[i].trim().endsWith("\$\$")) { b.append("\n").append(lines[i]); i++ }
                if (i < lines.size) { b.append("\n").append(lines[i]); i++ }
                sb.append("<div class=m>").append(esc(b.toString())).append("</div>")
                continue
            }
            if (ln.startsWith("\\[") && !ln.contains("\\]")) {
                close()
                val b = StringBuilder(ln)
                i++
                while (i < lines.size && !lines[i].contains("\\]")) { b.append("\n").append(lines[i]); i++ }
                if (i < lines.size) { b.append("\n").append(lines[i]); i++ }
                sb.append("<div class=m>").append(esc(b.toString())).append("</div>")
                continue
            }
            if (ln.startsWith("|")) {
                close()
                val rows = ArrayList<List<String>>()
                while (i < lines.size && lines[i].trim().startsWith("|")) {
                    val r = lines[i].trim().trim('|').split("|").map { it.trim() }
                    if (!r.all { c -> c.isNotEmpty() && c.all { ch -> ch == '-' || ch == ':' } }) rows.add(r)
                    i++
                }
                sb.append("<div class=t><table>")
                for (k in rows.indices) {
                    sb.append("<tr>")
                    for (c in rows[k]) sb.append(if (k == 0) "<th>" else "<td>").append(inline(c)).append(if (k == 0) "</th>" else "</td>")
                    sb.append("</tr>")
                }
                sb.append("</table></div>")
                continue
            }
            val hm = Regex("^(#{1,4})\\s+(.*)").find(ln)
            if (hm != null) {
                close()
                val tag = if (hm.groupValues[1].length <= 2) "h3" else "h4"
                sb.append("<").append(tag).append(">").append(inline(hm.groupValues[2])).append("</").append(tag).append(">")
                i++; continue
            }
            if (Regex("-{3,}").matches(ln)) { close(); sb.append("<hr>"); i++; continue }
            val um = Regex("^[-*•]\\s+(.*)").find(ln)
            val om = Regex("^\\d+[.)]\\s+(.*)").find(ln)
            if (um != null || om != null) {
                val want = if (um != null) "ul" else "ol"
                if (list != want) { close(); sb.append("<").append(want).append(">"); list = want }
                sb.append("<li>").append(inline((um ?: om)!!.groupValues[1])).append("</li>")
                i++; continue
            }
            close()
            sb.append("<p>").append(inline(ln)).append("</p>")
            i++
        }
        close()
        return sb.toString()
    }

    fun bubble(mine: Boolean, who: String, md: String): String =
        "<div class='b " + (if (mine) "me" else "ot") + "'>" +
            (if (who.isEmpty()) "" else "<div class=n>" + esc(who) + "</div>") + html(md) + "</div>"

    fun page(body: String, fill: Boolean): String =
        "<!doctype html><html><head><meta charset=utf-8><meta name=viewport content='width=device-width,initial-scale=1'>" +
            "<link rel=stylesheet href='https://cdn.jsdelivr.net/npm/katex@0.16.9/dist/katex.min.css'><style>" + CSS + "</style></head><body>" + body +
            "<script src='https://cdn.jsdelivr.net/npm/katex@0.16.9/dist/katex.min.js'></script>" +
            "<script src='https://cdn.jsdelivr.net/npm/katex@0.16.9/dist/contrib/auto-render.min.js'></script>" +
            "<script>var F=" + fill + ";function u(){if(F){window.scrollTo(0,document.documentElement.scrollHeight)}else{Android.h(document.documentElement.scrollHeight)}}" +
            "function r(){try{renderMathInElement(document.body,{delimiters:[{left:'\$\$',right:'\$\$',display:true},{left:'\\\\[',right:'\\\\]',display:true},{left:'\$',right:'\$',display:false},{left:'\\\\(',right:'\\\\)',display:false}],throwOnError:false})}catch(e){}u()}" +
            "window.addEventListener('load',r);try{new ResizeObserver(u).observe(document.body)}catch(e){}u();</script></body></html>"

    /** Texte à lire à voix haute : sans symboles Markdown ni LaTeX. */
    fun speakable(s: String): String {
        var t = Regex("\\\\frac\\{([^{}]*)\\}\\{([^{}]*)\\}").replace(s) { it.groupValues[1] + " sur " + it.groupValues[2] }
        t = Regex("\\\\sqrt\\{([^{}]*)\\}").replace(t) { "racine de " + it.groupValues[1] }
        t = t.replace(Regex("\\^\\{?2\\}?"), " au carré").replace(Regex("\\^\\{?3\\}?"), " au cube")
        t = Regex("\\^\\{?([^{}\\s]*)\\}?").replace(t) { " puissance " + it.groupValues[1] }
        t = t.replace("\\times", " fois ").replace("\\cdot", " fois ").replace("\\div", " divisé par ")
        t = t.replace(Regex("[\$\\\\{}*#|_`]"), " ").replace(Regex("-{3,}"), " ")
        return t.replace(Regex("\\s+"), " ").trim()
    }
}

@SuppressLint("SetJavaScriptEnabled")
class RichOut(private val a: MainActivity, private val fill: Boolean = false) {
    val view = FrameLayout(a)
    private val web = WebView(a)
    var onResized: (() -> Unit)? = null

    inner class Bridge {
        @JavascriptInterface
        fun h(px: Int) {
            if (fill) return
            web.post {
                val lp = web.layoutParams
                lp.height = (px * a.resources.displayMetrics.density).toInt() + 2
                web.layoutParams = lp
                onResized?.invoke()
            }
        }
    }

    init {
        web.setBackgroundColor(Color.TRANSPARENT)
        web.settings.javaScriptEnabled = true
        web.isVerticalScrollBarEnabled = false
        web.overScrollMode = View.OVER_SCROLL_NEVER
        web.addJavascriptInterface(Bridge(), "Android")
        view.background = a.shape(if (fill) Color.TRANSPARENT else a.CARD)
        view.setPadding(a.dp(6), a.dp(6), a.dp(6), a.dp(6))
        view.addView(web, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, if (fill) ViewGroup.LayoutParams.MATCH_PARENT else a.dp(60)))
    }

    var text: String = ""
        set(v) { field = v; load(Md.html(v)) }

    fun chat(bodyHtml: String) { load("<div class=c>" + bodyHtml + "</div>") }

    private fun load(body: String) {
        web.loadDataWithBaseURL("https://bh.local/", Md.page(body, fill), "text/html", "utf-8", null)
    }
}
