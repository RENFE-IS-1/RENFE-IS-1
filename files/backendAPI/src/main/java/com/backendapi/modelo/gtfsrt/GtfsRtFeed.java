package com.backendapi.modelo.gtfsrt;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public class GtfsRtFeed {

    @JsonProperty("entity")
    private List<FeedEntity> entity;

    public List<FeedEntity> getEntity() {
        return entity;
    }

    public void setEntity(List<FeedEntity> entity) {
        this.entity = entity;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class FeedEntity {
        @JsonProperty("id")
        private String id;

        @JsonProperty("trip_update")
        private TripUpdate tripUpdate;

        @JsonProperty("vehicle")
        private VehiclePosition vehicle;

        public String getId() { return id; }
        public TripUpdate getTripUpdate() { return tripUpdate; }
        public VehiclePosition getVehicle() { return vehicle; }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class TripUpdate {
        @JsonProperty("trip")
        private TripDescriptor trip;

        @JsonProperty("stop_time_update")
        private List<StopTimeUpdate> stopTimeUpdate;

        public TripDescriptor getTrip() { return trip; }
        public List<StopTimeUpdate> getStopTimeUpdate() { return stopTimeUpdate; }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class TripDescriptor {
        @JsonProperty("trip_id")
        private String tripId;

        @JsonProperty("route_id")
        private String routeId;

        public String getTripId() { return tripId; }
        public String getRouteId() { return routeId; }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class StopTimeUpdate {
        @JsonProperty("stop_id")
        private String stopId;

        @JsonProperty("arrival")
        private StopTimeEvent arrival;

        @JsonProperty("departure")
        private StopTimeEvent departure;

        public String getStopId() { return stopId; }
        public StopTimeEvent getArrival() { return arrival; }
        public StopTimeEvent getDeparture() { return departure; }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class StopTimeEvent {
        @JsonProperty("delay")
        private Integer delay; // en segundos

        @JsonProperty("time")
        private Long time;

        public Integer getDelay() { return delay; }
        public Long getTime() { return time; }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class VehiclePosition {
        @JsonProperty("trip")
        private TripDescriptor trip;

        @JsonProperty("position")
        private Position position;

        @JsonProperty("current_stop_sequence")
        private Integer currentStopSequence;

        @JsonProperty("stop_id")
        private String stopId;

        public TripDescriptor getTrip() { return trip; }
        public Position getPosition() { return position; }
        public Integer getCurrentStopSequence() { return currentStopSequence; }
        public String getStopId() { return stopId; }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Position {
        @JsonProperty("latitude")
        private Double latitude;

        @JsonProperty("longitude")
        private Double longitude;

        public Double getLatitude() { return latitude; }
        public Double getLongitude() { return longitude; }
    }
}