package bwapi;

import bwapi.Game;
import bwapi.Unit;
import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.io.Input;
import org.junit.Before;
import org.junit.Test;
import org.objenesis.strategy.StdInstantiatorStrategy;
import sun.nio.ch.DirectBuffer;

import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.zip.InflaterInputStream;
import java.util.zip.InflaterOutputStream;

import static org.junit.Assert.*;

public class BWEMTest {
    private Kryo kryo;

    @Before
    public void setup() {
        kryo = new Kryo();
        kryo.setReferences(true);
        kryo.setRegistrationRequired(false);
        kryo.setInstantiatorStrategy(new StdInstantiatorStrategy());
    }

    private Game loadGameState(String location) throws IOException {
        Input input = new Input(new InflaterInputStream(new FileInputStream(location + "_game.bin")));
        Game game = kryo.readObject(input, Game.class);
        input.close();

        byte[] compressedBytes = Files.readAllBytes(Paths.get(location + "_buffer.bin"));
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        InflaterOutputStream zin = new InflaterOutputStream(out);
        zin.write(compressedBytes);
        zin.flush();
        zin.close();
        byte[] bytes = out.toByteArray();
        ByteBuffer buffer = ByteBuffer.allocateDirect(bytes.length);
        buffer.put(bytes);
        game.client.client.buffer = new WrappedBuffer(buffer);
        System.out.println(game.client.client.buffer.getInt(0));
        return game;
    }

    @Test
    public void simple() throws IOException {
        Game game = loadGameState("src/test/resources/2019-10-12_(2)Hitchhiker1.1.scx");
        System.out.println(game.mapFileName());
        for (Unit unit : game.self().getUnits()) {
            System.out.println(unit.getID());
        }
    }
}
