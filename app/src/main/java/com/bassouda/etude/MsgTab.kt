package com.bassouda.etude

import android.app.Activity
import android.app.AlertDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import org.json.JSONArray
import org.json.JSONObject

class MsgTab(private val a: MainActivity) {
    private val prefs = a.getSharedPreferences("bh", Activity.MODE_PRIVATE)
    private val box = LinearLayout(a)
    private val handler = Handler(Looper.getMainLooper())
    private var me = prefs.getString("pseudo", "") ?: ""
    private var salons = JSONArray(prefs.getString("salons", "[]") ?: "[]")
    private var cur: JSONObject? = null
    private var busy = false
    private var lastBody = ""
    private var shown = ""
    private lateinit var scroll: ScrollView
    private var log: RichOut? = null
    private var input: EditText? = null

    private val RULES = "{\"rules\":{\"rooms\":{\".read\":false,\".write\":false,\"\$room\":{\".read\":true,\".write\":true}}}}"

    fun build(): View {
        box.orientation = LinearLayout.VERTICAL
        box.setPadding(a.dp(14), a.dp(6), a.dp(14), a.dp(20))
        scroll = ScrollView(a)
        scroll.addView(box)
        render()
        tick()
        return scroll
    }

    private fun toast(s: String) { Toast.makeText(a, s, Toast.LENGTH_LONG).show() }
    private fun key(): String = me + "|" + (cur?.optString("id") ?: "") + "|" + Db.base

    private fun addSalon(b: String, id: String, n: String): JSONObject {
        val arr = JSONArray()
        for (i in 0 until salons.length()) if (salons.getJSONObject(i).getString("id") != id) arr.put(salons.getJSONObject(i))
        val o = JSONObject().put("b", b).put("id", id).put("n", n)
        arr.put(o)
        salons = arr
        prefs.edit().putString("salons", arr.toString()).apply()
        return o
    }

    private fun tick() {
        handler.postDelayed({ tick() }, 3000)
        if (a.curTab != 3) return
        if (shown != key()) { render(); return }
        poll()
    }

    private fun poll() {
        val c = cur ?: return
        if (busy) return
        busy = true
        val who = me
        a.bg({
            Db.fetch(c.getString("b"), c.getString("id")).joinToString("") { p ->
                Md.bubble(p.first == who, if (p.first == who) "" else p.first, p.second)
            }
        }) { r ->
            busy = false
            if (r.startsWith("Erreur")) { if (lastBody.isEmpty()) log?.text = r }
            else if (r != lastBody) {
                lastBody = r
                log?.chat(if (r.isEmpty()) Md.bubble(false, "", "Aucun message pour l'instant. Écris le premier !") else r)
            }
        }
    }

    private fun render() {
        shown = key()
        box.removeAllViews()
        log = null
        input = null
        box.addView(a.label("Messagerie"))
        val c = cur
        if (me.isEmpty()) pseudoForm() else if (c == null) home() else room(c)
        if (c != null && me.isNotEmpty()) poll()
    }

    private fun pseudoForm() {
        val t = a.out()
        t.text = "Choisis ton pseudo : c'est le nom que tes amis verront dans les salons."
        box.addView(t)
        val p = a.edit("Ton pseudo (3 caractères minimum)", 1)
        box.addView(p)
        box.addView(a.btn("Valider mon pseudo") {
            val n = Db.pseudo(p.text.toString())
            if (n.length < 3) toast("Pseudo trop court : 3 lettres ou chiffres minimum.")
            else { me = n; prefs.edit().putString("pseudo", n).apply(); render() }
        })
    }

    private fun home() {
        val who = TextView(a)
        who.text = "Mon pseudo : @" + me
        who.setTextColor(Color.WHITE)
        who.setPadding(0, 0, 0, a.dp(6))
        box.addView(who)
        val r = a.row()
        r.addView(a.btn("➕ Créer un salon") { createDialog() }, a.w1())
        r.addView(a.btn("🔑 Rejoindre un salon", 0xFF1B8A5A.toInt()) { joinDialog() }, a.w1())
        box.addView(r)
        if (salons.length() > 0) {
            box.addView(a.label("Mes salons"))
            for (i in 0 until salons.length()) {
                val o = salons.getJSONObject(i)
                box.addView(a.btn("💬  " + o.getString("n"), a.CARD) { cur = o; lastBody = ""; render() })
            }
        }
        if (!Db.ok()) {
            box.addView(a.label("Pour créer un salon"))
            val t = a.out()
            t.text = "Il faut un petit serveur gratuit (Firebase) :\n" +
                "1. console.firebase.google.com : crée un projet.\n" +
                "2. Build > Realtime Database > Créer une base de données.\n" +
                "3. Onglet Règles : colle le texte ci-dessous puis Publier (seuls ceux qui ont le code du salon peuvent y entrer) :\n\n" + RULES + "\n\n" +
                "4. Copie l'adresse de la base (https://….firebaseio.com) dans Réglages.\n\n" +
                "Pour rejoindre un salon, rien à configurer : le code d'invitation suffit."
            box.addView(t)
            box.addView(a.btn("Réglages") { a.askKey() })
        }
    }

