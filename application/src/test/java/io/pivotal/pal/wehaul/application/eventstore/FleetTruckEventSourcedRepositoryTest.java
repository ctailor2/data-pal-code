package io.pivotal.pal.wehaul.application.eventstore;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.pivotal.pal.wehaul.fleet.domain.FleetTruck;
import io.pivotal.pal.wehaul.fleet.domain.FleetTruckStatus;
import io.pivotal.pal.wehaul.fleet.domain.Vin;
import io.pivotal.pal.wehaul.fleet.domain.event.FleetTruckEvent;
import io.pivotal.pal.wehaul.fleet.domain.event.FleetTruckPurchased;
import io.pivotal.pal.wehaul.fleet.domain.event.FleetTruckReturnedFromInspection;
import io.pivotal.pal.wehaul.fleet.domain.event.FleetTruckSentForInspection;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;
import org.springframework.data.domain.Sort;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.stream.StreamSupport;

import static com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS;
import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static java.util.stream.Collectors.toList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.*;

@RunWith(MockitoJUnitRunner.class)
public class FleetTruckEventSourcedRepositoryTest {

    private static final ObjectMapper objectMapper = new ObjectMapper()
            .disable(WRITE_DATES_AS_TIMESTAMPS)
            .findAndRegisterModules();

    @Captor
    private ArgumentCaptor<Iterable<FleetTruckEventStoreEntity>> eventEntitiesCaptor;

    @Mock
    private FleetTruckEventStoreRepository mockEventStoreRepository;

    private FleetTruckEventSourcedRepository fleetTruckRepository;

    @Before
    public void setUp() {
        fleetTruckRepository = spy(new FleetTruckEventSourcedRepository(mockEventStoreRepository));
    }

    @Test
    public void save_savesToEventStore() throws IOException {
        FleetTruckEvent fleetTruckEvent1 = new FleetTruckPurchased("vin", FleetTruckStatus.INSPECTABLE.toString(), 0, 0);
        FleetTruckEvent fleetTruckEvent2 = new FleetTruckSentForInspection("vin", FleetTruckStatus.IN_INSPECTION.toString());
        List<FleetTruckEvent> eventsBeforeSave = Arrays.asList(fleetTruckEvent1, fleetTruckEvent2);

        FleetTruck fleetTruck = FleetTruck.fromEvents(eventsBeforeSave);

        fleetTruck.returnFromInspection("some-notes", 100);
        fleetTruckRepository.save(fleetTruck);


        verify(mockEventStoreRepository).save(eventEntitiesCaptor.capture());

        List<FleetTruckEventStoreEntity> savedEventEntities =
                StreamSupport.stream(eventEntitiesCaptor.getValue().spliterator(), false)
                        .collect(toList());

        assertThat(savedEventEntities).hasSize(1);
        FleetTruckEventStoreEntity savedEventEntity = savedEventEntities.get(0);

        assertThat(savedEventEntity.getKey().getVin()).isEqualTo(fleetTruck.getVin().getVin());
        assertThat(savedEventEntity.getKey().getVersion()).isEqualTo(2);
        assertThat(savedEventEntity.getEventClass()).isEqualTo(FleetTruckReturnedFromInspection.class);

        FleetTruckReturnedFromInspection savedEvent = objectMapper.readValue(
                savedEventEntity.getData(),
                FleetTruckReturnedFromInspection.class
        );

        FleetTruckEvent fleetTruckEvent3 = new FleetTruckReturnedFromInspection("vin", FleetTruckStatus.INSPECTABLE.toString(), 100, "some-notes");
        assertThat(savedEvent).isEqualToIgnoringGivenFields(fleetTruckEvent3, "createdDate");
    }

    @Test
    public void save_returnsVersionIncremented() {
        FleetTruckEvent fleetTruckEvent1 = new FleetTruckPurchased("vin", FleetTruckStatus.INSPECTABLE.toString(), 0, 0);
        FleetTruckEvent fleetTruckEvent2 = new FleetTruckSentForInspection("vin", FleetTruckStatus.IN_INSPECTION.toString());
        List<FleetTruckEvent> eventsBeforeSave = Arrays.asList(fleetTruckEvent1, fleetTruckEvent2);

        FleetTruck fleetTruck = FleetTruck.fromEvents(eventsBeforeSave);

        // stubs expected state of fleet truck for findOne invocation
        FleetTruckEvent fleetTruckEvent3 = new FleetTruckReturnedFromInspection("vin", FleetTruckStatus.INSPECTABLE.toString(), 100, "some-notes");
        List<FleetTruckEvent> eventsAfterSave = Arrays.asList(fleetTruckEvent1, fleetTruckEvent2, fleetTruckEvent3);
        FleetTruck expectedFleetTruckAfterSave = FleetTruck.fromEvents(eventsAfterSave);
        doReturn(expectedFleetTruckAfterSave).when(fleetTruckRepository).findOne(any());


        fleetTruck.returnFromInspection("some-notes", 100);
        FleetTruck savedFleetTruck = fleetTruckRepository.save(fleetTruck);


        verify(fleetTruckRepository).findOne(Vin.of("vin"));

        assertThat(savedFleetTruck.getVersion()).isEqualTo(2);
        assertThat(savedFleetTruck).isEqualToComparingFieldByField(expectedFleetTruckAfterSave);
    }

