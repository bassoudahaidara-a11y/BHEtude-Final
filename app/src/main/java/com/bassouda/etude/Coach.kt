package com.bassouda.etude

import android.animation.ObjectAnimator
import android.animation.PropertyValuesHolder
import android.animation.ValueAnimator
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.TextView
import org.json.JSONArray
import org.json.JSONObject

/** Le « petit coin » : un coach moderne qui parle, disponible dans toutes les parties. */
class Coach(private val a: MainActivity) {
    private val hist = ArrayList<Pair<String, String>>()
    private val bubbles = StringBuilder()
    private var voice = true
    private var panelH = 0
    private lateinit var fab: Button
    private lateinit var panel: LinearLayout
    private lateinit var log: RichOut
    private lateinit var input: EditText
    private lateinit var status: TextView

    private fun oval(c: Int, c2: Int = c): GradientDrawable {
        val g = GradientDrawable(GradientDrawable.Orientation.TL_BR, intArrayOf(c, c2))
        g.shape = GradientDrawable.OVAL
        return g
    }
    private fun round(c: Int, r: Int): GradientDrawable {
        val g = GradientDrawable()
        g.setColor(c)
        g.cornerRadius = a.dp(r).toFloat()
        return g
    }
    private fun icon(t: String, bg: GradientDrawable, onClick: () -> Unit): Button {
        val b = Button(a)
        b.text = t
        b.textSize = 18f
        b.setTextColor(Color.WHITE)
        b.setPadding(0, 0, 0, 0)
        b.minWidth = 0
        b.minHeight = 0
        b.background = bg
        b.setOnClickListener { onClick() }
        return b
    }
    private fun sq(size: Int): LinearLayout.LayoutParams {
        val p = LinearLayout.LayoutParams(a.dp(size), a.dp(size))
        p.setMargins(a.dp(3), 0, a.dp(3), 0)
        return p
    }

