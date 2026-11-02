package bwapi;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.lang.reflect.Field;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class ClientProcessSelectionTest {
    private String previousProcessId;
    private Client client;
    private ClientConnection connection;
    private WrappedBuffer table;

    @Before
    public void setup() throws Exception {
        previousProcessId = System.getProperty("bwapi.serverProcessId");
        System.clearProperty("bwapi.serverProcessId");
        BWClient owner = mock(BWClient.class);
        when(owner.getConfiguration()).thenReturn(BWClientConfiguration.DEFAULT);
        client = new Client(owner);
        connection = mock(ClientConnection.class);
        Field connector = Client.class.getDeclaredField("clientConnector");
        connector.setAccessible(true);
        connector.set(client, connection);
        table = new WrappedBuffer(GameTable.SIZE);
        when(connection.getGameTable()).thenReturn(table);
        WrappedBuffer game = GameBuilder.binToBuffer(GameBuilder.DEFAULT_BUFFER_PATH);
        when(connection.getSharedMemory(anyInt())).thenReturn(game);
        instance(0, 101, false, 20);
        instance(1, 202, false, 10);
    }

    @After
    public void restoreProcessId() {
        if (previousProcessId == null) {
            System.clearProperty("bwapi.serverProcessId");
        } else {
            System.setProperty("bwapi.serverProcessId", previousProcessId);
        }
    }

    private void instance(int index, int processId, boolean connected, int lastKeepAlive) {
        table.putInt(index * GameInstance.SIZE, processId);
        table.putInt(index * GameInstance.SIZE + 4, connected ? 1 : 0);
        table.putInt(index * GameInstance.SIZE + 8, lastKeepAlive);
    }

    @Test
    public void explicitProcessTakesPrecedenceOverAutomaticSelection() throws Exception {
        System.setProperty("bwapi.serverProcessId", "101");
        assertTrue(client.connect());
        verify(connection).getSharedMemory(101);
        verify(connection).connectSharedLock(101);
        verify(connection, never()).getSharedMemory(202);
    }

    @Test
    public void automaticSelectionChoosesOldestUnconnectedServer() {
        assertTrue(client.connect());
        verify(connection).getSharedMemory(202);
    }

    @Test
    public void automaticSelectionSkipsConnectedServers() {
        instance(1, 202, true, 10);
        assertTrue(client.connect());
        verify(connection).getSharedMemory(101);
    }

    @Test
    public void unavailableExplicitProcessDoesNotFallBackToAnotherServer() {
        System.setProperty("bwapi.serverProcessId", "303");
        when(connection.getSharedMemory(303)).thenThrow(new IllegalStateException("Server unavailable"));
        assertFalse(client.connect());
        verify(connection).getSharedMemory(303);
        verify(connection, never()).getSharedMemory(101);
        verify(connection, never()).getSharedMemory(202);
    }

    @Test
    public void zeroProcessIdWithNoAvailableServerDoesNotAttemptConnection() {
        System.setProperty("bwapi.serverProcessId", "0");
        instance(0, 0, false, 0);
        instance(1, 0, false, 0);
        assertFalse(client.connect());
        verify(connection, never()).getSharedMemory(anyInt());
    }

    @Test
    public void missingGameTableDoesNotAttemptConnection() {
        when(connection.getGameTable()).thenThrow(new IllegalStateException("No game table"));
        assertFalse(client.connect());
        verify(connection, never()).getSharedMemory(anyInt());
    }
}
