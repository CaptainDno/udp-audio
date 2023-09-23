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
// -p or -b for play or broadcast respectively
// max payload size in bytes
// path to file
// multicast address
// port

// Press Shift twice to open the Search Everywhere dialog and type `show whitespaces`,
// then press Enter. You can now see whitespace characters in your code.
public class Main {

    public static void main(String[] args) throws IOException, UnsupportedAudioFileException {

        System.out.println("Starting...");

        String mode = args[0];

        NetworkInterface networkInterface = getNetworkInterface();

        // How much audio data will we put in one packet
        int maxPayloadSize = Integer.parseInt(args[1]);
        // Path to audio file
        String filepath = args[2];
        // Multicast address
        InetAddress target = InetAddress.getByName(args[3]);

        int port = Integer.parseInt(args[4]);
        if (mode.equals("-p")){
            MediaClient client = new MediaClient(target, networkInterface, port, maxPayloadSize * 5);
            Thread thread = new Thread(client);
            thread.setPriority(Thread.MAX_PRIORITY);
            thread.setName("Media client");
            thread.start();
        }
        if (mode.equals("-b")){
            System.out.println("Opening file stream");
            AudioInputStream audioStream = AudioSystem.getAudioInputStream(new File(filepath));
            MediaServer server = new MediaServer(audioStream, audioStream.getFormat(), maxPayloadSize, System.currentTimeMillis() + Duration.ofSeconds(1).toMillis(), target, port);
            ScheduledExecutorService executorService = Executors.newSingleThreadScheduledExecutor();
            executorService.scheduleAtFixedRate(server, 0, 1, TimeUnit.SECONDS);

        }
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