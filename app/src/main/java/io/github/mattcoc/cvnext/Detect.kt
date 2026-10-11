package io.github.mattcoc.cvnext

import android.content.Context
import java.text.Normalizer

/** Minuscole e senza accenti, per confrontare testi scritti in modi diversi. */
fun String.plain(): String = Normalizer.normalize(this, Normalizer.Form.NFD).replace(Regex("\\p{M}+"), "").lowercase()

/**
 * Riconosce verifiche e interrogazioni nel testo dell'agenda, senza IA.
 * Una parola come "verifica" o "compito" conta solo se annuncia la prova, non se la nomina
 * soltanto ("esercizi in preparazione al compito", "ripassare per la verifica", "correzione della verifica").
 */
object TestDetector {
    private val keyword = Regex("\\b(verifica|verifiche|interrogazione|interrogazioni|interrogati|interrogata|interrogato|compito|test|prova|esame|simulazione)\\b")

    /** Parole che, prima della verifica nella stessa frase, indicano che se ne parla soltanto. */
    private val beforeCue = Regex(
        "prepar|in vista|ripass|studia|studio|studiare|eserci|correg|correz|consegn|svolg|risultat|ritir|riguard|argoment|esito|voti d|valutazion"
    )

    /** La verifica preceduta da una preposizione articolata ("per la", "della", "alla"...) è nominata, non annunciata. */
    private val beforePreposition = Regex(
        "((\\bper|\\bdella|\\bdel|\\bdello|\\balla|\\bal|\\ballo|\\bsulla|\\bsul|\\bnella|\\bnel|\\bdalla|\\bdal|\\bdopo|\\bprima|\\bcon)\\s+(la\\s+|il\\s+|lo\\s+|l')?|\\b(dell|all|nell|sull|dall)')\\s*$"
    )

    /** "compito per casa" o "compiti a casa" sono compiti, non verifiche. */
    private val homeworkAfter = Regex("^\\s*(per|a)\\s+casa")

    /** "prova" è anche un verbo: conta solo come "prova scritta/orale/pratica/di ..." e simili. */
    private val provaAfter = Regex("^\\s*(scritta|orale|pratica|di|d'|comune|intermedia|finale|strutturata|invalsi|parallela)")

    fun isTest(text: String): Boolean {
        val t = text.plain()
        return keyword.findAll(t).any { m ->
            val sentenceStart = t.lastIndexOfAny(charArrayOf('.', ';', '!', '?', '\n'), m.range.first - 1).let { if (it < 0) 0 else it + 1 }
            val before = t.substring(maxOf(sentenceStart, m.range.first - 45), m.range.first)
            val after = t.substring(m.range.last + 1).take(20)
            when {
                beforeCue.containsMatchIn(before) -> false
                beforePreposition.containsMatchIn(before) -> false
                m.value == "compito" && homeworkAfter.containsMatchIn(after) -> false
                m.value == "prova" && !provaAfter.containsMatchIn(after) -> false
                else -> true
            }
        }
    }
}

/** Un evento dell'agenda che sembra una verifica o un'interrogazione. */
val Event.isTest get() = TestDetector.isTest("$notes ${if (homework) "" else subject}")

/** Avvisa quando il proprio nome compare in agenda (es. "Interrogazione: Rossi, Bianchi"). */
object Mentions {
    private fun prefs(context: Context) = context.getSharedPreferences("mentions", Context.MODE_PRIVATE)

    /** Di base il cognome: tutto tranne il primo nome ("Matteo De Rossi" → "De Rossi"). */
    fun defaultNames(accountName: String): List<String> {
        val words = accountName.trim().split(Regex("\\s+")).filter { it.isNotBlank() }
        return listOf(if (words.size > 1) words.drop(1).joinToString(" ") else accountName.trim()).filter { it.isNotBlank() }
    }

    fun names(context: Context, account: Account): List<String> =
        prefs(context).getString("names|${account.key}", null)?.split(',')?.map { it.trim() }?.filter { it.isNotBlank() }
            ?: defaultNames(account.name)

    fun save(context: Context, account: Account, names: String) {
        prefs(context).edit().putString("names|${account.key}", names).apply()
    }

    fun matches(text: String, names: List<String>): Boolean {
        val t = text.plain()
        return names.map { it.plain().trim() }.filter { it.length >= 3 }.any { n ->
            Regex("(?<![\\p{L}])" + Regex.escape(n) + "(?![\\p{L}])").containsMatchIn(t)
        }
    }
}
