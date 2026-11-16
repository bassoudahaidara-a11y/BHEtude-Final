package com.bassouda.etude

import android.util.Base64
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.security.SecureRandom

/** Salons de discussion : Firebase Realtime Database via adresse web (REST). Chaque salon a un code d'invitation secret. */
object Db {
    var base = ""
    fun ok(): Boolean = base.isNotEmpty()
    fun clean(u: String): String {
        var s = u.trim().trimEnd('/')
        if (s.isNotEmpty() && !s.startsWith("http")) s = "https://" + s
        return s
    }
    fun pseudo(s: String): String = s.lowercase().replace(Regex("[^a-z0-9_-]"), "")

    private fun req(base: String, method: String, path: String, body: String?): String {
        val c = URL(base + path).openConnection() as HttpURLConnection
        c.requestMethod = method
        c.connectTimeout = 15000
        c.readTimeout = 20000
        if (body != null) {
            c.doOutput = true
            c.setRequestProperty("Content-Type", "application/json")
            c.outputStream.use { it.write(body.toByteArray()) }
        }
        val code = c.responseCode
        val s = (if (code in 200..299) c.inputStream else c.errorStream)?.bufferedReader()?.use { it.readText() } ?: ""
        if (code == 401 || code == 403) throw Exception("accès refusé : vérifie les règles de la base Firebase.")
        if (code !in 200..299) throw Exception("serveur injoignable (" + code + "), vérifie l'adresse Firebase.")
        return s
    }

    fun newId(): String {
        val chars = "abcdefghijklmnopqrstuvwxyz0123456789"
        val r = SecureRandom()
        val sb = StringBuilder()
        for (i in 0 until 14) sb.append(chars[r.nextInt(chars.length)])
        return sb.toString()
    }

    fun invite(base: String, id: String, name: String): String =
        "BH-" + Base64.encodeToString((base + "|" + id + "|" + name).toByteArray(), Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)

    fun decode(code: String): Triple<String, String, String>? {
        val m = Regex("BH-([A-Za-z0-9_-]+)").find(code.trim()) ?: return null
        return try {
            val p = String(Base64.decode(m.groupValues[1], Base64.URL_SAFE)).split("|")
            if (p.size < 3 || !p[0].startsWith("http") || p[1].isEmpty()) null else Triple(p[0], p[1], p.drop(2).joinToString("|"))
        } catch (e: Exception) { null }
    }

    fun createRoom(base: String, id: String, name: String, owner: String) {
        val b = JSONObject().put("name", name).put("owner", owner).put("t", System.currentTimeMillis()).toString()
        req(base, "PUT", "/rooms/" + id + "/meta.json", b)
    }

    fun roomExists(base: String, id: String): Boolean = req(base, "GET", "/rooms/" + id + "/meta.json", null).trim() != "null"

    fun send(base: String, id: String, from: String, text: String) {
        val b = JSONObject().put("from", from).put("text", text).put("t", System.currentTimeMillis()).toString()
        req(base, "POST", "/rooms/" + id + "/msgs.json", b)
    }

    fun fetch(base: String, id: String): List<Pair<String, String>> {
        val s = req(base, "GET", "/rooms/" + id + "/msgs.json?orderBy=%22%24key%22&limitToLast=60", null).trim()
        if (s == "null" || s.isEmpty()) return emptyList()
        val o = JSONObject(s)
        val keys = ArrayList<String>()
        val ks = o.keys()
        while (ks.hasNext()) keys.add(ks.next())
        keys.sort()
        return keys.map { k -> Pair(o.getJSONObject(k).optString("from"), o.getJSONObject(k).optString("text")) }
    }
}
