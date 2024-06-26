
import javax.sound.sampled.LineUnavailableException;
import java.io.IOException;
import java.net.*;

public class MediaClient implements Runnable{
    private final MulticastSocket socket;
    private Thread playerThread = null;
    private Player player = null;

    private InfoMessage currentInfo = null;

    private final byte[] buffer;

    private boolean running = false;

    public MediaClient(InetAddress group, NetworkInterface i, int port, int bufferSize) throws IOException {
        socket = new MulticastSocket(port);
        socket.setReceiveBufferSize(bufferSize * 100);
        socket.joinGroup(new InetSocketAddress(group, 0), i);
        buffer = new byte[bufferSize];
    }

    @Override
    public void run() {
        System.out.println("[CLIENT] Running new player client");
        while (true){
            DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
            try {
                socket.receive(packet);
                byte head = buffer[0];

                // If this is an info message
                if (head == Headers.INFO.value){
                    InfoMessage msg = InfoMessage.fromBytes(buffer);
                    assert msg != null;
                    // If nothing changed, we keep playing
                    if (msg.equals(currentInfo)) continue;
                    System.out.printf("[CLIENT] Audio settings changed\n%s\n", msg.toString());
                    currentInfo = msg;
                    if (running){
                        player.stop();
                        playerThread.interrupt();
                        player = null;
                        playerThread = null;
                        running = false;
                    }
                } else if (head == Headers.DATA.value) {
                    DataMessage message = DataMessage.fromBytes(buffer, packet.getLength());
                    // If player is running, write to queue
                    if (running) {
                        if (playerThread.isAlive()) player.queue.offer(message);
                        else {
                            stopPlayer();
                            System.out.println("[CLIENT] Stopping player because no data is available");
                        }
                    } else if (currentInfo != null) {
                        assert message != null;
                        int frameRateInteger = Math.round(currentInfo.serializableAudioFormat.getFrameRate());
                        if (message.frame % frameRateInteger != 0) continue;

                        long seconds = message.frame / frameRateInteger;
                        System.out.printf("[CLIENT] Frame %d position: %d seconds\n", message.frame, seconds);
                        long millis = seconds * 1000 + currentInfo.playbackStartTime;
                        // Create new player
                        this.player = new Player(
                                currentInfo,
                                Math.round(currentInfo.serializableAudioFormat.getFrameRate() * currentInfo.serializableAudioFormat.getFrameSize()),
                                message.frame,
                                millis
                        );
                        player.queue.offer(message);
                        this.playerThread = new Thread(player, "Player");
                        playerThread.setPriority(Thread.MAX_PRIORITY);
                        playerThread.start();
                        running = true;
                    }
                }
            } catch (IOException | LineUnavailableException e) {
                throw new RuntimeException(e);
            }
        }
    }
    void stopPlayer(){
        player.stop();
        playerThread.interrupt();
        player = null;
        playerThread = null;
        running = false;
    }
}
