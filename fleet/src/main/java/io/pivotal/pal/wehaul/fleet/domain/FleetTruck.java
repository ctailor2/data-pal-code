package io.pivotal.pal.wehaul.fleet.domain;

import io.pivotal.pal.wehaul.fleet.domain.event.*;
import org.springframework.data.domain.AbstractAggregateRoot;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class FleetTruck extends AbstractAggregateRoot {

    private Vin vin;
    private int version;
    private FleetTruckStatus status;
    private Integer odometerReading;
    private Integer truckLength;
    private List<TruckInspection> inspections = new ArrayList<>();


    private FleetTruck() {
        // construct via static initializer
    }

    public FleetTruck(Vin vin, Integer odometerReading, Integer truckLength) {
        if (odometerReading < 0) {
            throw new IllegalArgumentException("Cannot buy a truck with negative odometer reading");
        }

        FleetTruckPurchased event = new FleetTruckPurchased(
                vin.getVin(),
                FleetTruckStatus.INSPECTABLE.toString(),
                truckLength,
                odometerReading
        );

        handleEvent(event);

        this.version = 0;
    }

    public static FleetTruck fromEvents(List<FleetTruckEvent> events) {
        final FleetTruck fleetTruck = new FleetTruck();
        events.forEach(event -> fleetTruck.applyEvent(event));
        fleetTruck.version = events.size() - 1;
        fleetTruck.clearDomainEvents();
        return fleetTruck;
    }

    private void applyEvent(FleetTruckEvent event) {
        if (event instanceof FleetTruckPurchased) {
            handleEvent((FleetTruckPurchased) event);
        } else if (event instanceof FleetTruckSentForInspection) {
            handleEvent((FleetTruckSentForInspection) event);
        } else if (event instanceof FleetTruckReturnedFromInspection) {
            handleEvent((FleetTruckReturnedFromInspection) event);
        } else if (event instanceof FleetTruckRemovedFromYard) {
            handleEvent((FleetTruckRemovedFromYard) event);
        } else if (event instanceof FleetTruckReturnedToYard) {
            handleEvent((FleetTruckReturnedToYard) event);
        } else {
            throw new IllegalArgumentException("Unknown event type: " + event.getClass());
        }
    }

    private void handleEvent(FleetTruckPurchased event) {
        this.vin = Vin.of(event.getVin());
        this.status = FleetTruckStatus.valueOf(event.getStatus());
        this.odometerReading = event.getOdometerReading();
        this.truckLength = event.getTruckLength();

        this.registerEvent(event);
    }

    public void returnFromInspection(String notes, int odometerReading) {
        FleetTruckReturnedFromInspection event = new FleetTruckReturnedFromInspection(
                this.vin.getVin(),
                FleetTruckStatus.INSPECTABLE.toString(),
                odometerReading,
                notes
        );
        handleEvent(event);
    }

    private void handleEvent(FleetTruckReturnedFromInspection event) {
        if (this.status != FleetTruckStatus.IN_INSPECTION) {
            throw new IllegalStateException("Truck is not currently in inspection");
        }
        if (this.odometerReading > event.getOdometerReading()) {
            throw new IllegalArgumentException("Odometer reading cannot be less than previous reading");
        }


        this.status = FleetTruckStatus.valueOf(event.getStatus());
        this.odometerReading = event.getOdometerReading();

        TruckInspection truckInspection =
                new TruckInspection(this.vin, event.getOdometerReading(), event.getNotes());
        this.inspections.add(truckInspection);

        this.registerEvent(event);
    }

    public void sendForInspection() {
        FleetTruckSentForInspection event = new FleetTruckSentForInspection(
                this.vin.getVin(),
                FleetTruckStatus.IN_INSPECTION.toString()
        );
        handleEvent(event);
    }

    private void handleEvent(FleetTruckSentForInspection event) {
        if (this.status != FleetTruckStatus.INSPECTABLE) {
            throw new IllegalStateException("Truck cannot be inspected");
        }
        this.status = FleetTruckStatus.valueOf(event.getStatus());
        this.registerEvent(event);
    }

    public void removeFromYard() {
        FleetTruckRemovedFromYard event = new FleetTruckRemovedFromYard(
                this.vin.getVin(),
                FleetTruckStatus.NOT_INSPECTABLE.toString()
        );
        handleEvent(event);
    }

    private void handleEvent(FleetTruckRemovedFromYard event) {
        if (this.status != FleetTruckStatus.INSPECTABLE) {
            throw new IllegalStateException("Cannot remove truck, currently in inspection");
        }

        this.status = FleetTruckStatus.valueOf(event.getStatus());

        this.registerEvent(event);
    }

    public void returnToYard(int distanceTraveled) {
        FleetTruckReturnedToYard event = new FleetTruckReturnedToYard(
                this.vin.getVin(),
                FleetTruckStatus.INSPECTABLE.toString(),
                distanceTraveled
        );
        handleEvent(event);
    }

    private void handleEvent(FleetTruckReturnedToYard event) {
        if (this.status != FleetTruckStatus.NOT_INSPECTABLE) {
            throw new IllegalStateException("Truck is not currently out of yard");
        }
        if (event.getDistanceTraveled() < 0) {
            throw new IllegalArgumentException("Distance traveled cannot be negative");
        }


        this.status = FleetTruckStatus.valueOf(event.getStatus());
        this.odometerReading += event.getDistanceTraveled();

        this.registerEvent(event);

    }

    public List<FleetTruckEvent> getDirtyEvents() {
        return domainEvents().stream()
                .map(obj -> (FleetTruckEvent) obj)
                .collect(Collectors.toList());
    }

    public Vin getVin() {
        return vin;
    }

    public int getVersion() {
        return version;
    }

    public FleetTruckStatus getStatus() {
        return status;
    }

    public Integer getOdometerReading() {
        return odometerReading;
    }

    public Integer getTruckLength() {
        return truckLength;
    }

    public List<TruckInspection> getInspections() {
        return inspections;
    }

    @Override
    public String toString() {
        return "FleetTruck{" +
                "vin=" + vin +
                ", version=" + version +
                ", status=" + status +
                ", odometerReading=" + odometerReading +
                ", truckLength=" + truckLength +
                ", inspections=" + inspections +
                '}';
    }
}
