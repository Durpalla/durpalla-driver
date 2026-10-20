package com.durpalla.driver.domain

import com.durpalla.driver.i18n.AppLocale
import com.durpalla.driver.i18n.DigitFormatter
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/*
 * DISPLAY formatting only. Everything here follows the selected app language (Bangla digits in Bangla mode).
 * Never use these for API/Pusher/stored values: those are plain Ints / ISO strings built with Locale.ROOT.
 */

/** ৳ amount. English: ৳12,345. Bangla: ৳১২,৩৪৫ (lakh grouping above 99,999: ৳১,২৩,৪৫৬). */
fun taka(amount: Int, bangla: Boolean = AppLocale.isBangla): String =
    (if (amount < 0) "-৳" else "৳") + DigitFormatter.toLocalDigits(group(kotlin.math.abs(amount).toLong(), bangla), bangla)

/** Locale-aware plain number (counts, ratings, km). */
fun num(n: Number, bangla: Boolean = AppLocale.isBangla): String = when (n) {
    is Double, is Float -> DigitFormatter.toLocalDigits(String.format(Locale.ROOT, "%.1f", n.toDouble()), bangla)
    else -> DigitFormatter.toLocalDigits(group(n.toLong(), bangla), bangla)
}

internal fun group(v: Long, lakh: Boolean): String {
    val s = v.toString()
    if (s.length <= 3) return s
    val last3 = s.takeLast(3); var rest = s.dropLast(3)
    val parts = ArrayList<String>()
    val step = if (lakh) 2 else 3
    while (rest.length > step) { parts.add(0, rest.takeLast(step)); rest = rest.dropLast(step) }
    parts.add(0, rest)
    return parts.joinToString(",") + "," + last3
}

private val enDay = DateTimeFormatter.ofPattern("EEE dd MMM", Locale.US)
private val enTime = DateTimeFormatter.ofPattern("h:mm a", Locale.US)
private val enDate = DateTimeFormatter.ofPattern("dd MMM yyyy", Locale.US)
private val bnDays = listOf("সোম", "মঙ্গল", "বুধ", "বৃহস্পতি", "শুক্র", "শনি", "রবি") // Monday-first, ISO order
private val bnMonths = listOf("জানুয়ারি", "ফেব্রুয়ারি", "মার্চ", "এপ্রিল", "মে", "জুন", "জুলাই", "আগস্ট", "সেপ্টেম্বর", "অক্টোবর", "নভেম্বর", "ডিসেম্বর")

/** Natural Bangla time: "সকাল ৮:০০", "বিকাল ৩:৩০", "রাত ৯:১৫". */
fun bnTime(t: LocalDateTime): String {
    val h = t.hour
    val period = when (h) { in 4..5 -> "ভোর"; in 6..11 -> "সকাল"; in 12..14 -> "দুপুর"; in 15..17 -> "বিকাল"; in 18..19 -> "সন্ধ্যা"; else -> "রাত" }
    val h12 = if (h % 12 == 0) 12 else h % 12
    return DigitFormatter.toLocalDigits("$period $h12:${t.minute.toString().padStart(2, '0')}", true)
}

fun timeText(t: LocalDateTime, bangla: Boolean = AppLocale.isBangla) = if (bangla) bnTime(t) else t.format(enTime)
fun dayText(d: LocalDate, bangla: Boolean = AppLocale.isBangla): String =
    if (bangla) DigitFormatter.toLocalDigits("${bnDays[d.dayOfWeek.value - 1]}, ${d.dayOfMonth} ${bnMonths[d.monthValue - 1]}", true) else d.format(enDay)
fun dateText(d: LocalDate, bangla: Boolean = AppLocale.isBangla): String =
    if (bangla) DigitFormatter.toLocalDigits("${d.dayOfMonth} ${bnMonths[d.monthValue - 1]} ${d.year}", true) else d.format(enDate)
fun monthText(month: Int, year: Int, bangla: Boolean = AppLocale.isBangla): String =
    if (bangla) DigitFormatter.toLocalDigits("${bnMonths[month - 1]} $year", true)
    else java.time.Month.of(month).getDisplayName(java.time.format.TextStyle.FULL, Locale.US) + " $year"

fun TimeSlot.label(bangla: Boolean = AppLocale.isBangla): String = "${dayText(start.toLocalDate(), bangla)} · ${timeLabel(bangla)}"
fun TimeSlot.timeLabel(bangla: Boolean = AppLocale.isBangla): String {
    if (!bangla) return "${timeText(start, false)}–${timeText(end, false)}"
    val a = bnTime(start); val b = bnTime(end)
    // Same part of day: "সকাল ৮:০০–১০:০০" instead of repeating "সকাল".
    return if (a.substringBefore(' ') == b.substringBefore(' ')) "$a–${b.substringAfter(' ')}" else "$a–$b"
}
