package io.github.peeresla.ods.core

// Отпечаток вопроса — текст и правильный ответ. Поменяли их — это уже другой вопрос,
// и прежняя статистика к нему не относится. Переехал на другой номер — статистика переезжает с ним.

/** FNV-1a, 32 бита, по UTF-16 code units (как charCodeAt в JS), результат — беззнаковое число в base36. */
fun fingerprint(text: String): String {
    var h = 0x811c9dc5.toInt()
    for (ch in text) {
        h = h xor ch.code
        h *= 0x01000193
    }
    return (h.toLong() and 0xffffffffL).toString(36)
}

fun questionFp(q: Question): String = fingerprint(q.question + "\n" + q.options[q.correct])

fun scenarioFp(sc: Scenario): String = fingerprint(sc.situation)

/** Отпечатки банка 1.0–1.1: прогресс, записанный до появления отпечатков, сверяется с ними. Не удалять. */
val LEGACY_QUESTION_FP: Map<String, String> = mapOf(
        "1" to "1yw355g", "2" to "hcwugo", "3" to "1mckxb", "4" to "18neotb", "5" to "66gcql", "6" to "sz9vj8",
        "7" to "bj5ko3", "8" to "9jjzb9", "9" to "cbpkkc", "10" to "r0cefl", "11" to "1wwwx4l", "12" to "119bzas",
        "13" to "1f4xsre", "14" to "18fi8cm", "15" to "1ar3s9e", "16" to "g0ysiq", "17" to "mw1d4u", "18" to "fm9uph",
        "19" to "1gkb05s", "20" to "10xnnru", "21" to "1gwxefc", "22" to "1k4zgmq", "23" to "eeb8az", "24" to "d4ayfr",
        "25" to "pjca2z", "26" to "14ojjk6", "27" to "17fktja", "28" to "1yr0qdr", "29" to "18wmf3x", "30" to "1h6sr88",
        "31" to "70wrcm", "32" to "15r9yw4", "33" to "1b4h70y", "34" to "1jguynh", "35" to "17tgnhq", "36" to "1bs893n",
        "37" to "6swpjj", "38" to "ra0lsr", "39" to "1vvu6m6", "40" to "8vsli9", "41" to "1vrfdw3", "42" to "1ug0qex",
        "43" to "fd0hx3", "44" to "si6e5p", "45" to "1894lgr", "46" to "14b7sj5", "47" to "1swac8u", "48" to "1rrk4k5",
        "49" to "95umg3", "50" to "1dtw42t", "51" to "1hs2lyn", "52" to "wblmyz", "53" to "1hnko4n", "54" to "qtb7ta",
        "55" to "1bpsotx", "56" to "1s52hww", "57" to "c9r8jk", "58" to "2d896k", "59" to "1nwhcj9", "60" to "1hqecus",
        "61" to "qahh4j", "62" to "wwh9a", "63" to "1qi8vey", "64" to "1aq90ag", "65" to "1s8m2u3", "66" to "1nm4drl",
        "67" to "wnory3", "68" to "vsobt3", "69" to "5006c5", "70" to "1ekte3i", "71" to "1uk7c34", "72" to "mkjrq0",
        "73" to "1c8716r", "74" to "10ei4d0", "75" to "jc2crd", "76" to "14gimtk", "77" to "4jmlvi", "78" to "7gyr9d",
        "79" to "xt70wd", "80" to "uylul3", "81" to "1yu3r71", "82" to "17gf45r", "83" to "143a993", "84" to "1e4bzpb",
        "85" to "mmkvsz", "86" to "jxfhci", "87" to "nuwyfh", "88" to "puxnjy", "89" to "8v9rjw", "90" to "1dara4x",
        "91" to "107bydm", "92" to "12gp1z", "93" to "1hmigif", "94" to "10684nw", "95" to "7p5u4", "96" to "wjmko",
        "97" to "1m23jp1", "98" to "wglis4", "99" to "15jp1f5", "100" to "1fjexqo", "101" to "1ladon7", "102" to "t10kva",
        "103" to "191cddd",
)

val LEGACY_SCENARIO_FP: Map<String, String> = mapOf(
        "s1" to "18qkcde", "s2" to "k9g7yf", "s3" to "10hnwan", "s4" to "1ve6gei", "s5" to "1wr6q1s", "s6" to "hs9y0w",
        "s7" to "1uxeqqz", "s8" to "cr7hq4", "s9" to "dikipi", "s10" to "1ivh5v5", "s11" to "l2jhq1", "s12" to "140hrps",
        "s13" to "11h2l3n", "s14" to "3349m7",
)
