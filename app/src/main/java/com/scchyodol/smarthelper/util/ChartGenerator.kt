package com.scchyodol.smarthelper.util

import android.graphics.*
import android.util.Log
import com.scchyodol.smarthelper.data.model.CareRecord
import java.util.*

class ChartGenerator {

    companion object {
        private const val TAG = "ChartGenerator"
        private val CHART_COLORS = listOf(
            Color.parseColor("#FF6B6B"),
            Color.parseColor("#4ECDC4"),
            Color.parseColor("#45B7D1"),
            Color.parseColor("#96CEB4"),
            Color.parseColor("#FFEAA7"),
            Color.parseColor("#DDA0DD"),
            Color.parseColor("#98D8C8"),
            Color.parseColor("#F7DC6F")
        )
    }

    // ── 카테고리별 파이차트 ─────────────────────────────────────
    fun generateCategoryPieChart(records: List<CareRecord>, width: Int = 600, height: Int = 350): Bitmap {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.WHITE)

        val categoryStats = records.groupBy { it.category.displayName }.mapValues { it.value.size }

        if (categoryStats.isEmpty()) {
            drawNoDataMessage(canvas, width, height, "데이터가 없습니다")
            return bitmap
        }

        val total = categoryStats.values.sum().toFloat()
        val centerX = width * 0.35f
        val centerY = height / 2f
        val radius = minOf(width, height) * 0.25f
        val pieRect = RectF(centerX - radius, centerY - radius, centerX + radius, centerY + radius)

        var startAngle = -90f
        val piePaint = Paint().apply { isAntiAlias = true }

        categoryStats.entries.forEachIndexed { index, (_, count) ->
            val sweepAngle = (count / total) * 360f
            piePaint.color = CHART_COLORS[index % CHART_COLORS.size]
            canvas.drawArc(pieRect, startAngle, sweepAngle, true, piePaint)
            startAngle += sweepAngle
        }

        // 범례
        val legendX = width * 0.65f
        var legendY = height * 0.2f
        val legendPaint = Paint().apply { isAntiAlias = true }
        val legendTextPaint = Paint().apply {
            color = Color.BLACK
            textSize = 20f
            isAntiAlias = true
        }
        categoryStats.entries.forEachIndexed { index, (category, count) ->
            legendPaint.color = CHART_COLORS[index % CHART_COLORS.size]
            canvas.drawRect(legendX, legendY - 12f, legendX + 20f, legendY + 8f, legendPaint)
            canvas.drawText("$category ($count)", legendX + 28f, legendY + 2f, legendTextPaint)
            legendY += 32f
        }

