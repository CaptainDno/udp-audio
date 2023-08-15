import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.SourceDataLine;
import java.util.Arrays;
import java.util.Comparator;
import java.util.concurrent.PriorityBlockingQueue;
import java.util.concurrent.TimeUnit;

public class Player implements Runnable{
    final SourceDataLine dataLine;
    final long playbackStartTime;
    final PriorityBlockingQueue<DataMessage> queue = new PriorityBlockingQueue<DataMessage>(100, Comparator.comparingLong(msg -> msg.frame));
    long expectedFrame;
    final float frameRate;
    final byte[] silentFrame;

    final int frameSize;
    public Player(InfoMessage message, int bufferSize, long expectedFrame, long playbackStartTime) throws LineUnavailableException {
        System.out.printf("Playback start after %.2f seconds\n", (playbackStartTime - System.currentTimeMillis()) / 1_000d);
        final DataLine.Info info = new DataLine.Info(SourceDataLine.class, message.serializableAudioFormat, bufferSize);
        this.dataLine = (SourceDataLine) AudioSystem.getLine(info);
        dataLine.open(message.serializableAudioFormat, bufferSize);
        this.playbackStartTime = playbackStartTime;
        this.expectedFrame = expectedFrame;
        byte[] silentFrame = new byte[message.serializableAudioFormat.getFrameSize()];
        Arrays.fill(silentFrame, (byte) 0);
        this.silentFrame = silentFrame;
        this.frameRate = message.serializableAudioFormat.getFrameRate();
        this.frameSize = message.serializableAudioFormat.getFrameSize();
    }

    @Override
    public void run() {
        try {
            // Take from queue and write to buffer
            while(true){
                long time = System.currentTimeMillis();
                if (time < playbackStartTime) {
                    while (dataLine.available() > frameRate / 5) pipe();
                    while (time < playbackStartTime){
                        time = System.currentTimeMillis();
                        if (playbackStartTime - time > 100) Thread.sleep(20);
                    }
                    dataLine.start();
                    System.out.printf("Started playing with delay of %d ms\n", System.currentTimeMillis() - playbackStartTime);
                }
                else {
                    if (!pipe()) return;
                }
            }
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }
    boolean pipe() throws InterruptedException {
        DataMessage next = queue.poll(1, TimeUnit.SECONDS);
        if (next == null) {
            if (!dataLine.isRunning()){
                stop();
                return false;
            }
            return true;
        }
        //If we receive old frames - ignore them, it is too late to write them in buffer
        if (expectedFrame > next.frame){
            System.out.println("OldFrame");
            return true;
        }
        //Replace missing frames with prepared silent frame
        while (expectedFrame < next.frame){
            dataLine.write(silentFrame, silentFrame.length, 0);
            expectedFrame++;
        }
        dataLine.write(next.data, 0, next.data.length);
        expectedFrame += next.data.length / frameSize;
        return true;
    }
    public void stop(){
        dataLine.stop();
        dataLine.flush();
        dataLine.close();
    }
}
