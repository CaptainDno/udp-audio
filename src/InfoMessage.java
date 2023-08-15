import javax.sound.sampled.AudioFormat;
import java.io.*;
import java.util.Objects;

public class InfoMessage implements Message{

    public final long playbackStartTime;
    public final AudioFormat serializableAudioFormat;

    public InfoMessage(long playbackStartTime, AudioFormat format){
        this.playbackStartTime = playbackStartTime;
        this.serializableAudioFormat = format;
    }

    @Override
    public byte[] toBytes() throws IOException {
        try (ByteArrayOutputStream bos = new ByteArrayOutputStream()){
            DataOutputStream dos = new DataOutputStream(bos);
            dos.writeByte(MediaServer.Headers.INFO.value);
            dos.writeLong(playbackStartTime);
            dos.writeBoolean(serializableAudioFormat.isBigEndian());
            dos.writeInt(serializableAudioFormat.getChannels());
            dos.writeUTF(serializableAudioFormat.getEncoding().toString());
            dos.writeFloat(serializableAudioFormat.getFrameRate());
            dos.writeInt(serializableAudioFormat.getFrameSize());
            dos.writeFloat(serializableAudioFormat.getSampleRate());
            dos.writeInt(serializableAudioFormat.getSampleSizeInBits());
            return bos.toByteArray();
        }
    }

    public static InfoMessage fromBytes(byte[] bytes) throws IOException {
        try (ByteArrayInputStream bis = new ByteArrayInputStream(bytes)){
            DataInputStream dis = new DataInputStream(bis);
            byte header = dis.readByte();
            if (header != Headers.INFO.value) return null;

            long playbackStartTime = dis.readLong();
            boolean isBigEndian = dis.readBoolean();
            int channels = dis.readInt();
            AudioFormat.Encoding encoding = new AudioFormat.Encoding(dis.readUTF());
            float frameRate = dis.readFloat();
            int frameSize = dis.readInt();
            float sampleRate = dis.readFloat();
            int sampleSize = dis.readInt();

            AudioFormat format = new AudioFormat(
                    encoding,
                    sampleRate,
                    sampleSize,
                    channels,
                    frameSize,
                    frameRate,
                    isBigEndian
            );
            return new InfoMessage(playbackStartTime, format);
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        InfoMessage that = (InfoMessage) o;
        return playbackStartTime == that.playbackStartTime;
    }

    @Override
    public int hashCode() {
        return Objects.hash(playbackStartTime);
    }

    @Override
    public String toString() {
        return "InfoMessage{" +
                "playbackStartTime=" + playbackStartTime +
                ", serializableAudioFormat=" + serializableAudioFormat.toString() +
                '}';
    }
}
