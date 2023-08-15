public enum Headers {
    INFO(0b10000000),
    DATA(0b00000000);

    public final byte value;

    Headers(int value){
        this.value = (byte) value;
    }
}
