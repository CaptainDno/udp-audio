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
        System.out.printf("[PLAYER] Playback start after %.2f seconds\n", (playbackStartTime - System.currentTimeMillis()) / 1_000d);
        final DataLine.Info info = new DataLine.Info(SourceDataLine.class, message.serializableAudioFormat, bufferSize);
        this.dataLine = (SourceDataLine) AudioSystem.getLine(info);
        dataLine.open(message.serializableAudioFormat, bufferSize);
        dataLine.start();
        this.playbackStartTime = playbackStartTime;
        this.expectedFrame = expectedFrame;
        byte[] silentFrame = new byte[message.serializableAudioFormat.getFrameSize()];
        Arrays.fill(silentFrame, (byte) 0);
        this.silentFrame = silentFrame;
        this.frameRate = message.serializableAudioFormat.getFrameRate();
        this.frameSize = message.serializableAudioFormat.getFrameSize();
        dataLine.write(silentFrame, 0, silentFrame.length);
        dataLine.write(silentFrame, 0, silentFrame.length);
        dataLine.write(silentFrame, 0, silentFrame.length);
        dataLine.write(silentFrame, 0, silentFrame.length);
    }

    @Override
    public void run() {
        try {
            for(;;){
                long time = System.currentTimeMillis();
                if (time < playbackStartTime) {
                    // Technically it is not necessary to do this, but audio API has inconstant latency for some buffer writes (especially first one after a long break)
                    // So we write some frames kind of "warming up" everything to reduce latency
                    /*int frameCount = Math.round(Math.max(0, (playbackStartTime - time - 100)) / 1000f * frameRate);
                    for (int i = 0; i < frameCount; i++) {

                    }*/
                    while (time < playbackStartTime){
                        time = System.currentTimeMillis();
                    }
                    System.out.printf("[PLAYER] Started playing with delay of %d ms\n", System.currentTimeMillis() - playbackStartTime);
                }
                else {
                    // Take from queue and write to buffer
                    if (!pipe()) return;
                }
            }
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    int counter = 0;
    long silentFrames = 0;
    long oldFrames = 0;

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
            oldFrames++;
            return true;
        }
        //Replace missing frames with prepared silent frame
        while (expectedFrame < next.frame){
            dataLine.write(silentFrame, 0, silentFrame.length);
            expectedFrame++;
            silentFrames++;
        }
        dataLine.write(next.data, 0, next.data.length);
        expectedFrame += next.data.length / frameSize;;
        counter++;
        if (counter > 200) {
            System.out.printf("\r[PLAYER] Playing audio (%.2f sec);Frames total: %d Silent frames total: %d; Old frames total: %d; ", (System.currentTimeMillis() - playbackStartTime) / 1000f, expectedFrame, silentFrames, oldFrames);
            counter = 0;
        }
        return true;
    }
    public void stop(){
        dataLine.stop();
        dataLine.flush();
        dataLine.close();
    }
}