    private fun createDialog() {
        if (!Db.ok()) { toast("Colle d'abord l'adresse Firebase dans Réglages."); a.askKey(); return }
        val e = EditText(a)
        e.hint = "Nom du salon (ex. Maths 3e)"
        AlertDialog.Builder(a).setTitle("Créer un salon").setView(e)
            .setPositiveButton("Créer") { _, _ ->
                val name = e.text.toString().trim().ifEmpty { "Mon salon" }
                val id = Db.newId()
                val base = Db.base
                val owner = me
                a.bg({ Db.createRoom(base, id, name, owner); "ok" }) { r ->
                    if (r.startsWith("Erreur")) toast(r)
                    else { val o = addSalon(base, id, name); cur = o; lastBody = ""; render(); shareInvite(o) }
                }
            }
            .setNegativeButton("Annuler", null).show()
    }

    private fun joinDialog() {
        val e = EditText(a)
        e.hint = "Colle le code d'invitation (BH-…)"
        AlertDialog.Builder(a).setTitle("Rejoindre un salon").setView(e)
            .setPositiveButton("Rejoindre") { _, _ ->
                val t = Db.decode(e.text.toString())
                if (t == null) toast("Code invalide : il commence par BH-.")
                else {
                    a.bg({
                        if (!Db.roomExists(t.first, t.second)) throw Exception("salon introuvable.")
                        "ok"
                    }) { r ->
                        if (r.startsWith("Erreur")) toast(r)
                        else { val o = addSalon(t.first, t.second, t.third); cur = o; lastBody = ""; render() }
                    }
                }
            }
            .setNegativeButton("Annuler", null).show()
    }

    private fun shareInvite(o: JSONObject) {
        val code = Db.invite(o.getString("b"), o.getString("id"), o.getString("n"))
        val msg = "Rejoins mon salon « " + o.getString("n") + " » sur BH Étude : onglet Messages > Rejoindre un salon, puis colle ce code :\n" + code
        AlertDialog.Builder(a).setTitle("Code d'invitation").setMessage(code)
            .setPositiveButton("Partager") { _, _ ->
                val i = Intent(Intent.ACTION_SEND)
                i.type = "text/plain"
                i.putExtra(Intent.EXTRA_TEXT, msg)
                a.startActivity(Intent.createChooser(i, "Partager le code"))
            }
            .setNeutralButton("Copier") { _, _ ->
                val cm = a.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                cm.setPrimaryClip(ClipData.newPlainText("code", code))
                toast("Code copié")
            }
            .setNegativeButton("Fermer", null).show()
    }

    private fun room(c: JSONObject) {
        box.addView(a.btn("← Mes salons", a.CARD) { cur = null; render() })
        box.addView(a.label("💬 " + c.getString("n")))
        val lg = RichOut(a)
        lg.onResized = { scroll.post { scroll.fullScroll(View.FOCUS_DOWN) } }
        lg.text = "Chargement…"
        log = lg
        box.addView(lg.view)
        val inp = a.edit("Ton message…", 2)
        input = inp
        box.addView(inp)
        val r = a.row()
        r.addView(a.btn("🎤") { a.dictate(inp) }, a.w1())
        r.addView(a.btn("Envoyer") { send(inp.text.toString()) }, a.w1())
        r.addView(a.btn("🔗 Inviter", a.CARD) { shareInvite(c) }, a.w1())
        box.addView(r)
        box.addView(a.btn("📎 Envoyer mon résumé", 0xFF1B8A5A.toInt()) {
            val s = a.lessonOut.text
            if (s.length < 20) toast("Fais d'abord un résumé dans l'onglet Leçon.") else send(s)
        })
    }

    private fun send(text: String) {
        val t = text.trim()
        val c = cur ?: return
        if (t.isEmpty()) return
        val who = me
        a.bg({ Db.send(c.getString("b"), c.getString("id"), who, t); "ok" }) { r ->
            if (r.startsWith("Erreur")) toast(r)
            else { input?.setText(""); poll() }
        }
    }
}
