package modelo;

import java.util.List;
import com.fasterxml.jackson.annotation.JsonProperty;

public class GtfsRtJsonDto {
    private List<Entity> entity;

    public List<Entity> getEntity() { return entity; }
    public void setEntity(List<Entity> entity) { this.entity = entity; }

    public static class Entity {
        private String id;
        @JsonProperty("trip_update")
        private TripUpdate tripUpdate;
        private VehiclePosition vehicle;
        private Alert alert;

        public String getId() { return id; }
        public void setId(String id) { this.id = id; }
        public TripUpdate getTripUpdate() { return tripUpdate; }
        public void setTripUpdate(TripUpdate tripUpdate) { this.tripUpdate = tripUpdate; }
        public VehiclePosition getVehicle() { return vehicle; }
        public void setVehicle(VehiclePosition vehicle) { this.vehicle = vehicle; }
        public Alert getAlert() { return alert; }
        public void setAlert(Alert alert) { this.alert = alert; }
    }

    public static class TripUpdate {
        private Trip trip;
        @JsonProperty("stop_time_update")
        private List<StopTimeUpdate> stopTimeUpdate;

        public Trip getTrip() { return trip; }
        public void setTrip(Trip trip) { this.trip = trip; }
        public List<StopTimeUpdate> getStopTimeUpdate() { return stopTimeUpdate; }
        public void setStopTimeUpdate(List<StopTimeUpdate> stopTimeUpdate) { this.stopTimeUpdate = stopTimeUpdate; }
    }

    public static class VehiclePosition {
        private Trip trip;
        private Position position;

        public Trip getTrip() { return trip; }
        public void setTrip(Trip trip) { this.trip = trip; }
        public Position getPosition() { return position; }
        public void setPosition(Position position) { this.position = position; }
    }

    public static class Alert {
        @JsonProperty("header_text")
        private TranslatedString headerText;
        @JsonProperty("description_text")
        private TranslatedString descriptionText;

        public TranslatedString getHeaderText() { return headerText; }
        public void setHeaderText(TranslatedString headerText) { this.headerText = headerText; }
        public TranslatedString getDescriptionText() { return descriptionText; }
        public void setDescriptionText(TranslatedString descriptionText) { this.descriptionText = descriptionText; }
    }

    public static class TranslatedString {
        private List<Translation> translation;

        public List<Translation> getTranslation() { return translation; }
        public void setTranslation(List<Translation> translation) { this.translation = translation; }
    }

    public static class Translation {
        private String text;
        private String language;

        public String getText() { return text; }
        public void setText(String text) { this.text = text; }
        public String getLanguage() { return language; }
        public void setLanguage(String language) { this.language = language; }
    }

    public static class Trip {
        @JsonProperty("trip_id")
        private String tripId;

        public String getTripId() { return tripId; }
        public void setTripId(String tripId) { this.tripId = tripId; }
    }

    public static class StopTimeUpdate {
        private Arrival arrival;
        private Arrival departure;

        public Arrival getArrival() { return arrival; }
        public void setArrival(Arrival arrival) { this.arrival = arrival; }
        public Arrival getDeparture() { return departure; }
        public void setDeparture(Arrival departure) { this.departure = departure; }
    }

    public static class Arrival {
        private int delay;

        public int getDelay() { return delay; }
        public void setDelay(int delay) { this.delay = delay; }
    }

    public static class Position {
        private double latitude;
        private double longitude;

        public double getLatitude() { return latitude; }
        public void setLatitude(double latitude) { this.latitude = latitude; }
        public double getLongitude() { return longitude; }
        public void setLongitude(double longitude) { this.longitude = longitude; }
    }
}