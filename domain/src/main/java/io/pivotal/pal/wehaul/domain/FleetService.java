package io.pivotal.pal.wehaul.domain;

import javax.transaction.Transactional;
import java.util.Collection;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

public class FleetService {

    private final TruckSizeChart truckSizeChart;
    private final TruckRepository truckRepository;
    private final TruckInspectionRepository truckInspectionRepository;

    public FleetService(TruckSizeChart truckSizeChart,
                        TruckRepository truckRepository,
                        TruckInspectionRepository truckInspectionRepository) {
        this.truckSizeChart = truckSizeChart;
        this.truckRepository = truckRepository;
        this.truckInspectionRepository = truckInspectionRepository;
    }

    public void buyTruck(Vin vin, int odometerReading, int truckLength) {
        TruckSize truckSize = truckSizeChart.getSizeByTruckLength(truckLength);

        Truck truck = new Truck(vin, odometerReading, truckSize, truckLength);

        truckRepository.save(truck);
    }

    @Transactional
    public void sendForInspection(Vin vin) {
        Truck truck = truckRepository.findOne(vin);

        if (truck == null) {
            throw new IllegalArgumentException(String.format("No truck found with VIN=%s", vin));
        }

        truck.sendForInspection();

        truckRepository.save(truck);
    }

    @Transactional
    public void returnFromInspection(Vin vin, String notes, int odometerReading) {
        Truck truck = truckRepository.findOne(vin);

        if (truck == null) {
            throw new IllegalArgumentException(String.format("No truck found with VIN=%s", vin));
        }
        truck.returnFromInspection(odometerReading);
        truckRepository.save(truck);

        TruckInspection truckInspection = new TruckInspection(vin, odometerReading, notes);
        truckInspectionRepository.save(truckInspection);
    }

    public Collection<Truck> findAll() {

        return StreamSupport.stream(truckRepository.findAll().spliterator(), false)
                .collect(Collectors.toList());
    }
}