        return bitmap
    }

    // ── 요일별 막대차트 ──────────────────────────────────────────
    fun generateWeeklyBarChart(records: List<CareRecord>, width: Int = 600, height: Int = 320): Bitmap {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.WHITE)

        if (records.isEmpty()) {
            drawNoDataMessage(canvas, width, height, "데이터가 없습니다")
            return bitmap
        }

        // 요일별 집계 (월=0, 화=1, ..., 일=6)
        val dayStats = IntArray(7)
        val calendar = Calendar.getInstance()

        records.forEach { record ->
            calendar.timeInMillis = record.timestamp
            val dayOfWeek = calendar.get(Calendar.DAY_OF_WEEK)
            // Calendar: 일요일=1, 월요일=2, ... 토요일=7
            // 변환: 월요일=0, 화요일=1, ..., 일요일=6
            val adjustedDay = when (dayOfWeek) {
                Calendar.SUNDAY -> 6
                else -> dayOfWeek - 2
            }
            dayStats[adjustedDay]++
        }

        val maxCount = dayStats.maxOrNull()?.coerceAtLeast(1) ?: 1
        val dayNames = arrayOf("월", "화", "수", "목", "금", "토", "일")

        val chartLeft = 50f
        val chartTop = 30f
        val chartRight = width - 30f
        val chartBottom = height - 50f
        val chartWidth = chartRight - chartLeft
        val chartHeight = chartBottom - chartTop
        val barSlot = chartWidth / 7f
        val barWidth = barSlot * 0.65f

        // 격자선 (Y축)
        val gridPaint = Paint().apply {
            color = Color.parseColor("#E8E8E8")
            strokeWidth = 1f
        }
        for (i in 1..4) {
            val y = chartBottom - (i / 4f) * chartHeight
            canvas.drawLine(chartLeft, y, chartRight, y, gridPaint)
        }

        val barPaint = Paint().apply {
            color = Color.parseColor("#4ECDC4")
            isAntiAlias = true
        }
        val textPaint = Paint().apply {
            color = Color.BLACK
            textSize = 18f
            textAlign = Paint.Align.CENTER
            isAntiAlias = true
        }

        dayStats.forEachIndexed { index, count ->
            val slotCenter = chartLeft + index * barSlot + barSlot / 2
            val left = slotCenter - barWidth / 2
            val right = slotCenter + barWidth / 2
            val barHeight = if (maxCount > 0) (count.toFloat() / maxCount) * chartHeight else 0f
            val top = chartBottom - barHeight

            // 막대 그리기
            if (count > 0) {
                canvas.drawRoundRect(left, top, right, chartBottom, 8f, 8f, barPaint)

                // 값 표시
                canvas.drawText(count.toString(), slotCenter, top - 8f, textPaint)
            }

            // 요일 라벨
            canvas.drawText(dayNames[index], slotCenter, chartBottom + 25f, textPaint)
        }

        return bitmap
    }

    // ── 시간대별 히트맵 ──────────────────────────────────────────
    fun generateHourlyHeatmap(records: List<CareRecord>, width: Int = 600, height: Int = 180): Bitmap {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.WHITE)

        if (records.isEmpty()) {
            drawNoDataMessage(canvas, width, height, "데이터가 없습니다")
            return bitmap
        }

        val hourStats = IntArray(24)
        val calendar = Calendar.getInstance()
        records.forEach { record ->
            calendar.timeInMillis = record.timestamp
            hourStats[calendar.get(Calendar.HOUR_OF_DAY)]++
        }

        val maxCount = hourStats.maxOrNull()?.coerceAtLeast(1) ?: 1
        val mapLeft = 35f
        val mapTop = 20f
        val cellWidth = (width - 70f) / 24f
        val cellHeight = 45f

        val cellPaint = Paint().apply { isAntiAlias = true }
        val labelPaint = Paint().apply {
            color = Color.DKGRAY
            textSize = 14f
            textAlign = Paint.Align.CENTER
            isAntiAlias = true
        }

        hourStats.forEachIndexed { hour, count ->
            val intensity = if (maxCount > 0) count.toFloat() / maxCount else 0f
            val alpha = (intensity * 200 + 40).toInt().coerceIn(40, 240)
            cellPaint.color = Color.argb(alpha, 76, 175, 80)

            val left = mapLeft + hour * cellWidth
            canvas.drawRoundRect(
                left + 1f, mapTop,
                left + cellWidth - 1f, mapTop + cellHeight,
                4f, 4f, cellPaint
            )

            // 시간 라벨 (3시간 간격)
            if (hour % 4 == 0) {
                canvas.drawText("${hour}시", left + cellWidth / 2, mapTop + cellHeight + 18f, labelPaint)
            }
        }

        return bitmap
    }

    // ── 월별 라인차트 ────────────────────────────────────────────
    fun generateMonthlyTrend(records: List<CareRecord>, width: Int = 600, height: Int = 320): Bitmap {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.WHITE)

        val monthStats = records.groupBy { record ->
            val cal = Calendar.getInstance().apply { timeInMillis = record.timestamp }
            "${cal.get(Calendar.YEAR)}-${String.format("%02d", cal.get(Calendar.MONTH) + 1)}"
        }.mapValues { it.value.size }

        if (monthStats.size < 2) {
            drawNoDataMessage(canvas, width, height, "최소 2개월 데이터 필요")
            return bitmap
        }

        val sorted = monthStats.toList().sortedBy { it.first }
        val maxCount = sorted.maxOf { it.second }.coerceAtLeast(1)

        val chartLeft = 60f
        val chartTop = 30f
        val chartRight = width - 30f
        val chartBottom = height - 50f
        val chartWidth = chartRight - chartLeft
        val chartHeight = chartBottom - chartTop

        // 격자선
        val gridPaint = Paint().apply {
            color = Color.parseColor("#E8E8E8")
            strokeWidth = 1f
        }
        for (i in 1..4) {
            val y = chartBottom - (i / 4f) * chartHeight
            canvas.drawLine(chartLeft, y, chartRight, y, gridPaint)
        }

        // 영역 채우기
        val fillPaint = Paint().apply {
            color = Color.argb(50, 255, 107, 107)
            isAntiAlias = true
        }
        val fillPath = Path()

        val linePaint = Paint().apply {
            color = Color.parseColor("#FF6B6B")
            strokeWidth = 4f
            style = Paint.Style.STROKE
            isAntiAlias = true
            strokeCap = Paint.Cap.ROUND
        }
        val linePath = Path()

        val pointPaint = Paint().apply {
            color = Color.parseColor("#FF6B6B")
            isAntiAlias = true
        }
        val textPaint = Paint().apply {
            color = Color.BLACK
            textSize = 16f
            textAlign = Paint.Align.CENTER
            isAntiAlias = true
        }

        val points = sorted.mapIndexed { index, (_, count) ->
            val x = if (sorted.size == 1) chartLeft + chartWidth / 2
            else chartLeft + (index.toFloat() / (sorted.size - 1)) * chartWidth
            val y = chartBottom - (count.toFloat() / maxCount * chartHeight)
            Pair(x, y)
        }

        // 영역 채우기
        if (points.isNotEmpty()) {
            fillPath.moveTo(points.first().first, chartBottom)
            points.forEach { (x, y) -> fillPath.lineTo(x, y) }
            fillPath.lineTo(points.last().first, chartBottom)
            fillPath.close()
            canvas.drawPath(fillPath, fillPaint)

            // 라인 그리기
            points.forEachIndexed { i, (x, y) ->
                if (i == 0) linePath.moveTo(x, y) else linePath.lineTo(x, y)
            }
            canvas.drawPath(linePath, linePaint)

            // 포인트 + 라벨
            sorted.forEachIndexed { index, (month, count) ->
                val (x, y) = points[index]
                canvas.drawCircle(x, y, 7f, pointPaint)
                canvas.drawText("${month.substring(5)}월", x, chartBottom + 22f, textPaint)
                if (count > 0) {
                    canvas.drawText(count.toString(), x, y - 12f, textPaint)
                }
            }
        }

        return bitmap
    }

    private fun drawNoDataMessage(canvas: Canvas, width: Int, height: Int, message: String) {
        val paint = Paint().apply {
            color = Color.GRAY
            textSize = 24f
            textAlign = Paint.Align.CENTER
            isAntiAlias = true
        }
        canvas.drawText(message, width / 2f, height / 2f, paint)
    }
}
