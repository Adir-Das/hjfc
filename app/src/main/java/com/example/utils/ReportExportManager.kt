package com.example.utils

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import androidx.core.content.FileProvider
import androidx.core.content.res.ResourcesCompat
import com.example.R
import com.example.core.i18n.Formatters
import com.example.data.repository.TeamWorkspaceSnapshot
import java.io.File
import java.io.FileOutputStream

object ReportExportManager {

    fun generateMonthlyPdf(context: Context, snapshot: TeamWorkspaceSnapshot, period: String): File {
        val reportsDir = File(context.cacheDir, "reports").apply { mkdirs() }
        val file = File(reportsDir, "JSC_Report_${period}.pdf")

        val bengaliTypeface = ResourcesCompat.getFont(context, R.font.noto_sans_bengali) ?: Typeface.DEFAULT
        val doc = PdfDocument()
        val pageInfo = PdfDocument.PageInfo.Builder(595, 842, 1).create()
        val page = doc.startPage(pageInfo)
        val canvas = page.canvas

        val navyPaint = Paint().apply { color = Color.parseColor("#102A43"); style = Paint.Style.FILL }
        val greenPaint = Paint().apply { color = Color.parseColor("#159957"); style = Paint.Style.FILL }
        val headerTextPaint = Paint().apply {
            color = Color.WHITE
            textSize = 16f
            typeface = Typeface.create(bengaliTypeface, Typeface.BOLD)
            isAntiAlias = true
        }
        val subHeaderPaint = Paint().apply {
            color = Color.parseColor("#D4AF37")
            textSize = 10.5f
            typeface = bengaliTypeface
            isAntiAlias = true
        }
        val sectionTitlePaint = Paint().apply {
            color = Color.parseColor("#102A43")
            textSize = 13f
            typeface = Typeface.create(bengaliTypeface, Typeface.BOLD)
            isAntiAlias = true
        }
        val bodyPaint = Paint().apply {
            color = Color.parseColor("#1E293B")
            textSize = 10f
            typeface = bengaliTypeface
            isAntiAlias = true
        }
        val dividerPaint = Paint().apply {
            color = Color.parseColor("#CBD5E1")
            strokeWidth = 1f
        }

        // Header banner
        canvas.drawRect(0f, 0f, 595f, 88f, navyPaint)
        canvas.drawRect(0f, 84f, 595f, 88f, greenPaint)

        // Club emblem
        runCatching {
            val rawBmp = BitmapFactory.decodeResource(context.resources, R.drawable.img_jsc_emblem)
            val scaled = Bitmap.createScaledBitmap(rawBmp, 54, 54, true)
            canvas.drawBitmap(scaled, null, Rect(24, 17, 78, 71), null)
        }

        canvas.drawText(snapshot.team.name, 92f, 38f, headerTextPaint)
        canvas.drawText(
            "মাসিক আর্থিক ও দলীয় প্রতিবেদন | Monthly Report ($period) • Generated: ${Formatters.todayDhaka()}",
            92f,
            58f,
            subHeaderPaint
        )

        var y = 114f
        val approvedTxns = snapshot.transactions.filter { it.approvalStatus == "APPROVED" }
        val contributions = approvedTxns.filter { it.type == "INCOME" && it.category.contains("Contribution", true) }.sumOf { it.amount }
        val otherIncome = approvedTxns.filter { it.type == "INCOME" && !it.category.contains("Contribution", true) }.sumOf { it.amount }
        val totalExpense = approvedTxns.filter { it.type == "EXPENSE" }.sumOf { it.amount }
        val opening = snapshot.fundAccounts.sumOf { it.openingBalance }
        val closing = snapshot.fundAccounts.sumOf { it.currentBalance }
        val totalDues = snapshot.memberDues.sumOf { it.outstandingBalance }
        val totalAdvance = snapshot.memberDues.sumOf { it.advanceBalance }

        // Section 1: Financial Summary
        canvas.drawText("১. আর্থিক সারসংক্ষেপ / Financial Summary (BDT ৳)", 28f, y, sectionTitlePaint)
        y += 18f
        val summaryRows = listOf(
            "Opening Balance (প্রারম্ভিক স্থিতি): ${Formatters.formatBdt(opening)}",
            "Member Contributions (সদস্য চাঁদা): ${Formatters.formatBdt(contributions)}",
            "Other Income (অন্যান্য আয়): ${Formatters.formatBdt(otherIncome)}",
            "Approved Expenses (অনুমোদিত ব্যয়): ${Formatters.formatBdt(totalExpense)}",
            "Closing Fund Balance (বর্তমান তহবিল): ${Formatters.formatBdt(closing)}",
            "Outstanding Dues (বকেয়া চাঁদা): ${Formatters.formatBdt(totalDues)}   |   Advance (অগ্রিম): ${Formatters.formatBdt(totalAdvance)}"
        )
        summaryRows.forEach { line ->
            canvas.drawText(line, 34f, y, bodyPaint)
            y += 16f
        }

        y += 8f
        canvas.drawLine(28f, y, 567f, y, dividerPaint)
        y += 20f

        // Section 2: Member Dues Summary
        canvas.drawText("২. সদস্য তালিকা ও বকেয়া / Member Financial Summary", 28f, y, sectionTitlePaint)
        y += 18f
        snapshot.members.take(12).forEach { m ->
            val due = snapshot.memberDues.firstOrNull { it.memberId == m.id }
            val attended = snapshot.attendance.count { it.memberId == m.id && (it.status == "PRESENT" || it.status == "LATE") }
            val line = "${m.memberCode} ${m.fullName} | Att: $attended | Payable: ${Formatters.formatBdt(due?.totalPayable ?: 0.0)} | Paid: ${Formatters.formatBdt(due?.totalPaid ?: 0.0)} | Due: ${Formatters.formatBdt(due?.outstandingBalance ?: 0.0)}"
            canvas.drawText(line.take(92), 34f, y, bodyPaint)
            y += 15f
        }

        y += 8f
        canvas.drawLine(28f, y, 567f, y, dividerPaint)
        y += 20f

        // Section 3: Match & Budget Summary
        canvas.drawText("৩. ম্যাচ ও বাজেট সারসংক্ষেপ / Matches & Budget", 28f, y, sectionTitlePaint)
        y += 18f
        snapshot.matches.take(6).forEach { g ->
            val res = snapshot.matchResults.firstOrNull { it.gameId == g.id }
            val scoreText = res?.let { "${it.teamScore}-${it.opponentScore} (${it.outcome})" } ?: g.status
            canvas.drawText("${g.matchDate} • ${g.title} vs ${g.opponentName} — ${g.venueName} [$scoreText]".take(92), 34f, y, bodyPaint)
            y += 15f
        }

        val monthlyBudget = snapshot.budgets.firstOrNull()?.budgetAmount ?: 0.0
        y += 6f
        canvas.drawText(
            "Monthly Budget: ${Formatters.formatBdt(monthlyBudget)} | Actual Expense: ${Formatters.formatBdt(totalExpense)} | Remaining: ${Formatters.formatBdt((monthlyBudget - totalExpense).coerceAtLeast(0.0))}",
            34f,
            y,
            bodyPaint
        )

        doc.finishPage(page)
        FileOutputStream(file).use { doc.writeTo(it) }
        doc.close()
        return file
    }

