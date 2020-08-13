/*
MIT License

Copyright (c) 2018 Hannes Bredberg
Modified work Copyright (c) 2018 Jasper

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
*/

package bwapi;

import com.sun.jna.Platform;
import sun.nio.ch.DirectBuffer;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Circular buffer of game states.
 */
class FrameBuffer {
    private static final int BUFFER_SIZE = ClientData.GameData.SIZE;

    private ByteBuffer liveData;
    private PerformanceMetrics performanceMetrics;
    private int size;
    private int stepGame = 0;
    private int stepBot = 0;
    private ArrayList<ByteBuffer> dataBuffer = new ArrayList<>();

    // Synchronization locks
    private final Lock lockWrite = new ReentrantLock();
    final Lock lockSize = new ReentrantLock();
    final Condition conditionSize = lockSize.newCondition();

    FrameBuffer(int size) {
        this.size = size;
        while(dataBuffer.size() < size) {
            dataBuffer.add(ByteBuffer.allocateDirect(BUFFER_SIZE));
        }
    }

    /**
     * Resets for a new game
     */
    void initialize(ByteBuffer liveData, PerformanceMetrics performanceMetrics) {
        this.liveData = liveData;
        this.performanceMetrics = performanceMetrics;
        stepGame = 0;
        stepBot = 0;
    }

    /**
     * @return The number of frames currently buffered ahead of the bot's current frame
     */
    synchronized int framesBuffered() {
        return stepGame - stepBot;
    }

    /**
     * @return Whether the frame buffer is empty and has no frames available for the bot to consume.
     */
    boolean empty() {
        lockSize.lock();
        try {
            return framesBuffered() <= 0;
        }  finally {
            lockSize.unlock();
        }
    }

    /**
     * @return Whether the frame buffer is full and can not buffer any additional frames.
     * When the frame buffer is full, JBWAPI must wait for the bot to complete a frame before returning control to StarCraft.
     */
    boolean full() {
        lockSize.lock();
        try {
            return framesBuffered() >= size - 1;
        } finally {
            lockSize.unlock();
        }
    }

    private int indexGame() {
        return stepGame % size;
    }

    private int indexBot() {
        return stepBot % size;
    }

    /**
     * Copy dataBuffer from shared memory into the head of the frame buffer.
     */
    void enqueueFrame() {
        lockWrite.lock();
        try {
            lockSize.lock();
            try {
                while (full()) {
                    performanceMetrics.intentionallyBlocking.startTiming();
                    conditionSize.awaitUninterruptibly();
                }
                performanceMetrics.intentionallyBlocking.stopTiming();
            } finally { lockSize.unlock(); };

            performanceMetrics.copyingToBuffer.time(() -> {
                ByteBuffer dataTarget = dataBuffer.get(indexGame());
                copyBuffer(liveData, dataTarget);
            });

            lockSize.lock();
            try {
                ++stepGame;
                conditionSize.signalAll();
            } finally { lockSize.unlock(); }
        } finally { lockWrite.unlock(); }
    }

    /**
     * Peeks the front-most value in the buffer.
     */
    ByteBuffer peek() {
        lockSize.lock();
        try {
            while(empty()) conditionSize.awaitUninterruptibly();
            return dataBuffer.get(indexBot());
        } finally { lockSize.unlock(); }
    }

    /**
     * Removes the front-most frame in the buffer.
     */
    void dequeue() {
        lockSize.lock();
        try {
            while(empty()) conditionSize.awaitUninterruptibly();
            ++stepBot;
            conditionSize.signalAll();
        } finally { lockSize.unlock(); }
    }

    void copyBuffer(ByteBuffer source, ByteBuffer destination) {
        /*
        The speed at which we copy data into the frame buffer is a major cost of JBWAPI's asynchronous operation.
        Copy times observed in the wild range from 2.6ms - 12ms.

        The normal Java way to execute this copy is via ByteBuffer.put(), which has reasonably good performance characteristics.
        Some experiments in 64-bit JRE have shown that using a native memcpy achieves a 35% speedup.
        Some experiments in 32-bit JRE show no difference in performance.

        So, speculatively, we attempt to do a native memcpy.
         */
        long addressSource = ((DirectBuffer) source).address();
        long addressDestination = ((DirectBuffer) destination).address();
        try {
            if (Platform.isWindows()) {
                if (Platform.is64Bit()) {
                    MSVCRT.INSTANCE.memcpy(addressDestination, addressSource, FrameBuffer.BUFFER_SIZE);
                    return;
                } else {
                    MSVCRT.INSTANCE.memcpy((int) addressDestination, (int) addressSource, FrameBuffer.BUFFER_SIZE);
                    return;
                }
            }
        }
        catch(Exception ignored) {}

        // There's no specific case where we expect to fail above,
        // but this is a safe fallback regardless,
        // and serves to document the known-good (and cross-platform, for BWAPI 5) way to executing the copy.
        source.rewind();
        destination.rewind();
        destination.put(liveData);
    }
}
