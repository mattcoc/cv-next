package io.github.mattcoc.cvnext

import java.time.LocalDate

/** Tutti gli indirizzi e gli identificativi dei servizi esterni, in un unico punto. */
object Endpoints {
    const val WEB = "https://web.spaggiari.eu"
    const val REST = "$WEB/rest/v1"
    const val WEB_LOGIN = "$WEB/auth-p7/app/default/AuthApi4.php?a=aLoginPwd"
    fun payments(studentId: String) = "$WEB/rest/w1/parents/$studentId/pagoonline/payments/?"

    /** Intestazioni richieste dalle API dell'app ufficiale. */
    const val USER_AGENT = "CVVS/std/4.1.7 Android/10"
    const val API_KEY = "Tg1NWEwNGIgIC0K"

    const val RELEASES = "https://api.github.com/repos/mattcoc/cv-next/releases/latest"

    /** L'anno scolastico inizia a settembre: a ottobre 2026 l'anno precedente è il 2025/26, ospitato su web25. */
    fun lastYearHost(today: LocalDate = LocalDate.now()): String {
        val start = if (today.monthValue >= 9) today.year else today.year - 1
        return "https://web%02d.spaggiari.eu".format((start - 1) % 100)
    }
}