    fun attach(frame: FrameLayout) {
        panelH = (a.resources.displayMetrics.heightPixels * 0.62f).toInt()

        fab = icon("🤖", oval(a.CYAN, a.ACC)) { open() }
        fab.textSize = 26f
        fab.elevation = a.dp(8).toFloat()
        val lp = FrameLayout.LayoutParams(a.dp(62), a.dp(62), Gravity.BOTTOM or Gravity.END)
        lp.setMargins(0, 0, a.dp(14), a.dp(14))
        frame.addView(fab, lp)
        val pulse = ObjectAnimator.ofPropertyValuesHolder(fab, PropertyValuesHolder.ofFloat("scaleX", 1f, 1.1f), PropertyValuesHolder.ofFloat("scaleY", 1f, 1.1f))
        pulse.duration = 900
        pulse.repeatCount = ValueAnimator.INFINITE
        pulse.repeatMode = ValueAnimator.REVERSE
        pulse.start()

        panel = LinearLayout(a)
        panel.orientation = LinearLayout.VERTICAL
        val pg = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(0xFF16275A.toInt(), 0xFF0A1330.toInt()))
        val rr = a.dp(28).toFloat()
        pg.cornerRadii = floatArrayOf(rr, rr, rr, rr, 0f, 0f, 0f, 0f)
        panel.background = pg
        panel.elevation = a.dp(16).toFloat()
        panel.setPadding(a.dp(12), a.dp(8), a.dp(12), a.dp(10))
        panel.visibility = View.GONE
        frame.addView(panel, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, panelH, Gravity.BOTTOM))

        val handle = View(a)
        handle.background = round(0x55FFFFFF, 3)
        val hp = LinearLayout.LayoutParams(a.dp(44), a.dp(5))
        hp.gravity = Gravity.CENTER_HORIZONTAL
        hp.bottomMargin = a.dp(8)
        panel.addView(handle, hp)

        val head = LinearLayout(a)
        head.gravity = Gravity.CENTER_VERTICAL
        val av = TextView(a)
        av.text = "🤖"
        av.textSize = 22f
        av.gravity = Gravity.CENTER
        av.background = oval(a.CYAN, a.ACC)
        head.addView(av, LinearLayout.LayoutParams(a.dp(44), a.dp(44)))
        val col = LinearLayout(a)
        col.orientation = LinearLayout.VERTICAL
        col.setPadding(a.dp(10), 0, 0, 0)
        val title = TextView(a)
        title.text = "BH Coach"
        title.textSize = 17f
        title.setTextColor(Color.WHITE)
        title.typeface = Typeface.DEFAULT_BOLD
        status = TextView(a)
        status.text = "● En ligne, je t'écoute"
        status.textSize = 12f
        status.setTextColor(a.CYAN)
        col.addView(title)
        col.addView(status)
        head.addView(col, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        val vb = icon("🔊", oval(0x22FFFFFF)) { }
        vb.setOnClickListener { voice = !voice; vb.text = if (voice) "🔊" else "🔇"; if (!voice) a.stopSpeak() }
        head.addView(vb, sq(40))
        head.addView(icon("✖", oval(0x22FFFFFF)) { close() }, sq(40))
        panel.addView(head)

        log = RichOut(a, true)
        val ll = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f)
        ll.setMargins(0, a.dp(6), 0, 0)
        panel.addView(log.view, ll)

        val hs = HorizontalScrollView(a)
        hs.isHorizontalScrollBarEnabled = false
        val chips = LinearLayout(a)
        val items = listOf(
            "💡 Explique simplement" to "Explique-moi simplement ma leçon, comme à un débutant.",
            "📌 Donne un exemple" to "Donne-moi un exemple concret pour bien comprendre.",
            "📝 Résume ma leçon" to "Résume ma leçon en points clairs.",
            "🎯 Interroge-moi" to "Pose-moi une question sur ma leçon.",
            "📚 Comment réviser ?" to "Comment réviser efficacement cette leçon ?"
        )
        for (p in items) {
            val b = a.btn(p.first, 0xFF1F3A7A.toInt()) { send(p.second) }
            b.textSize = 12f
            val cp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            cp.setMargins(a.dp(3), a.dp(6), a.dp(3), a.dp(6))
            chips.addView(b, cp)
        }
        hs.addView(chips)
        panel.addView(hs)

        val bar = LinearLayout(a)
        bar.gravity = Gravity.CENTER_VERTICAL
        input = EditText(a)
        input.hint = "Pose ta question…"
        input.setHintTextColor(0xFF8899BB.toInt())
        input.setTextColor(Color.WHITE)
        input.maxLines = 3
        input.background = round(0xFF1B2E5E.toInt(), 26)
        input.setPadding(a.dp(16), a.dp(10), a.dp(16), a.dp(10))
        bar.addView(input, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        bar.addView(icon("🎤", oval(a.CYAN, a.ACC)) { a.dictate(input) }, sq(48))
        bar.addView(icon("➤", oval(a.ACC, 0xFF1B5FD0.toInt())) { send(input.text.toString()) }, sq(48))
        panel.addView(bar)

        a.onDictated = { t -> if (t === input) send(input.text.toString()) }
    }

    private fun open() {
        panel.visibility = View.VISIBLE
        fab.visibility = View.GONE
        panel.translationY = panelH.toFloat()
        panel.animate().translationY(0f).setDuration(260).start()
        if (bubbles.isEmpty()) push(false, "Salut Bassouda ! Je suis ton coach. Pose-moi une question ou touche le micro et parle.", true)
    }

    private fun close() {
        a.stopSpeak()
        panel.animate().translationY(panelH.toFloat()).setDuration(220).withEndAction {
            panel.visibility = View.GONE
            fab.visibility = View.VISIBLE
        }.start()
    }

    private fun push(mine: Boolean, md: String, say: Boolean = false) {
        bubbles.append(Md.bubble(mine, "", md))
        log.chat(bubbles.toString())
        if (say && voice) a.speak(md)
    }

    private fun sys(): String {
        val names = listOf("Leçon", "Compris (ma compréhension)", "Évaluer", "Messages")
        return a.SYS + " Tu es le coach, dans un petit coin de l'application. Réponds en 2 à 5 phrases courtes, comme à l'oral, puis propose une action utile. " +
            "L'application a 4 onglets : Leçon (importer PDF, image ou vidéo, résumé, explications avec exemples), Compris, Évaluer, Messages (salons). " +
            "Onglet actuel : " + names[a.curTab] + ". Leçon de l'élève (peut être vide) :\n" + a.lessonBox.text.toString().take(4000)
    }

    private fun send(t0: String) {
        val t = t0.trim()
        if (t.isEmpty() || !a.hasKey()) return
        input.setText("")
        hist.add(Pair("user", t))
        push(true, t)
        log.chat(bubbles.toString() + Md.bubble(false, "", "…"))
        status.text = "● Je réfléchis…"
        val msgs = JSONArray().put(JSONObject().put("role", "system").put("content", sys()))
        for (p in hist.takeLast(10)) msgs.put(JSONObject().put("role", p.first).put("content", p.second))
        a.bg({ Groq.chat(msgs) }) { r ->
            status.text = "● En ligne, je t'écoute"
            if (r.startsWith("Erreur")) push(false, r)
            else { hist.add(Pair("assistant", r)); push(false, r, true) }
        }
    }
}
