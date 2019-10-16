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

import bwapi.ClientData.Command;
import bwapi.ClientData.GameData;
import bwapi.ClientData.Shape;
import com.sun.jna.Native;
import com.sun.jna.platform.win32.Kernel32;
import com.sun.jna.win32.W32APIOptions;

import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;
import java.util.List;

class Client {
    private static final int READ_WRITE = 0x1 | 0x2 | 0x4;
    private static final int GAME_SIZE = 4 // ServerProcID
            + 4 // IsConnected
            + 4 // LastKeepAliveTime
            ;
    private static final List<Integer> SUPPORTED_BWAPI_VERSIONS = Arrays.asList(10003);
    static final int MAX_COUNT = 19999;

    private static final int maxNumGames = 8;
    private static final int gameTableSize = GAME_SIZE * maxNumGames;
    private RandomAccessFile pipe;
    ClientData client;

    Client() throws Exception {
        final ByteBuffer gameList = Kernel32.INSTANCE.MapViewOfFile(MappingKernel.INSTANCE.OpenFileMapping(READ_WRITE, false, "Local\\bwapi_shared_memory_game_list"), READ_WRITE, 0, 0, gameTableSize).getByteBuffer(0, GAME_SIZE * 8);
        gameList.order(ByteOrder.LITTLE_ENDIAN);
        for (int i = 0; i < 8; ++i) {
            final int procID = gameList.getInt(GAME_SIZE * i);
            final boolean connected = gameList.get(GAME_SIZE * i + 4) != 0;

            if (procID != 0 && !connected) {
                try {
                    this.connect(procID);
                    return;
                } catch (final Exception e) {
                    System.err.println(e.getMessage());
                }
            }
        }
        throw new Exception("All servers busy!");
    }

    /**
     * For test purposes only
     */
    Client(ByteBuffer buffer) {
        client = new ClientData(buffer);
    }

    public GameData getData() {
        return client.data;
    }

    private void connect(final int procID) throws Exception {
        pipe = new RandomAccessFile("\\\\.\\pipe\\bwapi_pipe_" + procID, "rw");

        byte code = 1;
        while (code != 2) {
            code = pipe.readByte();
        }

        final ByteBuffer sharedMemory = Kernel32.INSTANCE.MapViewOfFile(MappingKernel.INSTANCE
                        .OpenFileMapping(READ_WRITE, false, "Local\\bwapi_shared_memory_" + procID), READ_WRITE,
                0, 0, GameData.SIZE).getByteBuffer(0, GameData.SIZE);

        client = new ClientData(sharedMemory);

        final int clientVersion = getData().getClient_version();
        if (!SUPPORTED_BWAPI_VERSIONS.contains(clientVersion)) {
            throw new Exception("BWAPI version mismatch, expected one of: " + SUPPORTED_BWAPI_VERSIONS + ", got: " + clientVersion);
        }

        System.out.println("Connected to BWAPI@" + procID + " with version " + clientVersion + ": " + getData().getRevision());
    }

    void update(final EventHandler handler) throws Exception {
        byte code = 1;
        pipe.writeByte(code);
        while (code != 2) {
            code = pipe.readByte();
        }
        for (int i = 0; i < getData().getEventCount(); ++i) {
            handler.operation(getData().getEvents(i));
        }
    }

    interface MappingKernel extends Kernel32 {
        MappingKernel INSTANCE = Native.load(MappingKernel.class, W32APIOptions.DEFAULT_OPTIONS);

        HANDLE OpenFileMapping(int desiredAccess, boolean inherit, String name);
    }

    public interface EventHandler {
        void operation(ClientData.Event event);
    }


    String eventString(final int s) {
        return getData().getEventStrings(s);
    }

    int addString(final String s) {
        int stringCount = getData().getStringCount();
        if (stringCount >= MAX_COUNT) throw new IllegalStateException("Too many strings!");
        getData().setStringCount(stringCount + 1);
        getData().setStrings(stringCount, s);
        return stringCount;
    }

    Shape addShape() {
        int shapeCount = getData().getShapeCount();
        if (shapeCount >= MAX_COUNT) throw new IllegalStateException("Too many shapes!");
        getData().setShapeCount(shapeCount + 1);
        return getData().getShapes(shapeCount);
    }

    Command addCommand() {
        final int commandCount = getData().getCommandCount();
        if (commandCount >= MAX_COUNT) throw new IllegalStateException("Too many commands!");
        getData().setCommandCount(commandCount + 1);
        return getData().getCommands(commandCount);
    }

    ClientData.UnitCommand addUnitCommand() {
        int unitCommandCount = getData().getUnitCommandCount();
        if (unitCommandCount >= MAX_COUNT) throw new IllegalStateException("Too many unit commands!");
        getData().setUnitCommandCount(unitCommandCount + 1);
        return getData().getUnitCommands(unitCommandCount);
    }
}
