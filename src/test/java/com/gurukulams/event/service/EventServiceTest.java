
package com.gurukulams.event.service;

import com.gurukulams.event.model.Event;
import com.gurukulams.event.store.EventStore;
import com.gurukulams.event.util.TestUtil;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static com.gurukulams.event.store.EventStore.eventDate;
import static com.gurukulams.event.store.EventStore.id;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Locale;
import java.util.UUID;


class EventServiceTest {


    private static final String USERNAME_1 = "hari";
    private static final String USERNAME_2 = "hari2";
    private final EventService eventService;

    private final EventStore eventStore;
    private final List<String> categories ;
    private final List<String> tags ;

    EventServiceTest() {
        ValidatorFactory validatorFactory = Validation.buildDefaultValidatorFactory();
        Validator validator = validatorFactory.getValidator();
        this.eventService = new EventService(TestUtil.eventManager(), validator);
        this.eventStore = TestUtil.eventManager().getEventStore();
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
    void createInvalid() throws SQLException {
        Event newevent = anEvent();

        // Past Event Days ? - Not valid
        newevent.setEventDate(LocalDateTime.now().minusDays(2L));
        Assertions.assertThrows(ConstraintViolationException.class, () -> {
            eventService.create(categories, tags, USERNAME_1
                    , null, newevent);
        });

        // Beyond 20 Days ? - Not valid
        newevent.setEventDate(LocalDateTime.now().plusDays(21L));
        Assertions.assertThrows(ConstraintViolationException.class, () -> {
            eventService.create(categories, tags, USERNAME_1
                    , null, newevent);
        });


    }

    @Test
    void create() throws SQLException {
        final Event event = eventService.create(categories,tags, USERNAME_1
                , null, anEvent());
        Assertions.assertTrue(eventService.read(USERNAME_1, event.getId(), null).isPresent(), "Created Event");
    }

    @Test
    void createLocalized() throws SQLException {
        final Event event = eventService.create(categories,tags, USERNAME_1
                , Locale.GERMAN, anEvent());
        Assertions.assertTrue(eventService.read(USERNAME_1, event.getId(), Locale.GERMAN).isPresent(), "Created Localized Event");
        Assertions.assertTrue(eventService.read(USERNAME_1, event.getId(), null).isPresent(), "Created Event");
    }

    @Test
    void read() throws SQLException {
        final Event event = eventService.create(categories,tags, USERNAME_1,
                null, anEvent());
        Assertions.assertTrue(eventService.read(USERNAME_1, event.getId(), null).isPresent(),
                "Created Event");
    }

    @Test
    void update() throws SQLException {

        final Event event = eventService.create(categories,tags, USERNAME_1,
                null, anEvent());

        Event updatedEvent ;

        LocalDateTime eventDate = LocalDateTime.now().plusDays(4L).truncatedTo(ChronoUnit.SECONDS);

        event.setTitle("MyTitle2");
        event.setDescription("MyDescription2");
        event.setEventDate(eventDate);
        updatedEvent = eventService
                .update(event.getId(), USERNAME_1, null, event);
        Assertions.assertEquals("MyTitle2", updatedEvent.getTitle(), "Updated");

        Assertions.assertEquals("MyDescription2", updatedEvent.getDescription(), "Updated");
        Assertions.assertEquals(eventDate, updatedEvent.getEventDate(), "Updated");

        // Update With Another ID ? - Not valid
        Assertions.assertThrows(IllegalArgumentException.class, () -> {
            eventService
                    .update(UUID.randomUUID(), "priya", null, event);
        });
        // Update By Another User ? - Not valid
        Assertions.assertThrows(IllegalArgumentException.class, () -> {
            eventService
                    .update(event.getId(), USERNAME_2, null, event);
        });

        // Past Event Days ? - Not valid
        event.setEventDate(LocalDateTime.now().minusDays(2L));
        Assertions.assertThrows(ConstraintViolationException.class, () -> {
            eventService
                    .update(event.getId(), USERNAME_2, null, event);
        });

    }

    @Test
    void updateLocalized() throws SQLException {

        final Event event = eventService.create(categories,tags, USERNAME_1,
                null, anEvent());
        Event newEvent = anEvent();
        newEvent.setId(event.getId());
        newEvent.setTitle("HansiEvent");
        Event updatedEvent = eventService
                .update(event.getId(), USERNAME_1, Locale.GERMAN, newEvent);

        Assertions.assertEquals("HansiEvent", eventService.read(USERNAME_1, event.getId(), Locale.GERMAN).get().getTitle(), "Updated");
        Assertions.assertNotEquals("HansiEvent", eventService.read(USERNAME_1, event.getId(), null).get().getTitle(), "Updated");


        Assertions.assertThrows(IllegalArgumentException.class, () -> {
            eventService
                    .update(UUID.randomUUID(), USERNAME_1, null, newEvent);
        });
    }

    @Test
    void register() throws SQLException {

        final Event event = eventService.create(categories, tags, USERNAME_1, null,
                anEvent());

        // owner registering for his own event ? - Invalid
        Assertions.assertThrows(IllegalArgumentException.class, () -> {
            eventService.register(USERNAME_1, event.getId());
        });

        // registering for invalid event ? - Invalid
        Assertions.assertThrows(IllegalArgumentException.class, () -> {
            eventService.register(USERNAME_2, UUID.randomUUID());
        });
        Assertions.assertFalse(eventService.isRegistered(USERNAME_2, event.getId()));

        Assertions.assertTrue(eventService.register(USERNAME_2, event.getId()));

        Assertions.assertTrue(eventService.isRegistered(USERNAME_2, event.getId()));

        // registering again ? - Invalid
        Assertions.assertThrows(SQLException.class, () -> {
            eventService.register(USERNAME_2, event.getId());
        });
    }

    @Test
    void start() throws SQLException, MalformedURLException {

        final Event newEvent = anEvent();

        final Event event = eventService.create(categories, tags, USERNAME_1, null,
                newEvent);

        // Start with Non Owner.
        Assertions.assertThrows(IllegalArgumentException.class, () -> {
            eventService.start(USERNAME_2, event.getId(),
                    new URL("https://github.com/techatpark"));
        });

        // Start with Null URL.
        Assertions.assertThrows(IllegalArgumentException.class, () -> {
            eventService.start(USERNAME_1, event.getId(),
                    null);
        });

        // Start with Invalid ID.
        Assertions.assertThrows(IllegalArgumentException.class, () -> {
            eventService.start(USERNAME_1, UUID.randomUUID(),
                    new URL("https://github.com/techatpark"));
        });

        // Start Event Too Early ?.
        Assertions.assertThrows(IllegalArgumentException.class, () -> {
                    eventService.start(USERNAME_1, event.getId(),
                            new URL("https://github.com/techatpark"));
                });

        event.setEventDate(LocalDateTime.now().plusMinutes(4));
        eventService.update(event.getId(),USERNAME_1,null, event);
        Assertions.assertTrue(eventService.start(USERNAME_1, event.getId(),
                new URL("https://github.com/techatpark")));

        // Double Start ?
        Assertions.assertThrows(SQLException.class, () -> {
        eventService.start(USERNAME_1, event.getId(),
                new URL("https://github.com/techatpark"));
        });
    }

    @Test
    void join() throws SQLException, MalformedURLException {

        final Event event = eventService.create(categories, tags, USERNAME_1, null,
                anEvent());
        // Without Registration ? - Invalid
        Assertions.assertThrows(IllegalArgumentException.class, () -> {
            eventService.join(USERNAME_2, event.getId());
        });

        // Without Event ? - Invalid
        Assertions.assertThrows(IllegalArgumentException.class, () -> {
            eventService.join(USERNAME_2, UUID.randomUUID());
        });

        // Owner Joining Before Start
        Assertions.assertThrows(IllegalArgumentException.class, () -> {
            eventService.join(USERNAME_1, event.getId());
        });

        eventStore.update()
                .set(eventDate(LocalDateTime.now().minusMinutes(4)))
                .where(id().eq(event.getId()))
                .execute();
        eventService.start(USERNAME_1, event.getId(),
                new URL("https://github.com/techatpark"));
        // Owner Joining After Start
        Assertions.assertNotNull(eventService.join(USERNAME_1, event.getId()));

        eventService.register(USERNAME_2, event.getId());

        // Participant Joining
        Assertions.assertNotNull(eventService.join(USERNAME_2, event.getId()));

    }

    @Test
    void delete() throws SQLException {

        final Event event = eventService.create(categories,tags, USERNAME_1, null,
                anEvent());
        // Not a owner ? - Invalid
        Assertions.assertThrows(IllegalArgumentException.class, () -> {
            eventService.delete(USERNAME_2, event.getId());
        });

        // Not existing event ? - Invalid
        Assertions.assertThrows(IllegalArgumentException.class, () -> {
            eventService.delete(USERNAME_1, UUID.randomUUID());
        });

        eventService.delete(USERNAME_1, event.getId());
        Assertions.assertFalse(eventService.read(USERNAME_1, event.getId(), null).isPresent(), "Deleted Event");
    }


    @Test
    void listUserEvents() throws SQLException {
        listUserEvents(null);
        cleanUp();
        listUserEvents(Locale.GERMAN);
    }

    void listUserEvents(Locale locale) throws SQLException {
        final Event event = eventService.create(categories,tags, USERNAME_1, locale,
                anEvent());
        Event newEvent = anEvent();
        eventService.create(categories,tags, USERNAME_1, locale,
                newEvent);
        List<Event> events = eventService.list(USERNAME_1, locale);
        Assertions.assertEquals(2,  events.size());

        Assertions.assertEquals(0, eventService.list(USERNAME_2, locale).size());
        eventService.register(USERNAME_2, events.get(0).getId());
        Assertions.assertEquals(1, eventService.list(USERNAME_2, locale).size());
        eventService.create(categories,tags, USERNAME_2, locale,
                anEvent());
        Assertions.assertEquals(2, eventService.list(USERNAME_2, locale).size());
        eventService.register(USERNAME_2, events.get(1).getId());
        Assertions.assertEquals(3, eventService.list(USERNAME_2, locale).size());
    }

    @Test
    void list() throws SQLException {

        final Event event = eventService.create(categories,tags, USERNAME_1, null,
                anEvent());
        Event newEvent = anEvent();
        eventService.create(categories,tags, USERNAME_1, null,
                newEvent);
        List<Event> listofEvents = eventService.list(USERNAME_1, null, categories);
        Assertions.assertEquals(2, listofEvents.size());


        // Check it ignores past events

        int updated = this.eventStore.update()
                .set(eventDate(LocalDateTime.now().minusDays(5L)))
                .where(id().eq(listofEvents.get(0).getId()))
                .execute();
        listofEvents = eventService.list(USERNAME_2, null, categories);
        Assertions.assertEquals(1, listofEvents.size());

    }

    @Test
    void listLocalized() throws SQLException {

        final Event event = eventService.create(categories,tags, USERNAME_1, Locale.GERMAN,
                anEvent());
        Event newEvent = anEvent();
        eventService.create(categories,tags, USERNAME_1, null,
                newEvent);
        List<Event> listofEvents = eventService.list(USERNAME_1, null, categories);
        Assertions.assertEquals(2, listofEvents.size());

        listofEvents = eventService.list(USERNAME_1, Locale.GERMAN, categories);
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
        event.setDescription("HariDescription");
        event.setEventDate(LocalDateTime.now().plusDays(2L));
        return event;
    }
}
