package org.libreoffice.kit

import java.nio.ByteBuffer

class Document(private val handle: ByteBuffer) {

    private var messageCallback: MessageCallback? = null

    init {
        bindMessageCallback()
    }

    fun setMessageCallback(messageCallback: MessageCallback?) {
        this.messageCallback = messageCallback
    }

    @Suppress("unused")
    private fun messageRetrieved(signalNumber: Int, payload: String) {
        messageCallback?.messageRetrieved(signalNumber, payload)
    }

    private external fun bindMessageCallback()

    external fun destroy()

    external fun getPart(): Int

    external fun setPart(partIndex: Int)

    external fun getParts(): Int

    external fun getPartName(partIndex: Int): String

    external fun setPartMode(partMode: Int)

    external fun getPartPageRectangles(): String

    external fun getDocumentHeight(): Long

    external fun getDocumentWidth(): Long

    private external fun getDocumentTypeNative(): Int

    external fun setClientZoom(nTilePixelWidth: Int, nTilePixelHeight: Int, nTileTwipWidth: Int, nTileTwipHeight: Int)

    external fun saveAs(url: String, format: String, options: String?)

    private external fun paintTileNative(buffer: ByteBuffer, canvasWidth: Int, canvasHeight: Int, tilePositionX: Int, tilePositionY: Int, tileWidth: Int, tileHeight: Int)

    fun getDocumentType(): Int = getDocumentTypeNative()

    fun paintTile(buffer: ByteBuffer, canvasWidth: Int, canvasHeight: Int, tilePositionX: Int, tilePositionY: Int, tileWidth: Int, tileHeight: Int) {
        paintTileNative(buffer, canvasWidth, canvasHeight, tilePositionX, tilePositionY, tileWidth, tileHeight)
    }

    external fun initializeForRendering()

    external fun postKeyEvent(type: Int, charCode: Int, keyCode: Int)

    external fun postMouseEvent(type: Int, x: Int, y: Int, count: Int, button: Int, modifier: Int)

    external fun postUnoCommand(command: String, arguments: String, notifyWhenFinished: Boolean)

    external fun setTextSelection(type: Int, x: Int, y: Int)

    external fun setGraphicSelection(type: Int, x: Int, y: Int)

    external fun getTextSelection(mimeType: String): String

    external fun paste(mimeType: String, data: String): Boolean

    external fun resetSelection()

    external fun getCommandValues(command: String): String

    fun interface MessageCallback {
        fun messageRetrieved(signalNumber: Int, payload: String)
    }

    companion object {
        const val PART_MODE_SLIDE = 0
        const val PART_MODE_NOTES = 1

        const val DOCTYPE_TEXT = 0
        const val DOCTYPE_SPREADSHEET = 1
        const val DOCTYPE_PRESENTATION = 2
        const val DOCTYPE_DRAWING = 3
        const val DOCTYPE_OTHER = 4

        const val MOUSE_EVENT_BUTTON_DOWN = 0
        const val MOUSE_EVENT_BUTTON_UP = 1
        const val MOUSE_EVENT_MOVE = 2

        const val KEY_EVENT_PRESS = 0
        const val KEY_EVENT_RELEASE = 1

        const val BOLD = 0
        const val ITALIC = 1
        const val UNDERLINE = 2
        const val STRIKEOUT = 3

        const val ALIGN_LEFT = 4
        const val ALIGN_CENTER = 5
        const val ALIGN_RIGHT = 6
        const val ALIGN_JUSTIFY = 7
        const val NUMBERED_LIST = 8
        const val BULLET_LIST = 9

        const val CALLBACK_INVALIDATE_TILES = 0
        const val CALLBACK_INVALIDATE_VISIBLE_CURSOR = 1
        const val CALLBACK_TEXT_SELECTION = 2
        const val CALLBACK_TEXT_SELECTION_START = 3
        const val CALLBACK_TEXT_SELECTION_END = 4
        const val CALLBACK_CURSOR_VISIBLE = 5
        const val CALLBACK_GRAPHIC_SELECTION = 6
        const val CALLBACK_HYPERLINK_CLICKED = 7
        const val CALLBACK_STATE_CHANGED = 8
        const val CALLBACK_STATUS_INDICATOR_START = 9
        const val CALLBACK_STATUS_INDICATOR_SET_VALUE = 10
        const val CALLBACK_STATUS_INDICATOR_FINISH = 11
        const val CALLBACK_SEARCH_NOT_FOUND = 12
        const val CALLBACK_DOCUMENT_SIZE_CHANGED = 13
        const val CALLBACK_SET_PART = 14
        const val CALLBACK_SEARCH_RESULT_SELECTION = 15
        const val CALLBACK_UNO_COMMAND_RESULT = 16
        const val CALLBACK_CELL_CURSOR = 17
        const val CALLBACK_MOUSE_POINTER = 18
        const val CALLBACK_CELL_FORMULA = 19
        const val CALLBACK_DOCUMENT_PASSWORD = 20
        const val CALLBACK_DOCUMENT_PASSWORD_TO_MODIFY = 21
        const val CALLBACK_ERROR = 22
        const val CALLBACK_CONTEXT_MENU = 23
        const val CALLBACK_INVALIDATE_VIEW_CURSOR = 24
        const val CALLBACK_TEXT_VIEW_SELECTION = 25
        const val CALLBACK_CELL_VIEW_CURSOR = 26
        const val CALLBACK_GRAPHIC_VIEW_SELECTION = 27
        const val CALLBACK_VIEW_CURSOR_VISIBLE = 28
        const val CALLBACK_VIEW_LOCK = 29
        const val CALLBACK_REDLINE_TABLE_SIZE_CHANGED = 30
        const val CALLBACK_REDLINE_TABLE_ENTRY_MODIFIED = 31
        const val CALLBACK_COMMENT = 32
        const val CALLBACK_INVALIDATE_HEADER = 33
        const val CALLBACK_CELL_ADDRESS = 34
        const val CALLBACK_SC_FOLLOW_JUMP = 54

        const val SET_TEXT_SELECTION_START = 0
        const val SET_TEXT_SELECTION_END = 1
        const val SET_TEXT_SELECTION_RESET = 2

        const val SET_GRAPHIC_SELECTION_START = 0
        const val SET_GRAPHIC_SELECTION_END = 1

        const val MOUSE_BUTTON_LEFT = 1
        const val MOUSE_BUTTON_MIDDLE = 2
        const val MOUSE_BUTTON_RIGHT = 4

        const val KEYBOARD_MODIFIER_NONE = 0x0000
        const val KEYBOARD_MODIFIER_SHIFT = 0x1000
        const val KEYBOARD_MODIFIER_MOD1 = 0x2000
        const val KEYBOARD_MODIFIER_MOD2 = 0x4000
        const val KEYBOARD_MODIFIER_MOD3 = 0x8000

        const val LOK_FEATURE_DOCUMENT_PASSWORD = 1L
        const val LOK_FEATURE_DOCUMENT_PASSWORD_TO_MODIFY = (1L shl 1)
        const val LOK_FEATURE_PART_IN_INVALIDATION_CALLBACK = (1L shl 2)
        const val LOK_FEATURE_NO_TILED_ANNOTATIONS = (1L shl 3)
    }
}
