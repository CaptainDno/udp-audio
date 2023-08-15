import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.UnsupportedAudioFileException;
import java.io.*;
import java.net.*;
import java.time.Duration;
import java.util.Enumeration;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

// Args:
// max payload size in bytes
// path to file
// multicast address
// port

// Press Shift twice to open the Search Everywhere dialog and type `show whitespaces`,
// then press Enter. You can now see whitespace characters in your code.
public class Main {
    static final int HEADER_SIZE = Long.BYTES + Byte.BYTES;

    static final byte DATA_HEADER = 0b00000000;

    public static void main(String[] args) throws IOException, UnsupportedAudioFileException {

        System.out.println("Starting...");

        NetworkInterface networkInterface = getNetworkInterface();

        // How much audio data will we put in one packet
        int maxPayloadSize = Integer.parseInt(args[0]);
        // Path to audio file
        String filepath = args[1];
        // Multicast address
        InetAddress target = InetAddress.getByName(args[2]);

        int port = Integer.parseInt(args[3]);

        for (int i = 0; i < 1; i++){
            MediaClient client = new MediaClient(target, networkInterface, port, maxPayloadSize * 10);
            Thread thread = new Thread(client);
            thread.setName("Media client");
            thread.start();
        }
        System.out.println("Opening file stream");
        AudioInputStream audioStream = AudioSystem.getAudioInputStream(new File(filepath));
        MediaServer server = new MediaServer(audioStream, audioStream.getFormat(), maxPayloadSize, System.currentTimeMillis() + Duration.ofSeconds(1).toMillis(), target, port);
        ScheduledExecutorService executorService = Executors.newSingleThreadScheduledExecutor();
        executorService.scheduleAtFixedRate(server, 0, 1, TimeUnit.SECONDS);

    }

    private static NetworkInterface getNetworkInterface() throws SocketException, UnknownHostException {
        final Enumeration<NetworkInterface> netifs = NetworkInterface.getNetworkInterfaces();

        // hostname is passed to your method
        InetAddress myAddr = InetAddress.getLocalHost();

        NetworkInterface networkInterface = null;

        while (netifs.hasMoreElements()) {
            NetworkInterface ni = netifs.nextElement();
            Enumeration<InetAddress> inAddrs = ni.getInetAddresses();
            while (inAddrs.hasMoreElements()) {
                InetAddress inAddr = inAddrs.nextElement();
                if (inAddr.equals(myAddr)) {
                    networkInterface = ni;
                    break;
                }
            }
        }
        return networkInterface;
    }
}