package vasuki.istanpdf.libreoffice

import android.util.Log
import org.json.JSONObject
import org.libreoffice.kit.Document

object SpreadsheetPageFitter {
    private const val TAG = "SpreadsheetPageFitter"
    private const val PRINT_RANGES_COMMAND = ".uno:DefinePrintArea"
    private const val GO_TO_CELL_COMMAND = ".uno:GoToCell"
    private const val GO_TO_END_COMMAND = ".uno:GoToEndOfData"
    private const val SELECT_DATA_COMMAND = ".uno:SelectData"
    private const val ADJUST_ZOOM_COMMAND = ".uno:AdjustPrintZoom"

    fun fit(document: Document) {
        val parts: Int
        try {
            document.initializeForRendering()
            parts = document.getParts()
        } catch (e: Exception) {
            Log.w(TAG, "Spreadsheet page fitting skipped", e)
            return
        }

        val printRanges = try {
            parsePrintRanges(document.getCommandValues(PRINT_RANGES_COMMAND))
        } catch (e: Exception) {
            Log.w(TAG, "Print range detection failed", e)
            emptyMap()
        }

        for (part in 0 until parts) {
            try {
                fitPart(document, part, printRanges[part])
            } catch (e: Exception) {
                Log.w(TAG, "Page fitting failed for sheet $part", e)
            }
        }
    }

    private fun fitPart(document: Document, part: Int, ranges: List<CellRange>?) {
        document.setPart(part)
        val range = ranges?.let(::boundingRange)
        if (range != null) {
            val arguments = """{"ToPoint":{"type":"string","value":"$range"}}"""
            UnoCommandHelper.postAndWait(
                document,
                GO_TO_CELL_COMMAND,
                arguments,
                UnoCommandHelper.HEAVY_TIMEOUT_MS
            )
        } else {
            UnoCommandHelper.postAndWait(document, GO_TO_END_COMMAND)
            UnoCommandHelper.postAndWait(
                document,
                SELECT_DATA_COMMAND,
                "",
                UnoCommandHelper.HEAVY_TIMEOUT_MS
            )
        }
        UnoCommandHelper.postAndWait(
            document,
            ADJUST_ZOOM_COMMAND,
            "",
            UnoCommandHelper.HEAVY_TIMEOUT_MS
        )
    }

    private fun boundingRange(ranges: List<CellRange>): String? {
        if (ranges.isEmpty()) return null
        val startCol = ranges.minOf { it.startCol }
        val startRow = ranges.minOf { it.startRow }
        val endCol = ranges.maxOf { it.endCol }
        val endRow = ranges.maxOf { it.endRow }
        return "${columnName(startCol)}${startRow + 1}:${columnName(endCol)}${endRow + 1}"
    }

    private fun columnName(index: Int): String {
        var value = index
        val name = StringBuilder()
        while (value >= 0) {
            name.insert(0, 'A' + value % 26)
            value = value / 26 - 1
        }
        return name.toString()
    }

    private fun parsePrintRanges(json: String): Map<Int, List<CellRange>> {
        if (json.isBlank()) return emptyMap()
        val result = mutableMapOf<Int, List<CellRange>>()
        val sheets = JSONObject(json).optJSONArray("printranges") ?: return emptyMap()
        for (sheetIndex in 0 until sheets.length()) {
            val sheet = sheets.optJSONObject(sheetIndex) ?: continue
            val part = sheet.optInt("sheet", -1)
            if (part < 0) continue
            val values = sheet.optJSONArray("ranges") ?: continue
            val ranges = mutableListOf<CellRange>()
            for (rangeIndex in 0 until values.length()) {
                val value = values.optJSONArray(rangeIndex) ?: continue
                if (value.length() < 4) continue
                ranges += CellRange(
                    startCol = value.getInt(0),
                    startRow = value.getInt(1),
                    endCol = value.getInt(2),
                    endRow = value.getInt(3)
                )
            }
            if (ranges.isNotEmpty()) result[part] = ranges
        }
        return result
    }

    private data class CellRange(
        val startCol: Int,
        val startRow: Int,
        val endCol: Int,
        val endRow: Int
    )
}
