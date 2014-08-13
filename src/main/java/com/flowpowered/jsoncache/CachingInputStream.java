/*
 * This file is part of Flow JSON Cache, licensed under the MIT License (MIT).
 *
 * Copyright (c) 2012 Spout LLC <https://spout.org/>
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package com.flowpowered.jsoncache;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;

/**
 * An InputStream implementation that reads from another InputStream while caching the data to an OutputStream.
 */
public class CachingInputStream extends InputStream {
    private InputStream readFrom = null;
    private OutputStream writeTo = null;
    private ByteBuffer buffer = ByteBuffer.allocate(1024);
    private Runnable onFinish = null;
    private Runnable onFailure = null;
    private long expectedBytes = -1;
    private long receivedBytes = 0;
    private boolean closed = false;
    private boolean exception = false;

    /**
     * Creates a new caching InputStream.
     *
     * @param readFrom the stream to read data from.
     * @param writeTo the stream to cache the read data to.
     */
    public CachingInputStream(InputStream readFrom, OutputStream writeTo) {
        super();
        this.readFrom = readFrom;
        this.writeTo = writeTo;
    }

    public void setOnFinish(Runnable onFinish) {
        this.onFinish = onFinish;
    }

    public void setOnFailure(Runnable onFailure) {
        this.onFailure = onFailure;
    }

    public synchronized void setExpectedBytes(long expectedBytes) {
        this.expectedBytes = expectedBytes;
    }

    public synchronized long getReceivedBytes() {
        return receivedBytes;
    }

    public long getExpectedBytes() {
        return expectedBytes;
    }

    public synchronized int read() throws IOException {
        int data = Integer.MAX_VALUE;
        try {
            data = readFrom.read();
            receivedBytes++;
            if (data == -1) {
                receivedBytes--;
                return data; // This is the end of the stream, no need to cache anything.
            }
            if (!buffer.hasRemaining()) { // Buffer is full.
                // Write buffer to output.
                writeTo.write(buffer.array(), 0, buffer.capacity());
                // Reset buffer.
                buffer.position(0);
            }
            buffer.put((byte) data);
            return data;
        } catch (IOException e) {
            exception = true;
            throw e;
        }
    }

    /**
     * Closes the stream it reads from and the stream it caches to.
     */
    @Override
    public void close() throws IOException {
        if (!closed) {
            closed = true;

            readFrom.close();
            super.close();

            // Write remaining stuff to output.
            try {
                if (buffer != null) {
                    writeTo.write(buffer.array(), 0, buffer.position());
                    buffer = null;
                }
                writeTo.close();
            } catch (IOException e) {
                exception = true;
                throw e;
            } finally {
                if (expectedBytes != -1 || !exception) {
                    if (expectedBytes == receivedBytes || (expectedBytes == -1 || !exception)) {
                        if (onFinish != null) {
                            try {
                                onFinish.run();
                            } catch (Exception e) {
                                e.printStackTrace();
                            }
                        }
                    } else {
                        if (onFailure != null) {
                            try {
                                onFailure.run();
                            } catch (Exception e) {
                                e.printStackTrace();
                            }
                        }
                        throw new IOException("File was not completely downloaded! Expected=" + getExpectedBytes() + " actual=" + getReceivedBytes());
                    }
                }
            }
        }
    }

    @Override
    public int available() throws IOException {
        return readFrom.available();
    }

    @Override
    public synchronized void mark(int readlimit) {
        readFrom.mark(readlimit);
    }

    @Override
    public boolean markSupported() {
        return readFrom.markSupported();
    }

    @Override
    public synchronized void reset() throws IOException {
        readFrom.reset();
    }

    @Override
    public long skip(long n) throws IOException {
        return readFrom.skip(n);
    }
}
