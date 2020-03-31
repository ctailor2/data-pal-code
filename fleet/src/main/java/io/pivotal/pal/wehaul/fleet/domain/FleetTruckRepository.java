package io.pivotal.pal.wehaul.fleet.domain;

import org.springframework.data.repository.NoRepositoryBean;
import org.springframework.data.repository.Repository;

import java.util.List;

@NoRepositoryBean
public interface FleetTruckRepository extends Repository<FleetTruck, Vin> {
    FleetTruck save(FleetTruck fleetTruck);

    FleetTruck findOne(Vin vin);
}
