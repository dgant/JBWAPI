package bwapi;

import java.util.Objects;

/**
 * Client class to connect to the game with.
 */
public class BWClient {
    private static final String version = "0.8.2";
    private final BWEventListener eventListener;

    private Client client;
    private EventHandler handler;


    public BWClient(final BWEventListener eventListener) {
        Objects.requireNonNull(eventListener);
        this.eventListener = eventListener;
    }

    /**
     * Get the {@link Game} instance of the currently running game.
     */
    public Game getGame() {
        return handler == null ? null : handler.getGame();
    }

    /**
     * Returns the JBWAPI version (NOT the BWAPI version)
     */
    public String version() {
        return version;
    }

    /**
     * Start the game.
     */
    public void startGame() {
        while (client == null) {
            try {
                client = new Client();
            } catch (final Exception t) {
                System.err.println("Game table mapping not found.");
                try {
                    Thread.sleep(1000);
                } catch (final Exception ignored) {
                }
            }
        }

        handler = new EventHandler(eventListener, client);

        try {
            while (!client.getData().isInGame()) {
                client.update(handler);
            }
            while (client.getData().isInGame()) {
                client.update(handler);
            }
        } catch (final Exception exception) {
            exception.printStackTrace();
        }
    }
}