package io.pivotal.pal.wehaul.fleet.domain;

import java.util.Collection;

public class FleetQueryService {

    private final FleetTruckSnapshotRepository fleetTruckSnapshotRepository;

    public FleetQueryService(FleetTruckSnapshotRepository fleetTruckSnapshotRepository) {
        this.fleetTruckSnapshotRepository = fleetTruckSnapshotRepository;
    }

    public Collection<FleetTruckSnapshot> findAll() {
        return fleetTruckSnapshotRepository.findAll();
    }
}
