

package org.libreoffice.kit;

import java.nio.ByteBuffer;

public class Office {
    private ByteBuffer handle;
    private MessageCallback messageCallback = null;

    public Office(ByteBuffer handle) {
        this.handle = handle;
        bindMessageCallback();
    }

    private native void bindMessageCallback();

    public native String getError();

    private native ByteBuffer documentLoadNative(String url);

    public Document documentLoad(String url) {
        ByteBuffer documentHandle = documentLoadNative(url);
        Document document = null;
        if (documentHandle != null) {
            document = new Document(documentHandle);
        }
        return document;
    }

    public native void destroy();
    public native void destroyAndExit();
    public native void setDocumentPassword(String url, String pwd);
    public native void setOptionalFeatures(long options);

    public void setMessageCallback(MessageCallback messageCallback) {
        this.messageCallback = messageCallback;
    }

    private void messageRetrievedLOKit(int signalNumber, String payload) {
        if (messageCallback != null) {
            messageCallback.messageRetrieved(signalNumber, payload);
        }

    }

    public interface MessageCallback {

        void messageRetrieved(int signalNumber, String payload);
    }
}
