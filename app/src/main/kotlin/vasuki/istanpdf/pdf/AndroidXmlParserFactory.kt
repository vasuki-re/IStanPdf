package vasuki.istanpdf.pdf

import com.itextpdf.kernel.utils.IXmlParserFactory
import org.xml.sax.XMLReader
import javax.xml.parsers.DocumentBuilder
import javax.xml.parsers.DocumentBuilderFactory
import javax.xml.parsers.SAXParserFactory
import javax.xml.transform.Transformer
import javax.xml.transform.TransformerFactory

/**
 * Custom implementation of [IXmlParserFactory] to bypass Android's XML parser limitations.
 *
 * Android's XML parser implementations throw [UnsupportedOperationException] when
 * setXIncludeAware is invoked. This implementation safely handles feature flags.
 */
class AndroidXmlParserFactory : IXmlParserFactory {

    override fun createDocumentBuilderInstance(isNamespaceAware: Boolean, isXIncludeAware: Boolean): DocumentBuilder {
        val factory = DocumentBuilderFactory.newInstance()
        factory.isNamespaceAware = isNamespaceAware

        try {
            factory.isXIncludeAware = isXIncludeAware
        } catch (e: UnsupportedOperationException) {
            // Android's XML parser does not support XInclude configuration; safely ignore
        } catch (e: Exception) {
            // Ignore other Android XML parser implementation quirks
        }

        try {
            factory.setFeature("http://javax.xml.XMLConstants/feature/secure-processing", true)
        } catch (e: Exception) {
            // Fall back silently if the security feature is unsupported
        }

        return factory.newDocumentBuilder()
    }

    override fun createXMLReaderInstance(isNamespaceAware: Boolean, isXIncludeAware: Boolean): XMLReader {
        val factory = SAXParserFactory.newInstance()
        factory.isNamespaceAware = isNamespaceAware

        try {
            factory.isXIncludeAware = isXIncludeAware
        } catch (e: UnsupportedOperationException) {
            // Safely ignore on Android runtime
        } catch (e: Exception) {
            // Ignore optional parser features unsupported on Android
        }

        return factory.newSAXParser().xmlReader
    }

    override fun createTransformerInstance(): Transformer {
        val factory = TransformerFactory.newInstance()
        return factory.newTransformer()
    }
}
