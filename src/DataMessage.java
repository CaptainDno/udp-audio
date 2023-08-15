import javax.sound.sampled.AudioFormat;
import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;

public class DataMessage {
    public final long frame;
    public final byte[] data;

    public DataMessage(long frame, byte[] data) {
        this.frame = frame;
        this.data = data;
    }

    public static DataMessage fromBytes(byte[] bytes, int length) throws IOException {
        try (ByteArrayInputStream bis = new ByteArrayInputStream(bytes)){
            DataInputStream dis = new DataInputStream(bis);
            byte header = dis.readByte();
            if (header != Headers.DATA.value) return null;
            long frame = dis.readLong();
            byte[] data = new byte[length - MediaServer.HEADER_SIZE];
            dis.read(data, 0, data.length);
            return new DataMessage(frame, data);
        }
    }
}
