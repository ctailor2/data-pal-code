package io.pivotal.pal.wehaul.domain;

import javax.transaction.Transactional;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

public class RentalService {

    private final TruckAllocationService truckAllocationService;
    private final RentalRepository rentalRepository;
    private final TruckRepository truckRepository;

    public RentalService(TruckAllocationService truckAllocationService,
                         RentalRepository rentalRepository,
                         TruckRepository truckRepository) {
        this.truckAllocationService = truckAllocationService;
        this.rentalRepository = rentalRepository;
        this.truckRepository = truckRepository;
    }

    @Transactional
    public void create(String customerName, TruckSize truckSize) {
        Truck truck = truckAllocationService.allocateTruck(truckSize);

        truck.reserve();
        truckRepository.save(truck);

        Rental rental = new Rental(customerName, truck.getVin());
        rentalRepository.save(rental);
    }

    @Transactional
    public void pickUp(ConfirmationNumber confirmationNumber) {
        Rental rental = rentalRepository.findOne(confirmationNumber);
        if (rental == null) {
            throw new IllegalArgumentException(String.format("No rental found for id=%s", confirmationNumber));
        }
        rental.pickUp();

        rentalRepository.save(rental);

        Truck truck = truckRepository.findOne(rental.getTruckVin());
        truck.pickUp();
        truckRepository.save(truck);
    }

    @Transactional
    public void dropOff(ConfirmationNumber confirmationNumber, int distanceTraveled) {
        Rental rental = rentalRepository.findOne(confirmationNumber);
        if (rental == null) {
            throw new IllegalArgumentException(String.format("No rental found for id=%s", confirmationNumber));
        }
        rental.dropOff(distanceTraveled);

        rentalRepository.save(rental);

        Vin vin = rental.getTruckVin();
        Truck truck = truckRepository.findOne(vin);

        int odometerReading = truck.getOdometerReading() + distanceTraveled;

        truck.returnToService(odometerReading);

        truckRepository.save(truck);
    }

    public Collection<Rental> findAll() {

        return StreamSupport.stream(rentalRepository.findAll().spliterator(), false)
                .collect(Collectors.toList());
    }
}
