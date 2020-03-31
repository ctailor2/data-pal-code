package io.pivotal.pal.wehaul.application.eventstore;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import io.pivotal.pal.wehaul.fleet.domain.FleetTruck;
import io.pivotal.pal.wehaul.fleet.domain.FleetTruckRepository;
import io.pivotal.pal.wehaul.fleet.domain.Vin;
import io.pivotal.pal.wehaul.fleet.domain.event.FleetTruckEvent;
import org.springframework.data.domain.Sort;
import org.springframework.data.repository.NoRepositoryBean;

import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

@NoRepositoryBean
public class FleetTruckEventSourcedRepository implements FleetTruckRepository {

    private static final ObjectMapper objectMapper = new ObjectMapper()
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .findAndRegisterModules();

    private final FleetTruckEventStoreRepository eventStoreRepository;

    public FleetTruckEventSourcedRepository(FleetTruckEventStoreRepository eventStoreRepository) {
        this.eventStoreRepository = eventStoreRepository;
    }

    @Override
    public FleetTruck save(FleetTruck fleetTruck) {
        List<FleetTruckEventStoreEntity> fleetTruckEventStoreEntities = mapEventToEntities(fleetTruck.getDirtyEvents(), fleetTruck.getVersion());
        eventStoreRepository.save(fleetTruckEventStoreEntities);
        return findOne(fleetTruck.getVin());
    }

    @Override
    public FleetTruck findOne(Vin vin) {
        List<FleetTruckEventStoreEntity> fleetTruckEventStoreEntities = eventStoreRepository.findAllByKeyVinOrderByKeyVersion(vin.getVin());
        if (fleetTruckEventStoreEntities.isEmpty()) {
            return null;
        }
        List<FleetTruckEvent> fleetTruckEvents = mapEntitiesToEvents(fleetTruckEventStoreEntities);
        return FleetTruck.fromEvents(fleetTruckEvents);
    }

    @Override
    public List<FleetTruck> findAll() {
        Map<String, List<FleetTruckEventStoreEntity>> eventEntitiesByVin =
                eventStoreRepository.findAll(new Sort(Sort.Direction.ASC, "key.vin", "key.version"))
                        .stream()
                        .collect(Collectors.groupingBy(eventEntity -> eventEntity.getKey().getVin()));

        return eventEntitiesByVin.entrySet()
                .stream()
                .map(eventEntities -> mapEntitiesToEvents(eventEntities.getValue()))
                .map(FleetTruck::fromEvents)
                .sorted(Comparator.comparing(truck -> truck.getVin().getVin()))
                .collect(Collectors.toList());
    }

    private List<FleetTruckEventStoreEntity> mapEventToEntities(List<FleetTruckEvent> events, Integer versionStart) {
        return IntStream.range(0, events.size())
                .mapToObj(i -> {
                    FleetTruckEvent fleetTruckEvent = events.get(i);
                        return new FleetTruckEventStoreEntity(
                                new FleetTruckEventStoreEntityKey(fleetTruckEvent.getVin(), versionStart + 1 + i),
                                fleetTruckEvent.getClass(),
                                serializeEvent(fleetTruckEvent));
                })
                .collect(Collectors.toList());
    }

    private List<FleetTruckEvent> mapEntitiesToEvents(List<FleetTruckEventStoreEntity> eventEntities) {
        return eventEntities.stream()
                .map(this::deserializeEvent)
                .collect(Collectors.toList());
    }

    private String serializeEvent(FleetTruckEvent event) {
        try {
            return objectMapper.writeValueAsString(event);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }

    private FleetTruckEvent deserializeEvent(FleetTruckEventStoreEntity eventEntity) {
        try {
            return objectMapper.readValue(eventEntity.getData(), eventEntity.getEventClass());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
