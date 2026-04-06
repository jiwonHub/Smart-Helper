package com.scchyodol.smarthelper.util

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import com.itextpdf.io.font.PdfEncodings
import com.itextpdf.io.image.ImageDataFactory
import com.itextpdf.kernel.font.PdfFont
import com.itextpdf.kernel.font.PdfFontFactory
import com.itextpdf.kernel.pdf.PdfDocument
import com.itextpdf.kernel.pdf.PdfWriter
import com.itextpdf.layout.Document
import com.itextpdf.layout.borders.Border
import com.itextpdf.layout.element.Cell
import com.itextpdf.layout.element.Image
import com.itextpdf.layout.element.Paragraph
import com.itextpdf.layout.element.Table
import com.itextpdf.layout.properties.TextAlignment
import com.itextpdf.layout.properties.UnitValue
import com.scchyodol.smarthelper.data.model.CareRecord
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*

class ReportGenerator {

    companion object {
        private const val TAG       = "ReportGenerator"
        private const val FONT_PATH = "fonts/NanumGothic.ttf"

        private val DAY_OF_WEEK_MAP = mapOf(
            0 to Calendar.MONDAY,
            1 to Calendar.TUESDAY,
            2 to Calendar.WEDNESDAY,
            3 to Calendar.THURSDAY,
            4 to Calendar.FRIDAY,
            5 to Calendar.SATURDAY,
            6 to Calendar.SUNDAY
        )
    }

    private val chartGenerator = ChartGenerator()

