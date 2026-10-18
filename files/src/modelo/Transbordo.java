package modelo;

public class Transbordo {
    private String fromStopId;
    private String toStopId;
    private int transferType;
    private int minTransferTime;

    public Transbordo(String fromStopId, String toStopId, int transferType, int minTransferTime) {
        this.fromStopId = fromStopId;
        this.toStopId = toStopId;
        this.transferType = transferType;
        this.minTransferTime = minTransferTime;
    }

    public String getFromStopId() { return fromStopId; }
    public String getToStopId() { return toStopId; }
    public int getTransferType() { return transferType; }
    public int getMinTransferTime() { return minTransferTime; }
}