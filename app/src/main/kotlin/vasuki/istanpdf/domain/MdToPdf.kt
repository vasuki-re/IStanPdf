package vasuki.istanpdf.domain

import android.content.Context
import android.net.Uri
import com.itextpdf.html2pdf.ConverterProperties
import com.itextpdf.html2pdf.HtmlConverter
import com.itextpdf.html2pdf.resolver.font.DefaultFontProvider
import org.commonmark.ext.autolink.AutolinkExtension
import org.commonmark.ext.gfm.strikethrough.StrikethroughExtension
import org.commonmark.ext.gfm.tables.TablesExtension
import org.commonmark.ext.ins.InsExtension
import org.commonmark.ext.task.list.items.TaskListItemsExtension
import org.commonmark.parser.Parser
import org.commonmark.renderer.html.HtmlRenderer
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

class MdToPdf(private val context: Context) {

    private val extensions = listOf(
        TablesExtension.create(),
        StrikethroughExtension.create(),
        TaskListItemsExtension.create(),
        AutolinkExtension.create(),
        InsExtension.create()
    )

    @Throws(Exception::class)
    fun execute(mdUri: Uri): File {
        val markdown = context.contentResolver.openInputStream(mdUri)?.use {
            it.bufferedReader().readText()
        } ?: throw IllegalArgumentException("Cannot read Markdown file")

        val parser = Parser.builder().extensions(extensions).build()
        val document = parser.parse(markdown)
        val renderer = HtmlRenderer.builder().extensions(extensions).build()
        val bodyHtml = renderer.render(document)

        val fullHtml = """
            <!DOCTYPE html>
            <html>
            <head>
            <meta charset="UTF-8"/>
            <style>
            body {
                font-family: 'Helvetica Neue', Helvetica, Arial, sans-serif;
                font-size: 14px;
                line-height: 1.6;
                color: #1a1a1a;
                max-width: 720px;
                margin: 0 auto;
                padding: 32px 24px;
            }
            h1 { font-size: 28px; margin: 24px 0 12px; border-bottom: 1px solid #ddd; padding-bottom: 8px; }
            h2 { font-size: 22px; margin: 20px 0 10px; border-bottom: 1px solid #eee; padding-bottom: 6px; }
            h3 { font-size: 18px; margin: 16px 0 8px; }
            h4 { font-size: 16px; margin: 14px 0 6px; }
            h5 { font-size: 14px; margin: 12px 0 4px; }
            h6 { font-size: 13px; color: #555; margin: 10px 0 4px; }
            p { margin: 0 0 12px; }
            a { color: #2563eb; text-decoration: none; }
            strong { font-weight: 700; }
            em { font-style: italic; }
            del { text-decoration: line-through; color: #6b7280; }
            ins { text-decoration: underline; }
            code {
                font-family: 'Courier New', Courier, monospace;
                font-size: 13px;
                background: #f4f4f5;
                padding: 2px 6px;
                border-radius: 3px;
            }
            pre {
                background: #f4f4f5;
                padding: 14px 16px;
                border-radius: 6px;
                overflow-x: auto;
                margin: 0 0 16px;
            }
            pre code { background: none; padding: 0; font-size: 13px; }
            blockquote {
                border-left: 4px solid #d1d5db;
                margin: 0 0 12px;
                padding: 8px 16px;
                color: #4b5563;
            }
            blockquote p { margin: 0; }
            ul, ol { padding-left: 24px; margin: 0 0 12px; }
            li { margin: 0 0 4px; }
            li.task-list-item { list-style: none; margin-left: -24px; padding-left: 6px; }
            li.task-list-item input[type="checkbox"] { margin-right: 8px; vertical-align: middle; }
            hr { border: none; border-top: 1px solid #ddd; margin: 24px 0; }
            table { border-collapse: collapse; width: 100%; margin: 0 0 16px; }
            th, td { border: 1px solid #d1d5db; padding: 8px 12px; text-align: left; }
            th { background: #f9fafb; font-weight: 600; }
            img { max-width: 100%; }
            sup a { color: #2563eb; text-decoration: none; font-size: 11px; }
            </style>
            </head>
            <body>
            $bodyHtml
            </body>
            </html>
        """.trimIndent()

        val htmlFile = File.createTempFile("istanpdf_md_", ".html", context.cacheDir)
        htmlFile.writeText(fullHtml)

        val pdfFile = File.createTempFile("istanpdf_md_", ".pdf", context.cacheDir)

        try {
            val fontProvider = DefaultFontProvider(true, true, false)
            val emojiFont = File(context.cacheDir, "NotoEmoji-Regular.ttf")
            if (!emojiFont.exists()) {
                context.assets.open("NotoEmoji-Regular.ttf").use { input ->
                    FileOutputStream(emojiFont).use { output -> input.copyTo(output) }
                }
            }
            fontProvider.addFont(emojiFont.absolutePath)

            val properties = ConverterProperties()
            properties.setFontProvider(fontProvider)

            FileInputStream(htmlFile).use { input ->
                FileOutputStream(pdfFile).use { output ->
                    HtmlConverter.convertToPdf(input, output, properties)
                }
            }
        } finally {
            if (htmlFile.exists()) htmlFile.delete()
        }

        return pdfFile
    }
}
