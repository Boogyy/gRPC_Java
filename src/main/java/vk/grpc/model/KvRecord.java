package vk.grpc.model;


public class KvRecord {
    private String key;
    private byte[] value;

    public void setValue(byte[] value) {
        this.value = value;
    }

    public void setKey(String key) {
        this.key = key;
    }

    public String getKey() {
        return this.key;
    }

    public byte[] getValue() {
        return this.value;
    }
}