    fun generateExcelSpreadsheet(context: Context, snapshot: TeamWorkspaceSnapshot, period: String): File {
        val reportsDir = File(context.cacheDir, "reports").apply { mkdirs() }
        val file = File(reportsDir, "JSC_Report_${period}.xls")

        val sb = StringBuilder()
        sb.appendLine("""<?xml version="1.0" encoding="UTF-8"?>""")
        sb.appendLine("""<?mso-application progid="Excel.Sheet"?>""")
        sb.appendLine("""<Workbook xmlns="urn:schemas-microsoft-com:office:spreadsheet" xmlns:ss="urn:schemas-microsoft-com:office:spreadsheet">""")

        // Sheet 1: Transactions
        sb.appendLine("""<Worksheet ss:Name="Transactions"><Table>""")
        sb.appendLine("<Row><Cell><Data ss:Type=\"String\">Ref</Data></Cell><Cell><Data ss:Type=\"String\">Date</Data></Cell><Cell><Data ss:Type=\"String\">Type</Data></Cell><Cell><Data ss:Type=\"String\">Category</Data></Cell><Cell><Data ss:Type=\"String\">Amount (BDT)</Data></Cell><Cell><Data ss:Type=\"String\">Method</Data></Cell><Cell><Data ss:Type=\"String\">Status</Data></Cell><Cell><Data ss:Type=\"String\">Description</Data></Cell></Row>")
        snapshot.transactions.forEach { t ->
            sb.appendLine(
                "<Row>" +
                    cell(t.transactionRef) + cell(t.txnDate) + cell(t.type) + cell(t.category) +
                    "<Cell><Data ss:Type=\"Number\">${t.amount}</Data></Cell>" +
                    cell(t.paymentMethod) + cell(t.approvalStatus) + cell(t.description) +
                    "</Row>"
            )
        }
        sb.appendLine("</Table></Worksheet>")

        // Sheet 2: Member Dues
        sb.appendLine("""<Worksheet ss:Name="MemberDues"><Table>""")
        sb.appendLine("<Row><Cell><Data ss:Type=\"String\">Code</Data></Cell><Cell><Data ss:Type=\"String\">Name</Data></Cell><Cell><Data ss:Type=\"String\">Role</Data></Cell><Cell><Data ss:Type=\"String\">Payable</Data></Cell><Cell><Data ss:Type=\"String\">Paid</Data></Cell><Cell><Data ss:Type=\"String\">Advance</Data></Cell><Cell><Data ss:Type=\"String\">Outstanding</Data></Cell></Row>")
        snapshot.members.forEach { m ->
            val d = snapshot.memberDues.firstOrNull { it.memberId == m.id }
            sb.appendLine(
                "<Row>" + cell(m.memberCode) + cell(m.fullName) + cell(m.role) +
                    numCell(d?.totalPayable ?: 0.0) + numCell(d?.totalPaid ?: 0.0) +
                    numCell(d?.advanceBalance ?: 0.0) + numCell(d?.outstandingBalance ?: 0.0) +
                    "</Row>"
            )
        }
        sb.appendLine("</Table></Worksheet>")
        sb.appendLine("</Workbook>")

        file.writeText(sb.toString(), Charsets.UTF_8)
        return file
    }

    private fun escapeXml(s: String): String =
        s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")

    private fun cell(value: String) = "<Cell><Data ss:Type=\"String\">${escapeXml(value)}</Data></Cell>"
    private fun numCell(value: Double) = "<Cell><Data ss:Type=\"Number\">$value</Data></Cell>"

    fun shareFile(context: Context, file: File, mimeType: String, title: String) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, title)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, title))
    }
}
