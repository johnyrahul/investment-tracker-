package com.johnyrahul.investmenttracker.core.sync

import android.content.Context
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.encryption.InvalidPasswordException
import com.tom_roush.pdfbox.text.PDFTextStripper

/** Opens a (usually password-protected) CAS PDF on the device and extracts its text. */
class PdfStatementReader(context: Context) {

    init {
        PDFBoxResourceLoader.init(context.applicationContext)
    }

    sealed interface Result {
        data class Text(val text: String, val password: String) : Result
        data object Locked : Result
    }

    /** Tries no password, then each candidate in order. */
    fun read(pdf: ByteArray, passwords: List<String>): Result {
        for (password in (listOf("") + passwords).distinct()) {
            val document = try {
                PDDocument.load(pdf, password)
            } catch (_: InvalidPasswordException) {
                continue
            }
            document.use {
                // Sorting by position keeps table cells of one row on one line.
                val stripper = PDFTextStripper().apply { sortByPosition = true }
                return Result.Text(stripper.getText(it), password)
            }
        }
        return Result.Locked
    }
}
