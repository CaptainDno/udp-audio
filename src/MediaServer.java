import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.*;
import java.nio.ByteBuffer;
import java.time.Duration;

/**
 * Header structure:
 * bit|  meaning
 * 1     is info message
 * 2
 * 3
 * 4
 * 5
 * 6
 * 7
 * 8
 *
 * We just send data packets and sometimes info
 */
public class MediaServer implements Runnable{

    public static enum Headers {
        INFO(0b10000000),
        DATA(0b00000000);

        public final byte value;

        Headers(int value){
            this.value = (byte) value;
        }
    }

    private final DatagramSocket socket;
    private final AudioInputStream stream;
    private int additionalFrames;
    private long lastExecutionTime;
    private final int frameRate;
    private final int framesInChunk;
    private final int chunkSize;

    private int frameSize;

    private final int bufferSize;

    private final ByteBuffer buffer;

    private final InetAddress group;
    private final int port;

    private long nextFrame;

    private byte[] info;

    private int packetsSent;

    public static final int HEADER_SIZE = Byte.BYTES + Long.BYTES;

    public MediaServer(AudioInputStream stream, AudioFormat serializableAudioFormat, int maxPayloadSize, long playbackStartTime, InetAddress group, int port) throws IOException {
        this.stream = stream;

        frameRate = (int) serializableAudioFormat.getFrameRate();
        //Subtract first byte and size of long in bytes
        framesInChunk = Math.floorDiv(maxPayloadSize - HEADER_SIZE, serializableAudioFormat.getFrameSize());
        //Size of chunk to read from file
        chunkSize = framesInChunk * serializableAudioFormat.getFrameSize();
        //Buffer size for each datagram with data
        bufferSize = chunkSize + HEADER_SIZE;

        frameSize = serializableAudioFormat.getFrameSize();

        socket = new DatagramSocket();
        socket.setTrafficClass(15);
        socket.setSendBufferSize(maxPayloadSize * 5);
        socket.setOption(StandardSocketOptions.IP_MULTICAST_TTL, 20);

        buffer = ByteBuffer.allocate(bufferSize);

        this.port = port;
        this.group = group;
        //Prepare info message
        info = new InfoMessage(playbackStartTime, serializableAudioFormat).toBytes();
        lastExecutionTime = System.nanoTime();
        additionalFrames = frameRate;
    }

    @Override
    public void run() {
        DatagramPacket infoPacket = new DatagramPacket(info, info.length, group, port);
        try {
            // Sending packet with information about currently playing audio
            socket.send(infoPacket);
            packetsSent++;
            long currentTime = System.nanoTime();
            // We always will be late => should compensate for it once drift is bigger than chunk size
            double drift = (double) (currentTime - lastExecutionTime - Duration.ofSeconds(0).toNanos()) / 1_000_000_000;
            additionalFrames += (int) Math.round(frameRate * drift);
            long framesToSend = frameRate;
            //Check if we can send full chunk of additional samples
            if (additionalFrames >= framesInChunk) {
                framesToSend += framesInChunk;
                additionalFrames -= framesInChunk;
            }
            System.out.printf("Running server. Frames sent: %d  Total packets sent: %d\n", framesToSend, packetsSent);
            // Sending data
            for (; framesToSend > 0; framesToSend -= framesInChunk){
                int length = stream.read(buffer.array(), HEADER_SIZE, chunkSize);
                if (length == -1) break;
                // Set packet type to data
                buffer.put(0, Headers.DATA.value);
                // Set starting frame
                buffer.putLong(Byte.BYTES, nextFrame);
                // Send data packet
                DatagramPacket packet = new DatagramPacket(buffer.array(), length + HEADER_SIZE, group, port);
                for (int i = 0; i < 2; i++){
                    socket.send(packet);
                    packetsSent++;
                }

                nextFrame += length / frameSize;
            }

        } catch (IOException e) {
            throw new RuntimeException(e);
        }


    }
}