    @Test
    public void findOne() throws JsonProcessingException {
        FleetTruckPurchased existingEvent1 =
                new FleetTruckPurchased("some-vin", FleetTruckStatus.INSPECTABLE.toString(), 20, 1000);
        FleetTruckEventStoreEntity existingEventStoreEntity1 = new FleetTruckEventStoreEntity(
                new FleetTruckEventStoreEntityKey("some-vin", 0),
                FleetTruckPurchased.class,
                objectMapper.writeValueAsString(existingEvent1)
        );

        FleetTruckSentForInspection existingEvent2 =
                new FleetTruckSentForInspection("some-vin", FleetTruckStatus.IN_INSPECTION.toString());
        FleetTruckEventStoreEntity existingEventStoreEntity2 = new FleetTruckEventStoreEntity(
                new FleetTruckEventStoreEntityKey("some-vin", 1),
                FleetTruckSentForInspection.class,
                objectMapper.writeValueAsString(existingEvent2)
        );

        when(mockEventStoreRepository.findAllByKeyVinOrderByKeyVersion(any()))
                .thenReturn(asList(existingEventStoreEntity1, existingEventStoreEntity2));


        FleetTruck fleetTruck = fleetTruckRepository.findOne(Vin.of("some-vin"));


        assertThat(fleetTruck.getVin()).isEqualTo(Vin.of("some-vin"));
        assertThat(fleetTruck.getStatus()).isEqualTo(FleetTruckStatus.IN_INSPECTION);
        assertThat(fleetTruck.getOdometerReading()).isEqualTo(1000);
        assertThat(fleetTruck.getTruckLength()).isEqualTo(20);

        assertThat(fleetTruck.getInspections()).isEmpty();

        assertThat(fleetTruck.getDirtyEvents()).isEmpty();

        verify(mockEventStoreRepository).findAllByKeyVinOrderByKeyVersion("some-vin");
    }

    @Test
    public void findOne_notFound() {
        when(mockEventStoreRepository.findAllByKeyVinOrderByKeyVersion(any())).thenReturn(emptyList());

        FleetTruck found = fleetTruckRepository.findOne(Vin.of("bad-vin"));

        assertThat(found).isNull();
    }

    @Test
    public void findAll() throws JsonProcessingException {
        FleetTruckPurchased event1 = new FleetTruckPurchased("vin-1", FleetTruckStatus.INSPECTABLE.toString(), 0, 5);
        FleetTruckPurchased event2 = new FleetTruckPurchased("vin-2", FleetTruckStatus.INSPECTABLE.toString(), 0, 100);
        FleetTruckSentForInspection event3 = new FleetTruckSentForInspection("vin-2", FleetTruckStatus.IN_INSPECTION.toString());

        when(mockEventStoreRepository.findAll(any(Sort.class)))
                .thenReturn(asList(
                        new FleetTruckEventStoreEntity(new FleetTruckEventStoreEntityKey("vin-1", 0), FleetTruckPurchased.class, objectMapper.writeValueAsString(event1)),
                        new FleetTruckEventStoreEntity(new FleetTruckEventStoreEntityKey("vin-2", 0), FleetTruckPurchased.class, objectMapper.writeValueAsString(event2)),
                        new FleetTruckEventStoreEntity(new FleetTruckEventStoreEntityKey("vin-2", 1), FleetTruckSentForInspection.class, objectMapper.writeValueAsString(event3))
                ));


        List<FleetTruck> trucks = fleetTruckRepository.findAll();


        assertThat(trucks).hasSize(2);

        assertThat(trucks.get(0).getVin()).isEqualTo(Vin.of("vin-1"));
        assertThat(trucks.get(0).getStatus()).isEqualTo(FleetTruckStatus.INSPECTABLE);
        assertThat(trucks.get(0).getOdometerReading()).isEqualTo(5);

        assertThat(trucks.get(1).getVin()).isEqualTo(Vin.of("vin-2"));
        assertThat(trucks.get(1).getStatus()).isEqualTo(FleetTruckStatus.IN_INSPECTION);
        assertThat(trucks.get(1).getOdometerReading()).isEqualTo(100);
    }
}
