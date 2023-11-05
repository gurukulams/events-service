
package com.gurukulams.event.service;

import com.gurukulams.event.model.Event;
import com.gurukulams.event.util.TestUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Locale;
import java.util.UUID;


class EventServiceTest {


    private final EventService eventService;
    private final List<String> categories ;
    private final List<String> tags ;

    EventServiceTest() {
        this.eventService = new EventService(TestUtil.eventManager());
        categories = List.of("c1", "c2");
        tags = List.of("t1", "t2");
    }

    /**
     * Before.
     *
     * @throws IOException the io exception
     */
    @BeforeEach
    void before() throws SQLException {
        cleanUp();
    }

    /**
     * After.
     */
    @AfterEach
    void after() throws SQLException {
        cleanUp();
    }

    private void cleanUp() throws SQLException {
        eventService.delete();
    }


    @Test
    void create() throws SQLException {
        final Event event = eventService.create(categories,tags,"hari"
                , null, anEvent());
        Assertions.assertTrue(eventService.read("hari", event.getId(), null).isPresent(), "Created Event");
    }

    @Test
    void createLocalized() throws SQLException {
        final Event event = eventService.create(categories,tags,"hari"
                , Locale.GERMAN, anEvent());
        Assertions.assertTrue(eventService.read("hari", event.getId(), Locale.GERMAN).isPresent(), "Created Localized Event");
        Assertions.assertTrue(eventService.read("hari", event.getId(), null).isPresent(), "Created Event");
    }

    @Test
    void read() throws SQLException {
        final Event event = eventService.create(categories,tags,"hari",
                null, anEvent());
        Assertions.assertTrue(eventService.read("hari", event.getId(), null).isPresent(),
                "Created Event");
    }

    @Test
    void update() throws SQLException {

        final Event event = eventService.create(categories,tags,"hari",
                null, anEvent());

        Event updatedEvent ;

        event.setTitle("MyTitle");
        updatedEvent = eventService
                .update(event.getId(), "hari", null, event);
        Assertions.assertEquals("MyTitle", updatedEvent.getTitle(), "Updated");

        event.setDescription("MyDescription");
        updatedEvent = eventService
                .update(event.getId(), "hari", null, event);
        Assertions.assertEquals("MyDescription", updatedEvent.getDescription(), "Updated");

        LocalDateTime eventDate = LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS);;
        event.setEventDate(eventDate);
        updatedEvent = eventService
                .update(event.getId(), "hari", null, event);
        Assertions.assertEquals(eventDate, updatedEvent.getEventDate(), "Updated");

        Assertions.assertThrows(IllegalArgumentException.class, () -> {
            eventService
                    .update(UUID.randomUUID(), "priya", null, event);
        });
    }

    @Test
    void updateLocalized() throws SQLException {

        final Event event = eventService.create(categories,tags,"hari",
                null, anEvent());
        Event newEvent = anEvent();
        newEvent.setId(event.getId());
        newEvent.setTitle("HansiEvent");
        Event updatedEvent = eventService
                .update(event.getId(), "priya", Locale.GERMAN, newEvent);

        Assertions.assertEquals("HansiEvent", eventService.read("mani", event.getId(), Locale.GERMAN).get().getTitle(), "Updated");
        Assertions.assertNotEquals("HansiEvent", eventService.read("mani", event.getId(), null).get().getTitle(), "Updated");


        Assertions.assertThrows(IllegalArgumentException.class, () -> {
            eventService
                    .update(UUID.randomUUID(), "priya", null, newEvent);
        });
    }

    @Test
    void delete() throws SQLException {

        final Event event = eventService.create(categories,tags,"hari", null,
                anEvent());
        eventService.delete("mani", event.getId());
        Assertions.assertFalse(eventService.read("mani", event.getId(), null).isPresent(), "Deleted Event");
    }

    @Test
    void list() throws SQLException {

        final Event event = eventService.create(categories,tags,"hari", null,
                anEvent());
        Event newEvent = anEvent();
        eventService.create(categories,tags,"hari", null,
                newEvent);
        List<Event> listofEvents = eventService.list("hari", null, categories);
        Assertions.assertEquals(2, listofEvents.size());

    }

    @Test
    void listLocalized() throws SQLException {

        final Event event = eventService.create(categories,tags,"hari", Locale.GERMAN,
                anEvent());
        Event newEvent = anEvent();
        eventService.create(categories,tags,"hari", null,
                newEvent);
        List<Event> listofEvents = eventService.list("hari", null, categories);
        Assertions.assertEquals(2, listofEvents.size());

        listofEvents = eventService.list("hari", Locale.GERMAN, categories);
        Assertions.assertEquals(2, listofEvents.size());

    }


    /**
     * Gets practice.
     *
     * @return the practice
     */
    Event anEvent() {
        Event event = new Event();
        event.setId(UUID.randomUUID());
        event.setTitle("HariEvent");
        event.setEventDate(LocalDateTime.now().minusDays(2L));
        return event;
    }
}
