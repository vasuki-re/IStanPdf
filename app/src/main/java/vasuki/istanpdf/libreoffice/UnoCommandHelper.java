package vasuki.istanpdf.libreoffice;

import org.libreoffice.kit.Document;

import java.util.concurrent.TimeUnit;


public final class UnoCommandHelper {

    private static final long DEFAULT_TIMEOUT_MS = 5000;

    private static final Object lock = new Object();

    private static volatile boolean commandComplete;

    private static final Document.MessageCallback SHARED_CALLBACK = (signalNumber, payload) -> {
        if (signalNumber == Document.CALLBACK_UNO_COMMAND_RESULT) {
            synchronized (lock) {
                commandComplete = true;
                lock.notifyAll();
            }
        }
    };

    private UnoCommandHelper() {
    }

   
    public static void postAndWait(Document doc, String command) throws InterruptedException {
        postAndWait(doc, command, "", DEFAULT_TIMEOUT_MS);
    }

   
    public static void postAndWait(Document doc, String command, String arguments) throws InterruptedException {
        postAndWait(doc, command, arguments, DEFAULT_TIMEOUT_MS);
    }

   
    public static void postAndWait(Document doc, String command, String arguments, long timeoutMs)
            throws InterruptedException {
        synchronized (lock) {
            commandComplete = false;
        }

        doc.setMessageCallback(SHARED_CALLBACK);
        doc.postUnoCommand(command, arguments, true);

        synchronized (lock) {
            long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMs);
            while (!commandComplete) {
                long remaining = TimeUnit.NANOSECONDS.toMillis(deadline - System.nanoTime());
                if (remaining <= 0) break;
                lock.wait(remaining);
            }
        }
    }

    public static void postFireAndForget(Document doc, String command) {
        doc.postUnoCommand(command, "", false);
    }

    public static void postFireAndForget(Document doc, String command, String arguments) {
        doc.postUnoCommand(command, arguments, false);
    }
}
