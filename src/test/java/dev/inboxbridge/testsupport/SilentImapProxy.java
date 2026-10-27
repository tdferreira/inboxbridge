package dev.inboxbridge.testsupport;

import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/** Keeps the first watch TCP session open while dropping only its server responses. */
public final class SilentImapProxy implements AutoCloseable {
    private final ServerSocket listener;
    private final List<Socket> sockets = new CopyOnWriteArrayList<>();
    private final AtomicInteger connections = new AtomicInteger();
    private final AtomicBoolean silent = new AtomicBoolean();
    public final CountDownLatch responseSuppressed = new CountDownLatch(1);
    private volatile Socket first;

    public SilentImapProxy(int serverPort) throws IOException {
        listener = new ServerSocket(0, 50, InetAddress.getLoopbackAddress());
        Thread.startVirtualThread(() -> {
            try {
                while (!listener.isClosed()) {
                    Socket client = listener.accept();
                    Socket server = new Socket(InetAddress.getLoopbackAddress(), serverPort);
                    sockets.add(client); sockets.add(server);
                    boolean watch = connections.incrementAndGet() == 1;
                    if (watch) first = client;
                    Thread.startVirtualThread(() -> forward(client, server, false));
                    Thread.startVirtualThread(() -> forward(server, client, watch));
                }
            } catch (IOException ignored) { /* Closing the fixture ends accept. */ }
        });
    }
    public int port() { return listener.getLocalPort(); }
    public void silenceWatch() { silent.set(true); }
    public boolean firstConnectionOpen() { return first != null && !first.isClosed(); }

    private void forward(Socket input, Socket output, boolean watchResponses) {
        try {
            byte[] buffer = new byte[8192]; int count;
            while ((count = input.getInputStream().read(buffer)) != -1) {
                if (watchResponses && silent.get()) { responseSuppressed.countDown(); continue; }
                output.getOutputStream().write(buffer, 0, count);
                output.getOutputStream().flush();
            }
        } catch (IOException ignored) { /* Expected when the watcher cancels a stalled session. */ }
        finally { close(input); close(output); }
    }
    private void close(Socket socket) { try { socket.close(); } catch (IOException ignored) { } }
    @Override public void close() throws IOException {
        listener.close(); sockets.forEach(this::close);
    }
}
