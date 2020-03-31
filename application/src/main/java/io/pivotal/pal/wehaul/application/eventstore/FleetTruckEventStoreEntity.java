package io.pivotal.pal.wehaul.application.eventstore;

import io.pivotal.pal.wehaul.fleet.domain.event.FleetTruckEvent;

import javax.persistence.Column;
import javax.persistence.EmbeddedId;
import javax.persistence.Entity;
import javax.persistence.Table;
import java.util.Objects;

@Table(name = "fleet_truck_event_store")
@Entity
public class FleetTruckEventStoreEntity {

    @EmbeddedId
    private FleetTruckEventStoreEntityKey key;

    @Column
    private Class<? extends FleetTruckEvent> eventClass;

    @Column(length = 4096)
    private String data;

    public FleetTruckEventStoreEntity(FleetTruckEventStoreEntityKey key, Class<? extends FleetTruckEvent> eventClass, String data) {
        this.key = key;
        this.eventClass = eventClass;
        this.data = data;
    }

    FleetTruckEventStoreEntity() {
        // default constructor for JPA
    }

    public FleetTruckEventStoreEntityKey getKey() {
        return key;
    }

    public Class<? extends FleetTruckEvent> getEventClass() {
        return eventClass;
    }

    public String getData() {
        return data;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        FleetTruckEventStoreEntity that = (FleetTruckEventStoreEntity) o;
        return Objects.equals(key, that.key);
    }

    @Override
    public int hashCode() {
        return Objects.hash(key);
    }

    @Override
    public String toString() {
        return "FleetTruckEventStoreEntity{" +
                "key=" + key +
                ", eventClass=" + eventClass +
                ", data='" + data + '\'' +
                '}';
    }
}
