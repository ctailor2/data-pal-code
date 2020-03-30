package io.pivotal.pal.wehaul.domain;

import java.util.List;

public class TruckAllocationService {

    private final TruckRepository truckRepository;

    public TruckAllocationService(TruckRepository truckRepository) {
        this.truckRepository = truckRepository;
    }

    public Truck allocateTruck(TruckSize truckSize) {
        List<Truck> availableTrucks =
                truckRepository.findAllByTruckSizeAndStatus(truckSize, TruckStatus.RENTABLE);
        if (availableTrucks.size() < 1) {
            throw new IllegalStateException("No trucks available to rent");
        }
        return availableTrucks.get(0);
    }
}
