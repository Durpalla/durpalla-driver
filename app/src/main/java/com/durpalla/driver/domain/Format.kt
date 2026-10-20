package com.durpalla.driver.domain

import java.text.NumberFormat
import java.time.format.DateTimeFormatter
import java.util.Locale

private val nf: NumberFormat = NumberFormat.getIntegerInstance(Locale.US)
fun taka(amount: Int): String = (if (amount < 0) "-৳" else "৳") + nf.format(kotlin.math.abs(amount))

private val dayFmt = DateTimeFormatter.ofPattern("EEE dd MMM", Locale.US)
private val timeFmt = DateTimeFormatter.ofPattern("h:mm a", Locale.US)
fun TimeSlot.label(): String = "${start.format(dayFmt)} · ${start.format(timeFmt)}–${end.format(timeFmt)}"
fun TimeSlot.timeLabel(): String = "${start.format(timeFmt)}–${end.format(timeFmt)}"
