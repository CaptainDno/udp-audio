import java.io.IOException;
import java.net.DatagramPacket;

public interface Message {
    byte[] toBytes() throws IOException;
}