    // ── 외부 진입점: 이번달 PDF 생성 ──────────────────────────────
    fun generateMonthlyPDF(
        context : Context,
        records : List<CareRecord>,   // DB에서 가져온 원본 (반복 포함)
        year    : Int,
        month   : Int                 // 0-based (Calendar.MONTH 기준)
    ): File? {
        return try {
            // 1) 이번달 시작/끝 타임스탬프
            val startCal = Calendar.getInstance().apply {
                set(year, month, 1, 0, 0, 0)
                set(Calendar.MILLISECOND, 0)
            }
            val endCal = Calendar.getInstance().apply {
                set(year, month, 1, 0, 0, 0)
                set(Calendar.MILLISECOND, 0)
                add(Calendar.MONTH, 1)
                add(Calendar.MILLISECOND, -1)
            }
            val startOfMonth = startCal.timeInMillis
            val endOfMonth   = endCal.timeInMillis

            // 2) 반복 레코드 → 이번달 실제 발생일로 확장
            val expandedRecords = expandRecordsForMonth(records, startOfMonth, endOfMonth)

            Log.d(TAG, "원본 레코드: ${records.size}건, 확장 후: ${expandedRecords.size}건")

            if (expandedRecords.isEmpty()) {
                Log.w(TAG, "이번달 표시할 데이터 없음")
                return null
            }

            // 3) 파일명
            val monthLabel = String.format("%d년 %02d월", year, month + 1)
            val fileName   = "셀프케어_리포트_${year}${String.format("%02d", month + 1)}.pdf"

            // 4) 저장
            val file = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                saveToDownloadsWithMediaStore(
                    context, fileName, expandedRecords, startOfMonth, endOfMonth, monthLabel
                )
            } else {
                saveToDownloadsLegacy(
                    context, fileName, expandedRecords, startOfMonth, endOfMonth, monthLabel
                )
            }

            Log.d(TAG, "PDF 생성 완료: ${file?.absolutePath}")
            file
        } catch (e: Exception) {
            Log.e(TAG, "PDF 생성 실패", e)
            null
        }
    }

    // ── 반복 레코드 확장 핵심 로직 ────────────────────────────────
    /**
     * isRepeat=true 레코드를 해당 월의 실제 발생일(가상 CareRecord)로 펼쳐줌
     * isRepeat=false 레코드는 startOfMonth ~ endOfMonth 범위 것만 그대로 포함
     */
    private fun expandRecordsForMonth(
        records      : List<CareRecord>,
        startOfMonth : Long,
        endOfMonth   : Long
    ): List<CareRecord> {

        val result  = mutableListOf<CareRecord>()
        val iterCal = Calendar.getInstance()
        val timeCal = Calendar.getInstance()

        records.forEach { record ->
            if (!record.isRepeat) {
                // 일반 레코드: 이번달 범위 것만 포함
                if (record.timestamp in startOfMonth..endOfMonth) {
                    result.add(record)
                }
            } else {
                // 반복 레코드: 이번달 해당 요일들을 가상 레코드로 생성
                if (record.repeatDays.isBlank()) return@forEach

                val repeatDayInts = record.repeatDays.split(",")
                    .mapNotNull { it.trim().toIntOrNull() }

                val targetCalDays = repeatDayInts
                    .mapNotNull { DAY_OF_WEEK_MAP[it] }
                    .toSet()

                if (targetCalDays.isEmpty()) return@forEach

                // 기준 시각 (시:분)
                timeCal.timeInMillis = record.timestamp
                val baseHour   = timeCal.get(Calendar.HOUR_OF_DAY)
                val baseMinute = timeCal.get(Calendar.MINUTE)

                // 이번달 1일부터 말일까지 순회
                iterCal.timeInMillis = startOfMonth

                while (iterCal.timeInMillis <= endOfMonth) {
                    if (iterCal.get(Calendar.DAY_OF_WEEK) in targetCalDays) {
                        // 해당 날 + 기준 시각으로 타임스탬프 생성
                        val occurrenceCal = Calendar.getInstance().apply {
                            timeInMillis = iterCal.timeInMillis
                            set(Calendar.HOUR_OF_DAY, baseHour)
                            set(Calendar.MINUTE,      baseMinute)
                            set(Calendar.SECOND,      0)
                            set(Calendar.MILLISECOND, 0)
                        }
                        val occurrenceTs = occurrenceCal.timeInMillis

                        // 반복 시작일(record.timestamp) 이후인 것만 포함
                        if (occurrenceTs >= record.timestamp) {
                            result.add(
                                record.copy(
                                    timestamp = occurrenceTs,
                                    isRepeat  = false   // 확장된 가상 레코드는 단건 취급
                                )
                            )
                        }
                    }
                    iterCal.add(Calendar.DAY_OF_MONTH, 1)
                }
            }
        }

        // 날짜 오름차순 정렬
        return result.sortedBy { it.timestamp }
    }

    // ── Android 10+ ──────────────────────────────────────────────
    private fun saveToDownloadsWithMediaStore(
        context      : Context,
        fileName     : String,
        records      : List<CareRecord>,
        startDate    : Long,
        endDate      : Long,
        monthLabel   : String
    ): File? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return null

        val resolver = context.contentResolver
        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
            put(MediaStore.MediaColumns.MIME_TYPE, "application/pdf")
            put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
        }

        val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)
            ?: return null

        return try {
            resolver.openOutputStream(uri)?.use { outputStream ->
                val document = Document(PdfDocument(PdfWriter(outputStream)))
                val font     = loadKoreanFont(context)
                generatePdfContent(document, records, startDate, endDate, font, monthLabel)
                document.close()
            }
            File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                fileName
            )
        } catch (e: Exception) {
            resolver.delete(uri, null, null)
            throw e
        }
    }

    // ── Android 9 이하 ────────────────────────────────────────────
    private fun saveToDownloadsLegacy(
        context    : Context,
        fileName   : String,
        records    : List<CareRecord>,
        startDate  : Long,
        endDate    : Long,
        monthLabel : String
    ): File {
        val downloadsDir =
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        if (!downloadsDir.exists()) downloadsDir.mkdirs()

        val file = File(downloadsDir, fileName)
        FileOutputStream(file).use { outputStream ->
            val document = Document(PdfDocument(PdfWriter(outputStream)))
            val font     = loadKoreanFont(context)
            generatePdfContent(document, records, startDate, endDate, font, monthLabel)
            document.close()
        }
        return file
    }

    // ── 한글 폰트 로드 ─────────────────────────────────────────────
    private fun loadKoreanFont(context: Context): PdfFont {
        return try {
            val fontBytes = context.assets.open(FONT_PATH).readBytes()
            PdfFontFactory.createFont(
                fontBytes,
                PdfEncodings.IDENTITY_H,
                PdfFontFactory.EmbeddingStrategy.FORCE_EMBEDDED
            )
        } catch (e: Exception) {
            Log.e(TAG, "한글 폰트 로드 실패: ${e.message}")
            PdfFontFactory.createFont()
        }
    }

    // ── PDF 내용 생성 ──────────────────────────────────────────────
    private fun generatePdfContent(
        document   : Document,
        records    : List<CareRecord>,
        startDate  : Long,
        endDate    : Long,
        koreanFont : PdfFont,
        monthLabel : String
    ) {
        val dateFormat = SimpleDateFormat("yyyy.MM.dd", Locale.KOREA)
        val timeFormat = SimpleDateFormat("MM/dd (E) HH:mm", Locale.KOREA)

        // ── 제목 ─────────────────────────────────────────────────
        document.add(
            Paragraph("셀프케어 월간 리포트")
                .setFont(koreanFont).setFontSize(22f).setBold()
                .setTextAlignment(TextAlignment.CENTER).setMarginBottom(6f)
        )
        document.add(
            Paragraph(monthLabel)
                .setFont(koreanFont).setFontSize(15f)
                .setTextAlignment(TextAlignment.CENTER).setMarginBottom(4f)
        )
        document.add(
            Paragraph("기간: ${dateFormat.format(startDate)} ~ ${dateFormat.format(endDate)}")
                .setFont(koreanFont).setFontSize(11f)
                .setTextAlignment(TextAlignment.CENTER)
        )
        document.add(
            Paragraph("총 활동 횟수: ${records.size}회")
                .setFont(koreanFont).setFontSize(11f)
                .setTextAlignment(TextAlignment.CENTER).setMarginBottom(20f)
        )

        // ── 차트 섹션 ─────────────────────────────────────────────
        if (records.isNotEmpty()) {

            // 카테고리별 분포
            document.add(
                Paragraph("카테고리별 분포")
                    .setFont(koreanFont).setFontSize(14f).setBold().setMarginBottom(6f)
            )
            document.add(
                bitmapToImage(chartGenerator.generateCategoryPieChart(records))
                    .setMarginBottom(16f)
            )

            // 요일별 활동량
            val weeklyTable = Table(UnitValue.createPercentArray(floatArrayOf(100f)))
                .useAllAvailableWidth()
                .setKeepTogether(true)  // ← 페이지 분할 방지

            weeklyTable.addCell(
                Cell().add(
                    Paragraph("요일별 활동량")
                        .setFont(koreanFont).setFontSize(14f).setBold()
                ).setBorder(Border.NO_BORDER)
            )
            weeklyTable.addCell(
                Cell().add(bitmapToImage(chartGenerator.generateWeeklyBarChart(records)))
                    .setBorder(Border.NO_BORDER)
            )
            document.add(weeklyTable.setMarginBottom(16f))

            // 시간대별 활동 분포
            document.add(
                Paragraph("시간대별 활동 분포")
                    .setFont(koreanFont).setFontSize(14f).setBold().setMarginBottom(6f)
            )
            document.add(
                bitmapToImage(chartGenerator.generateHourlyHeatmap(records))
                    .setMarginBottom(16f)
            )
        }

        // ── 카테고리별 통계 ────────────────────────────────────────
        document.add(
            Paragraph("카테고리별 통계")
                .setFont(koreanFont).setFontSize(14f).setBold().setMarginBottom(8f)
        )
        records.groupBy { it.category }
            .mapValues { it.value.size }
            .forEach { (category, count) ->
                document.add(
                    Paragraph("• ${category.displayName}: ${count}회")
                        .setFont(koreanFont).setFontSize(11f)
                )
            }

        document.add(Paragraph(" ").setMarginBottom(12f))

        // ── 상세 기록 테이블 ───────────────────────────────────────
        document.add(
            Paragraph("상세 기록")
                .setFont(koreanFont).setFontSize(14f).setBold().setMarginBottom(8f)
        )

        val table = Table(UnitValue.createPercentArray(floatArrayOf(35f, 25f, 40f)))
            .useAllAvailableWidth()

        listOf("날짜/시간", "카테고리", "내용").forEach { header ->
            table.addHeaderCell(
                Cell().add(
                    Paragraph(header).setFont(koreanFont).setFontSize(11f).setBold()
                )
            )
        }

        records.sortedByDescending { it.timestamp }.forEach { record ->
            listOf(
                timeFormat.format(record.timestamp),
                record.category.displayName,
                record.value ?: "-"
            ).forEach { cellText ->
                table.addCell(
                    Cell().add(Paragraph(cellText).setFont(koreanFont).setFontSize(10f))
                )
            }
        }

        document.add(table)
    }

    // ── 비트맵 → PDF 이미지 변환 ──────────────────────────────────
    private fun bitmapToImage(bitmap: Bitmap): Image {
        val stream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
        return Image(ImageDataFactory.create(stream.toByteArray())).scaleToFit(500f, 300f)
    }
}
