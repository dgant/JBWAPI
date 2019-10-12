package bwapi;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.io.Output;

import java.io.*;
import java.nio.ByteBuffer;
import java.util.*;
import java.util.zip.DeflaterOutputStream;

import static java.util.Calendar.*;

class DumpGameState extends DefaultBWListener {
    final BWClient bwClient;
    final Calendar cal = Calendar.getInstance(TimeZone.getDefault());

    String name;
    Game game;

    DumpGameState() {
        bwClient = new BWClient(this);
        bwClient.startGame();
    }

    public void onStart() {
        game = bwClient.getGame();

        name = "test/resources/" + cal.get(YEAR) + "-" + (cal.get(MONTH) + 1) + "-" +  cal.get(DATE) + "_" + game.mapFileName();

        try {
            dumpGame();
            dumpBuffer();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void dumpBuffer() throws IOException {
        ByteBuffer buf = game.client.client.buffer.getBuffer();
        buf.rewind();
        byte[] bytearr = new byte[buf.remaining()];
        buf.get(bytearr);

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        DeflaterOutputStream zout = new DeflaterOutputStream(out);
        zout.write(bytearr);
        zout.flush();
        zout.close();
        byte[] compressed = out.toByteArray();
        File file = new File(name +"_buffer.bin");
        FileOutputStream fos = new FileOutputStream(file, false);
        fos.write(compressed);
        fos.close();
    }

    private void dumpGame() throws FileNotFoundException {

        Kryo kryo = new Kryo();
        kryo.setReferences(true);
        kryo.setRegistrationRequired(false);
        Output output = new Output(new DeflaterOutputStream(new FileOutputStream(name +"_game.bin")));
        kryo.writeObject(output, game);
        output.close();
    }

    public static void main(String[] args) {
        new DumpGameState();
    }
}
