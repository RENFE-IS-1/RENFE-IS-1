package modelo;

public class ParadaHorario implements Comparable<ParadaHorario> {
    private String tripId;
    private int arrivalTime;
    private int departureTime;
    private String stopId;
    private int stopSequence;

    public ParadaHorario(String tripId, int arrivalTime, int departureTime, String stopId, int stopSequence) {
        this.tripId = tripId;
        this.arrivalTime = arrivalTime;
        this.departureTime = departureTime;
        this.stopId = stopId;
        this.stopSequence = stopSequence;
    }

    public String getTripId() { return tripId; }
    public int getArrivalTime() { return arrivalTime; }
    public int getDepartureTime() { return departureTime; }
    public String getStopId() { return stopId; }
    public int getStopSequence() { return stopSequence; }

    @Override
    public int compareTo(ParadaHorario o) {
        return Integer.compare(this.arrivalTime, o.arrivalTime);
    }
}